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
import pl.bitgrind.messages.Messages._

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

  val db: scala.slick.jdbc.JdbcBackend#DatabaseDef
  def repo: MessageSlick2Repository
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
              db.withSession { implicit session =>
                repo.find(id) match {
                  case Right(msg) => msg
                  case Left(err) => withErrorStatus(err)
                }
              }
            }
          } ~
          delete {
            complete {
              db.withSession { implicit session =>
                repo.remove(id) match {
                  case Right(res) => ok(res)
                  case Left(err) => withErrorStatus(err)
                }
              }
            }
          } ~
          (patch & entity(as[MessagePatch])) { messagePatch =>
            complete {
              db.withSession { implicit session =>
                repo.patch(id, messagePatch) match {
                  case Right(res) => ok(res)
                  case Left(err) => withErrorStatus(err)
                }
              }
            }
          } ~
          (put & entity(as[UnpersistedMessage])) { message =>
            complete {
              db.withSession { implicit session =>
                repo.update(id, message) match {
                  case Right(res) => ok(res)
                  case Left(err) => withErrorStatus(err)
                }
              }
            }
          }
        } ~
        (post & entity(as[UnpersistedMessage])) { message =>
          complete {
            db.withSession { implicit session =>
              repo.add(message) match {
                case Right(res) => ok(res)
                case Left(err) => withErrorStatus(err)
              }
            }
          }
        } ~
        get {
          parameters("el".as[Int], "before".as[Int]?, "after".as[Int]?) { (el, before, after) =>
            complete {
              db.withSession { implicit session =>
                repo.list(el, before, after) match {
                  case Right(messages) => ServiceResponse(ok = true, None, None, Some(messages))
                  case Left(error) => withErrorStatus(error)
                }
              }
            }
          }
        } ~
        get {
          complete {
            db.withSession { implicit session =>
              ServiceResponse(ok = true, None, None, Some(repo.list))
            }
          }
        }
      }
    }
  }
}

object AkkaHttpMicroservice extends App with Service {
  import scala.slick.driver.H2Driver.simple._
  import slick.driver.H2Driver.profile

  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorFlowMaterializer()

  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)

  val maxResults = 1 max config.getInt("messages.maxResults")

  override val db = Database.forURL(
    config.getString("messagesH2Db.url"),
    driver = config.getString("messagesH2Db.driver")
  )
  override val repo = new MessageSlick2Repository(new Tables(profile), maxResults)
  repo.createTable(db.createSession())

  Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))
}
