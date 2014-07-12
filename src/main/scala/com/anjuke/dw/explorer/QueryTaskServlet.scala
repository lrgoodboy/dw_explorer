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
import com.anjuke.dw.explorer.models.User
import akka.actor.{ActorSystem, Props, Actor, ActorRef, PoisonPill}

class QueryTaskServlet extends WebSocketServlet {

  def doWebSocketConnect(request: HttpServletRequest, protocol: String): WebSocket = {
    val actorSystem = getServletContext.getAttribute("actorSystem").asInstanceOf[ActorSystem]
    new QueryTaskWebSocket(actorSystem)
  }

}

sealed class QueryTaskWebSocket(actorSystem: ActorSystem) extends WebSocket.OnTextMessage {

  protected implicit val jsonFormats: Formats = DefaultFormats
  private val logger = LoggerFactory.getLogger(getClass)
  private var connection: Connection = null
  private var user: User = null
  private var subscriber: ActorRef = null

  def onOpen(connection: Connection): Unit = {
    this.connection = connection
    logger.info(s"Connected ${connection.hashCode}")
  }

  def onClose(closeCode: Int, message: String): Unit = {
    unsubscribe
    logger.info(s"Disconnected ${connection.hashCode}, closeCode: ${closeCode}, message: ${message}")
  }

  def onMessage(data: String): Unit = {
    try {
      process(data)
    } catch {
      case e: Exception => {
        logger.info("Fail to process message: ${data}", e)
        sendMessage("error", Map("msg" -> e.toString))
      }
    }
  }

  private def process(data: String): Unit = {

    val request = parse(data)

    (request \ "action").extract[String] match {
      case "subscribe" =>
        val token = (request \ "token").extract[String]
        RememberMeStrategy.validateToken(token) match {
          case Some(user) =>
            this.user = user
            subscribe
          case None => throw new Exception("Invalid token.")
        }
    }
  }

  private def sendMessage(status: String, data: Map[String, JValue]): Unit = {
    val message = compact(render(data + ("status" -> JString(status))))
    connection.sendMessage(message)
  }

  private def subscribe: Unit = {

    subscriber = actorSystem.actorOf(Props(new Subscriber(connection, user)))

    if (actorSystem.eventStream.subscribe(subscriber, classOf[TaskEvent])) {
      logger.info("Subscribed user: " + user.id)
    } else {
      subscriber ! PoisonPill
      throw new Exception("Fail to subscribe.")
    }

  }

  private def unsubscribe: Unit = {
    if (subscriber != null) {
      actorSystem.eventStream.unsubscribe(subscriber)
      subscriber ! PoisonPill
    }
  }

}

sealed class Subscriber(connection: Connection, user: User) extends Actor {

  def receive = {
    case TaskEvent(task) if task.userId == user.id =>
      val data = ("task" -> QueryEditorServlet.formatTask(task))
      connection.sendMessage(compact(render(data)))
  }

}
