package sample.cluster

import akka.actor.Address
import akka.cluster._
import akka.remote.testkit.MultiNodeSpec
import akka.remote.{DefaultFailureDetectorRegistry, FailureDetector}
import org.scalatest.Suite

/**
  * @author Anton Gnutov
  */
trait MultiNodeClusterSpec extends Suite { self: MultiNodeSpec ⇒

  override def initialParticipants: Int = roles.size

  /**
    * Get the cluster node to use.
    */
  def cluster: Cluster = Cluster(system)

  def muteDeadLetters(): Unit = {
    muteDeadLetters(
      classOf[ClusterMessage],
      classOf[akka.remote.transport.AssociationHandle.Disassociated],
      classOf[akka.remote.transport.ActorTransportAdapter.DisassociateUnderlying],
      classOf[akka.remote.transport.AssociationHandle.InboundPayload])(system)
  }

  /**
    * Marks a node as available in the failure detector if
    * [[sample.cluster.FailureDetectorPuppet]] is used as
    * failure detector.
    */
  def markNodeAsAvailable(address: Address): Unit =
    failureDetectorPuppet(address) foreach (_.markNodeAsAvailable())

  /**
    * Marks a node as unavailable in the failure detector if
    * [[sample.cluster.FailureDetectorPuppet]] is used as
    * failure detector.
    */
  def markNodeAsUnavailable(address: Address): Unit = {
    if (isFailureDetectorPuppet) {
      // before marking it as unavailable there should be at least one heartbeat
      // to create the FailureDetectorPuppet in the FailureDetectorRegistry
      cluster.failureDetector.heartbeat(address)
      failureDetectorPuppet(address) foreach (_.markNodeAsUnavailable())
    }
  }

  private def isFailureDetectorPuppet: Boolean =
    cluster.settings.FailureDetectorImplementationClass == classOf[FailureDetectorPuppet].getName

  import scala.reflect.runtime.{universe => ru}
  private def failureDetectorPuppet(address: Address): Option[FailureDetectorPuppet] =
    cluster.failureDetector match {
      case reg: DefaultFailureDetectorRegistry[Address] ⇒
        // reflection used here because called method is private
        val mirror = ru.runtimeMirror(reg.getClass.getClassLoader)
        val instanceMirror = mirror.reflect(reg)
        val methodSymbol = ru.typeOf[DefaultFailureDetectorRegistry[Address]].decl(ru.TermName("failureDetector")).asMethod

        instanceMirror.reflectMethod(methodSymbol)(address).asInstanceOf[Option[FailureDetector]].collect { case p: FailureDetectorPuppet ⇒ p }
      case _ ⇒ None
    }

  def createMember(address: UniqueAddress, status: MemberStatus = MemberStatus.Up, roles: Set[String] = Set.empty): Member = {
    val cons = Class.forName("akka.cluster.Member").getDeclaredConstructors()(0)
    cons.setAccessible(true)
    cons.newInstance(address, Int.box(0), status, roles).asInstanceOf[Member]
  }
}
