package ar.com.manas.giskard

import java.io.IOException
import java.net.InetAddress
import java.text.DecimalFormat

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor.{Actor, Props, ActorLogging, ActorRef}

import macroid.Logging._
import macroid.AutoLogTag

import com.codeminders.ardrone.ARDrone

object Drone {
  case object Init
  case object Land
  case class Engage(address: Array[Byte])

  def props = Props(new Drone)
}

object DroneTimeouts {
  val navDataTimeout = 10000
  val videoTimeout = 60000
  val connectionTimeout = 10000
}

object DroneParams {
  val maxYaw = "control:control_yaw"
  val maxVertSpeed = "control:control_vz_max"
  val maxEULAAngle = "control:euler_angle_max"
  val maxAltitude = "control:altitude_max"

  val twoDForm = new DecimalFormat("#.##")
}

class Drone extends Actor with ActorLogging with AutoLogTag {
  import Drone._
  import DroneTimeouts._
  import DroneParams._

  var drone: Option[ARDrone] = None

  val network = context.actorOf(Props[NetworkManager], name = "network")

  def receive = {
    case Init =>
      logE"Init drone"()
      network ! NetworkManager.Connect
    case Engage(address) => 
      val addr = address.toString()
      logE"Engage address $addr"()

      engageDrone(address)

      drone match {
        case Some(d) =>
          d.takeOff
          context.system.scheduler.schedule(Duration.Zero, 5 seconds, self, Land)
        case None => logE"Lost connection with drone"
      }
    case Land =>
      drone match {
        case Some(d) => d.land
        case None => logE"Lost connection with drone"
      }
  }

  def engageDrone(address: Array[Byte]) = {
    try {
      val actualDrone = new ARDrone(InetAddress.getByAddress(address), navDataTimeout, videoTimeout)

      drone = Some(actualDrone)

      actualDrone.connect
      actualDrone.clearEmergencySignal
      actualDrone.trim
      actualDrone.waitForReady(connectionTimeout)
      actualDrone.playLED(1, 10, 4)
      actualDrone.selectVideoChannel(ARDrone.VideoChannel.HORIZONTAL_ONLY)      
      actualDrone.setCombinedYawMode(true)

      setupDrone
    } catch {    
      case ioe: IOException =>
        logE"Failed to connect to drone"()
      case _: Throwable =>  
        try {
          releaseDrone
        } catch {
          case _: Throwable => logE"Failed to clear drone state"()
        }        
    }    
  }

  def releaseDrone = {
    drone match {
      case Some(d) =>
        d.clearEmergencySignal()
        d.clearImageListeners()
        d.clearNavDataListeners()
        d.clearStatusChangeListeners()
        d.disconnect()  
      case _ =>
    }    
  }

  def setupDrone = {
    drone match {
      case Some(d) =>
        d.setConfigOption(maxAltitude, String.valueOf(Math.round(1.5f * 1000)))
        d.setConfigOption(maxEULAAngle, twoDForm.format(6f * Math.PI / 180f).replace(',', '.'))
        d.setConfigOption(maxVertSpeed, String.valueOf(Math.round(1f * 1000)))
        d.setConfigOption(maxYaw, twoDForm.format(50f * Math.PI / 180f).replace(',', '.'))      
      case None =>                
    }
  }
}