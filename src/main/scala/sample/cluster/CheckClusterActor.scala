package sample.cluster

import akka.actor.{Actor, ActorLogging, Address, Props}
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.pattern.ask
import akka.util.Timeout
import sample.cluster.CheckClusterActor.{CheckNodesRequest, CheckNodesResponse}
import sample.cluster.CheckHttpActor.{CheckHttpRequest, CheckHttpResponse}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * @author Anton Gnutov
  */
class CheckClusterActor(val apiPort: Int) extends Actor with ActorLogging {

  lazy val checkHttp = context.actorOf(CheckHttpActor.props, "checkHttp")

  implicit val timeout = Timeout(10.seconds)

  import context.dispatcher

  override def receive: Receive = {
    case CheckNodesRequest(currentState, nodes) =>
      log.debug("Checking cluster nodes: {}", nodes.mkString("[", ", ", "]"))

      val replyTo = sender()

      nodes.diff(currentState.members.flatMap(_.address.host).toList).flatMap(_.host).foreach { host =>
        (checkHttp ? CheckHttpRequest(s"http://$host:$apiPort/rest/cluster}")).onComplete {
          case Success(CheckHttpResponse(clusterState)) =>
            clusterState match {
              case Some(state) =>
                val currentHosts = currentState.members.flatMap(_.address.host).toList.sorted
                val newHosts = state.members.flatMap(_.address.host).toList.sorted

                if (newHosts.nonEmpty && (currentHosts.isEmpty || newHosts.head < currentHosts.head)) {
                  replyTo ! CheckNodesResponse(state.leader)
                }
              case None =>
            }

          case Failure(e) => log.warning("Could not receive response for host {} in 10 seconds: {}", host, e.getMessage)
        }
      }
  }
}

object CheckClusterActor {
  def props(apiPort: Int): Props = Props(classOf[CheckClusterActor], apiPort)

  case class CheckNodesRequest(currentState: CurrentClusterState, nodesList: List[Address])
  case class CheckNodesResponse(newSeedNode: Option[Address])
}