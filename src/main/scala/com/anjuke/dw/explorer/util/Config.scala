package com.anjuke.dw.explorer.util

import org.apache.commons.configuration.CompositeConfiguration
import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.commons.configuration.Configuration

object Config {

  private val configMap = Seq("database", "auth").map(section => {
    val config = new CompositeConfiguration
    config.addConfiguration(new PropertiesConfiguration(s"${section}.properties"))
    config.addConfiguration(new PropertiesConfiguration(s"override/${section}.properties"))
    (section, config: Configuration)
  }).toMap

  def apply(section: String, name: String) = configMap(section).getString(name)

}
