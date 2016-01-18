package sample.cluster

import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.ConfigFactory

/**
  * @author Anton Gnutov
  */
object MultiJvmSpecConfig extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")
  val fourth = role("fourth")
  val fifth = role("fifth")

  val nodeList: List[RoleName] = List(first, second, third, fourth, fifth)

  nodeList.foreach { role =>
    nodeConfig(role) {
      ConfigFactory.parseString(
        s"""
      akka.cluster.metrics.native-library-extract-folder=target/native/${role.name}
        """)
    }
  }

  commonConfig(ConfigFactory.parseString(
    """
    log-dead-letters = 0
    log-dead-letters-during-shutdown = off
    akka.cluster.metrics.enabled=off
    akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
    akka.remote.netty.tcp.hostname = "127.0.0.1"
    akka.remote.netty.tcp.port = 0
    akka.remote.log-remote-lifecycle-events = off
    """))
}
