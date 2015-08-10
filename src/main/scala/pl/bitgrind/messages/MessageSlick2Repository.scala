package pl.bitgrind.messages

import scala.slick.driver.H2Driver.simple._
import scalaz._
import MessageValidation._
import Messages._

class MessageSlick2Repository(db: scala.slick.driver.H2Driver.backend.DatabaseDef, maxResults: Int) extends MessageRepository {
  class MessagesTable(tag: Tag) extends Table[Message](tag, "MESSAGES") {
    def id = column[MessageId]("MSG_ID", O.PrimaryKey, O.AutoInc)
    def toUser = column[UserId]("TO_USER")
    def fromUser = column[UserId]("FROM_USER")
    def content = column[String]("CONTENT")
    def * = (id, toUser, fromUser, content) <>(Message.tupled, Message.unapply)
  }

  implicit val session: Session = db.createSession()
  val messages = TableQuery[MessagesTable]
  messages.ddl.create

  def ok = Right(Result(None, None))
  def errNotFound = Left(Result(Some(NOT_FOUND), None))
  def errValidation(validationErrors: List[String]) = Left(Result(Some(VALIDATION), Some(validationErrors)))

  def find(msgId: MessageId): Either[Result, Message] =
    messages.filter(_.id === msgId).firstOption match {
      case Some(m) => Right(m)
      case None => errNotFound
    }

  def list(msgId: MessageId, beforeOpt: Option[Int], afterOpt: Option[Int]): Either[Result, List[Message]] = {
    val sorted = messages.sortBy(_.id.asc)
    sorted.filter(_.id === msgId).firstOption match {
      case Some(m) =>
        val index = sorted.filter(_.id < msgId).length.run
        val before = index min (0 max beforeOpt.getOrElse(0))
        val after = 0 max afterOpt.getOrElse(0)
        val range = before + 1 + after
        // make sure that element is in range:
        val validBefore: Int = if(range > maxResults) before * maxResults / range else before
        val offset = index - validBefore
        val pageSize = (validBefore + 1 + after) min maxResults
        Right(sorted.drop(offset).take(pageSize).list)
      case None =>
        errNotFound
    }
  }

  def list: List[Message] =
    messages.sortBy(_.id.asc).take(maxResults).list

  def remove(msgId: MessageId): Either[Result, Result] = {
    messages.filter(_.id === msgId).delete
    ok
  }

  def add(msg: UnpersistedMessage) =
    validate(msg) match {
      case Success(validMsg) =>
        messages.insert(messageFromUnpersisted(validMsg))
        ok
      case Failure(errors) =>
        errValidation(errors.list)
    }

  def update(msgId: MessageId, msg: UnpersistedMessage): Either[Result, Result] =
    validate(messageFromUnpersisted(msg)) match {
      case Success(validMsg) =>
        messages.filter(_.id === msgId).update(validMsg) match {
          case 0 => errNotFound
          case _ => ok
        }
      case Failure(errors) =>
        errValidation(errors.list)
    }

  def patch(msgId: MessageId, msgPatch: MessagePatch): Either[Result, Result] =
    messages.filter(_.id === msgId).firstOption match {
      case Some(originalMsg) =>
        validate(patchMessage(originalMsg, msgPatch)) match {
          case Success(validMsg) =>
            messages
              .filter(_.id === msgId)
              .update(validMsg)
            ok
          case Failure(errors) =>
            errValidation(errors.list)
        }
      case None =>
        errNotFound
    }

  def loadFixtures(fixtures: Seq[UnpersistedMessage]) = {
    messages.ddl.drop
    messages.ddl.create
    messages ++= fixtures map messageFromUnpersisted
  }
}