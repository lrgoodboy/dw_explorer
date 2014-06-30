package com.anjuke.dw.explorer.models

import java.sql.Timestamp

import org.squeryl._, PrimitiveTypeMode._

import org.slf4j.LoggerFactory

class User(val username: String,
           val truename: String,
           val email: String,
           val role: Int,
           val created: Timestamp) extends KeyedEntity[Long] {
  val id: Long = 0

  def isRole(role: Int): Boolean = role match {
    case User.ROLE_GUEST => true
    case User.ROLE_ADMIN => Seq(User.ROLE_ADMIN) contains this.role
    case User.ROLE_DW => Seq(User.ROLE_ADMIN, User.ROLE_DW) contains this.role
    case User.ROLE_BI => Seq(User.ROLE_ADMIN, User.ROLE_DW, User.ROLE_BI) contains this.role
  }

}

object User {

  import DwExplorer._

  val ROLE_GUEST = 0
  val ROLE_ADMIN = 1
  val ROLE_DW = 2
  val ROLE_BI = 3

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
