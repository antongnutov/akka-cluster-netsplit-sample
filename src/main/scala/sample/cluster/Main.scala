package sample.cluster

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, AddressFromURIString}
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import sample.cluster.ClusterManagerActor.ClusterManagerConfig
import sample.cluster.api.ApiActor

import scala.concurrent.duration.{FiniteDuration, Duration}

/**
  * @author Anton Gnutov
  */
object Main extends App {
  val log = LoggerFactory.getLogger(Main.getClass)
  val config = ConfigFactory.load()

  val seedNode = AddressFromURIString(config.getString("sample.seed-node"))
  val unreachableTimeout = Duration(config.getString("sample.unreachable.timeout"))

  SeedNodeProvider.updateSeedNode(seedNode)

  import scala.collection.JavaConversions.collectionAsScalaIterable

  val nodesList = config.getStringList("sample.nodes").map(AddressFromURIString(_)).toList.sortBy(_.toString)

  val apiHost = config.getString("sample.api.host")
  val apiPort = config.getInt("sample.api.port")

  registerSystem(ActorSystem("sample"))

  def registerSystem(system: ActorSystem) {
    log.debug("Starting actor system ...")

    system.actorOf(ApiActor.props(apiHost, apiPort))
    system.actorOf(ClusterManagerActor.props(
      ClusterManagerConfig(SeedNodeProvider.getSeedNode, nodesList, FiniteDuration(unreachableTimeout.toSeconds, TimeUnit.SECONDS), apiPort)),
      "clusterManager")

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