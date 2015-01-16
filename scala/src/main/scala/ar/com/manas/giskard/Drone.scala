package ar.com.manas.giskard

import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.text.DecimalFormat

import android.graphics.Bitmap
import android.os.Environment

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor.{Actor, Props, ActorLogging, ActorRef, Cancellable}

import macroid.Logging._
import macroid.AutoLogTag

import com.codeminders.ardrone.ARDrone
import com.codeminders.ardrone.DroneStatusChangeListener
import com.codeminders.ardrone.DroneVideoListener
import com.codeminders.ardrone.NavDataListener
import com.codeminders.ardrone.NavData

import org.opencv.highgui.Highgui
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.core.Scalar
import org.opencv.core.CvType
import org.opencv.core.Rect

object DroneUnits {
  type Meters = Float
  type Seconds = Double
}

object Drone {
  import DroneUnits._

  sealed abstract trait Message
  case object Init extends Message
  case object TakeOff extends Message
  case object Land extends Message
  case object PollState extends Message
  case object Disconnect extends Message
  case object PrintNavData extends Message
  case object AskAltitude extends Message
  case object SaveSnapshot extends Message
  case class Engage(address: Array[Byte]) extends Message
  case class Move(leftRightTilt: Float, frontBackTilt: Float, verticalSpeed: Meters, angularSpeed: Float) extends Message

  sealed abstract trait Response
  case class AltitudeIs(x: Meters) extends Response

  def props = Props(new Drone)
}

object DroneTimeouts {
  val NavDataTimeout = 10000
  val VideoTimeout = 60000
  val ConnectionTimeout = 10000
}

object DroneParams {
  val MaxYaw = "control:control_yaw"
  val MaxVertSpeed = "control:control_vz_max"
  val MaxEULAAngle = "control:euler_angle_max"
  val MaxAltitude = "control:altitude_max"

  val TwoDForm = new DecimalFormat("#.##")
}

case class VideoFrame(startX: Int, startY: Int, w: Int, h: Int, rgbArray: Array[Int], offset: Int, scansize: Int)

class Drone extends Actor with ActorLogging with AutoLogTag {
  import Drone._
  import DroneTimeouts._
  import DroneParams._

  implicit def readyWrapper(func: () => Unit) = new DroneStatusChangeListener { def ready() = func() }

  implicit def navDataReceivedWrapper(func: NavData => Unit) = new NavDataListener { def navDataReceived(nd: NavData) = func(nd) }

  implicit def videoReceiver(func: VideoFrame => Unit) = new DroneVideoListener {
    def frameReceived(startX: Int, startY: Int, w: Int, h: Int, rgbArray: Array[Int], offset: Int, scansize: Int) = {
      func(new VideoFrame(startX, startY, w, h, rgbArray, offset, scansize))
    }
  }

  var drone: Option[ARDrone] = None
  var landCommand: Option[Cancellable] = None
  var currentFrame: Option[VideoFrame] = None
  var currentNavData: Option[NavData] = None


  var rgba: Option[Mat] = None
  var blobColorHsv: Option[Scalar] = None
  var detector: Option[ColorBlobDetector] = None
  var spectrum: Option[Mat] = None

  def receive = {
    case message: Message => message match {
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
            logE"Received disconnection request"()
            releaseDrone
          case None => logE"Lost connection with drone"()
        }
      case Move(leftRightTilt, frontBackTilt, verticalSpeed, angularSpeed) =>
        drone match {
          case Some(d) =>
            logE"Received Move request ($leftRightTilt, $frontBackTilt, $verticalSpeed, $angularSpeed)"()
            d.move(leftRightTilt, frontBackTilt, verticalSpeed, angularSpeed)
          case None => logE"Lost connection with drone"()
        }
      case SaveSnapshot =>
        drone match {
          case Some(d) =>
            logE"Received save snapshot request"()
            saveSnapshot
          case None => logE"Lost connection with drone"()
        }
      case AskAltitude =>
        currentNavData match {
          case Some(n) => sender ! AltitudeIs(n.getAltitude())
          case None => sender ! AltitudeIs(0)
        }
    }
  }

  def saveSnapshot = {
    currentFrame match {
      case Some(VideoFrame(_, _, w, h, rgbArray, offset, scansize)) =>
        var b = Bitmap.createBitmap(rgbArray, offset, scansize, w, h, Bitmap.Config.RGB_565)
        b.setDensity(100)

        var photo = new File(Environment.getExternalStorageDirectory(), "photo.jpg")

        if (photo.exists()) {
          photo.delete()
        }

        var out = new FileOutputStream(photo.getPath())

        b.compress(Bitmap.CompressFormat.PNG, 100, out)
        if (out != null) {
            out.close()
        }

        initializeBlobDetection
        detector = Some(new ColorBlobDetector)

        val someRgba = Highgui.imread(photo.getPath())

        val bchsv = new Scalar(Array(26, 68, 85.9, 0.0))
        detector.get.setHsvColor(bchsv)

        detector.get.process(someRgba)

        val contours = detector.get.getContours()
        val amountOfContours = contours.size()

        logE"Contours count: $amountOfContours"()

        // Imgproc.drawContours(someRgba, contours, -1, color)

        // val colorLabel = someRgba.submat(4, 68, 4, 68)

        // val spectrumLabel = someRgba.submat(4, 4 + spectr.rows(), 70, 70 + spectr.cols())
        // spectrumLabel.copyTo(spectrumLabel)

        // var photo2 = new File(Environment.getExternalStorageDirectory(), "photo2.jpg")

        // if (photo2.exists()) {
        //   photo2.delete()
        // }

        // var out = new FileOutputStream(photo2.getPath())

        // b.compress(Bitmap.CompressFormat.PNG, 100, out)
        // if (out != null) {
        //     out.close()
        // }
        someRgba

      case None =>
        logE"No frame captured to save into image"()
    }

    // val someRgba = currentFrame.rgba()

  }

  def initializeBlobDetection = {
    rgba = Some(new Mat(480, 640, CvType.CV_8UC4))
    spectrum = Some(new Mat)
    blobColorHsv = Some(new Scalar(255))
  }

  def prepare: () => Unit = () => {
    logE"Drone Status is now READY"()

    drone match {
      case Some(d) =>
        try {
          logE"Adding video listener"()
          d.addImageListener(captureFrame)
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

  def captureFrame: (VideoFrame) => Unit = (f: VideoFrame) => {
    logE"Received video frame"()
    currentFrame = Some(f)
  }

  def navDataLog: (NavData) => Unit = (n: NavData) => {
    currentNavData = Some(n)
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

          logE"Adding method navDataLog as new nav data listener"()
          d.addNavDataListener(navDataLog)

          logE"Connecting..."()
          d.connect

          logE"Selecting video channel..."()
          d.selectVideoChannel(ARDrone.VideoChannel.HORIZONTAL_ONLY)

          logE"Clearing emergency signal"()
          d.clearEmergencySignal

          logE"Setting up drone"()
          setupDrone
        case None =>
          logE"Creating ARDrone instance"()
          drone = Some(new ARDrone(InetAddress.getByAddress(address), NavDataTimeout, VideoTimeout))
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
        d.setConfigOption(MaxAltitude, String.valueOf(Math.round(1.5f * 1000)))
        d.setConfigOption(MaxEULAAngle, TwoDForm.format(6f * Math.PI / 180f).replace(',', '.'))
        d.setConfigOption(MaxVertSpeed, String.valueOf(Math.round(1f * 1000)))
        d.setConfigOption(MaxYaw, TwoDForm.format(50f * Math.PI / 180f).replace(',', '.'))
      case None =>
    }
  }
}
