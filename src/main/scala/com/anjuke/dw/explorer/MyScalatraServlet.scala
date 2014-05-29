package com.anjuke.dw.explorer

import org.scalatra._
import scalate.ScalateSupport
import org.scalatra.json.JacksonJsonSupport
import org.json4s.Formats
import org.json4s.DefaultFormats

class MyScalatraServlet extends DwExplorerStack with JacksonJsonSupport {

  protected implicit val jsonFormats: Formats = DefaultFormats

  get("/") {
    <html>
      <body>
        <h1>Hello, world!</h1>
        Say <a href="hello-scalate">hello to Scalate</a>.
      </body>
    </html>
  }

  get("/hello/dojo") {
    contentType = "text/html"
    ssp("hello-dojo", "hello" -> "dojo")
  }

  get("/query-editor") {
    contentType = "text/html"
    ssp("query-editor", "layout" -> "")
  }

  post("/query-editor/api/run") {
    contentType = formats("json")
    Map("status" -> "ok", "queries" -> parsedBody \ "queries")
  }

}
