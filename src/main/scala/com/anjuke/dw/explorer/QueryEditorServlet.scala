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
    contentType = formats("json")

    val file = new File(TaskActor.outputFile(params("id").toLong))
    if (!file.exists) {
      halt(NotFound())
    }

    val lines = io.Source.fromFile(file).getLines

    if (!lines.hasNext) {
      Map("columns" -> Nil)
    } else {

      val columns = lines.next().split("\t").map(label => {
        Map("label" -> label, "field" -> label, "sortable" -> false)
      }).toList

      if (columns.isEmpty) {
        Map("columns" -> Nil)
      } else {

        val rows = lines.take(100).map(line => {
          val cols = line.split("\t")
          val row = for (i <- cols.indices if i < columns.length) yield {
            (columns(i)("label"), cols(i))
          }
          row.toMap
        }).toList

        if (rows.isEmpty) {
          Map("columns" -> columns, "rows" -> Nil)
        } else {

          val columnsWidth = columns.map(column => {
            val width = rows.map(_.getOrElse(column("label"), "").length).filter(_ > 0) match {
              case widthList if widthList.nonEmpty => widthList.sum / widthList.length
              case _ => 0
            }
            column + ("width" -> (if (width > 5) width else 5) * 8)
          })

          Map("columns" -> columnsWidth, "rows" -> rows)
        }
      }
    }

  }

    get("/api/task/error/:id") {
    new File(TaskActor.errorFile(params("id").toLong)) match {
      case file if file.exists => file
      case _ => halt(NotFound())
    }
  }

  get("/api/metadata/?") {
    contentType = formats("json")

    params.get("parent") match {
      case Some("root") =>
        List(
          Map("id" -> "dw_db", "name" -> "dw_db", "parent" -> "root", "children" -> true),
          Map("id" -> "dw_stage", "name" -> "dw_stage", "parent" -> "root", "children" -> true),
          Map("id" -> "dw_extract", "name" -> "dw_extract", "parent" -> "root", "children" -> true),
          Map("id" -> "dw_db_temp", "name" -> "dw_db_temp", "parent" -> "root", "children" -> true),
          Map("id" -> "dw_db_test", "name" -> "dw_db_test", "parent" -> "root", "children" -> true)
        )

      case Some("dw_db") =>
        List(
          Map("id" -> "dw_db.dw_soj_imp_dtl", "name" -> "dw_soj_imp_dtl", "parent" -> "dw_db", "children" -> false),
          Map("id" -> "dw_db.dw_soj_imp_dtl_npv", "name" -> "dw_soj_imp_dtl_npv", "parent" -> "dw_db", "children" -> false)
        )

      case Some("dw_stage") =>
        List(
          Map("id" -> "dw_stage.st_dw_soj_imp_dtl", "name" -> "st_dw_soj_imp_dtl", "parent" -> "dw_stage", "children" -> false)
        )

      case None => List(Map("id" -> "root", "name" -> "Data Warehouse", "children" -> true))

      case _ => Nil
    }
  }

  get("/api/doc/:id") {
    contentType = formats("json")

    Doc.lookup(params("id").toLong) match {
      case Some(doc) =>
        Map(
          "id" -> doc.id,
          "parent" -> doc.parentId,
          "name" -> doc.filename,
          "content" -> doc.content,
          "isFolder" -> doc.isFolder
        )

      case None => halt(NotFound())
    }
  }

  get("/api/doc/?") {
      contentType = formats("json")

      def doc2map(doc: Doc) = Map(
        "id" -> doc.id,
        "parent" -> doc.parentId,
        "name" -> doc.filename,
        "isFolder" -> doc.isFolder
      )

      params.get("parent") match {
        case Some(parent) =>
          Doc.findList(user.id, Some(parent.toLong)).map(doc2map)

        case None =>
          Map("id" -> 0, "name" -> "My Documents", "isFolder" -> true) ::
          Doc.findList(user.id).map(doc2map)
      }
  }

  post("/api/doc/?") {
    contentType = formats("json")

    def newDoc(parentId: Long) = new Doc(
      userId = user.id,
      parentId = parentId,
      isFolder = (parsedBody \ "isFolder").extract[Boolean],
      filename = (parsedBody \ "filename").extract[String],
      content = (parsedBody \ "content").extractOrElse[String](""),
      isDeleted = false,
      created = currentTimestamp,
      updated = currentTimestamp
    )

    val child = (parsedBody \ "parent").extract[Long] match {
      case 0 => newDoc(0)
      case parentId =>
        Doc.lookup(parentId) match {
          case Some(parent) if (parent.userId == user.id && parent.isDeleted == false) =>
            newDoc(parent.id)
          case _ => halt(NotFound())
        }
    }

    Doc.create(child) match {
      case Some(child) =>
        Map(
          "id" -> child.id,
          "parent" -> child.parentId,
          "name" -> child.filename,
          "content" -> child.content,
          "isFolder" -> child.isFolder
        )

      case None => halt(InternalServerError("Fail to create doc."))
    }
  }

  post("/api/doc/content/:id") {
    Doc.lookup(params("id").toLong) match {
      case Some(doc) if (doc.userId == user.id && doc.isDeleted == false) =>
        Doc.updatePartial(id = doc.id, content = Some(request.body))
        Unit
      case _ => halt(BadRequest())
    }
  }

  put("/api/doc/:id") {
    contentType = formats("json")

    Doc.lookup(params("id").toLong) match {
      case Some(doc) if (doc.userId == user.id && doc.isDeleted == false) =>
        Doc.updatePartial(id = doc.id,
                          filename = (parsedBody \ "filename").extractOpt[String],
                          content = (parsedBody \ "content").extractOpt[String])

        val newDoc = Doc.lookup(doc.id).get
        Map(
          "id" -> newDoc.id,
          "parent" -> newDoc.parentId,
          "name" -> newDoc.filename,
          "content" -> newDoc.content,
          "isFolder" -> newDoc.isFolder
        )

      case _ => halt(BadRequest())
    }
  }

  delete("/api/doc/:id") {
    contentType = formats("json")

    Doc.lookup(params("id").toLong) match {
      case Some(doc) if (doc.userId == user.id && doc.isDeleted == false) =>
        Doc.updatePartial(id = doc.id, isDeleted = Some(true))
        Unit

      case _ => halt(BadRequest())
    }
  }

  private def currentTimestamp = new Timestamp(System.currentTimeMillis)

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
