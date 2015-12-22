package sample.cluster

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import sample.cluster.ClusterManagerActor._

import scala.concurrent.duration._

/**
  * @author Anton Gnutov
  */
class ClusterManagerActor(seedNode: Address, nodesList: List[Address], unreachableTimeout: Long)
  extends FSM[State, Data] with ActorLogging {

  val cluster = Cluster(context.system)

  import context.dispatcher
  val joinTimer = context.system.scheduler.schedule(1.second, 5.seconds, self, JoinCluster)

  override def preStart(): Unit = {
    log.debug("Expected cluster nodes: {}", nodesList.mkString("[", ", ", "]"))
    log.debug("Seed node: {}", seedNode)
    cluster.subscribe(self, initialStateMode = InitialStateAsSnapshot, classOf[MemberEvent], classOf[ReachabilityEvent])
  }

  override def postStop(): Unit = {
    joinTimer.cancel()
    cluster.unsubscribe(self)
    cluster.down(cluster.selfAddress)
  }

  startWith(Start, Empty)

  when(Start) {
    case Event(JoinCluster, _) =>
      log.debug("Trying to join the cluster ...")
      cluster.join(seedNode)
      stay()

    case Event(MemberJoined(member), _) =>
      if (member.address == cluster.selfAddress) {
        log.debug("Joining the cluster")
        joinTimer.cancel()
      }
      stay()

    case Event(MemberUp(member), _) =>
      log.debug("Node is up: {}", member)
      if (member.address == cluster.selfAddress) {
        log.info("Joined the cluster")
        joinTimer.cancel()
        goto(Active)
      } else {
        stay()
      }

    case Event(s: CurrentClusterState, _) => stay()
  }

  when(Active) {
    case Event(UnreachableMember(member), _) =>
      log.debug("Node is unreachable: {}", member)
      val cancellable = context.system.scheduler.scheduleOnce(unreachableTimeout.seconds, self, UnreachableTimeout(member.address))
      goto(Incomplete) using Unreachable(Map(member.address -> cancellable))

    case Event(ev: MemberEvent, _) =>
      log.debug("[Event: {}", ev)
      stay()
  }

  when(Incomplete) {
    case Event(UnreachableTimeout(address), Unreachable(schedules)) =>
      log.debug("Unreachable timeout received: {}", address)
      cluster.state.members.find(_.address == address).foreach { m =>
        if (address == seedNode) {
          log.info("seedNode is unreachable: restarting ...")
          context.stop(self)
        } else {
          log.info("Removing node [{}] from cluster ...", address)
          cluster.down(address)
        }
      }
      val newSchedules = schedules - address
      if (newSchedules.isEmpty) {
        goto(Active) using Empty
      } else {
        stay() using Unreachable(newSchedules)
      }

    case Event(UnreachableMember(member), Unreachable(schedules)) =>
      log.debug("Node is unreachable: {}", member)
      val cancellable = context.system.scheduler.scheduleOnce(unreachableTimeout.seconds, self, UnreachableTimeout(member.address))
      goto(Incomplete) using Unreachable(schedules + (member.address -> cancellable))

    case Event(ReachableMember(member), Unreachable(schedules)) =>
      log.debug("Node is reachable again: {}", member)
      schedules.get(member.address).foreach(_.cancel())
      val newSchedules = schedules - member.address
      if (newSchedules.isEmpty) {
        goto(Active) using Empty
      } else {
        stay() using Unreachable(newSchedules)
      }
  }

  whenUnhandled {
    case Event(e, s) =>
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      stay
  }

  onTransition {
    case Active -> Incomplete =>
      log.info("Cluster becomes Incomplete")
    case Incomplete -> Active =>
      log.info("Cluster becomes Active")
  }
}

object ClusterManagerActor {

  sealed trait Data
  case object Empty extends Data
  case class Unreachable(schedules: Map[Address, Cancellable]) extends Data

  sealed trait State
  case object Start extends State
  case object Active extends State
  case object Incomplete extends State

  case object JoinCluster
  case class UnreachableTimeout(address: Address)

  def props(seedNode: Address, nodesList: List[Address], unreachableTimeout: Long): Props =
    Props(classOf[ClusterManagerActor], seedNode, nodesList, unreachableTimeout)
}