package sample.cluster

import akka.actor.{ActorRef, ActorSystem, Address}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.testkit.TestProbe
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import sample.cluster.CheckHttpActor.{CheckHttpRequest, CheckHttpResponse, GracefulStop, StopSuccess}
import sample.cluster.api.json.{ApiDecoder, ClusterState}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class CheckHttpActorSpec extends FlatSpec with Matchers with BeforeAndAfterAll {
  implicit lazy val system = ActorSystem("sample")
  implicit val executionContext = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val port = 9999
  val fakeLeader = Some(Address("akka.tcp", "sample", "localhost", port))
  var bindingFuture: Future[ServerBinding] = _

  override protected def beforeAll(): Unit = {
    var item: Int = 0
    val restPath = pathPrefix("rest")
    val restRoute = restPath {
      path("cluster") {
        get {
          item += 1
          item match {
            case 1 => complete(ApiDecoder.encode(ClusterState(Set.empty, fakeLeader)))
            case 2 => complete("""{"json" :"true"}""")
            case x: Int if x > 2 => reject
          }
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

  it should "correctly handle errors" in {
    val probe = TestProbe()
    implicit val sender: ActorRef = probe.ref
    val checkHttp = system.actorOf(CheckHttpActor.props)

    // incorrect json
    checkHttp ! CheckHttpRequest(s"http://localhost:$port/rest/cluster")
    probe.expectMsg(CheckHttpResponse(None))

    // bad request
    checkHttp ! CheckHttpRequest(s"http://localhost:$port/rest/cluster")
    probe.expectMsg(CheckHttpResponse(None))
  }
}
