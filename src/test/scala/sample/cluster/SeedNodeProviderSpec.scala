package sample.cluster

import akka.actor.Address
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

class SeedNodeProviderSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    SeedNodeProvider.updateSeedNode(null)
  }

  "SeedNodeProvider" should "answer None before initialization" in {
    SeedNodeProvider.getSeedNode should be(None)
  }

  it should "answer value after initialization" in {
    val address: Address = Address("akka.tcp", "dash", "localhost", 2501)
    SeedNodeProvider.updateSeedNode(address)
    SeedNodeProvider.getSeedNode should be(Some(address))
  }
}
