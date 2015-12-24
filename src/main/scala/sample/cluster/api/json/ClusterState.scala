package sample.cluster.api.json

import akka.actor.Address
import akka.cluster.Member

case class ClusterState(members: Set[Member], leader: Option[Address])

