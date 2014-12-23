package ar.com.manas.giskard

import java.io.IOException

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.math._

import akka.actor.{Actor, Props, ActorLogging, ActorRef, Cancellable, FSM}

import macroid.Logging._
import macroid.AutoLogTag

import DroneUnits._

object RiseAndHover {
  sealed trait Message  
  case object Start extends Message
  case object Stop extends Message

  sealed trait State
  case object Off extends State
  case object Stabilizing extends State
  case object ApproachingTarget extends State
  case object AtTarget extends State

  sealed trait Data
  case object None extends Data
  case class Target(altitude: Meters) extends Data

  def props = Props(new RiseAndHover)
}

class RiseAndHover extends Behavior with FSM[RiseAndHover.State, RiseAndHover.Data] {
  import RiseAndHover._

  val StabilizingDelay = 8 seconds
  val VerticalStep = 0.5f
  val FinalAltitude = 3 * VerticalStep
  val ErrorTolerance = 0.05

  startWith(Off, None)

  when(Off) {
    case Event(Start, None) => 
      drone ! Drone.TakeOff
      goto(Stabilizing)
  }

  when(Stabilizing, stateTimeout = 8 seconds) {      
    case Event(FSM.StateTimeout, None) => 
      logE"Stabilizing"()
      logE"Current target altitude $VerticalStep"()
      goto(ApproachingTarget) using Target(VerticalStep)
  }

  when(ApproachingTarget) {
    case Event(Drone.AltitudeIs(currentAltitude), Target(altitude)) =>
      logE"ApproachingTarget"()
      logE"Current measured altitude $currentAltitude"()
      logE"Current target $altitude"()
      val delta = currentAltitude - altitude 
      if (delta.abs <= ErrorTolerance) {
        goto(AtTarget) using Target(altitude)
      }  else {        
        if (delta < 0) moveUp() else moveDown()

        drone ! Drone.AskAltitude
        stay
      }
  }

  when(AtTarget, stateTimeout = 5 seconds) {
    case Event(StateTimeout, Target(altitude)) => 
      logE"AtTarget"()
      logE"Current target $altitude"()
      if (altitude == FinalAltitude) {
        land
        goto(Off) using None
      } else {
        goto(ApproachingTarget) using Target(altitude + VerticalStep)
      }
  }

  // Candidate for an extended FSM
  whenUnhandled {
    case Event(e, s) =>       
      logE"received unhandled request $e in state $s"()
      stay
  }

  onTransition {
    case _ -> ApproachingTarget =>
      stateData match {
        case _ => 
          logE"Transitioning from stabilizing to ApproachingTarget"()
          drone ! Drone.AskAltitude
      }
  }

  initialize()
}