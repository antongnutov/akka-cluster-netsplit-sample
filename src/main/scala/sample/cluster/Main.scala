package sample.cluster

import akka.actor.{ActorSystem, AddressFromURIString}
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import sample.cluster.api.ApiActor

import scala.concurrent.duration.Duration

/**
 * @author Anton Gnutov
 */
object Main extends App {
  val log = LoggerFactory.getLogger(Main.getClass)
  val config = ConfigFactory.load()

  val seedNode = AddressFromURIString(config.getString("sample.seed-node"))
  val unreachableTimeout = Duration(config.getString("sample.unreachable.timeout"))

  import scala.collection.JavaConversions.collectionAsScalaIterable
  val nodesList = config.getStringList("sample.nodes").map(AddressFromURIString(_)).toList.sortBy(_.toString)

  val apiHost = config.getString("sample.api.host")
  val apiPort = config.getInt("sample.api.port")

  registerSystem(ActorSystem("sample"))

  def registerSystem(system: ActorSystem) {
    log.debug("Starting actor system ...")

    system.actorOf(ApiActor.props(apiHost, apiPort))
    system.actorOf(ClusterManagerActor.props(seedNode, nodesList, unreachableTimeout), "ClusterManager")

    Cluster(system).registerOnMemberRemoved {
      log.warn("Removed from cluster, terminating actor system ...")

      system.terminate()
      registerSystem(ActorSystem("sample"))
    }

    sys.addShutdownHook {
      system.terminate()
    }
  }
}