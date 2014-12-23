package ar.com.manas.giskard

import java.io.IOException

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor.{Actor, Props, ActorLogging, ActorRef, Cancellable}

import macroid.Logging._
import macroid.AutoLogTag

import Drone._
import DroneUnits._

trait Behavior extends Actor with ActorLogging with AutoLogTag {
  val CommandDelay = 20


  lazy val drone = context.actorSelection("/user/drone")

  def pause(t: Seconds) = { Thread.sleep((t * 1000).toInt) }
  def forward() = { command(0, -0.2f, 0, 0) }
  def rotate() = { command(0, 0, 0, 1f) }
  def moveUp() = { command(0, 0, 0.5f, 0) }
  def moveDown() = { command(0, 0, -0.5f, 0) }
  def land = { drone ! Drone.Land }

  private def command(leftRightTilt: Float, frontBackTilt: Float, verticalSpeed: Meters, angularSpeed: Float) = {
    for (i <- 0 until 10) {
      drone ! Drone.Move(leftRightTilt, frontBackTilt, verticalSpeed, angularSpeed)  
      Thread.sleep(CommandDelay)
    }
  }
}