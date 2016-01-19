package sample.cluster

import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import org.scalatest._
import sample.cluster.ClusterManagerActor.ClusterManagerConfig

import scala.concurrent.duration._

/**
  * @author Anton Gnutov
  */
abstract class ClusterManagerSpec extends MultiNodeSpec(MultiJvmSpecConfig) with WordSpecLike with Matchers
with BeforeAndAfterAll with ImplicitSender {

  override def initialParticipants = roles.size

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

  import MultiJvmSpecConfig._

  val unreachableTimeout = 3.seconds
  val nodes = nodeList.map(node)
  val addresses = nodes.map(_.address)
  val firstAddress = addresses.head

  "Cluster Manager" should {
    "start all nodes in cluster and join them" in within(30.seconds) {
      Cluster(system).subscribe(testActor, classOf[MemberUp])
      expectMsgClass(classOf[CurrentClusterState])

      system.actorOf(ClusterManagerActor.props(
        ClusterManagerConfig(firstAddress, addresses, unreachableTimeout, unreachableTimeout),
        StubActor.props))

      receiveN(initialParticipants).collect { case MemberUp(m) => m.address }.toSet should be(addresses.toSet)

      Cluster(system).unsubscribe(testActor)
      testConductor.enter("all-up")
    }

    "shut down a node" in within(30.seconds) {
      val thirdAddress = addresses(2)
      runOn(first) {
        testConductor.shutdown(third)
      }

      runOn(first, second, fourth, fifth) {
        testConductor.enter("node-shutdown")
      }

      runOn(first, second, fourth, fifth) {
        // verify that other nodes detect the crash

        awaitCond {
          Cluster(system).sendCurrentClusterState(testActor)
          receiveOne(3.seconds) match {
            case CurrentClusterState(members, _, _, _, _) if members.map(_.address) == addresses.toSet.filterNot(_ == thirdAddress) => true
            case _ => false
          }
        }
        testConductor.enter("node crash detected")
      }
    }
  }
}

class ClusterManagerSpecMultiJvmNode1 extends ClusterManagerSpec
class ClusterManagerSpecMultiJvmNode2 extends ClusterManagerSpec
class ClusterManagerSpecMultiJvmNode3 extends ClusterManagerSpec
class ClusterManagerSpecMultiJvmNode4 extends ClusterManagerSpec
class ClusterManagerSpecMultiJvmNode5 extends ClusterManagerSpec
