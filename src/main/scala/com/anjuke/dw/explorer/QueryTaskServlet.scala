package com.anjuke.dw.explorer

import org.scalatra.json.JacksonJsonSupport
import org.scalatra.ScalatraServlet
import org.scalatra.SessionSupport
import org.scalatra.atmosphere._
import scala.concurrent.ExecutionContext.Implicits.global
import org.json4s._

class QueryTaskServlet extends ScalatraServlet
  with JacksonJsonSupport with SessionSupport
  with AtmosphereSupport {

  protected implicit val jsonFormats: Formats = DefaultFormats

  atmosphere("/list/?") {
    new AtmosphereClient {
      def receive = {
        case Connected => println(s"Connected $uuid")
        case Disconnected(disconnector, _) => println(s"Disconnected $uuid")
        case TextMessage(text) => send(s"ECHO: $text")
        case Error(Some(error)) => println(error.toString)
      }
    }
  }

}
