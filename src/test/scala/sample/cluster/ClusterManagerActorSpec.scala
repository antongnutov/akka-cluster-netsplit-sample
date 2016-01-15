package sample.cluster

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll}
import sample.cluster.ClusterManagerActor.ClusterManagerConfig

import scala.concurrent.Await
import scala.concurrent.duration._

class ClusterManagerActorSpec extends FlatSpec with Matchers with BeforeAndAfterAll {
  implicit lazy val system = ActorSystem("dashTest", ConfigFactory.load())

  override def afterAll() {
    Await.result(system.terminate(), Duration.Inf)
  }

  "Cluster Manager" should "correctly join himself in alone scenario" in {
    val probe = TestProbe()
    Cluster(system).subscribe(probe.ref, classOf[MemberUp])
    probe.expectMsgClass(classOf[CurrentClusterState])

    val seedNode = Cluster(system).selfAddress
    val duration = 10.seconds
    system.actorOf(ClusterManagerActor.props(
      ClusterManagerConfig(seedNode, List.empty, duration, duration),
      StubActor.props))

    probe.expectMsgClass(classOf[MemberUp]).member.address should be (Cluster(system).selfAddress)
  }
}
