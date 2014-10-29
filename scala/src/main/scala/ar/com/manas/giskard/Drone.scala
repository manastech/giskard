package ar.com.manas.giskard

import akka.actor.{Actor, Props, ActorLogging, ActorRef}

import macroid.Logging._
import macroid.AutoLogTag

object Drone {
  case object Init
  def props = Props(new Drone)
}

class Drone extends Actor with ActorLogging with AutoLogTag {
  import Drone._

  val network = context.actorOf(Props[NetworkManager], name = "network")

  def receive = {
    case Init =>
      logE"Init drone"()
      network ! NetworkManager.Connect
  }
}