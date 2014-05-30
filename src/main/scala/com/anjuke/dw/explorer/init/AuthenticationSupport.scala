package com.anjuke.dw.explorer.init

import org.scalatra.ScalatraBase
import org.scalatra.auth.ScentryConfig
import org.scalatra.auth.ScentrySupport
import org.slf4j.LoggerFactory

import com.anjuke.dw.explorer.models.User

trait AuthenticationSupport extends ScalatraBase with ScentrySupport[User] {
  self: ScalatraBase =>

  protected def fromSession = { case id: String => User(id) }
  protected def toSession = { case user: User => user.id }

  protected val scentryConfig = (new ScentryConfig {
    override val login = "/sessions/new"
  }).asInstanceOf[ScentryConfiguration]

  private val logger = LoggerFactory.getLogger(getClass)

  protected def requireLogin() = {
    if (!isAuthenticated) {
      redirect(scentryConfig.login)
    }
  }

  override protected def configureScentry = {
    scentry.unauthenticated {
      scentry.strategies("UserPassword").unauthenticated()
    }
  }

  override protected def registerAuthStrategies = {
    scentry.register("UserPassword", app => new UserPasswordStrategy(app))
    scentry.register("RememberMe", app => new RememberMeStrategy(app))
  }

}
