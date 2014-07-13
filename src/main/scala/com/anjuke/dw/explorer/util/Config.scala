package com.anjuke.dw.explorer.util

import org.apache.commons.configuration._

object Config {

  private val configMap = Seq("common", "database", "auth", "service").map(section => {
    val config = new CompositeConfiguration
    try {
      config.addConfiguration(new PropertiesConfiguration(s"override/${section}.properties"))
    } catch {
      case e: ConfigurationException =>
    }
    config.addConfiguration(new PropertiesConfiguration(s"${section}.properties"))
    (section, config: Configuration)
  }).toMap

  def apply(section: String, name: String) = configMap(section).getString(name)

}
