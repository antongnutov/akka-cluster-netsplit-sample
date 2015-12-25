package sample.cluster

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.scaladsl.ImplicitMaterializer
import akka.util.ByteString
import sample.cluster.CheckHttpActor.{CheckHttpRequest, CheckHttpResponse, GracefulStop}
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
    case CheckHttpRequest(uri) =>
      log.debug("Checking uri: {} ...", uri)
      val replyTo = sender()
      http.singleRequest(HttpRequest(uri = uri)).pipeTo(self)(replyTo)

    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      val replyTo = sender()
      entity.dataBytes.runFold(ByteString(""))(_ ++ _).pipeTo(self)(replyTo)

    case HttpResponse(code, _, _, _) =>
      log.warning("Request failed, response code: {}", code)
      sender() ! CheckHttpResponse(None)

    case bs: ByteString =>
      val string: String = bs.decodeString("UTF-8")
      log.info("Received response: {}", string)

      ApiDecoder.decodeState(string) match {
        case Success(state) =>
          log.info("Cluster state: {}", state)
          sender() ! CheckHttpResponse(Some(state))
        case Failure(e) =>
          log.warning("Could not deserialize cluster state: {}", e.getMessage)
          sender() ! CheckHttpResponse(None)
      }

    case GracefulStop =>
      val replyTo = sender()
      http.shutdownAllConnectionPools().pipeTo(replyTo)
  }
}

object CheckHttpActor {
  def props: Props = Props(classOf[CheckHttpActor])

  case class CheckHttpRequest(uri: String)
  case class CheckHttpResponse(state: Option[ClusterState])
  case object GracefulStop
}

