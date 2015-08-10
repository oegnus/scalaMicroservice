package pl.bitgrind.messages

import scala.slick.driver.H2Driver.simple._
import com.typesafe.config.ConfigFactory

object Messages extends MessagesDb {
  import scalaz._
  import MessageValidation._

  val config = ConfigFactory.load()
  val maxResults = 1 max config.getInt("messages.maxResults")

  sealed trait ErrorCode { def name: String }
  case object NOT_FOUND extends ErrorCode { val name = "NOT_FOUND" }
  case object VALIDATION extends ErrorCode { val name = "VALIDATION" }

  case class Result(error: Option[ErrorCode], errors: Option[List[String]])

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

  def update(msgId: MessageId, msg: Message): Either[Result, Result] =
    updateOrPatch(msgId, Left(msg))

  def patch(msgId: MessageId, patch: MessagePatch): Either[Result, Result] =
    updateOrPatch(msgId, Right(patch))

  private def updateOrPatch(msgId: MessageId, msgOrPatch: Either[Message, MessagePatch] ): Either[Result, Result] =
    messages.filter(_.id === msgId).firstOption match {
      case Some(originalMsg) =>
        val newMsg = msgOrPatch match {
          case Left(msg) => msg
          case Right(msgPatch) => patchMessage(originalMsg, msgPatch)
        }
        validate(newMsg) match {
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
}

trait MessagesDb {
  type UserId = Int
  type MessageId = Int

  trait BaseMessage {
    def toUser: UserId
    def fromUser: UserId
    def content: String
  }
  case class Message(id: MessageId, toUser: UserId, fromUser: UserId, content: String) extends BaseMessage
  case class UnpersistedMessage(toUser: UserId, fromUser: UserId, content: String) extends BaseMessage

  case class MessagePatch(toUser: Option[UserId], fromUser: Option[UserId], content: Option[String])

  class MessagesTable(tag: Tag) extends Table[Message](tag, "MESSAGES") {
    def id = column[MessageId]("MSG_ID", O.PrimaryKey, O.AutoInc)
    def toUser = column[UserId]("FROM_USER")
    def fromUser = column[UserId]("TO_USER")
    def content = column[String]("CONTENT")
    def * = (id, toUser, fromUser, content) <>(Message.tupled, Message.unapply)
  }

  def patchMessage(msg: Message, patch: MessagePatch): Message =
    Message(
      msg.id,
      patch.toUser.getOrElse(msg.toUser),
      patch.fromUser.getOrElse(msg.fromUser),
      patch.content.getOrElse(msg.content)
    )

  def messageFromUnpersisted(uMsg: UnpersistedMessage): Message =
    Message(
      0,
      uMsg.toUser,
      uMsg.fromUser,
      uMsg.content
    )

  val db = Database.forURL("jdbc:h2:mem:messages", driver = "org.h2.Driver")
  implicit val session: Session = db.createSession()
  val messages = TableQuery[MessagesTable]
  messages.ddl.create

  def loadFixtures(fixtures: Seq[UnpersistedMessage]) = {
    messages.ddl.drop
    messages.ddl.create
    messages ++= fixtures map messageFromUnpersisted
  }
}
