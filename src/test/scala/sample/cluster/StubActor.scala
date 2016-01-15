package sample.cluster

import akka.actor.{Actor, Props}

/**
  * @author Anton Gnutov
  */
class StubActor extends Actor {
  override def receive: Receive = Actor.ignoringBehavior
}

object StubActor {
  def props: Props = Props(classOf[StubActor])
}
