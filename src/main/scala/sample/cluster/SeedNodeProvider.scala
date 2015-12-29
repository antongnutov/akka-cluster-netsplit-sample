package sample.cluster

import akka.actor.Address

/**
  * @author Anton Gnutov
  */
object SeedNodeProvider {

  private var seedNode: Address = _

  def getSeedNode: Address = synchronized {
    seedNode
  }

  def updateSeedNode(seedNode: Address): Unit = synchronized {
    this.seedNode = seedNode
  }
}
