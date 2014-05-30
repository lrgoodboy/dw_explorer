package com.anjuke.dw.explorer.init

import org.scalatra.ScalatraBase
import javax.servlet.http.HttpServletResponse
import org.scalatra.auth.ScentryStrategy
import javax.servlet.http.HttpServletRequest
import com.anjuke.dw.explorer.models.User
import org.scalatra.CookieOptions
import org.slf4j.LoggerFactory

class RememberMeStrategy(protected val app: ScalatraBase)(implicit request: HttpServletRequest, response: HttpServletResponse)
    extends ScentryStrategy[User] {

  val logger = LoggerFactory.getLogger(getClass)

  override def name: String = "RememberMe"

  val COOKIE_KEY = "rememberMe"
  private val oneWeek = 7 * 24 * 3600

  private def tokenVal = {
    app.cookies.get(COOKIE_KEY) match {
      case Some(token) => token
      case None => ""
    }
  }

  override def isValid(implicit request: HttpServletRequest): Boolean = {
    val isValid = tokenVal != ""
    logger.info("determining isValid: " + isValid.toString)
    tokenVal != ""
  }

  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse) = {
    logger.info("attempting authentication")
    if (tokenVal == "foobar") Some(User("foo")) else None
  }

  override def unauthenticated()(implicit request: HttpServletRequest, response: HttpServletResponse) {
    app.redirect("/sessions/new")
  }

  override def afterAuthenticate(winningStrategy: String, user: User)(implicit request: HttpServletRequest, response: HttpServletResponse) {
    logger.info("afterAuth fired")

    if (winningStrategy == "RememberMe" ||
        (winningStrategy == "UserPassword" && checkbox2boolean(app.params.get("rememberMe").getOrElse("").toString))) {

      val token = "foobar"
      app.cookies.set(COOKIE_KEY, token)(CookieOptions(maxAge = oneWeek, path = "/"))
    }
  }

  override def beforeLogout(user: User)(implicit request: HttpServletRequest, response: HttpServletResponse) {
    logger.info("beforeLogout")

    if (user != null) {
      user.forgetMe
    }
    app.cookies.delete(COOKIE_KEY)(CookieOptions(path = "/"))
  }

  private def checkbox2boolean(s: String): Boolean = {
    s match {
      case "yes" => true
      case "y" => true
      case "1" => true
      case "true" => true
      case _ => false
    }
  }

}
