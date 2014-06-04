package com.anjuke.dw.explorer.models

import java.sql.Timestamp

import org.squeryl._
import org.squeryl.dsl._
import org.squeryl.PrimitiveTypeMode._

class Task(val userId: Long,
           val queries: String,
           val status: Int,
           val progress: Int,
           val created: Timestamp,
           val updated: Timestamp) extends KeyedEntity[Long] {
  val id: Long = 0

  lazy val stmts: OneToMany[Stmt] = QueryEditor.taskToStmts.left(this)
}

object Task {

  import QueryEditor._

  val STATUS_NEW = 1
  val STATUS_RUNNING = 2
  val STATUS_OK = 3
  val STATUS_ERROR = 4

  def create(task: Task): Option[Task] = {
    inTransaction {
      tasks.insert(task) match {
        case task if task.isPersisted => Some(task)
        case _ => None
      }
    }
  }

}

class Stmt(val taskId: Long,
           val stmt: String,
           val status: Int,
           val progress: Int,
           val created: Timestamp,
           val updated: Timestamp) extends KeyedEntity[Long] {
  val id: Long = 0

  lazy val task: ManyToOne[Task] = QueryEditor.taskToStmts.right(this)
}

object Stmt {

  import QueryEditor._

  val STATUS_NEW = 1
  val STATUS_RUNNING = 2
  val STATUS_OK = 3
  val STATUS_ERROR = 4

  def create(stmt: Stmt): Option[Stmt] = {
    inTransaction {
      stmts.insert(stmt) match {
        case stmt if stmt.isPersisted => Some(stmt)
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

  val stmts = table[Stmt]

  val taskToStmts = oneToManyRelation(tasks, stmts).via((task, stmt) => task.id === stmt.taskId)

}
