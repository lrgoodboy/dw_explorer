package com.anjuke.dw.explorer.init

import org.scalatra.ScalatraBase
import javax.servlet.http.HttpServletResponse
import org.scalatra.auth.ScentryStrategy
import javax.servlet.http.HttpServletRequest
import com.anjuke.dw.explorer.models.User
import org.slf4j.LoggerFactory

class UserPasswordStrategy(protected val app: ScalatraBase)(implicit request: HttpServletRequest, response: HttpServletResponse)
    extends ScentryStrategy[User] {

  val logger = LoggerFactory.getLogger(getClass)

  override def name: String = "UserPassword"

  private def login = app.params.getOrElse("login", "")
  private def password = app.params.getOrElse("password", "")

  override def isValid(implicit request: HttpServletRequest) = {
    val isValid = login != "" && password != ""
    logger.info("determining isValid: " + isValid.toString)
    isValid
  }

  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] = {
    logger.info("attempting authentication");

    if (login == "foo" && password == "foo") {
      logger.info("login succeeded")
      Some(User("foo"))
    } else{
      logger.info("login failed")
      None
    }
  }

  override def unauthenticated()(implicit request: HttpServletRequest, response: HttpServletResponse) {
    app.redirect("/sessions/new")
  }

}
