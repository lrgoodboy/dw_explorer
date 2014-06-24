package com.anjuke.dw.explorer

import akka.event.Logging
import akka.actor.Actor
import dispatch._
import dispatch.Defaults._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.anjuke.dw.explorer.models.{Task, User}
import java.io.FileWriter
import java.util.concurrent.TimeUnit
import java.sql.Timestamp

object TaskActor {

  val HIVE_SERVER_URL = "http://10.20.8.70:8080/hive-server/api"
  val TASK_FOLDER = "/data2/dw_explorer/query/task"

  def outputFile(taskId: Long) = s"$TASK_FOLDER/query_task_$taskId.out"
  def errorFile(taskId: Long) = s"$TASK_FOLDER/query_task_$taskId.err"

}

class TaskActor extends Actor {

  import TaskActor._

  implicit val formats = DefaultFormats

  val logger = Logging(context.system, this)

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

  }

  private def calcDuration(created: Timestamp) = {
    val durationMilli = System.currentTimeMillis - created.getTime
    (durationMilli / 1000).asInstanceOf[Int]
  }

  def execute(task: Task) {

    val prefix = s"${User.lookup(task.userId).get.username}-${task.id}"

    val ptrnBuffer = "(?i)^(SET|ADD\\s+JAR|CREATE\\s+TEMPORARY\\s+FUNCTION|USE)\\s+".r
    val ptrnExport = "(?i)^EXPORT\\s+(HIVE|MYSQL)\\s+".r

    val buffer = new StringBuilder("SET hive.mapred.mode = strict;\nSET hive.cli.print.header = true;\n")

    for (sql <- normalizeQueries(task.queries)) {

      if (ptrnBuffer.findFirstIn(sql).nonEmpty) {
        buffer ++= sql + ";\n"
      } else if (ptrnExport.findFirstIn(sql).nonEmpty) {
        executeUpdate(task.id, prefix, sql)
      } else {
        executeUpdate(task.id, prefix, buffer.toString + sql)
      }

    }

    Task.updateStatus(task.id, Task.STATUS_OK)
  }

  def executeUpdate(taskId: Long, prefix: String, sql: String) {

    // send request
    val submitReq = (dispatch.url(HIVE_SERVER_URL) / "task" / "submit").POST
        .setContentType("application/json", "UTF-8")
        .setBody(compact(render(Map("query" -> sql, "prefix" -> prefix))))

    val remoteTaskIdFuture = Http(submitReq OK as.String).map(resultJson => {
      val result = parse(resultJson)
      result \ "status" match {
        case JString("ok") => (result \ "id").extract[Long]
        case _ => throw new Exception("Fail to submit task - " + (result \ "msg").extractOrElse[String]("unknown reason"))
      }
    })

    val remoteTaskId = remoteTaskIdFuture()
    logger.info(s"Remote task submmitted, id: $remoteTaskId")

    // wait for completion
    while (!Thread.interrupted) {

      // check task status
      if (Task.isInterrupted(taskId)) {
        val cancelReq = dispatch.url(HIVE_SERVER_URL) / "task" / "cancel" / remoteTaskId
        val cancelRes = Http(cancelReq OK as.String)
        cancelRes()
        throw new Exception("Task is interrupted.")
      }

      // check remote task status
      val statusReq = dispatch.url(HIVE_SERVER_URL) / "task" / "status" / remoteTaskId

      val remoteStatusFuture = Http(statusReq OK as.String).map(resultJson => {
        val result = parse(resultJson)
        result \ "status" match {
          case JString("ok") =>
            result \ "taskStatus" match {
              case JString("ok") =>

                // read standard output
                val outputWriter = new FileWriter(outputFile(taskId), true)

                val outputReq = dispatch.url(HIVE_SERVER_URL) / "task" / "output" / remoteTaskId
                val outputFuture = Http(outputReq > as.stream.Lines(line => {
                  outputWriter.write(line)
                  outputWriter.write("\n")
                }))
                outputFuture()

                outputWriter.close

                'break

              case JString("error") => throw new Exception((result \ "taskErrorMessage").extract[String])
              case _ => 'continue
            }

          case _ => throw new Exception("Fail to fetch task status - " + (result \ "msg").extractOrElse[String]("unknown reason"))
        }
      })

      remoteStatusFuture() match {
        case 'break => return
        case _ => TimeUnit.SECONDS.sleep(3)
      }

    }
  }

  private def log2file(file: String, log: String) {
    val fw = new FileWriter(file, true)
    fw.write(log)
    fw.close
  }

  private def normalizeQueries(queries: String) = {
    "(?s)/\\*.*?\\*/".r.replaceAllIn(queries, "").split(";").map(_.trim).filter(_.nonEmpty)
  }

}
