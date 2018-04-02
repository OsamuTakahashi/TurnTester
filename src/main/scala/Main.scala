import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import kamon.Kamon

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * Created by takahashi on 2017/05/26.
  */
object Main extends App {
  val config = ConfigFactory.load()
  val system = ActorSystem()

  Kamon.start()

  val turn = new InetSocketAddress(config.getString("turn.host"),config.getInt("turn.port"))
  val messageInterval = config.getInt("tester.message_interval")
  val messageSize = config.getInt("tester.message_size")

  (0 until config.getInt("tester.num_clients")).foreach {
    i =>
      val host = system.actorOf(Props(new ClientActor(config.getString("turn.username"),config.getString("turn.realm"),config.getString("turn.password"),turn,messageInterval,messageSize)),"host" + i)
      system.actorOf(Props(new ClientActor(config.getString("turn.username"),config.getString("turn.realm"),config.getString("turn.password"),turn,messageInterval,messageSize,Some(host))),"guest" + i)
  }
  
  sys.addShutdownHook({
    println("Bye!")
    system.terminate()
    Await.result(system.whenTerminated,Duration.Inf)
  })
}
