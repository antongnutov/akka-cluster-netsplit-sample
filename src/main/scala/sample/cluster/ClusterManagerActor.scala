package sample.cluster

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.pattern.ask
import akka.util.Timeout
import sample.cluster.CheckClusterActor.{CheckNodesRequest, CheckNodesResponse}
import sample.cluster.CheckHttpActor.GracefulStop
import sample.cluster.ClusterManagerActor._

import scala.concurrent.duration._

/**
  * @author Anton Gnutov
  */
class ClusterManagerActor(config: ClusterManagerConfig) extends FSM[State, Data] with ActorLogging {

  val cluster = Cluster(context.system)

  import context.dispatcher
  val joinTimer = context.system.scheduler.schedule(1.second, 5.seconds, self, JoinCluster)

  lazy val checkCluster = context.actorOf(CheckClusterActor.props(config.apiPort), "checkCluster")

  implicit val timeout = Timeout(10.seconds)

  override def preStart(): Unit = {
    log.debug("Expected cluster nodes: {}", config.nodesList.mkString("[", ", ", "]"))
    log.debug("Seed node: {}", config.seedNode)
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
      cluster.join(config.seedNode)
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
      val cancellable = context.system.scheduler.scheduleOnce(config.unreachableTimeout, self, UnreachableTimeout(member.address))
      goto(Incomplete) using Unreachable(Map(member.address -> cancellable))

    case Event(CheckNodesResponse(Some(leader)), _) =>
      log.debug("Stopping http client ...")
      context.actorSelection("/user/clusterManager/checkCluster/checkHttp") ? GracefulStop onComplete {
        case _ =>
          //TODO: invent more FP-style solution how to store leader
          log.info("Updating seedNode to {}", leader)
          SeedNodeProvider.updateSeedNode(leader)
          log.info("Restarting actor system ...")
          context.stop(self)
      }
      stay()

    case Event(ev: MemberEvent, _) =>
      log.debug("[Event: {}", ev)
      stay()
  }

  when(Incomplete) {
    case Event(UnreachableTimeout(address), Unreachable(schedules)) =>
      log.debug("Unreachable timeout received: {}", address)
      cluster.state.members.find(_.address == address).foreach { m =>
        log.info("Removing node [{}] from cluster ...", address)
        cluster.down(address)
      }
      stay() using Unreachable(schedules - address)

    case Event(MemberRemoved(member, previousStatus), Unreachable(schedules)) =>
      if (cluster.state.members.size == 1 && cluster.selfAddress != config.seedNode) {
        log.warning("Only 1 node remain in the cluster, restarting ...")
        context.stop(self)
      } else if (member.address == config.seedNode) {
        log.warning("Lost seedNode")
        log.debug("Scheduling cluster network split search ...")
        context.system.scheduler.schedule(1.second, config.netSplitRefreshInterval, checkCluster,
          CheckNodesRequest(cluster.state, config.nodesList))
      }

      if (schedules.isEmpty) {
        goto(Active) using Empty
      } else {
        stay()
      }

    case Event(UnreachableMember(member), Unreachable(schedules)) =>
      log.debug("Node is unreachable: {}", member)
      val cancellable = context.system.scheduler.scheduleOnce(config.unreachableTimeout, self, UnreachableTimeout(member.address))
      stay() using Unreachable(schedules + (member.address -> cancellable))

    case Event(ReachableMember(member), Unreachable(schedules)) =>
      log.debug("Node is reachable again: {}", member)
      schedules.get(member.address).foreach(_.cancel())
      val newSchedules = schedules - member.address
      if (newSchedules.isEmpty) {
        goto(Active) using Empty
      } else {
        stay() using Unreachable(newSchedules)
      }

    case Event(CheckNodesResponse(_), _) =>
      log.debug("Ignore http responses in Incomplete state")
      stay()
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

  case class ClusterManagerConfig(seedNode: Address, nodesList: List[Address], unreachableTimeout: FiniteDuration,
                                  netSplitRefreshInterval: FiniteDuration, apiPort: Int)

  sealed trait Data
  case object Empty extends Data
  case class Unreachable(schedules: Map[Address, Cancellable]) extends Data

  sealed trait State
  case object Start extends State
  case object Active extends State
  case object Incomplete extends State

  case object JoinCluster
  case class UnreachableTimeout(address: Address)

  def props(config: ClusterManagerConfig): Props =
    Props(classOf[ClusterManagerActor], config)
}
