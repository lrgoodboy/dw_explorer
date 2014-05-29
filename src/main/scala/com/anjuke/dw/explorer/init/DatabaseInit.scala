package com.anjuke.dw.explorer.init

import org.squeryl.Session
import org.squeryl.SessionFactory
import org.squeryl.adapters.MySQLAdapter

import com.mchange.v2.c3p0.ComboPooledDataSource

trait DatabaseInit {

  val databaseUsername = "root"
  val databasePassword = "password"
  val databaseConnection = "jdbc:mysql://localhost:3306/dw_explorer?useUnicode=true&characterEncoding=utf-8"

  var cpds = new ComboPooledDataSource

  def configureDb() {
    cpds.setDriverClass("com.mysql.jdbc.Driver")
    cpds.setJdbcUrl(databaseConnection)
    cpds.setUser(databaseUsername)
    cpds.setPassword(databasePassword)

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
