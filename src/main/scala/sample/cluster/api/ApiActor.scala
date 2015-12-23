package sample.cluster.api

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

/**
  * @author Anton Gnutov
  */
class ApiActor(host: String, port: Int) extends Actor with RestRoutes with ActorLogging {
  override implicit val system = context.system
  override implicit val materializer = ActorMaterializer()

  var binding: Option[Http.ServerBinding] = None

  import context.dispatcher

  override def preStart(): Unit = {
    val selfRef = self
    Http().bindAndHandle(restRoute, host,  port).foreach(bound => selfRef ! bound)
  }

  override def postStop(): Unit = {
    binding foreach(_.unbind())
  }


  override def receive: Receive = {
    case boundEvent: Http.ServerBinding =>
      log.info(s"API Started at: ${boundEvent.localAddress.toString}")
      binding = Some(boundEvent)
  }
}

object ApiActor {
  def props(host: String, port: Int): Props = Props(classOf[ApiActor], host, port)
}
