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
import pl.bitgrind.messages.Types._
import pl.bitgrind.messages.Types.ErrorCode._

case class ServiceResponse(ok: Boolean, error: Option[String], errors: Option[List[String]], results: Option[List[Message]])

trait Responses {
  def searchResult(messages: List[Message]) = ServiceResponse(ok = true, None, None, Some(messages))
  def ok(res: Result) = ServiceResponse(ok = true, None, None, None)
  def err(res: Result) = ServiceResponse(ok = false, Some(res.error.getOrElse("").toString), res.errors, None)

  def withErrorStatus(res: Result): (StatusCode, ServiceResponse) = res.error match {
    case Some(VALIDATION) => BadRequest -> err(res)
    case Some(NOT_FOUND) => NotFound -> err(res)
    case _ => BadRequest -> err(res)
  }

  def withStatus(result: Either[Result, Result]): (StatusCode, ServiceResponse) = result match {
    case Right(res) => OK -> ok(res)
    case Left(err) => withErrorStatus(err)
  }
}

trait Protocols extends DefaultJsonProtocol {
  implicit val messageFormat = jsonFormat4(Message)
  implicit val messagePatchFormat = jsonFormat3(MessagePatch)
  implicit val responseFormat = jsonFormat4(ServiceResponse)
}

trait Service extends Protocols with Responses {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: FlowMaterializer

  def config: Config
  val logger: LoggingAdapter

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
              withStatus(
                Messages.remove(id)
              )
            }
          } ~
          (patch & entity(as[MessagePatch])) { messagePatch =>
            complete {
              withStatus(
                Messages.patch(id, messagePatch)
              )
            }
          } ~
          (put & entity(as[Message])) { message =>
            complete {
              withStatus(
                Messages.update(id, message)
              )
            }
          }
        } ~
        (post & entity(as[Message])) { message =>
          complete {
            withStatus(
              Messages.add(message)
            )
          }
        } ~
        get {
          parameters("el".as[Int], "before".as[Int]?, "after".as[Int]?) { (el, before, after) =>
            complete {
              Messages.list(el, before, after) match {
                case Right(messages) => searchResult(messages)
                case Left(error) => withErrorStatus(error)
              }
            }
          }
        } ~
        get {
          complete {
            searchResult(Messages.list)
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
