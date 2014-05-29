package com.anjuke.dw.explorer

import akka.event.Logging
import akka.actor.Actor

class TaskActor extends Actor {
  val logger = Logging(context.system, this)

  override def preStart = {
    logger.info("taskActor started")
  }

  override def postStop = {
    logger.info("taskActor stopped")
  }

  def receive = {
    case taskId: Long => process(taskId)
    case _ => logger.debug("Unkown message.")
  }

  def process(taskId: Long) {
    logger.info("Received task id: " + taskId)
  }

}
