package com.anjuke.dw.explorer.init

import org.squeryl.Session
import org.squeryl.SessionFactory
import org.squeryl.adapters.MySQLAdapter
import com.mchange.v2.c3p0.ComboPooledDataSource
import com.anjuke.dw.explorer.util.Config

trait DatabaseInit {

  var cpds = new ComboPooledDataSource

  def configureDb() {
    cpds.setDriverClass(Config("database", "jdbc.driver"))
    cpds.setJdbcUrl(Config("database", "jdbc.url"))
    cpds.setUser(Config("database", "jdbc.user"))
    cpds.setPassword(Config("database", "jdbc.password"))

    cpds.setMinPoolSize(2)
    cpds.setAcquireIncrement(1)
    cpds.setMaxPoolSize(3)

    SessionFactory.concreteFactory = Some(() => {
      Session.create(cpds.getConnection, new MySQLAdapter)
    })
  }

  def closeDbConnection() {
    cpds.close()
  }
}
