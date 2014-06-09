package com.anjuke.dw.explorer

import org.json4s.Formats
import org.scalatra.json.JacksonJsonSupport
import org.json4s.DefaultFormats
import com.anjuke.dw.explorer.init.DatabaseSessionSupport
import com.anjuke.dw.explorer.init.AuthenticationSupport
import com.anjuke.dw.explorer.models.{Task, Doc}
import java.sql.Timestamp
import akka.actor.ActorRef
import org.json4s.JsonAST.JString
import java.util.{Calendar, Date}
import java.text.SimpleDateFormat
import org.scalatra.{BadRequest, InternalServerError, NotFound}
import java.io.File

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

  post("/api/task/?") {

    contentType = formats("json")

    val queries = (parsedBody \ "queries").extractOpt[String].map(_.trim).filter(!_.isEmpty) match {
      case Some(queries) => queries
      case None => halt(BadRequest("Queries cannot be empty."))
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
        formatTask(task)
      case None => halt(InternalServerError("Task submission failed."))
    }

  }

  val statusMap = Map(
    Task.STATUS_NEW -> "等待中",
    Task.STATUS_RUNNING -> "运行中",
    Task.STATUS_OK -> "运行成功",
    Task.STATUS_ERROR -> "运行失败"
  )

  get("/api/task/?") {
    contentType = formats("json")

    val updated = params.get("updated") match {
      case Some(updated) =>
        val dfDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        try {
          Some(dfDateTime.parse(updated))
        } catch {
          case e: Exception => halt(BadRequest("Invalid 'updated' value."))
        }
      case None => None
    }

    Task.findToday(user.id, updated).map(formatTask _)
  }

  get("/api/task/:id") {
    contentType = formats("json")
    val task = Task.lookup(params("id").toLong).get
    formatTask(task)
  }

  get("/api/task/output/:id") {
    new File(TaskActor.outputFile(params("id").toLong))
  }

  get("/api/metadata/:id") {
    contentType = formats("json")

    params("id") match {
      case "root" =>
        val databases = List(
          Map("id" -> "dw_db", "name" -> "dw_db", "children" -> true),
          Map("id" -> "dw_stage", "name" -> "dw_stage", "children" -> true),
          Map("id" -> "dw_extract", "name" -> "dw_extract", "children" -> true),
          Map("id" -> "dw_db_temp", "name" -> "dw_db_temp", "children" -> true),
          Map("id" -> "dw_db_test", "name" -> "dw_db_test", "children" -> true)
        )
        Map("id" -> "root", "name" -> "Data Warehouse", "children" -> databases)

      case "dw_db" =>
        val tables = List(
          Map("id" -> "dw_db.dw_soj_imp_dtl", "name" -> "dw_soj_imp_dtl"),
          Map("id" -> "dw_db.dw_soj_imp_dtl_npv", "name" -> "dw_soj_imp_dtl_npv")
        )
        Map("id" -> "dw_db", "name" -> "dw_db", "children" -> tables)

      case _ => Nil
    }
  }

  get("/api/doc/:id") {
    contentType = formats("json")

    params("id") match {
      case "root" =>

        val docs = Doc.findByUser(user.id).map(doc => {
          Map(
            "id" -> doc.id,
            "name" -> doc.filename,
            "children" -> doc.isFolder
          )
        })

        Map(
          "id" -> 0,
          "name" -> "My Documents",
          "children" -> docs
        )

      case id =>
        Doc.lookup(id.toLong) match {
          case Some(doc) =>
            val docs = Doc.findByParent(user.id, doc.id).map(doc => {
              Map(
                "id" -> doc.id,
                "name" -> doc.filename,
                "children" -> doc.isFolder
              )
            })
            Map(
              "id" -> doc.id,
              "name" -> doc.filename,
              "children" -> docs
            )

          case None => halt(NotFound())
        }
    }
  }

  private def formatDuration(duration: Int) = {
    val hour = duration / 60 / 60
    val minute = duration / 60 % 60
    val second = duration % 60
    f"$hour%02d:$minute%02d:$second%02d"
  }

  private def formatTask(task: Task) = {

    val dfDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    Map(
      "id" -> task.id,
      "created" -> dfDateTime.format(task.created),
      "queries" -> task.queries,
      "queriesBrief" -> {
        if (task.queries.length > 100) task.queries.substring(0, 100) else task.queries
      },
      "status" -> statusMap(task.status),
      "duration" -> {
        if (task.duration > 0) formatDuration(task.duration) else "-"
      },
      "updated" -> dfDateTime.format(task.updated)
    )
  }

}
