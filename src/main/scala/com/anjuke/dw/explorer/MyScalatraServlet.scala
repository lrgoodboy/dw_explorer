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
    import org.json4s._, org.json4s.jackson.JsonMethods._

    implicit val formats = DefaultFormats

    val accessToken = params.get("access_token")

    if (accessToken.nonEmpty) {

      // fetch user info
      val resourceReq = dispatch.url(AUTH_BASE_URL) / "resource.php" << Map("oauth_token" -> accessToken.get, "getinfo" -> "true")
      logger.info("curl: " + resourceReq.url)

      val result = Http(resourceReq OK as.String).map(parse(_))
      compact(render(result()))

    } else {

      // temporary token
      val authorizeReq = dispatch.url(AUTH_BASE_URL) / "authorize.php" << Map("client_id" -> AUTH_CLIENT_ID, "response_type" -> "code", "curl" -> "true")
      logger.info("curl: " + authorizeReq.url)

      val tokenReq = Http(authorizeReq OK as.String).map(result => {
        val code = ((parse(result) \ "code").extract[String])
        dispatch.url(AUTH_BASE_URL) / "token.php" <<? Map("client_id" -> AUTH_CLIENT_ID,
                                                          "client_secret" -> AUTH_CLIENT_SECRET,
                                                          "grant_type" -> "authorization_code",
                                                          "code" -> code)
      })

      val redirectUrl = tokenReq().url
      logger.info("redirect: " + redirectUrl)
      redirect(redirectUrl)

    }

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
