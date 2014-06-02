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
  val COOKIE_EXPIRE = 7 * 24 * 3600

  val cookiePath = request.getContextPath

  override def isValid(implicit request: HttpServletRequest): Boolean = {
    logger.info("isValid: true")
    true
  }

  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse) = {
    logger.info("attempting authentication")
    app.cookies.get(COOKIE_KEY) match {
      case Some(token) => if (token == "hash:userid") User.lookup(1) else None // TODO store user id in cookie with a signature
      case None => None
    }
  }

  override def unauthenticated()(implicit request: HttpServletRequest, response: HttpServletResponse) {
    app.redirect("/login") // TODO user scentryConfig
  }

  override def afterAuthenticate(winningStrategy: String, user: User)(implicit request: HttpServletRequest, response: HttpServletResponse) {
    logger.info("afterAuth fired")

    if (winningStrategy != "RememberMe") {
      val token = "hash:userid"
      app.cookies.set(COOKIE_KEY, token)(CookieOptions(maxAge = COOKIE_EXPIRE, path = cookiePath))
    }
  }

  override def beforeLogout(user: User)(implicit request: HttpServletRequest, response: HttpServletResponse) {
    logger.info("beforeLogout")
    app.cookies.delete(COOKIE_KEY)(CookieOptions(path = cookiePath))
  }

}
