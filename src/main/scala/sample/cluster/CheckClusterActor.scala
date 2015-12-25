package sample.cluster

import akka.actor.{Actor, ActorLogging, Address, Props}
import sample.cluster.CheckClusterActor.{CheckNodes, CheckNodesResponse}

/**
  * @author Anton Gnutov
  */
class CheckClusterActor extends Actor with ActorLogging {

  override def receive: Receive = {
    case CheckNodes(nodes) =>
      log.debug("Checking cluster nodes: {}", nodes.mkString("[", ", ", "]"))

      // TODO: implement real check

      sender() ! CheckNodesResponse(None)
      context.stop(self)
  }
}

object CheckClusterActor {
  def props: Props = Props(classOf[CheckClusterActor])

  case class CheckNodes(nodes: Seq[Address])
  case class CheckNodesResponse(newSeedNode: Option[Address])
}