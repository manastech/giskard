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
import com.codeminders.ardrone.DroneStatusChangeListener
import com.codeminders.ardrone.NavDataListener
import com.codeminders.ardrone.NavData

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

  implicit def readyWrapper(func: () => Unit) = new DroneStatusChangeListener { def ready() = func() }   

  implicit def navDataReceivedWrapper(func: (NavData) => Unit) = new NavDataListener { def navDataReceived(nd: NavData) = func(nd) }
    
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
    case Land =>
      drone match {
        case Some(d) => d.land
        case None => logE"Lost connection with drone"
      }
  }

  def prepareAndTakeOff: () => Unit = () => {
    logE"Drone Status is now READY"()

    drone match {
      case Some(d) =>
        logE"Trimming..."
        d.trim
        logE"Playing LED sequence..."
        d.playLED(1, 10, 4)
        logE"Selecting video channel..."
        d.selectVideoChannel(ARDrone.VideoChannel.HORIZONTAL_ONLY)      
        logE"Setting combined Yaw Mode..."
        d.setCombinedYawMode(true)

        logE"Alright, taking off!"
        d.takeOff
        context.system.scheduler.schedule(Duration.Zero, 5 seconds, self, Land)
      case None => logE"Lost connection with drone"
    }
  }

  def engageDrone(address: Array[Byte]) = {    
    try {
      drone = Some(new ARDrone(InetAddress.getByAddress(address), navDataTimeout, videoTimeout))

      drone match {
        case Some(d) =>
          d.connect
          d.clearEmergencySignal

          d.addStatusChangeListener(prepareAndTakeOff)
        case None =>
      }

      setupDrone
    } catch {    
      case ioe: IOException =>
        val msg = ioe.getMessage
        val cause = ioe.getCause.toString() 
        logE"Failed to connect to drone: $msg"()
        releaseDrone
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