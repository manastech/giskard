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

class Square extends Behavior {
  import Square._
  import Drone._

  def receive = {
    case Start =>
      drone ! Drone.TakeOff
      
      //Give it enough time to stabilize
      pause(8)

      for (i <- 0 until 40) {
        forward()
        pause(2)

        rotate()  
        pause(2)
      }    

      pause(2)

      land

    case Stop => land
  }
}