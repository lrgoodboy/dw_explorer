package com.anjuke.dw.explorer.models

import java.sql.Timestamp

import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode.inTransaction
import org.squeryl.Schema

class Task(val queries: String,
           val created: Timestamp) extends KeyedEntity[Long] {
  val id: Long = 0
}

object Task {

  def create(task: Task): Option[Long] = {
    inTransaction {
      QueryEditor.tasks.insert(task) match {
        case task if task.isPersisted => Some(task.id)
        case _ => None
      }
    }
  }

}

object QueryEditor extends Schema {

  override def tableNameFromClassName(n: String) =
    "query_" + NamingConventionTransforms.snakify(n)

  override def columnNameFromPropertyName(n: String) =
    NamingConventionTransforms.snakify(n)

  val tasks = table[Task]

}
