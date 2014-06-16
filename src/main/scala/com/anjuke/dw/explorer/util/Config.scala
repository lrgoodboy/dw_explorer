package com.anjuke.dw.explorer.util

import org.apache.commons.configuration._

object Config {

  private val configMap = Seq("database", "auth").map(section => {
    val config = new CompositeConfiguration
    config.addConfiguration(new PropertiesConfiguration(s"${section}.properties"))
    try {
      config.addConfiguration(new PropertiesConfiguration(s"override/${section}.properties"))
    } catch {
      case e: ConfigurationException =>
    }
    (section, config: Configuration)
  }).toMap

  def apply(section: String, name: String) = configMap(section).getString(name)

}
