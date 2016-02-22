package sample.cluster.mock

import akka.actor.{Actor, Props}
import sample.cluster.CheckHttpActor.{StopSuccess, GracefulStop, CheckHttpResponse, CheckHttpRequest}
import sample.cluster.api.json.ClusterState

/**
  * @author Anton Gnutov
  */
class CheckHttpMock(state: Option[ClusterState]) extends Actor {
  override def receive: Receive = {
    case CheckHttpRequest(_) => sender ! CheckHttpResponse(state)
    case GracefulStop => sender ! StopSuccess
  }
}

object CheckHttpMock {
  def props(response: Option[ClusterState]): Props = Props(classOf[CheckHttpMock], response)
}