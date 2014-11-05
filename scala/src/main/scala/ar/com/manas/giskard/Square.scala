package ar.com.manas.giskard

import java.io.IOException

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor.{Actor, Props, ActorLogging, ActorRef, Cancellable}

import macroid.Logging._
import macroid.AutoLogTag

object Square {
  case object Start
  case object Stop

  def props = Props(new Square)
}


class Square extends Actor with ActorLogging with AutoLogTag {
  import Square._
  import Drone._

  lazy val drone = context.actorSelection("/user/drone")

  def receive = {
    case Start =>
      drone ! Drone.TakeOff
      
      pause(2)

      forward(0.5f)

      for (i <- 0 until 1) {
        rotate(90)  
        forward(0.5f)
      }    

      pause(2)

      land

    case Stop => land
  }

  def pause(seconds: Double) = {
    Thread.sleep((seconds * 1000).toInt)
  }

  def forward(meters: Float) = {
    val angularSpeed = 0
    val frontBackTilt = -0.1f

    for (i <- 0 until 100) {
      drone ! Drone.Move(angularSpeed, frontBackTilt)  
      Thread.sleep(20)
    }
  }

  def rotate(degrees: Double) = {
    /*With current settings:
        d.setConfigOption(maxAltitude, String.valueOf(Math.round(1.5f * 1000)))
        d.setConfigOption(maxEULAAngle, twoDForm.format(6f * Math.PI / 180f).replace(',', '.'))
        d.setConfigOption(maxVertSpeed, String.valueOf(Math.round(1f * 1000)))
        d.setConfigOption(maxYaw, twoDForm.format(50f * Math.PI / 180f).replace(',', '.'))      

        An angular speed of 1f repeated 200 times once each 20ms yields a 180 degree rotation
    */
    val angularSpeed = 0.5f
    val frontBackTilt = 0

    for (i <- 0 until 200) {
      drone ! Drone.Move(angularSpeed, frontBackTilt)
      Thread.sleep(20)
    }
  }

  def land = {
    drone ! Drone.Land
  }
}