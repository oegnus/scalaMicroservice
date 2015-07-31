package pl.bitgrind.messages

import pl.bitgrind.messages.Types._

object MessageValidation {
  import scalaz._
  import scalaz.Scalaz._

  type StringValidation[T] = ValidationNel[String, T]

  def validate(message: Message): StringValidation[Message] = {
    def validUserId(userId: Int): StringValidation[Int] =
      if (userId <= 0) "INVALID_USER_ID".failureNel
      else userId.successNel

    def validContent(content: String): StringValidation[String] =
      if (content.length < 3 || content.length > 200) "CONTENT_TOO_SHORT".failureNel
      else content.successNel

    (validUserId(message.toUser)
      |@| validUserId(message.fromUser)
      |@| validContent(message.content))((_, _, _) => message)
  }
}
