package pl.bitgrind.messages

import Messages._

trait MessageRepository {
  def find(msgId: MessageId): Either[Result, Message]
  def list(msgId: MessageId, beforeOpt: Option[Int], afterOpt: Option[Int]): Either[Result, List[Message]]
  def list: List[Message]
  def remove(msgId: MessageId): Either[Result, Result]
  def add(msg: UnpersistedMessage): Either[Result, Result]
  def update(msgId: MessageId, msg: UnpersistedMessage): Either[Result, Result]
  def patch(msgId: MessageId, msgPatch: MessagePatch): Either[Result, Result]
  def loadFixtures(fixtures: Seq[UnpersistedMessage])
}
