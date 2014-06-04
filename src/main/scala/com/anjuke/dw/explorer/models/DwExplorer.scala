package com.anjuke.dw.explorer.models

import java.sql.Timestamp

import org.squeryl._, PrimitiveTypeMode._

import org.slf4j.LoggerFactory

class User(val username: String,
           val truename: String,
           val email: String,
           val created: Timestamp) extends KeyedEntity[Long] {
  val id: Long = 0
}

object User {

  import DwExplorer._

  private val logger = LoggerFactory.getLogger(getClass)

  def lookup(id: Long) = {
    inTransaction {
      users.lookup(id)
    }
  }

  def lookup(username: String) = {
    inTransaction {
      users.where(user => user.username === username).headOption
    }
  }

  def create(user: User) = {
    inTransaction {
      users.insert(user) match {
        case user if user.isPersisted => Some(user)
        case _ => None
      }
    }
  }

}

object DwExplorer extends Schema {

  override def tableNameFromClassName(n: String) =
    NamingConventionTransforms.snakify(n)

  override def columnNameFromPropertyName(n: String) =
    NamingConventionTransforms.snakify(n)

  val users = table[User]

}
