package ar.com.manas.giskard

import java.io.IOException
import java.net.InetAddress
import java.text.DecimalFormat

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor.{Actor, Props, ActorLogging, ActorRef, Cancellable}

import macroid.Logging._
import macroid.AutoLogTag

import com.codeminders.ardrone.ARDrone
import com.codeminders.ardrone.DroneStatusChangeListener
import com.codeminders.ardrone.NavDataListener
import com.codeminders.ardrone.NavData

object Drone {
  case object Init
  case object TakeOff
  case object Land
  case object PollState
  case object Disconnect
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
  var landCommand: Option[Cancellable] = None

  def receive = {
    case Init =>
      logE"Init drone"()
      self ! Engage(Array[Byte](192.toByte, 168.toByte, 1, 1))
    case Engage(address) => 
      val addr = address.toString()
      logE"Engage address $addr"()
      engageDrone(address)     
    case TakeOff =>
      logE"Received takeoff request"()
      drone match {
        case Some(d) => 
          d.takeOff
          context.system.scheduler.scheduleOnce(10 seconds, self, Land)
        case None => logE"Lost connection with drone"()
      }      
    case Land =>
      drone match {
        case Some(d) => 
          logE"Landing..."()
          d.land
        case None => logE"Lost connection with drone"()
      }
    case PollState =>
      drone match {
        case Some(d) =>
          val state = d.getState
          logE"Current drone state is: $state"()
        case None => logE"Lost connection with drone"()
      }
    case Disconnect =>
      drone match {
        case Some(d) => 
          logE"Received disconnection request"
          releaseDrone
        case None => logE"Lost connection with drone"()
      }
        
  }

  def prepare: () => Unit = () => {
    logE"Drone Status is now READY"()

    drone match {
      case Some(d) =>
        try {
          logE"Setting combined Yaw Mode..."()
          d.setCombinedYawMode(true)
          logE"Trimming..."()
          d.trim          
        } catch {
          case ioe: IOException => d.changeToErrorState(ioe)
        }        
      case None => logE"Lost connection with drone"()
    }
  }

  def engageDrone(address: Array[Byte]) : Unit = {    
    logE"Trying to engage drone"()
    try {
      logE"Matching over drone"()
      drone match {
        case Some(d) =>          
          logE"Getting drone state"()
          val droneState = d.getState          
          logE"Drone state is: $droneState"()

          if (droneState != ARDrone.State.DISCONNECTED) {
            logE"Disconnecting..."()
            d.disconnect  
          }
          
          logE"Clearing status change listeners"()
          d.clearStatusChangeListeners
          logE"Adding method prepare as new status change listener"()
          d.addStatusChangeListener(prepare)

          logE"Connecting..."()
          d.connect
          logE"Clearing emergency signal"()
          d.clearEmergencySignal
          
          logE"Setting up drone"()
          setupDrone
        case None =>
          logE"Creating ARDrone instance"()
          drone = Some(new ARDrone(InetAddress.getByAddress(address), navDataTimeout, videoTimeout))
          self ! Engage(address)
      }
    } catch {    
      case ioe: IOException =>
        val msg = ioe.getMessage
        val cause = ioe.getCause.toString() 
        logE"Failed to connect to drone: $msg"()
        releaseDrone
      case e: Exception =>
        e.printStackTrace
        val msg = e.getMessage
        logE"Failed to clear drone state: $msg"()
        releaseDrone        
      case t: Throwable =>  
        try {
          logE"Trying to release drone"()
          releaseDrone
        } catch {
          case t: Throwable => {
            val kindOfThrowable = t.getClass
            logE"Failed to clear drone state. Kind of throwable: $kindOfThrowable"()
          }
        }        
    }    
  }

  def releaseDrone = {
    drone match {
      case Some(d) =>
        logE"releaseDrone: Clearing emergency signal"()
        d.clearEmergencySignal
        logE"releaseDrone: Clearing image listeners"()
        d.clearImageListeners
        logE"releaseDrone: Clearing nav data listeners"()
        d.clearNavDataListeners
        logE"releaseDrone: Clearing status change listeners"()
        d.clearStatusChangeListeners
        logE"releaseDrone: disconnecting"()
        d.disconnect
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