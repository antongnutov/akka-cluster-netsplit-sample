package sample.cluster

import akka.actor.{ActorRef, ActorSystem, Address}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.testkit.TestProbe
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import sample.cluster.CheckHttpActor.{GracefulStop, StopSuccess, CheckHttpResponse, CheckHttpRequest}
import sample.cluster.api.json.{ClusterState, ApiDecoder}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class CheckHttpActorSpec extends FlatSpec with Matchers with BeforeAndAfterAll {
  implicit lazy val system = ActorSystem("dashTest")
  implicit val executionContext = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val port = 9999
  val fakeLeader = Some(Address("akka.tcp", "dashTest", "localhost", port))
  var bindingFuture: Future[ServerBinding] = _

  override protected def beforeAll(): Unit = {
    val restPath = pathPrefix("rest")
    val restRoute = restPath {
      path("cluster") {
        get {
          complete(ApiDecoder.encode(ClusterState(Set.empty, fakeLeader)))
        }
      }
    }
    bindingFuture = Http().bindAndHandle(restRoute, "localhost", port)
  }

  override def afterAll(): Unit = {
    bindingFuture
      .flatMap(_.unbind())
      .onComplete {
        _ ⇒ Await.result(system.terminate(), Duration.Inf)
      }
  }

  "Check Http" should "correctly parse cluster state" in {
    val probe = TestProbe()
    implicit val sender: ActorRef = probe.ref
    val checkHttp = system.actorOf(CheckHttpActor.props)

    probe.watch(checkHttp)

    checkHttp ! CheckHttpRequest(s"http://localhost:$port/rest/cluster")
    probe.expectMsg(10.seconds, CheckHttpResponse(Some(ClusterState(Set.empty, fakeLeader))))

    checkHttp ! GracefulStop
    probe.expectMsg(StopSuccess)

    probe.expectTerminated(checkHttp)
  }
}
