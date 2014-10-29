package ar.com.manas.giskard

import akka.actor.{Props, ActorLogging, ActorRef}
import macroid.akkafragments.FragmentActor
import macroid.Logging._
import macroid.AutoLogTag

object NetworkManager {
  case object Connect

  def props = Props(new NetworkManager)
}

class NetworkManager extends FragmentActor[NetworkManagerFragment] with ActorLogging with AutoLogTag {
  import NetworkManager._
  import FragmentActor._

  // receiveUi handles attaching and detaching UI
  // and then (sic!) passes the message to us
  def receive = receiveUi andThen {
    case Connect ⇒
      // boast
      logE"received connect request"()
  }
}
