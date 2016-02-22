package sample.cluster

import akka.actor._
import akka.cluster.{Member, MemberStatus, Cluster}
import akka.cluster.ClusterEvent._
import akka.pattern.ask
import akka.util.Timeout
import sample.cluster.CheckClusterActor.{CheckNodesRequest, CheckNodesResponse}
import sample.cluster.CheckHttpActor.GracefulStop
import sample.cluster.ClusterManagerActor._

import scala.concurrent.duration._

/**
  * Cluster manager actor, manages current instance cluster state, and makes decisions about cluster management.<br/>
  *
  * Implemented as FSM. States:
  * <ul>
  *   <li>Start</li>
  *   <li>Active</li>
  *   <li>Incomplete</li>
  * </ul>
  *
  * <code>Start</code> is used only before joining the cluster. After it actor goes to state <code>Active</code><br/>
  * <code>Active</code> state means that all nodes are accessible and there are no unreachable nodes.
  * When any node becomes unreachable actor goes to state <code>Incomplete</code> and it stays there until cluster is stable.
  * <br/><br/>
  *
  * When some nodes become unreachable this actor initializes other nodes search with <code>CheckClusterActor</code>.
  *
  * @see CheckClusterActor
  *
  * @author Anton Gnutov
  */
class ClusterManagerActor(config: ClusterManagerConfig, checkClusterProps: Props) extends FSM[State, Data] with ActorLogging {

  val cluster = Cluster(context.system)

  import context.dispatcher

  setTimer("JoinCluster", JoinCluster, 1.second, repeat = true)

  lazy val checkCluster = context.actorOf(checkClusterProps, "checkCluster")

  implicit val timeout = Timeout(10.seconds)

  override def preStart(): Unit = {
    log.debug("Expected cluster nodes: {}", config.nodesList.mkString("[", ", ", "]"))
    log.debug("Seed node: {}", config.seedNode)
    cluster.subscribe(self, initialStateMode = InitialStateAsSnapshot, classOf[MemberEvent], classOf[ReachabilityEvent])
  }

  override def postStop(): Unit = {
    cancelTimer("JoinCluster")
    cluster.unsubscribe(self)
    cluster.down(cluster.selfAddress)
  }

  startWith(Start, Empty)

  when(Start) {
    case Event(JoinCluster, _) =>
      log.debug("Trying to join the cluster ...")
      cluster.join(getSeedNode)
      stay()

    case Event(MemberJoined(member), _) =>
      if (member.address == cluster.selfAddress) {
        log.debug("Joining the cluster")
        cancelTimer("JoinCluster")
      }
      stay()

    case Event(MemberUp(member), _) =>
      log.debug("Node is up: {}", member)
      if (member.address == cluster.selfAddress) {
        log.info("Joined the cluster")
        cancelTimer("JoinCluster")
        goto(Active)
      } else {
        stay()
      }

    case Event(s: CurrentClusterState, _) => stay()
  }

  when(Active) {
    case Event(UnreachableMember(member), _) =>
      log.debug("Node is unreachable: {}", member)
      if (member.status == MemberStatus.Down) {
        stay()
      } else {
        setTimer(member.address.toString, UnreachableTimeout(member.address), config.unreachableTimeout)
        goto(Incomplete) using Unreachable(List(member.address))
      }

    case Event(MemberRemoved(member, _), _) =>
      onNodeRemoved(member)
      stay()

    case Event(CheckNodesResponse(Some(leader)), _) =>
      log.debug("Stopping http client ...")
      checkCluster ? GracefulStop onComplete {
        case _ =>
          //TODO: invent more FP-style solution how to store leader
          log.info("Updating seedNode to {}", leader)
          SeedNodeProvider.updateSeedNode(leader)
          log.info("Restarting actor system ...")
          context.stop(self)
      }
      stay()

    case Event(RefreshNetSplit, _) =>
      checkCluster ! CheckNodesRequest(cluster.state, config.nodesList)
      setTimer("RefreshNetSplit", RefreshNetSplit, config.netSplitRefreshInterval)
      stay()

    case Event(ev: MemberEvent, _) =>
      log.debug("[Event: {}", ev)
      stay()
  }

  when(Incomplete) {
    case Event(UnreachableTimeout(address), Unreachable(addresses)) =>
      log.debug("Unreachable timeout received: {}", address)
      log.debug("Current cluster members: {}", cluster.state.members.map(_.address).mkString("[", ", ", "]"))
      cluster.state.members.find(_.address == address).foreach { m =>
        log.info("Removing node [{}] from cluster ...", address)
        cluster.down(address)
      }
      stay() using Unreachable(addresses.filterNot(_ == address))

    case Event(MemberRemoved(member, _), Unreachable(schedules)) =>
      onNodeRemoved(member)

      if (schedules.isEmpty) {
        goto(Active) using Empty
      } else {
        stay()
      }

    case Event(UnreachableMember(member), Unreachable(addresses)) =>
      log.debug("Node is unreachable: {}", member)
      setTimer(member.address.toString, UnreachableTimeout(member.address), config.unreachableTimeout)
      stay() using Unreachable(member.address +: addresses)

    case Event(ReachableMember(member), Unreachable(addresses)) =>
      log.debug("Node is reachable again: {}", member)
      cancelTimer(member.address.toString)
      val newSchedules = addresses.filterNot(_ == member.address)
      if (newSchedules.isEmpty) {
        goto(Active) using Empty
      } else {
        stay() using Unreachable(newSchedules)
      }

    case Event(CheckNodesResponse(_), _) =>
      log.debug("Ignore http responses in Incomplete state")
      stay()

    case Event(RefreshNetSplit, _) =>
      log.debug("Ignore network split search in Incomplete state")
      setTimer("RefreshNetSplit", RefreshNetSplit, config.netSplitRefreshInterval)
      stay()
  }

  private def onNodeRemoved(member: Member): Any = {
    if (cluster.state.members.size == 1 && cluster.selfAddress != getSeedNode) {
      log.warning("Only 1 node remain in the cluster, restarting ...")
      context.stop(self)
    } else if (member.address == getSeedNode) {
      log.warning("Lost seedNode")
      log.debug("Scheduling cluster network split search ...")
      setTimer("RefreshNetSplit", RefreshNetSplit, 1.second)
    }
  }

  private def getSeedNode: Address = config.seedNode.getOrElse(cluster.selfAddress)

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

  case class ClusterManagerConfig(seedNode: Option[Address], nodesList: List[Address], unreachableTimeout: FiniteDuration,
                                  netSplitRefreshInterval: FiniteDuration)

  sealed trait Data
  case object Empty extends Data
  case class Unreachable(addresses: List[Address]) extends Data

  sealed trait State
  case object Start extends State
  case object Active extends State
  case object Incomplete extends State

  case object JoinCluster
  case object RefreshNetSplit
  case class UnreachableTimeout(address: Address)

  def props(config: ClusterManagerConfig, checkClusterProps: Props): Props =
    Props(classOf[ClusterManagerActor], config, checkClusterProps)
}
