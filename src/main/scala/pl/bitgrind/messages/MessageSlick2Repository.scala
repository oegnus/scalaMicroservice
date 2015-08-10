package pl.bitgrind.messages

import scalaz._
import MessageValidation._
import Messages._

class MessageSlick2Repository(t: Tables, maxResults: Int) {
  import t.profile.simple._

  type S = scala.slick.jdbc.JdbcBackend#SessionDef

  def ok = Right(Result(None, None))
  def errNotFound = Left(Result(Some(NOT_FOUND), None))
  def errValidation(validationErrors: List[String]) = Left(Result(Some(VALIDATION), Some(validationErrors)))

  def find(msgId: MessageId)(implicit session: S): Either[Result, Message] =
    t.messages.filter(_.id === msgId).firstOption match {
      case Some(m) => Right(m)
      case None => errNotFound
    }

  def list(msgId: MessageId, beforeOpt: Option[Int], afterOpt: Option[Int])(implicit session: S): Either[Result, List[Message]] = {
    val sorted = t.messages.sortBy(_.id.asc)
    sorted.filter(_.id === msgId).firstOption match {
      case Some(m) =>
        val index = sorted.filter(_.id < msgId).length.run
        val before = index min (0 max beforeOpt.getOrElse(0))
        val after = 0 max afterOpt.getOrElse(0)
        val range = before + 1 + after
        // make sure that element is in range:
        val validBefore: Int = if (range > maxResults) before * maxResults / range else before
        val offset = index - validBefore
        val pageSize = (validBefore + 1 + after) min maxResults
        Right(sorted.drop(offset).take(pageSize).list)
      case None =>
        errNotFound
    }
  }

  def list(implicit session: S): List[Message] =
    t.messages.sortBy(_.id.asc).take(maxResults).list

  def remove(msgId: MessageId)(implicit session: S): Either[Result, Result] = {
    t.messages.filter(_.id === msgId).delete
    ok
  }

  def add(msg: UnpersistedMessage)(implicit session: S) =
    validate(msg) match {
      case Success(validMsg) =>
        t.messages.insert(messageFromUnpersisted(validMsg))
        ok
      case Failure(errors) =>
        errValidation(errors.list)
    }

  def update(msgId: MessageId, msg: UnpersistedMessage)(implicit session: S): Either[Result, Result] =
    validate(messageFromUnpersisted(msg)) match {
      case Success(validMsg) =>
        t.messages.filter(_.id === msgId).update(validMsg) match {
          case 0 => errNotFound
          case _ => ok
        }
      case Failure(errors) =>
        errValidation(errors.list)
    }

  def patch(msgId: MessageId, msgPatch: MessagePatch)(implicit session: S): Either[Result, Result] =
    t.messages.filter(_.id === msgId).firstOption match {
      case Some(originalMsg) =>
        validate(patchMessage(originalMsg, msgPatch)) match {
          case Success(validMsg) =>
            t.messages
              .filter(_.id === msgId)
              .update(validMsg)
            ok
          case Failure(errors) =>
            errValidation(errors.list)
        }
      case None =>
        errNotFound
    }

  def createTable(implicit session: S) =
    t.messages.ddl.create

  def loadFixtures(fixtures: Seq[UnpersistedMessage])(implicit session: S) = {
    t.messages.ddl.drop
    t.messages.ddl.create
    t.messages ++= fixtures map messageFromUnpersisted
  }
}
