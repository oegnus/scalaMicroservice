import pl.bitgrind.messages._
import pl.bitgrind.messages.Messages.Message
import pl.bitgrind.messages.Messages.MessagePatch
import akka.event.NoLogging
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest._

class ServiceSpec extends FlatSpec with Matchers with ScalatestRouteTest with Service {
  override def testConfigSource = "akka.loglevel = WARNING"
  override def config = testConfig
  override val logger = NoLogging

  val okResult = ServiceResponse(ok = true, None, None, None)
  val validMessage = Message(None, 1, 1, "content")

  val fixtures = Seq(
    Message(None, 1, 2, "pierwszy"),
    Message(None, 2, 1, "drugi"),
    Message(None, 2, 1, "trzeci"),
    Message(None, 3, 4, "czwarty"),
    Message(None, 2, 1, "piaty"),
    Message(None, 2, 3, "szosty"),
    Message(None, 1, 6, "siodmy"),
    Message(None, 4, 5, "osmy"),
    Message(None, 2, 1, "dziewiaty"),
    Message(None, 5, 1, "dziesiaty")
  )

  // GET
  it should "respond with message" in {
    Messages.loadFixtures(fixtures)

    Get(s"/list/1") ~> routes ~> check {
      contentType shouldBe `application/json`
      responseAs[Message] shouldBe Message(Some(1), 1, 2, "pierwszy")
    }
  }

  it should "respond with list of messages" in {
    Messages.loadFixtures(fixtures)

    Get(s"/list") ~> routes ~> check {
      contentType shouldBe `application/json`
      responseAs[ServiceResponse].results.getOrElse(List()).length shouldBe fixtures.length
    }
  }

  it should "use 'before' and 'after' params" in {
    Messages.loadFixtures(fixtures)

    Get(s"/list?el=4&before=1&after=1") ~> routes ~> check {
      val results = responseAs[ServiceResponse].results.getOrElse(List())
      results.length shouldBe 3
      results.count(_.id == Some(4)) shouldBe 1
    }

    Get(s"/list?el=1&before=50&after=1") ~> routes ~> check {
      val results = responseAs[ServiceResponse].results.getOrElse(List())
      results.length shouldBe 2
      results.count(_.id == Some(1)) shouldBe 1
    }
  }

  // POST
  it should "respond to posting new message" in {
    Messages.loadFixtures(fixtures)

    Post(s"/list", validMessage) ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[ServiceResponse] shouldBe okResult
    }

    Get(s"/list") ~> routes ~> check {
      responseAs[ServiceResponse].results.getOrElse(List()).length shouldBe fixtures.length + 1
    }
  }

  it should "respond with bad request to posting invalid new message" in {
    Post(s"/list", Message(None, 0, 1, "content")) ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[ServiceResponse] shouldBe ServiceResponse(ok = false, Some("VALIDATION"), Some(List("INVALID_USER_ID")), None)
    }

    Post(s"/list", Message(None, 0, 0, "content")) ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[ServiceResponse] shouldBe ServiceResponse(ok = false, Some("VALIDATION"), Some(List("INVALID_USER_ID", "INVALID_USER_ID")), None)
    }

    Post(s"/list", Message(None, 1, 1, "")) ~> routes ~> check {
      status shouldBe BadRequest
      responseAs[ServiceResponse] shouldBe ServiceResponse(ok = false, Some("VALIDATION"), Some(List("CONTENT_TOO_SHORT")), None)
    }
  }

  // PUT
  it should "respond to putting new message" in {
    Messages.loadFixtures(fixtures)

    Put(s"/list/1", validMessage) ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[ServiceResponse] shouldBe okResult
    }
  }

  // PATCH
  it should "respond to patching message" in {
    Messages.loadFixtures(fixtures)
    val validPatch = MessagePatch(Some(1), None, Some("abcde"))

    Patch(s"/list/1", validPatch) ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[ServiceResponse] shouldBe okResult
    }
  }

  it should "patch message" in {
    Messages.loadFixtures(fixtures)
    val validPatch = MessagePatch(Some(1), None, Some("new content"))

    Patch(s"/list/1", validPatch) ~> routes ~> check {
      Get(s"/list/1") ~> routes ~> check {
        responseAs[Message] shouldBe Message(Some(1), 1, 2, "new content")
      }
    }
  }

  // DELETE
  it should "respond to deleting message" in {
    Messages.loadFixtures(fixtures)

    Delete(s"/list/1") ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[ServiceResponse] shouldBe okResult
    }

    Get(s"/list") ~> routes ~> check {
      responseAs[ServiceResponse].results.getOrElse(List()).length shouldBe fixtures.length - 1
    }
  }
}
