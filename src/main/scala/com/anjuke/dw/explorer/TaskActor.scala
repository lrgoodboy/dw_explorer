package com.anjuke.dw.explorer

import akka.event.Logging
import akka.actor.Actor
import dispatch._
import dispatch.Defaults._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.anjuke.dw.explorer.models.Task
import java.io.FileWriter
import java.util.concurrent.TimeUnit
import java.sql.Timestamp

object TaskActor {

  val HIVE_SERVER_URL = "http://10.20.8.70:8080/hive-server/api/task"
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
      execute(task.id, task.queries)
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

  def execute(taskId: Long, queries: String) {

    // send request
    val submitReq = (dispatch.url(HIVE_SERVER_URL) / "submit").POST
        .setContentType("application/json", "UTF-8")
        .setBody(compact(render(Map("query" -> queries))))

    val remoteTaskIdFuture = Http(submitReq OK as.String).map(resultJson => {
      val result = parse(resultJson)
      result \ "status" match {
        case JString("ok") => (result \ "id").extract[Long]
        case _ => throw new Exception("Fail to submit task - " + (result \ "msg").extractOrElse[String]("unknown reason"))
      }
    })

    val remoteTaskId = remoteTaskIdFuture()
    logger.info("Task submitted, id: " + remoteTaskId)

    // wait for completion
    while (!Thread.interrupted) {

      val statusReq = dispatch.url(HIVE_SERVER_URL) / "status" / remoteTaskId

      val remoteStatusFuture = Http(statusReq OK as.String).map(resultJson => {
        val result = parse(resultJson)
        result \ "status" match {
          case JString("ok") =>
            result \ "taskStatus" match {
              case JString("ok") =>

                // read standard output
                val outputWriter = new FileWriter(outputFile(taskId))

                val outputReq = dispatch.url(HIVE_SERVER_URL) / "output" / remoteTaskId
                val outputFuture = Http(outputReq > as.stream.Lines(line => {
                  outputWriter.write(line)
                  outputWriter.write("\n")
                }))
                outputFuture()

                outputWriter.close

                Task.updateStatus(taskId, Task.STATUS_OK)
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

}
