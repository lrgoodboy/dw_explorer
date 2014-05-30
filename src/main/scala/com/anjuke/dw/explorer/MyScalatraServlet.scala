package com.anjuke.dw.explorer

import org.slf4j.LoggerFactory

import com.anjuke.dw.explorer.init.AuthenticationSupport

class MyScalatraServlet extends DwExplorerStack with AuthenticationSupport {

  val AUTH_BASE_URL = "https://auth.corp.anjuke.com"
  val AUTH_CLIENT_ID = "dw_explorer_dev"
  val AUTH_CLIENT_SECRET = "e331cc2e"

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
    contentType = "text/html"
    ssp("hello-dojo", "hello" -> "dojo")
  }

  get("/login") {

    import dispatch._, Defaults._

    val accessToken = getParam("access_token")
    val code = getParam("code")

    if (accessToken.nonEmpty) {
      val authReq = dispatch.url(AUTH_BASE_URL) / "resource.php" <<? Map("oauth_token" -> accessToken.get, "getinfo" -> "1")
    } else if (code.nonEmpty) {
      val authReq = dispatch.url(AUTH_BASE_URL) / "token.php" <<? Map("client_id" -> AUTH_CLIENT_ID, "client_secret" -> AUTH_CLIENT_SECRET, "grant_type" -> "authorization_code", "code" -> code.get)
      logger.info(authReq.url)
      redirect(authReq.url)
    } else {

      // temporary token
      /*val authReq = dispatch.url(AUTH_BASE_URL) / "authorize.php" << Map("client_id" -> AUTH_CLIENT_ID, "response_type" -> "code", "curl" -> "true")
      for (result <- dispatch.Http(authReq OK as.String)) {

      }*/

      val myRequest = dispatch.url(AUTH_BASE_URL) / "authorize.php" <<? Map("client_id" -> AUTH_CLIENT_ID, "response_type" -> "code")
      logger.info(myRequest.url)
      redirect(myRequest.url)
    }

  }

  def getParam(key: String) = {
    params.get(key).map(_.trim).filter(!_.isEmpty)
  }

}
// http://dwms.local.dev.anjuke.com/explorer/login
// https://auth.corp.anjuke.com/authorize.php?client_id=dw_explorer_dev&response_type=code
// https://auth.corp.anjuke.com/token.php?client_id=dw_explorer_dw&client_secret=e331cc2e&grant_type=authorization_code&code=fb0aede001804eb8dd5784ae3e6fbd80
// https://auth.corp.anjuke.com/resource.php?oauth_token=
/*
应用显示名称：DW Explorer
应用访问标记：dw_explorer_dev
应用访问密码：e331cc2e
应用显示URL：http://dwms.local.dev.anjuke.com/explorer/
应用回传URL：http://dwms.local.dev.anjuke.com/explorer/login
*/
