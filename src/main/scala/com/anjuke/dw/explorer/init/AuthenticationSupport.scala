package com.anjuke.dw.explorer.init

import org.scalatra.ScalatraBase
import org.scalatra.auth.ScentrySupport
import org.slf4j.LoggerFactory
import com.anjuke.dw.explorer.models.User
import org.scalatra.auth.ScentryConfig
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import java.net.URLEncoder

trait AuthenticationSupport extends ScentrySupport[User] {
  self: ScalatraBase =>

  protected def fromSession = { case id: String => User.lookup(id.toLong).get }
  protected def toSession = { case user: User => user.id.toString }

  protected val scentryConfig = (new ScentryConfig {
    override val login = "/login"
    override val returnTo = "/"
    override val returnToKey = "returnTo"
  }).asInstanceOf[ScentryConfiguration]

  private val logger = LoggerFactory.getLogger(getClass)

  protected def requireLogin() = {
    if (!isAuthenticated) {
      val queryString = Option(request.getQueryString) match {
        case Some(s) => "?" + s
        case None => ""
      }
      val requestUrl = request.getRequestURL.toString + queryString
      val returnTo = URLEncoder.encode(requestUrl, "UTF-8")
      redirect(s"${scentryConfig.login}?${scentryConfig.returnToKey}=$returnTo")
    }
  }

  override protected def registerAuthStrategies() = {
    scentry.register("AnjukeAuth", app => new AnjukeAuthStrategy(app, scentryConfig))
    scentry.register("RememberMe", app => new RememberMeStrategy(app, scentryConfig))
  }

}
