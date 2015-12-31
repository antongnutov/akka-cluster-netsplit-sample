package sample.cluster

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, AddressFromURIString}
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import sample.cluster.ClusterManagerActor.ClusterManagerConfig
import sample.cluster.api.ApiActor

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

/**
  * @author Anton Gnutov
  */
object Main extends App {
  val log = LoggerFactory.getLogger(Main.getClass)
  val config = ConfigFactory.load()

  import scala.collection.JavaConversions.collectionAsScalaIterable

  val nodesList = config.getStringList("sample.nodes").map(AddressFromURIString(_)).toList.sortBy(_.toString)
  if (nodesList.isEmpty) {
    log.error("Nodes list must be non empty!")
    sys.exit(0)
  }
  val seedNode = nodesList.head
  SeedNodeProvider.updateSeedNode(seedNode)

  val unreachableTimeout = Duration(config.getString("sample.unreachable.timeout"))
  val netSplitRefreshInterval = Duration(config.getString("sample.netsplit.refresh-interval"))

  val apiHost = config.getString("sample.api.host")
  val apiPort = config.getInt("sample.api.port")

  registerSystem(ActorSystem("sample"))

  def registerSystem(system: ActorSystem) {
    log.debug("Starting actor system ...")

    system.actorOf(ApiActor.props(apiHost, apiPort))
    system.actorOf(ClusterManagerActor.props(
      ClusterManagerConfig(SeedNodeProvider.getSeedNode,
        nodesList,
        FiniteDuration(unreachableTimeout.toSeconds, TimeUnit.SECONDS),
        FiniteDuration(netSplitRefreshInterval.toSeconds, TimeUnit.SECONDS),
        apiPort)),
      "clusterManager")

    Cluster(system).registerOnMemberRemoved {
      log.warn("Removed from cluster, terminating actor system ...")

      system.terminate()

      if (Try(Await.ready(system.whenTerminated, 10.seconds)).isFailure) {
        log.error("Could not stop actor system in 10 seconds")
      }
      registerSystem(ActorSystem("sample"))
    }

    sys.addShutdownHook {
      system.terminate()
    }
  }
}