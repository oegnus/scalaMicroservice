package pl.bitgrind.messages

import pl.bitgrind.messages.MessageValidation._
import pl.bitgrind.messages.Messages._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scalaz._

class MessageSlick3Repository(db: slick.jdbc.JdbcBackend#DatabaseDef, t: Tables, maxResults: Int) {
  import t.profile.api._

  def ok = Right(Result(None, None))
  def errNotFound = Left(Result(Some(NOT_FOUND), None))
  def errValidation(validationErrors: List[String]) = Left(Result(Some(VALIDATION), Some(validationErrors)))

  def find(msgId: MessageId): Future[Either[Result, Message]] =
    db.run(
      t.messages.filter(_.id === msgId).result.headOption
    ) map {
      case Some(msg) => Right(msg)
      case None => errNotFound
    }

  def list(msgId: MessageId, beforeOpt: Option[Int], afterOpt: Option[Int]): Future[Either[Result, List[Message]]] = {
    val sortedQuery = t.messages.sortBy(_.id.asc)
    db.run(sortedQuery.filter(_.id === msgId).result.headOption) flatMap {
      case Some(m) =>
        val indexQuery = sortedQuery.filter(_.id < msgId).length.result
        db.run(indexQuery) flatMap { index =>
          val before = index min (0 max beforeOpt.getOrElse(0))
          val after = 0 max afterOpt.getOrElse(0)
          val range = before + 1 + after
          // make sure that element is in range:
          val validBefore: Int = if (range > maxResults) before * maxResults / range else before
          val offset = index - validBefore
          val pageSize = (validBefore + 1 + after) min maxResults
          db.run(sortedQuery.drop(offset).take(pageSize).result) flatMap { res =>
            Future.successful(Right(res.toList))
          }
        }
      case None =>
        Future.successful(errNotFound)
    }
  }

  def list: Future[List[Message]] =
    db.run(
      t.messages.sortBy(_.id.asc).take(maxResults).result
    ) map (_.toList)

  def remove(msgId: MessageId): Future[Either[Result, Result]] =
    db.run(
      t.messages.filter(_.id === msgId).delete
    ) map { _ => ok }

  def add(msg: UnpersistedMessage): Future[Either[Result, Result]] =
    validate(msg) match {
      case Success(validMsg) =>
        db.run(
          t.messages += messageFromUnpersisted(validMsg)
        ) map { _ => ok }
      case Failure(errors) =>
        Future.successful(errValidation(errors.list))
    }

  def update(msgId: MessageId, msg: UnpersistedMessage): Future[Either[Result, Result]] =
    validate(messageFromUnpersisted(msg)) match {
      case Success(validMsg) =>
        db.run(
          t.messages.filter(_.id === msgId).update(validMsg)
        ) map {
          case 0 => errNotFound
          case num => ok
        }
      case Failure(errors) =>
        Future.successful(errValidation(errors.list))
    }

  def patch(msgId: MessageId, msgPatch: MessagePatch): Future[Either[Result, Result]] =
    db.run(
      t.messages.filter(_.id === msgId).result.headOption
    ) flatMap {
      case Some(originalMsg) =>
        validate(patchMessage(originalMsg, msgPatch)) match {
          case Success(validMsg) =>
            db.run(
              t.messages
              .filter(_.id === msgId)
              .update(validMsg)
            ) map { _ => ok }
          case Failure(errors) =>
            Future.successful(errValidation(errors.list))
        }
      case None =>
        Future.successful(errNotFound)
    }

  def createTable =
    db.run(t.messages.schema.create)

  def loadFixtures(fixtures: Seq[UnpersistedMessage]) =
    db.run(
      t.messages.schema.drop >>
      t.messages.schema.create >>
      (t.messages ++= fixtures map messageFromUnpersisted)
    )
}
