package com.anjuke.dw.explorer

import org.slf4j.LoggerFactory

import com.anjuke.dw.explorer.init.DatabaseSessionSupport
import com.anjuke.dw.explorer.init.AuthenticationSupport
import com.anjuke.dw.explorer.init.AnjukeAuthStrategy

class MyScalatraServlet extends DwExplorerStack
    with DatabaseSessionSupport with AuthenticationSupport {

  val logger = LoggerFactory.getLogger(getClass)

  get("/") {
    redirect("/query-editor/")
  }

  get("/hello") {
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
    if (!isAuthenticated) {
      scentry.authenticate("RememberMe")
    }
    if (!isAuthenticated) {
      scentry.authenticate("AnjukeAuth")
    }
    if (!isAuthenticated) {
      halt(403)
    }
    def returnToParam = params.getOrElse(scentryConfig.returnToKey, scentryConfig.returnTo)
    redirect(request.getAsOrElse(scentryConfig.returnToKey, returnToParam))
  }

  get("/logout") {
    scentry.logout()
    redirect(scentry.strategies("AnjukeAuth").asInstanceOf[AnjukeAuthStrategy].logoutUrl)
  }

}
