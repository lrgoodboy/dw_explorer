package com.anjuke.dw.explorer

import org.scalatra.ScalatraServlet
import org.eclipse.jetty.websocket.WebSocketServlet
import javax.servlet.http.HttpServletRequest
import org.eclipse.jetty.websocket.WebSocket
import org.eclipse.jetty.websocket.WebSocket.Connection

class QueryTaskServlet extends WebSocketServlet {

  def doWebSocketConnect(request: HttpServletRequest, protocol: String): WebSocket = {
    new QueryTaskWebSocket
  }

  class QueryTaskWebSocket extends WebSocket.OnTextMessage {

    def onOpen(connection: Connection): Unit = {
      println("Connected " + connection.toString)
    }

    def onClose(closeCode: Int, message: String): Unit = {
      println("Disconnected")
    }

    def onMessage(data: String): Unit = {
      println("Receive " + data)
    }

  }

}
