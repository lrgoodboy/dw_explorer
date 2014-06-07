package com.anjuke.dw.explorer.models

import java.sql.Timestamp
import java.util.Date
import java.util.Calendar

import org.squeryl._
import org.squeryl.dsl._
import org.squeryl.PrimitiveTypeMode._
import org.slf4j.LoggerFactory

class Task(val userId: Long,
           val queries: String,
           val status: Int,
           val progress: Int,
           val duration: Int,
           val created: Timestamp,
           val updated: Timestamp) extends KeyedEntity[Long] {
  val id: Long = 0

  lazy val stmts: OneToMany[Stmt] = QueryEditor.taskToStmts.left(this)
}

object Task {

  import QueryEditor._

  private val logger = LoggerFactory.getLogger(getClass)

  val STATUS_NEW = 1
  val STATUS_RUNNING = 2
  val STATUS_OK = 3
  val STATUS_ERROR = 4
  val STATUS_DELETED = 99

  def lookup(id: Long): Option[Task] = {
    inTransaction {
      tasks.lookup(id)
    }
  }

  def create(task: Task): Option[Task] = {
    inTransaction {
      tasks.insert(task) match {
        case task if task.isPersisted => Some(task)
        case _ => None
      }
    }
  }

  def findList(userId: Long,
               statusSeq: Option[Seq[Int]] = None,
               createdStart: Option[Date] = None,
               createdEnd: Option[Date] = None,
               offset: Int = 0,
               limit: Int = 24): List[Task] = {

    var query = from(tasks)(task =>
      where(
        task.userId === userId and
        (task.status in statusSeq.getOrElse(Seq(STATUS_NEW, STATUS_RUNNING, STATUS_OK, STATUS_ERROR))) and
        task.created >= createdStart.map(date => new Timestamp(date.getTime)).? and
        task.created <= createdEnd.map(date => new Timestamp(date.getTime)).?
      )
      select(task)
      orderBy(task.created desc)
    ).page(offset, limit)

    query.toList
  }

  def findToday(userId: Long, updated: Option[Date]) = {
    from(tasks)(task =>
      where(
        task.userId === userId and
        task.status <> STATUS_DELETED and
        task.created >= new Timestamp(midnight.getTime) and
        task.updated >= updated.map(date => new Timestamp(date.getTime)).?
      )
      select(task)
      orderBy(task.created desc)
    ).toList
  }

  def updateStatus(taskId: Long, status: Int, progress: Option[Int] = None, duration: Option[Int] = None) = {
    inTransaction {
      update(tasks)(task =>
        where(task.id === taskId)
        set(List(
          Some(task.status := status),
          progress.map(task.progress := _),
          duration.map(task.duration := _)
        ).flatten:_*)
      )
    }
  }

  private def midnight = {
    val calendar = Calendar.getInstance
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    calendar.getTime
  }

}

class Stmt(val taskId: Long,
           val stmt: String,
           val status: Int,
           val progress: Int,
           val duration: Int,
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
