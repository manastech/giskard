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

class ColorBlobDetectionActivity extends FragmentActivity with Contexts[FragmentActivity] with IdGeneration with NicerScalars {

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
  var rgba = new Mat()
  var blobColorRgba = new Scalar(255)
  var blobColorHsv = new Scalar(255)
  var detector = new ColorBlobDetector
  var spectrum = new Mat

  var openCvCameraView : Option[CameraBridgeViewBase] = None

  var spectrumSize = new Size(200, 64)
  var contourColor = new Scalar(255, 0, 0, 255)

  def loadOpenCV(callback: BaseLoaderCallback, status: Int) = status match {
    case LoaderCallbackInterface.SUCCESS => {
      logE"OpenCV loaded successfully"()

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
    val camera = new JavaCameraView(this, 42)
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
    rgba = new Mat(height, width, CvType.CV_8UC4)
    detector = new ColorBlobDetector
    spectrum = new Mat
    blobColorRgba = new Scalar(255)
    blobColorHsv = new Scalar(255)
    spectrumSize = new Size(200, 64)
    contourColor = new Scalar(255, 0, 0, 255)
  }

  def onCameraViewStopped() = {
    rgba.release()
  }

  def handleTouch(event: MotionEvent): Boolean = {
    openCvCameraView match {
      case Some(c) => {
        val cols = rgba.cols()
        val rows = rgba.rows()

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

        val touchedRegionRgba = rgba.submat(touchedRect)
        val touchedRegionHsv = new Mat

        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL)

        blobColorHsv = Core.sumElems(touchedRegionHsv)

        val pointCount = touchedRect.width * touchedRect.height

        for (i <- 0 until blobColorHsv.value.length) {
          blobColorHsv.value(i) /= pointCount
        }

        blobColorRgba = convertScalarHsv2Rgba(blobColorHsv)

        logE"Touched rgba color: ($blobColorRgba.value[0], $blobColorRgba.value[1], $blobColorRgba.value[2], blobColorRgba[3])"()

        detector.setHsvColor(blobColorHsv)

        Imgproc.resize(detector.getSpectrum, spectrum, spectrumSize)

        isColorSelected = true

        touchedRegionRgba.release()
        touchedRegionHsv.release()

        false
      }
    }
  }


  def onCameraFrame(inputFrame : CvCameraViewFrame) = {
    rgba = inputFrame.rgba()

    if (isColorSelected) {
      detector.process(rgba)

      val contours = detector.getContours()

      logE"Contours count: $contours.size()"()

      Imgproc.drawContours(rgba, contours, -1, contourColor)

      val colorLabel = rgba.submat(4, 68, 4, 68)
      colorLabel.setTo(blobColorRgba)

      val spectrumLabel = rgba.submat(4, 4 + spectrum.rows(), 70, 70 + spectrum.cols())
      spectrumLabel.copyTo(spectrumLabel)
    }

    rgba
  }

  def convertScalarHsv2Rgba(hsvColor : Scalar) = {
    val pointMatRgba = new Mat
    val pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor)

    Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4)

    new Scalar(pointMatRgba.get(0, 0))
  }
}
