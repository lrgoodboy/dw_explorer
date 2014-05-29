package com.anjuke.dw.explorer

import org.json4s.Formats
import org.scalatra.json.JacksonJsonSupport
import org.json4s.DefaultFormats
import com.anjuke.dw.explorer.init.DatabaseSessionSupport
import com.anjuke.dw.explorer.models.Task
import java.sql.Timestamp

class QueryEditorServlet extends DwExplorerStack with JacksonJsonSupport with DatabaseSessionSupport {

  protected implicit val jsonFormats: Formats = DefaultFormats

  get("/index") {
    contentType = "text/html"
    ssp("query-editor/index", "layout" -> "")
  }

  post("/api/task") {
    contentType = formats("json")
    Task.create(new Task((parsedBody \ ("queries")).extract[String], new Timestamp(System.currentTimeMillis()))) match {
      case Some(taskId) => Map("status" -> "ok", "id" -> taskId)
      case None => Map("status" -> "error", "msg" -> "提交失败")
    }
  }

}
