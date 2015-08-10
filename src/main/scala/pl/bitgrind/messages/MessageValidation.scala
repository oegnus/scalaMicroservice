package pl.bitgrind.messages

import Messages._

object MessageValidation {
  import scalaz._
  import scalaz.Scalaz._

  type StringValidation[T] = ValidationNel[String, T]

  private def validUserId(userId: Int): StringValidation[Int] =
    if (userId <= 0) "INVALID_USER_ID".failureNel
    else userId.successNel

  private def validContent(content: String): StringValidation[String] =
    if (content.length < 3 || content.length > 200) "CONTENT_TOO_SHORT".failureNel
    else content.successNel

  def validate[T <: BaseMessage](message: T): StringValidation[T] = {
    (validUserId(message.toUser)
      |@| validUserId(message.fromUser)
      |@| validContent(message.content))((_, _, _) => message)
  }
}
