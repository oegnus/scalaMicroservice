package pl.bitgrind.messages

object Messages {
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

  sealed trait ErrorCode { def name: String }
  case object NOT_FOUND extends ErrorCode { val name = "NOT_FOUND" }
  case object VALIDATION extends ErrorCode { val name = "VALIDATION" }

  case class Result(error: Option[ErrorCode], errors: Option[List[String]])

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
}
