package pl.bitgrind.messages

import akka.actor.ActorSystem
import akka.event.{LoggingAdapter, Logging}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorFlowMaterializer, FlowMaterializer}
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import scala.concurrent.ExecutionContextExecutor
import spray.json._
import pl.bitgrind.messages.Messages.Message
import pl.bitgrind.messages.Messages.UnpersistedMessage
import pl.bitgrind.messages.Messages.MessagePatch
import pl.bitgrind.messages.Messages.Result
import pl.bitgrind.messages.Messages.VALIDATION
import pl.bitgrind.messages.Messages.NOT_FOUND

case class ServiceResponse(ok: Boolean, error: Option[String], errors: Option[List[String]], results: Option[List[Message]])

trait Protocols extends DefaultJsonProtocol {
  implicit val messageFormat = jsonFormat4(Message)
  implicit val unpersistedMessageFormat = jsonFormat3(UnpersistedMessage)
  implicit val messagePatchFormat = jsonFormat3(MessagePatch)
  implicit val responseFormat = jsonFormat4(ServiceResponse)
}

trait Service extends Protocols {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: FlowMaterializer

  def config: Config
  val logger: LoggingAdapter

  def ok(res: Result) = ServiceResponse(ok = true, None, None, None)
  def err(res: Result) = ServiceResponse(ok = false, Some(res.error.getOrElse("").toString), res.errors, None)

  def withErrorStatus(res: Result): (StatusCode, ServiceResponse) =
    res.error match {
      case Some(VALIDATION) => BadRequest -> err(res)
      case Some(NOT_FOUND) => NotFound -> err(res)
      case _ => BadRequest -> err(res)
    }

  val routes = {
    logRequestResult("akka-http-microservice") {
      pathPrefix("list") {
        path(IntNumber) { id =>
          get {
            complete {
              Messages.find(id) match {
                case Right(msg) => msg
                case Left(err) => withErrorStatus(err)
              }
            }
          } ~
          delete {
            complete {
              Messages.remove(id) match {
                case Right(res) => ok(res)
                case Left(err) => withErrorStatus(err)
              }
            }
          } ~
          (patch & entity(as[MessagePatch])) { messagePatch =>
            complete {
              Messages.patch(id, messagePatch) match {
                case Right(res) => ok(res)
                case Left(err) => withErrorStatus(err)
              }
            }
          } ~
          (put & entity(as[UnpersistedMessage])) { message =>
            complete {
              Messages.update(id, message) match {
                case Right(res) => ok(res)
                case Left(err) => withErrorStatus(err)
              }
            }
          }
        } ~
        (post & entity(as[UnpersistedMessage])) { message =>
          complete {
            Messages.add(message) match {
              case Right(res) => ok(res)
              case Left(err) => withErrorStatus(err)
            }
          }
        } ~
        get {
          parameters("el".as[Int], "before".as[Int]?, "after".as[Int]?) { (el, before, after) =>
            complete {
              Messages.list(el, before, after) match {
                case Right(messages) => ServiceResponse(ok = true, None, None, Some(messages))
                case Left(error) => withErrorStatus(error)
              }
            }
          }
        } ~
        get {
          complete {
            ServiceResponse(ok = true, None, None, Some(Messages.list))
          }
        }
      }
    }
  }
}

object AkkaHttpMicroservice extends App with Service {
  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorFlowMaterializer()

  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)

  Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))
}
