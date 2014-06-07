package com.anjuke.dw.explorer.init

import org.scalatra.ScalatraBase
import org.scalatra.auth.ScentryStrategy
import com.anjuke.dw.explorer.models.User
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import org.slf4j.LoggerFactory
import dispatch._
import dispatch.Defaults._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import java.sql.Timestamp
import org.scalatra.auth.ScentryConfig

class AnjukeAuthStrategy(protected val app: ScalatraBase, protected val scentryConfig: ScentryConfig)
    (implicit request: HttpServletRequest, response: HttpServletResponse)
    extends ScentryStrategy[User] {

  val AUTH_BASE_URL = "https://auth.corp.anjuke.com"
  val AUTH_CLIENT_ID = "dw_explorer_dev"
  val AUTH_CLIENT_SECRET = "e331cc2e"

  private val logger = LoggerFactory.getLogger(getClass)

  implicit val formats = DefaultFormats

  override def name: String = "AnjukeAuth"

  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] = {

    logger.info("attempting authentication")

    app.params.get("access_token") match {
      case Some(accessToken) =>

        // fetch user info
        val resourceReq = url(AUTH_BASE_URL) / "resource.php" << Map("oauth_token" -> accessToken, "getinfo" -> "true")
        logger.info("curl: " + resourceReq.url)

        val user = Http(resourceReq OK as.String).map(result => {

          logger.info("result: " + result)

          val userInfo = parse(result)
          val username = (userInfo \ "username").extract[String]

          User.lookup(username) match {
            case Some(user) => Some(user)

            case None =>
              logger.info("create user: " + username)
              val user = new User(username = username,
                                  truename = (userInfo \ "chinese_name").extract[String],
                                  email = (userInfo \ "email").extract[String],
                                  created = new Timestamp(System.currentTimeMillis))
              User.create(user)
          }
        })

        user()

      case None =>

        // temporary token
        val authorizeReq = url(AUTH_BASE_URL) / "authorize.php" << Map("client_id" -> AUTH_CLIENT_ID, "response_type" -> "code", "curl" -> "true")
        logger.info("curl: " + authorizeReq.url)

        // redirect to oauth
        val tokenReq = Http(authorizeReq OK as.String).map(result => {
          val code = (parse(result) \ "code").extract[String]
          val returnTo = app.params.getOrElse(scentryConfig.returnToKey, scentryConfig.returnTo)
          url(AUTH_BASE_URL) / "token.php" <<? Map("client_id" -> AUTH_CLIENT_ID,
                                                   "client_secret" -> AUTH_CLIENT_SECRET,
                                                   "grant_type" -> "authorization_code",
                                                   "code" -> code,
                                                   "custom" -> returnTo)
        })

        val redirectUrl = tokenReq().url
        logger.info("redirect: " + redirectUrl)
        app.redirect(redirectUrl)

    }

  }

  override def afterAuthenticate(winningStrategy: String, user: User)(implicit request: HttpServletRequest, response: HttpServletResponse) {
    if (winningStrategy == name) {
      logger.info("afterAuth fired")
      val returnTo = app.params.get("custom") match {
        case Some(custom) => custom
        case None => scentryConfig.returnTo
      }
      request.setAttribute(scentryConfig.returnToKey, returnTo)
    }
  }

  def logoutUrl() = {
    val logoutReq = dispatch.url(AUTH_BASE_URL) / "logout.php" <<? Map("client_id" -> AUTH_CLIENT_ID, "client_secret" -> AUTH_CLIENT_SECRET)
    logoutReq.url
  }

}
