package com.josuegalre.bank.app

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.util.Timeout
import com.josuegalre.bank.actors.Bank
import com.josuegalre.bank.http.BankRouter

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object BankApp {

  def startHttpServer(bank: ActorRef)(implicit system: ActorSystem): Unit = {
    implicit val scheduler: ExecutionContext = system.dispatcher

    val router = new BankRouter(bank)
    val routes = router.routes

    val bindingFuture = Http().newServerAt("localhost", 8080).bind(routes)

    bindingFuture.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(s"Server online at http://${address.getHostString}:${address.getPort}")

      case Failure(exception) =>
        system.log.error(s"Failed to bing HTTP server, because: $exception")
        system.terminate()
    }
  }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("BankApp")
    implicit val timeout: Timeout = Timeout(2.seconds)

    val bankActor = system.actorOf(Props[Bank], "bank")

    startHttpServer(bankActor)
  }
}
