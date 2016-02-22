package sample.cluster

import akka.actor.Address
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import akka.cluster.{Member, UniqueAddress}
import akka.remote.testkit.MultiNodeSpec
import akka.remote.transport.ThrottlerTransportAdapter.Direction
import akka.testkit.ImplicitSender
import org.scalatest._
import sample.cluster.CheckClusterActor.{CheckNodesRequest, CheckNodesResponse}
import sample.cluster.CheckHttpActor.{StopSuccess, GracefulStop}
import sample.cluster.ClusterManagerActor.ClusterManagerConfig
import sample.cluster.api.json.ClusterState
import sample.cluster.mock.CheckHttpMock

import scala.concurrent.duration._

/**
  * @author Anton Gnutov
  */
abstract class ClusterManagerSpec extends MultiNodeSpec(MultiJvmSpecConfig) with MultiNodeClusterSpec
  with WordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {

  override def initialParticipants = roles.size

  override def beforeAll(): Unit = {
    multiNodeSpecBeforeAll()
    muteDeadLetters()
  }

  override def afterAll(): Unit = multiNodeSpecAfterAll()

  import MultiJvmSpecConfig._

  val unreachableTimeout = 3.seconds
  val addresses = nodeList.map(node).map(_.address)
  val firstAddress = node(first).address
  val secondAddress = node(second).address
  val thirdAddress = node(third).address
  val fourthAddress = node(fourth).address
  val fifthAddress = node(fifth).address

  val side1 = Vector(first, second)
  val side2 = Vector(third, fourth, fifth)
  var allMembers: Set[Member] = _

  "Cluster Manager" should {
    "start all nodes in cluster and join them" in within(50.seconds) {
      cluster.subscribe(testActor, classOf[MemberUp])
      expectMsgClass(classOf[CurrentClusterState])

      system.actorOf(ClusterManagerActor.props(
        ClusterManagerConfig(Some(firstAddress), addresses, unreachableTimeout, unreachableTimeout),
        StubActor.props))

      receiveN(initialParticipants).collect { case MemberUp(m) => m.address }.toSet should be(
        Set(firstAddress, secondAddress, thirdAddress, fourthAddress, fifthAddress)
      )

      cluster.unsubscribe(testActor)

      allMembers = cluster.state.members

      enterBarrier("all-up")
    }

    "resolve network split" in within(30.seconds) {
      enterBarrier("before-split")
      runOn(first) {
        // split the cluster in two parts (first, second) / (third, fourth, fifth)
        for (role1 ← side1; role2 ← side2) {
          testConductor.blackhole(role1, role2, Direction.Both).await
        }
      }
      enterBarrier("after-split")

      runOn(side1: _*) {
        for (role ← side2) markNodeAsUnavailable(node(role).address)
        // verify cluster split
        awaitCond {
          cluster.sendCurrentClusterState(testActor)
          receiveOne(3.seconds) match {
            case CurrentClusterState(members, _, _, _, _) if members.map(_.address) == Set(firstAddress, secondAddress) => true
            case _ => false
          }
        }
      }

      runOn(side2: _*) {
        for (role ← side1) markNodeAsUnavailable(node(role).address)
        // verify cluster split
        awaitCond {
          cluster.sendCurrentClusterState(testActor)
          receiveOne(3.seconds) match {
            case CurrentClusterState(members, _, _, _, _) if members.map(_.address) == Set(thirdAddress, fourthAddress, fifthAddress) => true
            case _ => false
          }
        }
      }

      enterBarrier("split-completed")
    }

    "correctly answer on CheckCluster request" in within(10.seconds) {
      val first = Address("akka.tcp", "sample", "1.1.1.1", 2500)
      val last = Address("akka.tcp", "sample", "zxc", 2500)
      val veryLast = Address("akka.tcp", "sample", "zzz", 2500)


      runOn(side1: _*) {
        val maxId = cluster.state.members.map(_.uniqueAddress.uid).max
        val response = ClusterState(Set(createMember(UniqueAddress(first, maxId + 1))), Some(first))

        val checkCluster = system.actorOf(CheckClusterActor.props(8080, CheckHttpMock.props(Some(response))))
        checkCluster ! CheckNodesRequest(cluster.state, List(first, last))

        receiveWhile(3.seconds, 3.seconds, side2.size) {
          case CheckNodesResponse(option) => option
        }.toSet should be(Set(Some(first)))
      }

      runOn(side2: _*) {
        val maxId = cluster.state.members.map(_.uniqueAddress.uid).max
        val response = ClusterState(
          Set(createMember(UniqueAddress(last, maxId + 1)), createMember(UniqueAddress(veryLast, maxId + 2))), Some(last))

        val checkCluster = system.actorOf(CheckClusterActor.props(8080, CheckHttpMock.props(Some(response))))
        checkCluster ! CheckNodesRequest(cluster.state, List(veryLast, last))
        expectNoMsg(3.seconds)
      }

      enterBarrier("check-completed")
    }

    "should stop CheckHttp child" in {
      val checkCluster = system.actorOf(CheckClusterActor.props(8080, CheckHttpMock.props(None)))

      checkCluster ! GracefulStop
      expectMsg(StopSuccess)
      enterBarrier("stop-success")
    }
  }
}

class ClusterManagerSpecMultiJvmNode1 extends ClusterManagerSpec
class ClusterManagerSpecMultiJvmNode2 extends ClusterManagerSpec
class ClusterManagerSpecMultiJvmNode3 extends ClusterManagerSpec
class ClusterManagerSpecMultiJvmNode4 extends ClusterManagerSpec
class ClusterManagerSpecMultiJvmNode5 extends ClusterManagerSpec
