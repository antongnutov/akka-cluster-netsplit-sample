package sample.cluster

import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import org.scalatest.{BeforeAndAfterAll, Matchers, FlatSpecLike}
import sample.cluster.ClusterManagerActor.ClusterManagerConfig

import scala.concurrent.duration._

/**
  * @author Anton Gnutov
  */
abstract class ClusterManagerSpec extends MultiNodeSpec(MultiJvmSpecConfig) with FlatSpecLike with Matchers
with BeforeAndAfterAll with ImplicitSender {

  override def initialParticipants = roles.size

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

  import MultiJvmSpecConfig._

  "Cluster" should "start all nodes in cluster and join them" in within(30.seconds) {
    Cluster(system).subscribe(testActor, classOf[MemberUp])
    expectMsgClass(classOf[CurrentClusterState])

    val nodes = nodeList.map(node)
    val addresses = nodes.map(_.address)
    val firstAddress = addresses.head

    system.actorOf(ClusterManagerActor.props(
      ClusterManagerConfig(firstAddress, addresses, 5.seconds, 5.seconds),
      StubActor.props))

    receiveN(initialParticipants).collect { case MemberUp(m) => m.address }.toSet should be(
      addresses.toSet)

    Cluster(system).unsubscribe(testActor)
    testConductor.enter("all-up")
  }
}

class ClusterManagerSpecMultiJvmNode1 extends ClusterManagerSpec
class ClusterManagerSpecMultiJvmNode2 extends ClusterManagerSpec
class ClusterManagerSpecMultiJvmNode3 extends ClusterManagerSpec
class ClusterManagerSpecMultiJvmNode4 extends ClusterManagerSpec
class ClusterManagerSpecMultiJvmNode5 extends ClusterManagerSpec
