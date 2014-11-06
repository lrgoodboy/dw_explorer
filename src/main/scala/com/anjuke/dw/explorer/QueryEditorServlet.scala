package com.anjuke.dw.explorer

import org.scalatra.json.JacksonJsonSupport
import com.anjuke.dw.explorer.init.DatabaseSessionSupport
import com.anjuke.dw.explorer.init.AuthenticationSupport
import com.anjuke.dw.explorer.models.{Task, Doc, User}
import java.sql.Timestamp
import akka.actor.ActorRef
import java.util.{Calendar, Date}
import java.text.SimpleDateFormat
import org.scalatra.{BadRequest, InternalServerError, NotFound}
import java.io.File
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.anjuke.dw.explorer.util.Config
import com.anjuke.dw.explorer.init.RememberMeStrategy

case class Column(name: String, dataType: String, comment: String)
case class DgridColumn(label: String, field: String, sortable: Boolean = false, width: Int = 40)

object QueryEditorServlet {

  val statusMap = Map(
    Task.STATUS_NEW -> "等待中",
    Task.STATUS_RUNNING -> "运行中",
    Task.STATUS_OK -> "运行成功",
    Task.STATUS_ERROR -> "运行失败",
    Task.STATUS_INTERRUPTED -> "取消中"
  )

  def formatTask(task: Task): Map[String, JValue] = {

    val dfDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    Map(
      "id" -> task.id,
      "created" -> dfDateTime.format(task.created),
      "queries" -> task.queries,
      "queriesBrief" -> {

        val ptrnBuffer = "(?i)^(SET|ADD\\s+JAR|CREATE\\s+TEMPORARY\\s+FUNCTION|USE)\\s+".r
        val queries = task.queries
                          .replaceAll("(?s)/\\*.*?\\*/", "")
                          .split(";").map(_.trim).filter(_.nonEmpty)
                          .filter(ptrnBuffer.findFirstIn(_).isEmpty)
                          .mkString(";")

        if (queries.length > 100) queries.substring(0, 100) else queries
      },
      "status" -> statusMap(task.status),
      "duration" -> {
        if (task.duration > 0) formatDuration(task.duration) else "-"
      },
      "updated" -> dfDateTime.format(task.updated)
    )
  }

  private def formatDuration(duration: Int) = {
    val hour = duration / 60 / 60
    val minute = duration / 60 % 60
    val second = duration % 60
    f"$hour%02d:$minute%02d:$second%02d"
  }
}

class QueryEditorServlet(taskActor: ActorRef) extends DwExplorerStack
    with JacksonJsonSupport with DatabaseSessionSupport with AuthenticationSupport {

  import QueryEditorServlet._

  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    requireLogin()
  }

  get("/") {
    redirect("/query-editor/index")
  }

  get("/index") {
    requireRole(User.ROLE_BI)
    contentType = "text/html"
    ssp("query-editor/index", "layout" -> "",
        "version" -> Config("common", "version"),
        "cookieKey" -> RememberMeStrategy.COOKIE_KEY,
        "websocketServer" -> Config("service", "websocket.server"))
  }

  get("/task/result/:id") {
    requireRole(User.ROLE_BI)

    val id = params("id").toLong
    val (columns, rows, hasMore) = getResult(id, 5000)

    val result =
      ("columns" ->
        columns.map { column =>
          ("label" -> column.label) ~
          ("field" -> column.field) ~
          ("sortable" -> column.sortable) ~
          ("width" -> column.width)
        }) ~
      ("rows" -> rows) ~
      ("hasMore" -> hasMore)

    contentType = "text/html"
    ssp("query-editor/task-result", "layout" -> "",
        "id" -> id,
        "result" -> compact(render(result)))
  }

  post("/api/task/?") {

    contentType = formats("json")

    val queries = (parsedBody \ "queries").extractOpt[String].map(_.trim).filter(!_.isEmpty) match {
      case Some(queries) => replaceParameters(queries)
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

  get("/api/task/cancel/:id") {
    val taskId = params("id").toLong
    Task.lookup(taskId) match {
      case Some(task) =>
        if (task.userId == user.id && (task.status == Task.STATUS_NEW || task.status == Task.STATUS_RUNNING)) {
          Task.updateStatus(taskId, Task.STATUS_INTERRUPTED);
        } else {
          halt(BadRequest())
        }
      case None => halt(NotFound())
    }
    Unit
  }

  get("/api/task/output/:id") {
    contentType = formats("json")
    val (columns, rows, hasMore) = getResult(params("id").toLong, 1000)
    Map(
      "columns" -> columns,
      "rows" -> rows,
      "hasMore" -> hasMore
    )
  }

  private def getResult(id: Long, limit: Int): Tuple3[List[DgridColumn], List[Map[String, String]], Boolean] = {

    val file = new File(TaskActor.outputFile(id))
    if (!file.exists) {
      halt(NotFound())
    }

    val lines = io.Source.fromFile(file).getLines

    if (!lines.hasNext) {
      (Nil, Nil, false)
    } else {

      val columns = lines.next().split("\t") map { label =>
        DgridColumn(label, label)
      } toList

      val rows = if (columns.nonEmpty) {

        lines.take(limit).map(line => {
          val cols = line.split("\t")
          val row = for (i <- cols.indices if i < columns.length) yield {
            (columns(i).label, cols(i))
          }
          row.toMap
        }).toList

      } else Nil

      val hasMore = rows.size == limit && lines.hasNext

      val columnsWidth = if (rows.nonEmpty) calcColumnsWidth(columns, rows) else columns

      (columnsWidth, rows, hasMore)
    }

  }

  get("/api/task/excel/:id") {

    import org.apache.poi.ss.usermodel.{Workbook, Sheet, Row, Cell}
    import org.apache.poi.xssf.streaming.SXSSFWorkbook

    val id = params("id").toLong

    val file = new File(TaskActor.outputFile(id))
    if (!file.exists) {
      halt(NotFound())
    }

    val lines = io.Source.fromFile(file).getLines

    if (!lines.hasNext) {
      halt(NotFound())
    }

    val columns = lines.next.split("\t")
    if (columns.isEmpty) {
      halt(NotFound())
    }

    val wb = new SXSSFWorkbook
    val sheet = wb.createSheet(s"task_result_$id")

    val row = sheet.createRow(0)
    for (columnIndex <- columns.indices) {
      val cell = row.createCell(columnIndex)
      cell.setCellValue(columns(columnIndex))
    }

    var rowIndex = 1
    for (line <- lines if line.nonEmpty) {
        val row = sheet.createRow(rowIndex)

        val columns = line.split("\t")
        for (columnIndex <- columns.indices) {
          val cell = row.createCell(columnIndex)
          cell.setCellValue(columns(columnIndex))
        }

        rowIndex += 1
    }

    contentType = "application/octet-stream"
    response.setHeader("content-disposition", s"attachment; filename=task_result_$id.xlsx")
    wb.write(response.outputStream)
    wb.dispose

    Unit
  }

  get("/api/task/error/:id") {
    new File(TaskActor.errorFile(params("id").toLong)) match {
      case file if file.exists => file
      case _ => halt(NotFound())
    }
  }

  get("/api/metadata/?") {
    contentType = formats("json")

    params.get("database") match {
      case Some(database) =>

        import dispatch._
        import dispatch.Defaults._

        val req = dispatch.url(TaskActor.HIVE_SERVER_URL) / "table" / "list" / database
        val tables = Http(req OK as.String).map(resultJson => {
          val result = parse(resultJson)
          for (JString(table) <- result) yield Map(
            "id" -> s"$database.$table",
            "name" -> table
          )
        })

        tables()

      case None =>
        Seq("dw_db", "dw_stage", "dw_extract", "dw_db_temp", "dw_db_test").map(database => {
          Map("id" -> database, "name" -> database)
        }).toList
    }
  }

  get("/api/metadata/desc/?") {
    contentType = formats("json")

    val database = params("database")
    val table = params("table")

    import dispatch._
    import dispatch.Defaults._

    val req = dispatch.url(TaskActor.HIVE_SERVER_URL) / "table" / "desc" / database / table
    val info = Http(req OK as.String).map(resultJson => {
        val result = parse(resultJson)

        val partitions = for {
          JObject(column) <- result \ "columns"
          JField("partition", JBool(partition)) <- column
          JField("name", JString(name)) <- column
          if partition
        } yield name

        val columns = for {
          JObject(column) <- result \ "columns"
          JField("name", JString(name)) <- column
          JField("type", JString(dataType)) <- column
          JField("comment", JString(comment)) <- column
        } yield Column(name, dataType, comment)

        val rows = for (JArray(row) <- result \ "rows") yield {
          val rowData = for (JString(col) <- row) yield col
          val pairs = for (i <- rowData.indices) yield (columns(i).name, rowData(i))
          pairs.toMap
        }

        val size = (result \ "size").extract[Long].toString + "B"

        val info = List(Map(
          "database" -> database,
          "table" -> table,
          "partitions" -> {
            if (partitions.nonEmpty) partitions.mkString(", ") else "未分区"
          },
          "size" -> size
        ))

        val tableColumns = columns map { column =>
            DgridColumn(column.name, column.name)
        }
        val tableColumnsWidth = if (rows.nonEmpty) calcColumnsWidth(tableColumns, rows) else columns

        Map(
          "info" -> info,
          "columns" -> columns,
          "rows" -> rows,
          "tableColumns" -> tableColumnsWidth
        )
    })

    info()
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

  private def calcColumnsWidth(columns: List[DgridColumn], rows: List[Map[String, String]]): List[DgridColumn] = {
    columns map { column =>
      val width = rows.map(_.getOrElse(column.label, "").length).filter(_ > 0) match {
        case widthList if widthList.nonEmpty => widthList.sum / widthList.length
        case _ => 0
      }
      column.copy(width = (if (width > 5) width else 5) * 8)
    }
  }

  private def replaceParameters(queries: String) = {

    val parameters = Map(
      "dealDate" -> ("'" + dealDate + "'"),
      "outFileSuffix" -> dealDate,
      "dateSuffix" -> dealDate.replace("-", "")
    )

    var result = queries
    parameters.foreach {
      case (p, v) =>
        result = result.replace("${" + p + "}", v)
    }

    // replace udf path
    result = result.replace("/home/hadoop/dwetl/hiveudf/", Config("service", "dw.hiveserver.udf.path"))

    // replace count(*)
    result = result.replaceAll("(?i)COUNT\\(\\*\\)", "COUNT(1)")

    result
  }

  private def dealDate = {
    val cal = Calendar.getInstance
    cal.add(Calendar.DATE, -1)
    new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime)
  }

}
