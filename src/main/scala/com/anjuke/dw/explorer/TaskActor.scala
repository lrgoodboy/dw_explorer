package com.anjuke.dw.explorer

import akka.event.Logging
import akka.actor.{Actor, ActorSystem}
import dispatch._
import dispatch.Defaults._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.anjuke.dw.explorer.models.{Task, User}
import java.io.FileWriter
import java.util.concurrent.TimeUnit
import java.sql.Timestamp
import com.anjuke.dw.explorer.util.Config
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.{Await, TimeoutException}

case class TaskEvent(val task: Task)

object TaskActor {

  val HIVE_SERVER_URL = Config("service", "dw.hiveserver.url")
  val TASK_FOLDER = "/data2/dw_explorer/query/task"

  def outputFile(taskId: Long) = s"$TASK_FOLDER/query_task_$taskId.out"
  def errorFile(taskId: Long) = s"$TASK_FOLDER/query_task_$taskId.err"

}

class TaskActor(actorSystem: ActorSystem) extends Actor {

  import TaskActor._

  implicit val formats = DefaultFormats

  val logger = Logging(context.system, this)
  val hiveserver = actorSystem.actorFor(Config("service", "dw.hiveserver.akka.url"))
  implicit val timeout = Timeout(2 hours)

  override def preStart = {
    logger.info("taskActor started")
  }

  override def postStop = {
    logger.info("taskActor stopped")
  }

  def receive = {
    case taskId: Long => process(taskId)
    case _ => logger.debug("Unkown message.")
  }

  def process(taskId: Long) {

    val task = Task.lookup(taskId) match {
      case Some(task) => task
      case None =>
        logger.error("Invalid task id: " + taskId)
        return
    }

    logger.info("Processing task id: " + taskId)
    Task.updateStatus(task.id, Task.STATUS_RUNNING)
    publishTask(task.id)

    try {
      execute(task)
      Task.updateStatus(task.id, Task.STATUS_OK, duration = Some(calcDuration(task.created)))
      logger.info(s"Task ${task.id} finished.")
    } catch {
      case e: Exception =>
        Task.updateStatus(task.id, Task.STATUS_ERROR)
        logger.error(e, s"Task ${task.id} failed.")
        log2file(errorFile(task.id), e.getMessage)
    }

    publishTask(task.id)
  }

  private def publishTask(id: Long): Unit = {
    val task = Task.lookup(id).get
    actorSystem.eventStream.publish(TaskEvent(task))
  }

  private def calcDuration(created: Timestamp) = {
    val durationMilli = System.currentTimeMillis - created.getTime
    (durationMilli / 1000).asInstanceOf[Int]
  }

  def execute(task: Task) {

    val user = User.lookup(task.userId).get
    val prefix = s"${user.username}-${task.id}"

    for (sql <- parseQueries(task.queries, user)) {
      executeUpdate(task.id, prefix, sql)
    }

  }

  def executeUpdate(taskId: Long, prefix: String, sql: String): Unit = {

    // enqueue
    val enqueueFuture = hiveserver ? compact(render(("action" -> "enqueue") ~ ("query" -> sql) ~ ("prefix" -> prefix)))

    val remoteTaskIdFuture = enqueueFuture.mapTo[String] map { resultJson =>
      val result = parse(resultJson)
      result \ "status" match {
        case JString("ok") => (result \ "id").extract[Long]
        case _ => throw new Exception("Fail to submit task - " + (result \ "msg").extractOrElse[String]("unknown reason"))
      }
    }

    val remoteTaskId = remoteTaskIdFuture()
    logger.info(s"Remote task submmitted, id: $remoteTaskId")

    // execute
    val executeFuture = hiveserver ? compact(render(("action" -> "execute") ~ ("id" -> remoteTaskId)))
    val finishFuture = executeFuture.mapTo[String] map { resultJson =>
      val result = parse(resultJson)
      result \ "status" match {
        case JString("ok") => {
          result \ "taskStatus" match {
            case JString("ok") => {

              // read standard output
              val outputWriter = new FileWriter(outputFile(taskId), true)

              val outputReq = dispatch.url(HIVE_SERVER_URL) / "task" / "output" / remoteTaskId
              val outputFuture = Http(outputReq > as.stream.Lines(line => {
                outputWriter.write(line)
                outputWriter.write("\n")
              }))
              outputFuture()

              outputWriter.close
            }

            case JString("error") => throw new Exception((result \ "taskErrorMessage").extract[String])
            case _ => throw new Exception("Unknown task status.")
          }
        }

        case _ => throw new Exception("Fail to execute task - " + (result \ "msg").extractOrElse[String]("unknown reason"))
      }
    }

    // wait
    while (!Task.isInterrupted(taskId)) {
      try {
        Await.result(finishFuture, 1 second)
        return
      } catch {
        case _: TimeoutException =>
      }
    }

    // interrupted
    val cancelReq = dispatch.url(HIVE_SERVER_URL) / "task" / "cancel" / remoteTaskId
    val cancelRes = Http(cancelReq OK as.String)
    cancelRes()
    throw new Exception("Task is interrupted.")
  }

  private def log2file(file: String, log: String) {
    val fw = new FileWriter(file, true)
    fw.write(log)
    fw.close
  }

  private def normalizeQueries(queries: String) = {
    "(?s)/\\*.*?\\*/".r.replaceAllIn(queries, "").split(";").map(_.trim).filter(_.nonEmpty)
  }

  private def checkPrivilege(user: User, database: String) {
    if (user.isRole(User.ROLE_DW)) {
      return
    } else if (user.isRole(User.ROLE_BI)) {
      if (Seq("dw_db_temp", "dw_db_test") contains database) {
        return
      }
    }
    throw new Exception("Access denied.")
  }

  private def parseQueries(queries: String, user: User) = {

    val ptrnBuffer = "(?i)^(SET|ADD\\s+JAR|CREATE\\s+TEMPORARY\\s+FUNCTION|USE)\\s+".r
    val ptrnExport = "(?i)^EXPORT\\s+(HIVE|MYSQL)\\s+\\w+\\.\\w+\\s+TO\\s+(MYSQL|HIVE)\\s+(\\w+)\\.\\w+".r
    val ptrnManipulateDatabase = "(?i)^(CREATE|DROP|ALTER)\\s+(DATABASE|SCHEMA)\\s+".r
    val ptrnCreateTable = "(?i)^CREATE\\s+(EXTERNAL\\s+)?TABLE\\s+(IF\\s+NOT\\s+EXISTS\\s+)?((\\w+)\\.)?\\w+".r
    val ptrnDropTable = "(?i)^DROP\\s+TABLE\\s+(IF\\s+EXISTS\\s+)?((\\w+)\\.)?\\w+".r
    val ptrnAlterTable = "(?i)^ALTER\\s+TABLE\\s+".r

    val ptrnChangeDatabase = "(?i)^USE\\s+(\\w+)".r
    var currentDatabase = "default"

    val buffer = new StringBuilder
    val fixedBuffer = "SET hive.mapred.mode = strict;\nSET hive.cli.print.header = true;\n"

    def parseQuery(sql: String): Option[String] = {

      currentDatabase = ptrnChangeDatabase.findFirstMatchIn(sql) match {
        case Some(m) => m.group(1)
        case None => currentDatabase
      }

      if (ptrnBuffer.findFirstIn(sql).nonEmpty) {
        buffer ++= sql + ";\n"
        return None
      }

      ptrnExport.findFirstMatchIn(sql) match {
        case Some(m) =>
          checkPrivilege(user, m.group(3))
          return Some(sql)
        case None =>
      }

      if (ptrnManipulateDatabase.findFirstIn(sql).nonEmpty) {
        throw new Exception("Unable to manipulate database.")
      }

      ptrnCreateTable.findFirstMatchIn(sql) match {
        case Some(m) => checkPrivilege(user, if (m.group(4) != null) m.group(4) else currentDatabase)
        case None =>
      }

      ptrnDropTable.findFirstMatchIn(sql) match {
        case Some(m) => checkPrivilege(user, if (m.group(3) != null) m.group(3) else currentDatabase)
        case None =>
      }

      if (ptrnAlterTable.findFirstIn(sql).nonEmpty) {
        checkPrivilege(user, currentDatabase)
      }

      Some(buffer.toString + fixedBuffer + sql)
    }

    normalizeQueries(queries).map(parseQuery _).filter(_.nonEmpty).map(_.get)

  }

}
