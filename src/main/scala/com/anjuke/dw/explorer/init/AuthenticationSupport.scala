package com.anjuke.dw.explorer.init

import org.scalatra.ScalatraBase
import org.scalatra.auth.ScentrySupport
import org.slf4j.LoggerFactory
import com.anjuke.dw.explorer.models.User
import org.scalatra.auth.ScentryConfig
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

trait AuthenticationSupport extends ScentrySupport[User] {
  self: ScalatraBase =>

  protected def fromSession = { case id: String => User.lookup(id.toLong).get }
  protected def toSession = { case user: User => user.id.toString }

  protected val scentryConfig = (new ScentryConfig {
    override val login = "/login"
  }).asInstanceOf[ScentryConfiguration]

  private val logger = LoggerFactory.getLogger(getClass)

  protected def requireLogin() = {
    if (!isAuthenticated) {
      redirect(scentryConfig.login)
    }
  }

  override protected def configureScentry = {
    scentry.unauthenticated {
      halt(403)
    }
  }

  override protected def registerAuthStrategies = {
    scentry.register("AnjukeAuth", app => new AnjukeAuthStrategy(app))
    scentry.register("RememberMe", app => new RememberMeStrategy(app))
  }

}
