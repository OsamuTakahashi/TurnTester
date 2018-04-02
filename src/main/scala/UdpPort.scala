import java.net.{InetAddress, InetSocketAddress}

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.io.{IO, Udp}
import akka.util.ByteString

/**
  * Created by takahashi on 2017/05/25.
  */
class UdpPort(remote: InetSocketAddress,listener:ActorRef) extends Actor with ActorLogging {
  import context.system
  IO(Udp) ! Udp.Bind(self,new InetSocketAddress("0.0.0.0",0))

  def receive = {
    case Udp.Bound(local) =>
//      log.info(s"Ready to send:$local")
      context.become(ready(sender()))
      listener ! "READY"
  }

  def ready(send: ActorRef): Receive = {
    case Udp.Received(data,_) =>
//      log.info("receive from remote")
      listener ! data

    case bytes: ByteString =>
//      log.info("send to remote")
      send ! Udp.Send(bytes, remote)

    case (addr:InetSocketAddress,bytes:ByteString) =>
//      log.info("send to remote via relay")
      send ! Udp.Send(bytes, addr)
  }
}
