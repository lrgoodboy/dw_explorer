package com.anjuke.dw.explorer.init

import org.scalatra.ScalatraBase
import javax.servlet.http.HttpServletResponse
import org.scalatra.auth.ScentryStrategy
import javax.servlet.http.HttpServletRequest
import com.anjuke.dw.explorer.models.User
import org.scalatra.CookieOptions
import org.slf4j.LoggerFactory
import org.scalatra.auth.ScentryConfig
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac

object RememberMeStrategy {
  val COOKIE_KEY = "dw_explorer_auth"
  val COOKIE_EXPIRE = 7 * 24 * 3600
  val PTRN_TOKEN = "^([0-9]+)_(.*)$".r
}

class RememberMeStrategy(protected val app: ScalatraBase, protected val scentryConfig: ScentryConfig)
    (implicit request: HttpServletRequest, response: HttpServletResponse)
    extends ScentryStrategy[User] {

  val logger = LoggerFactory.getLogger(getClass)

  override def name: String = "RememberMe"

  import RememberMeStrategy._

  val cookiePath = request.getContextPath

  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse) = {
    logger.info("attempting authentication")

    app.cookies.get(COOKIE_KEY) match {
      case Some(token) =>
        PTRN_TOKEN findFirstIn token match {
          case Some(PTRN_TOKEN(userId, signature)) =>
            if (signature == hmac(userId)) User.lookup(userId.toLong) else None
          case _ => None
        }
      case None => None
    }
  }

  override def afterAuthenticate(winningStrategy: String, user: User)(implicit request: HttpServletRequest, response: HttpServletResponse) {
    if (winningStrategy != name) {
      logger.info("afterAuth fired")
      val token = s"${user.id}_${hmac(user.id.toString)}"
      app.cookies.set(COOKIE_KEY, token)(CookieOptions(maxAge = COOKIE_EXPIRE, path = cookiePath))
    }
  }

  override def beforeLogout(user: User)(implicit request: HttpServletRequest, response: HttpServletResponse) {
    logger.info("beforeLogout")
    app.cookies.delete(COOKIE_KEY)(CookieOptions(path = cookiePath))
  }

  val HMAC_KEY = "dwrocks"
  val HMAC_ALGORITHM = "HmacMD5"

  def hmac(input: String): String = {
    val mac = Mac.getInstance(HMAC_ALGORITHM)
    mac.init(new SecretKeySpec(HMAC_KEY.getBytes, HMAC_ALGORITHM))
    mac.doFinal(input.getBytes).map("%02x".format(_)).mkString
  }

}
