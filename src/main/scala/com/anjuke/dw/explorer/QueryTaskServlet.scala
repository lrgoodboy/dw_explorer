package com.anjuke.dw.explorer

import org.scalatra.ScalatraServlet
import org.eclipse.jetty.websocket.WebSocketServlet
import javax.servlet.http.HttpServletRequest
import org.eclipse.jetty.websocket.WebSocket
import org.eclipse.jetty.websocket.WebSocket.Connection
import org.slf4j.LoggerFactory
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonDSL._
import com.anjuke.dw.explorer.init.RememberMeStrategy

class QueryTaskServlet extends WebSocketServlet {

  def doWebSocketConnect(request: HttpServletRequest, protocol: String): WebSocket = {
    new QueryTaskWebSocket
  }

}

sealed class QueryTaskWebSocket extends WebSocket.OnTextMessage {

  private val logger = LoggerFactory.getLogger(getClass)

  protected implicit val jsonFormats: Formats = DefaultFormats

  private[this] var connection: Connection = null

  def onOpen(connection: Connection): Unit = {
    this.connection = connection
    logger.info(s"Connected ${connection.hashCode}")
  }

  def onClose(closeCode: Int, message: String): Unit = {
    logger.info(s"Disconnected ${connection.hashCode}, closeCode: ${closeCode}, message: ${message}")
  }

  def onMessage(data: String): Unit = {
    val request = parse(data)
    (request \ "action").extract[String] match {
      case "authenticate" => {
        val token = (request \ "token").extract[String]
        RememberMeStrategy.validateToken(token) match {
          case Some(user) => connection.sendMessage(s"Hello, ${user.truename}!")
          case None => connection.sendMessage("Invalid token.")
        }
      }
    }
  }

}
