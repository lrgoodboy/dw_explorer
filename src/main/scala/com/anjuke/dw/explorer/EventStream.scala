package com.anjuke.dw.explorer

import akka.actor.{Actor, Props, ActorSystem, ActorRef}

sealed class Subscriber(f: (String, Any) => Unit) extends Actor {
  def receive = {
    case (topic: String, payload: Any) => f(topic, payload)
  }
}

class EventStream(system: ActorSystem) {

  def subscribe(f: (String, Any) => Unit): ActorRef = {

    val subscriber = system.actorOf(Props(new Subscriber(f)))

    if (system.eventStream.subscribe(subscriber, classOf[(String, Any)])) {
      println("Subscribe " + subscriber.toString)
      subscriber
    } else {
      throw new Exception("Fail to subscribe.")
    }
  }

  def publish(topic: String, payload: Any): Unit = {
    system.eventStream.publish(topic, payload)
  }

  def unsubscribe(subscriber: ActorRef): Unit = {
    system.eventStream.unsubscribe(subscriber)
    println("Unsubscribe " + subscriber.toString)
  }

}
