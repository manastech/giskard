package ar.com.manas.giskard

import akka.actor.{Props, ActorLogging, ActorRef}

import macroid.akkafragments.FragmentActor
import macroid.Logging._
import macroid.AutoLogTag
import macroid.util.Ui

object NetworkManager {
  case object Connect
  case class WifiEnabled(e: Boolean)

  def props = Props(new NetworkManager)

  val droneAddress = Array[Byte](192.toByte, 168.toByte, 1, 1)
}

class NetworkManager extends FragmentActor[NetworkManagerFragment] with ActorLogging with AutoLogTag {
  import NetworkManager._
  import FragmentActor._
  import Drone._

  def receive = receiveUi andThen {
    case Connect => 
      logE"received connect request"()      
      withUi(f => Ui { f.receive })      
    case WifiEnabled(enabled) =>
      logE"received wifi enabled"()
      enabled match {
        case true => 
          context.actorSelection("/user/drone") ! Drone.Engage(NetworkManager.droneAddress)          
        case false => throw new Exception("Wifi Not Enabled")
      }

      case AttachUi(_) ⇒
        withUi(f ⇒ f.receive)
        
      case DetachUi ⇒      
  }


}
