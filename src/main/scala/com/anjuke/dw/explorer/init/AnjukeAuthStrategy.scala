package com.anjuke.dw.explorer.init

import org.scalatra.ScalatraBase
import org.scalatra.auth.ScentryStrategy
import com.anjuke.dw.explorer.models.User
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import org.slf4j.LoggerFactory
import dispatch._, Defaults._
import org.json4s._, jackson.JsonMethods._

class AnjukeAuthStrategy(protected val app: ScalatraBase)(implicit request: HttpServletRequest, response: HttpServletResponse)
  extends ScentryStrategy[User] {

  val AUTH_BASE_URL = "https://auth.corp.anjuke.com"
  val AUTH_CLIENT_ID = "dw_explorer_dev"
  val AUTH_CLIENT_SECRET = "e331cc2e"

  private val logger = LoggerFactory.getLogger(getClass)

  override def isValid(implicit request: HttpServletRequest): Boolean = {
    request.getPathInfo == "/login"
  }

  implicit val formats = DefaultFormats

  override def name: String = "AnjukeAuth"

  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] = {

    logger.info("attempting authentication")

    app.params.get("access_token") match {
      case Some(accessToken) =>

        // fetch user info
        val resourceReq = url(AUTH_BASE_URL) / "resource.php" << Map("oauth_token" -> accessToken, "getinfo" -> "true")
        logger.info("curl: " + resourceReq.url)

        val redirectUrl = Http(resourceReq OK as.String).map(result => {
          val userInfo = parse(result)

          logger.info(compact(userInfo))

          "/"
        })

        app.redirect(redirectUrl())

      case None =>

        // temporary token
        val authorizeReq = url(AUTH_BASE_URL) / "authorize.php" << Map("client_id" -> AUTH_CLIENT_ID, "response_type" -> "code", "curl" -> "true")
        logger.info("curl: " + authorizeReq.url)

        // redirect to oauth
        val tokenReq = Http(authorizeReq OK as.String).map(result => {
          val code = (parse(result) \ "code").extract[String]
          url(AUTH_BASE_URL) / "token.php" <<? Map("client_id" -> AUTH_CLIENT_ID,
                                                            "client_secret" -> AUTH_CLIENT_SECRET,
                                                            "grant_type" -> "authorization_code",
                                                            "code" -> code)
        })

        val redirectUrl = tokenReq().url
        logger.info("redirect: " + redirectUrl)
        app.redirect(redirectUrl)

    }

  }

  override def unauthenticated()(implicit request: HttpServletRequest, response: HttpServletResponse) {
    app.redirect("/login")
  }

}
