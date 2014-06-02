package com.anjuke.dw.explorer

import org.slf4j.LoggerFactory

import com.anjuke.dw.explorer.init.AuthenticationSupport

class MyScalatraServlet extends DwExplorerStack with AuthenticationSupport {

  val logger = LoggerFactory.getLogger(getClass)

  get("/") {
    <html>
      <body>
        <h1>Hello, world!</h1>
        Say <a href="hello-scalate">hello to Scalate</a>.
      </body>
    </html>
  }

  get("/hello/dojo") {
    requireLogin

    contentType = "text/html"
    ssp("hello-dojo", "hello" -> "dojo")
  }

  get("/login") {
    scentry.authenticate()
  }

}
