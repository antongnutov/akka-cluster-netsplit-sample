package sample.cluster

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCode, StatusCodes}
import akka.stream.scaladsl.ImplicitMaterializer
import akka.util.ByteString
import sample.cluster.CheckHttpActor.{CheckRequest, CheckResponse, Stop}
import sample.cluster.api.json.{ApiDecoder, ClusterState}

import scala.util.{Failure, Success}

/**
  * @author Anton Gnutov
  */
class CheckHttpActor extends Actor with ImplicitMaterializer with ActorLogging {

  import akka.pattern.pipe
  import context.dispatcher

  val http = Http(context.system)

  override def receive = {
    case CheckRequest(uri) =>
      log.debug("Checking uri: {} ...", uri)
      http.singleRequest(HttpRequest(uri = uri)).pipeTo(self)(sender())

    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      entity.dataBytes.runFold(ByteString(""))(_ ++ _).pipeTo(self)(sender())

    case HttpResponse(code, _, _, _) =>
      log.warning("Request failed, response code: {}", code)
      sender() ! CheckResponse(code)

    case bs: ByteString =>
      val string: String = bs.decodeString("UTF-8")
      log.info("Received response: {}", string)

      ApiDecoder.decodeState(string) match {
        case Success(state) => log.info("Cluster state: {}", state)
        case Failure(e) => log.warning("Could not deserialize cluster state: {}", e.getMessage)
      }

      sender() ! CheckResponse(StatusCodes.OK)

    case Stop => http.shutdownAllConnectionPools().pipeTo(sender())
  }
}

object CheckHttpActor {
  def props: Props = Props(classOf[CheckHttpActor])

  case class CheckRequest(uri: String)
  case class CheckResponse(code: StatusCode)
  case object Stop
}

