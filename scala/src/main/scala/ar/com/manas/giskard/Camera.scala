package ar.com.manas.giskard

import java.io.IOException

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor.{Actor, Props, ActorLogging, ActorRef, Cancellable}

import macroid.Logging._
import macroid.AutoLogTag

object Camera {
  case object SaveSnapshot

  def props = Props(new Camera)
}

class Camera extends Behavior {
  import Camera._
  import Drone._

  def receive = {
    case Camera.SaveSnapshot =>
      logE"Received SaveSnapshot"()
      drone ! Drone.SaveSnapshot
  }
}