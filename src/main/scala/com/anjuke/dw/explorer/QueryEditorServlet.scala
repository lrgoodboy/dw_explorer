package com.anjuke.dw.explorer

import org.json4s.Formats
import org.scalatra.json.JacksonJsonSupport
import org.json4s.DefaultFormats
import com.anjuke.dw.explorer.init.DatabaseSessionSupport
import com.anjuke.dw.explorer.models.Task
import java.sql.Timestamp
import akka.actor.ActorRef
import org.json4s.JsonAST.JString

class QueryEditorServlet(taskActor: ActorRef)
    extends DwExplorerStack with JacksonJsonSupport with DatabaseSessionSupport {

  protected implicit val jsonFormats: Formats = DefaultFormats

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
      val task = new Task(queries = queries,
                          created = new Timestamp(System.currentTimeMillis()))
      Task.create(task) match {
        case Some(taskId) =>
          taskActor ! taskId
          Map("status" -> "ok", "id" -> taskId)
        case None => throw new Exception("Task submission failed.")
      }

    } catch {
      case e: Exception => Map("status" -> "error", "msg" -> e.getMessage())
    }

  }

}