package sample.cluster.api

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import sample.cluster.api.json.{ClusterState, ApiDecoder}

/**
  * REST Route, used to provide siple claster state via http.
  *
  * Http access at following up: http://[hostname]:[port]/rest
  */
trait RestRoutes {
  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  lazy val cluster = Cluster(system)

  private val restPath = pathPrefix("rest")

  val restRoute = restPath {
    path("cluster") {
      get {
         complete(ApiDecoder.encode(ClusterState(cluster.state.members, cluster.state.leader)))
      }
    }
  }
}
