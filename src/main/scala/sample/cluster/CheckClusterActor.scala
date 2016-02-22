package sample.cluster

import akka.actor.{Actor, ActorLogging, Address, Props}
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.pattern.ask
import akka.pattern.pipe
import akka.util.Timeout
import sample.cluster.CheckClusterActor.{CheckNodesRequest, CheckNodesResponse}
import sample.cluster.CheckHttpActor.{GracefulStop, CheckHttpRequest, CheckHttpResponse}

import scala.concurrent.duration._
import scala.util.Success

/**
  * Actor that checks cluster state via <code>CheckHttpActor</code> actor. It requests all nodes in specified
  * addresses sequence via http. If it receives answer from any node it compares cluster nodes with current ones.
  * Cluster winner is defined by alphabetically order of node names. If new cluster wins then current cluster
  * should be stopped. Only in such case <code>CheckNodesResponse(leader)</code> is sent to original sender. <br/>
  *
  * Input message: <code>CheckNodesRequest(currentState, nodes)</code>
  * Output message: <code>CheckNodesResponse(leader)</code>
  *
  * @author Anton Gnutov
  */
class CheckClusterActor(val apiPort: Int, checkHttpProps: Props) extends Actor with ActorLogging {

  lazy val checkHttp = context.actorOf(checkHttpProps, "checkHttp")

  implicit val timeout = Timeout(10.seconds)

  import context.dispatcher

  override def receive: Receive = {
    case CheckNodesRequest(currentState, nodes) =>
      val currentHosts: Set[String] = currentState.members.flatMap(_.address.host).toSet
      val diff: Set[String] = nodes.flatMap(_.host).toSet.diff(currentHosts)
      log.debug("Checking nodes for http connection: {}", diff.mkString("[", ", ", "]"))

      val replyTo = sender()

      diff.foreach { host =>
        (checkHttp ? CheckHttpRequest(s"http://$host:$apiPort/rest/cluster")).onComplete {
          case Success(CheckHttpResponse(Some(clusterState))) =>
            val newHosts = clusterState.members.flatMap(_.address.host).toList.sorted

            log.debug("Detected cluster with hosts: {}", newHosts.mkString("[", ", ", "]"))
            log.debug("Current cluster hosts: {}", currentHosts.mkString("[", ", ", "]"))

            if (newHosts.nonEmpty && (currentHosts.isEmpty || newHosts.head < currentHosts.toList.sorted.head)) {
              replyTo ! CheckNodesResponse(clusterState.leader)
            }

          case _ => log.warning("Could not receive response for host {}", host)
        }
      }

    case GracefulStop =>
      val replyTo = sender()
      checkHttp ? GracefulStop pipeTo replyTo
  }
}

object CheckClusterActor {
  def props(apiPort: Int, checkHttpProps: Props): Props = Props(classOf[CheckClusterActor], apiPort, checkHttpProps)

  case class CheckNodesRequest(currentState: CurrentClusterState, nodesList: List[Address])
  case class CheckNodesResponse(newSeedNode: Option[Address])
}