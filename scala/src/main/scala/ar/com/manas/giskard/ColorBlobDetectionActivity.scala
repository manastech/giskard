package ar.com.manas.giskard

import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.util.Log
import android.view.ViewGroup.LayoutParams._
import android.view.ViewGroup.LayoutParams
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.view.View.OnTouchListener
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Button

import macroid._
import macroid.FullDsl._
import macroid.akkafragments.AkkaActivity
import macroid.util.Ui

import akka.actor.Props
import akka.actor.Kill
import akka.actor.ActorRef
import akka.event.Logging._

import org.opencv.android.BaseLoaderCallback
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame
import org.opencv.android.JavaCameraView
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.core.Scalar
import org.opencv.core.CvType
import org.opencv.core.Rect

import org.opencv.imgproc.Imgproc

import android.util.Log

class ColorBlobDetectionActivity extends FragmentActivity with Contexts[FragmentActivity] with IdGeneration with AutoLogTag with NicerScalars {

  lazy val openCvLoaderCallback = new BaseLoaderCallback(this) {
    override def onManagerConnected(status: Int) = loadOpenCV(this, status)
  }

  lazy val touchCallback = new OnTouchListener {
    def onTouch(v: View, event: MotionEvent) = handleTouch(event)
  }

  implicit def cameraViewListenerWrapper() = new CvCameraViewListener2 {
    def onCameraFrame(inputFrame: CvCameraViewFrame) = ColorBlobDetectionActivity.this.onCameraFrame(inputFrame)
    def onCameraViewStarted(width: Int, height: Int) = ColorBlobDetectionActivity.this.onCameraViewStarted(width, height)
    def onCameraViewStopped() = ColorBlobDetectionActivity.this.onCameraViewStopped()
  }

  var isColorSelected = false
  var rgba: Option[Mat] = None
  var blobColorRgba: Option[Scalar] = None
  var blobColorHsv: Option[Scalar] = None
  var detector: Option[ColorBlobDetector] = None
  var spectrum: Option[Mat] = None

  var openCvCameraView : Option[CameraBridgeViewBase] = None

  var spectrumSize: Option[Size] = None
  var contourColor: Option[Scalar] = None

  def loadOpenCV(callback: BaseLoaderCallback, status: Int) = status match {
    case LoaderCallbackInterface.SUCCESS => {
      Log.e("ColorBlobDetectionActivity", "OpenCV loaded successfully")

      openCvCameraView match {
        case Some(c) => {
          c.enableView
          c.setOnTouchListener(touchCallback)
        }
      }
    }
    case _ => callback.onManagerConnected(status)
  }

  override def onCreate(savedInstanceState: Bundle) = {
    logE"called onCreate of ColorBlobDetectorActivity"()
    super.onCreate(savedInstanceState)

    requestWindowFeature(Window.FEATURE_NO_TITLE)
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    arrangeView
  }

  def arrangeView = {
    val cameraLayoutParams = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT)

    // Open CV needs a view id to retrieve this view. Since
    // we're not using the usual XML resource system, we just assign
    // it manually
    val camera = new JavaCameraView(this, CameraBridgeViewBase.CAMERA_ID_ANY)
    camera.setLayoutParams(cameraLayoutParams)
    camera.setCvCameraViewListener(cameraViewListenerWrapper)

    openCvCameraView = Some(camera)

    val rootLayoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

    val linearLayout = new LinearLayout(this)
    linearLayout.setLayoutParams(rootLayoutParams)
    linearLayout.addView(camera)

    setContentView(linearLayout)
  }

  override def onPause() = {
    super.onPause()
    disableCamera
  }

  override def onResume() = {
    super.onResume()

    logE"Loading OpenCV"()

    OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, openCvLoaderCallback)
  }

  override def onDestroy() = {
    super.onDestroy()
    disableCamera
  }

  override def onStart() = {
    super.onStart()
  }

  def disableCamera = openCvCameraView match {
    case Some(bridge) => bridge.disableView()
  }

  def onCameraViewStarted(width : Int, height : Int) = {
    rgba = Some(new Mat(height, width, CvType.CV_8UC4))
    detector = Some(new ColorBlobDetector)
    spectrum = Some(new Mat)
    blobColorRgba = Some(new Scalar(255))
    blobColorHsv = Some(new Scalar(255))
    spectrumSize = Some(new Size(200, 64))
    contourColor = Some(new Scalar(255, 0, 0, 255))
  }

  def onCameraViewStopped() = rgba match { case Some(r) => r.release() }


  def handleTouch(event: MotionEvent): Boolean = {
    (openCvCameraView, rgba, spectrum, spectrumSize, detector) match {
      case (Some(c), Some(someRgba), Some(spectr), Some(spectrSize), Some(det)) => {
        val cols = someRgba.cols()
        val rows = someRgba.rows()

        val xOffset = (c.getWidth() - cols) / 2
        val yOffset = (c.getHeight() - rows) / 2

        val x = event.getX() - xOffset
        val y = event.getY() - yOffset

        logE"Touch image coordinates: ($x, $y)"()

        if (x < 0 || y < 0 || x > cols || y > rows) {
          return false
        }

        val touchedRect = new Rect

        touchedRect.x = (if (x > 4) (x - 4) else 0).toInt
        touchedRect.y = (if (y > 4) (y - 4).toInt else 0).toInt

        touchedRect.width = (if (x + 4 < cols) x + 4 - touchedRect.x else cols - touchedRect.x).toInt

        touchedRect.height = (if (y + 4 < rows) y + 4 - touchedRect.y else rows - touchedRect.y).toInt

        val touchedRegionRgba = someRgba.submat(touchedRect)
        val touchedRegionHsv = new Mat

        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL)

        logE"Touched HSV region: $touchedRegionHsv"()

        val bchsv = Core.sumElems(touchedRegionHsv)
        logE"Touched HSV apenas inicializado: $bchsv"()
        blobColorHsv = Some(bchsv)

        val pointCount = touchedRect.width * touchedRect.height

        for (i <- 0 until bchsv.value.length) {
          bchsv.value(i) /= pointCount
        }

        val bcrgba = convertScalarHsv2Rgba(bchsv)
        blobColorRgba = Some(bcrgba)

        logE"Touched HSV color: $bchsv"()
        logE"Touched rgba color: $bcrgba"()

        det.setHsvColor(bchsv)

        Imgproc.resize(det.getSpectrum, spectr, spectrSize)

        isColorSelected = true

        touchedRegionRgba.release()
        touchedRegionHsv.release()

        false
      }
    }
  }


  def onCameraFrame(inputFrame : CvCameraViewFrame)  = {
    logE"Received Frame"()

    val someRgba = inputFrame.rgba()
    rgba = Some(someRgba)

    (contourColor, blobColorRgba, spectrum, detector) match {
      case (Some(color), Some(blobRgba), Some(spectr), Some(det)) =>
        if (isColorSelected) {
          det.process(someRgba)

          val contours = det.getContours()
          val amountOfContours = contours.size()

          logE"Contours count: $amountOfContours"()

          Imgproc.drawContours(someRgba, contours, -1, color)

          val colorLabel = someRgba.submat(4, 68, 4, 68)
          colorLabel.setTo(blobRgba)

          val spectrumLabel = someRgba.submat(4, 4 + spectr.rows(), 70, 70 + spectr.cols())
          spectrumLabel.copyTo(spectrumLabel)
        }
      case _ =>
        logE"Unexpected status when receiving frame"()

    }

    someRgba
  }

  def convertScalarHsv2Rgba(hsvColor : Scalar) = {
    val pointMatRgba = new Mat
    val pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor)

    Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4)

    new Scalar(pointMatRgba.get(0, 0))
  }
}
