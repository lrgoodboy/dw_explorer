package com.anjuke.dw.explorer

import org.scalatra._
import scalate.ScalateSupport

class MyScalatraServlet extends DwExplorerStack {

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

}
