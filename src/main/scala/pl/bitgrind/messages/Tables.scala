package pl.bitgrind.messages

import pl.bitgrind.messages.Messages._
import slick.driver._

class Tables(val profile: JdbcProfile) {
  import profile.api._

  class MessagesTable(tag: Tag) extends Table[Message](tag, "MESSAGES") {
    def id = column[MessageId]("MSG_ID", O.PrimaryKey, O.AutoInc)
    def toUser = column[UserId]("TO_USER")
    def fromUser = column[UserId]("FROM_USER")
    def content = column[String]("CONTENT")
    def * = (id, toUser, fromUser, content) <>(Message.tupled, Message.unapply)
  }

  val messages = TableQuery[MessagesTable]
}