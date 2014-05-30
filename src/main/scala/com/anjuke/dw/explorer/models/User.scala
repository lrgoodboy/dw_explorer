package com.anjuke.dw.explorer.models

import org.slf4j.LoggerFactory

case class User(id: String) {
  val logger = LoggerFactory.getLogger(getClass)

  def forgetMe = {
    logger.info("TODO: Invalidate the token")
  }
}
