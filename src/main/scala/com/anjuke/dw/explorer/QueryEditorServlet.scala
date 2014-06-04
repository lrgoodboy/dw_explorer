package com.anjuke.dw.explorer

import org.json4s.Formats
import org.scalatra.json.JacksonJsonSupport
import org.json4s.DefaultFormats
import com.anjuke.dw.explorer.init.DatabaseSessionSupport
import com.anjuke.dw.explorer.init.AuthenticationSupport
import com.anjuke.dw.explorer.models.Task
import java.sql.Timestamp
import akka.actor.ActorRef
import org.json4s.JsonAST.JString
import java.util.Calendar
import java.text.SimpleDateFormat

class QueryEditorServlet(taskActor: ActorRef) extends DwExplorerStack
    with JacksonJsonSupport with DatabaseSessionSupport with AuthenticationSupport {

  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    requireLogin()
  }

  get("/index") {
    contentType = "text/html"
    ssp("query-editor/index", "layout" -> "")
  }

  post("/api/task") {

    contentType = formats("json")

    try {

      val queries = (parsedBody \ "queries").extractOpt[String].map(_.trim).filter(!_.isEmpty) match {
        case Some(queries) => queries
        case None => throw new Exception("Queries cannot be empty.")
      }

      val created = new Timestamp(System.currentTimeMillis())
      val task = new Task(userId = user.id,
                          queries = queries,
                          status = Task.STATUS_NEW,
                          progress = 0,
                          duration = 0,
                          created = created,
                          updated = created)
      Task.create(task) match {
        case Some(task) =>
          taskActor ! task.id
          Map("status" -> "ok", "id" -> task.id)
        case None => throw new Exception("Task submission failed.")
      }

    } catch {
      case e: Exception => Map("status" -> "error", "msg" -> e.getMessage())
    }

  }

  val statusMap = Map(
    Task.STATUS_NEW -> "等待中",
    Task.STATUS_RUNNING -> "运行中",
    Task.STATUS_OK -> "运行成功",
    Task.STATUS_ERROR -> "运行失败"
  )

  get("/api/task") {

    contentType = formats("json")

    val dfDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    Task.findList(user.id, createdStart = Some(midnight)).map(task => {
      Map(
        "id" -> task.id,
        "created" -> dfDateTime.format(task.created),
        "queries" -> task.queries,
        "status" -> statusMap(task.status),
        "duration" -> {
          val dur = if (task.duration > 0) task.duration else {
            ((System.currentTimeMillis - task.created.getTime()) / 1000).asInstanceOf[Int]
          }
          formatDuration(dur)
        }
      )
    })
  }

  private def midnight = {
    val calendar = Calendar.getInstance
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    calendar.getTime
  }

  private def formatDuration(duration: Int) = {
    val hour = duration / 60 / 60
    val minute = duration / 60 % 60
    val second = duration % 60
    f"$hour%02d:$minute%02d:$second%02d"
  }

}
