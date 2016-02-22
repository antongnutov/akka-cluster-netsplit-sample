package sample.cluster

import akka.actor.Address

/**
  * @author Anton Gnutov
  */
object SeedNodeProvider {

  private var seedNode: Option[Address] = None

  def getSeedNode: Option[Address] = synchronized(seedNode)

  def updateSeedNode(seedNode: Address): Unit = synchronized {
    this.seedNode = Option(seedNode)
  }
}
