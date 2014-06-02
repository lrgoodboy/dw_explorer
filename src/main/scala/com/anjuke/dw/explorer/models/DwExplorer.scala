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

  def lookup(id: Long) = users.lookup(id)

}

object DwExplorer extends Schema {

  override def columnNameFromPropertyName(n: String) =
    NamingConventionTransforms.snakify(n)

  val users = table[User]

}
