package sample.cluster

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, AddressFromURIString}
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

/**
 * @author Anton Gnutov
 */
object Main extends App {
  val log = LoggerFactory.getLogger(Main.getClass)
  val config = ConfigFactory.load()

  val seedNode = AddressFromURIString(config.getString("sample.seed-node"))
  val unreachableTimeout = config.getDuration("sample.unreachable.timeout", TimeUnit.SECONDS)

  import scala.collection.JavaConversions.collectionAsScalaIterable
  val nodesList = config.getStringList("sample.nodes").map(AddressFromURIString(_)).toList.sortBy(_.toString)

  registerSystem(ActorSystem("sample"))

  def registerSystem(system: ActorSystem) {
    log.debug("Starting actor system ...")
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