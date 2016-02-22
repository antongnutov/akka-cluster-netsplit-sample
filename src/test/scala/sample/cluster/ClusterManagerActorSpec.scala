package sample.cluster

import akka.actor.{ActorRef, ActorSystem, Address}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import sample.cluster.CheckClusterActor.CheckNodesResponse
import sample.cluster.ClusterManagerActor.ClusterManagerConfig
import sample.cluster.mock.CheckHttpMock

import scala.concurrent.Await
import scala.concurrent.duration._

class ClusterManagerActorSpec extends WordSpecLike with Matchers with BeforeAndAfterAll {
  implicit lazy val system = ActorSystem("sample", ConfigFactory.load())

  var actor: ActorRef = _

  override def afterAll() {
    SeedNodeProvider.updateSeedNode(null)
    Await.result(system.terminate(), Duration.Inf)
  }

  "Cluster Manager" should {
    "correctly join himself in alone scenario" in {
      val probe = TestProbe()
      Cluster(system).subscribe(probe.ref, classOf[MemberUp])
      probe.expectMsgClass(classOf[CurrentClusterState])

      val seedNode = Cluster(system).selfAddress
      val duration = 10.seconds
      actor = system.actorOf(ClusterManagerActor.props(
        ClusterManagerConfig(Some(seedNode), List.empty, duration, duration),
        CheckHttpMock.props(None)))

      probe.expectMsgClass(classOf[MemberUp]).member.address should be (Cluster(system).selfAddress)
    }

    "be stopped on CheckNodesResponse(Some(leader)) message" in {
      val probe = TestProbe()
      probe.watch(actor)

      actor ! CheckNodesResponse(Some(Address("akka.tcp", "sample")))
      probe.expectTerminated(actor)
    }
  }
}
