package ar.com.manas.giskard

import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.util.Log
import android.view.ViewGroup.LayoutParams._
import android.view.Gravity
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

class ColorBlobDetectionActivity extends FragmentActivity with Contexts[FragmentActivity] with IdGeneration with AkkaActivity {

  implicit def loaderCallbackWrapper(func: Integer => Unit) = new BaseLoaderCallback(self) { 
      def onManagerConnected(status: Integer) = func(status)
    }   

  var isColorSelected = false
  var rgba : Mat
  var blobColorRgba : Scalar
  var blobColorHsv : Scalar
  var detector : ColorBlobDetector
  var spectrum : Mat

  var openCvCameraView : Option[CameraBridgeViewBase] = None

  val SpectrumSize : Size
  val ContourColor : Scalar

  def loadOpenCV(status: Integer) = match status {
    case LoaderCallbackInterface.SUCCESS => {
      logE"OpenCV loaded successfully"()
      openCvCameraView.enableView()
      openCvCameraView.setOnTouchListener(ColorBlobDetectionActivity.this)
    }
    case _ => {
      super.onManagerConnected(status)
    }
  }

  override def onCreate(savedInstanceState: Bundle) = {
    logE"called onCreate"()
    super.onCreate(savedInstanceState)

    requestWindowFeature(Window.FEATURE_NO_TITLE)
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    setContentView(R.layout.color_blob_detection_surface_view)

    openCvCameraView = findViewById(R.id.color_blob_detection_activity_surface_view)

    openCvCameraView.setCvCameraViewListener(self)

    setContentView(getUi(view))
  }

  override def onPause() = {
    super.onPause()
    disableCamera()
  }

  override def onResume() = {
    super.onResume()
    OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, self, loadOpenCV)
  }

  override def onDestroy() = {
    super.onDestroy()
    disableCamera()
  }

  override def onStart() = {
    super.onStart()
  }

  def disableCamera = openCvCameraView match {
    case Just(bridge) => bridge.disableView()
  }

  def onCameraViewStarted(width : Integer, height : Integer) = {
    rgba = new Mat(height, width, CvType.CV_8UC4)
    detector = new ColorBlobDetector
    spectrum = new Mat
    blobColorRgba = new Scalar(255)
    blobColorHsv = new Scalar(255)
    SpectrumSize = new Size(200, 64)
    ContourColor = new Scalar(255, 0, 0, 255)
  }

  def onCameraViewStopped() = {
    rgba.release()
  }

  def onTouch(v : View, event : MotionEvent) = openCvCameraView match {
      case Just(c) =>
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

        touchedRect.x = if (x > 4) x - 4 else 0
        touchedRect.y = if (y > 4) y - 4 else 0

        touchedRect.width = if (x + 4 < cols) x + 4 - touchedRect.x else cols - touchedRect.x

        touchedRect.height = if (y + 4 < rows) y + 4 - touchedRect.y else rows - touchedRect.y 

        val touchedRegionRgba = rgba.submat(touchedRect)
        val touchedRegionHsv = new Mat

        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL)

        blobColorHsv = Core.sumElems(touchedRegionHsv)

        val pointCount = touchedRect.width * touchedRect.height

        for (i <- 0 until blobColorHsv.val.length) {
          blobColorHsv.val[i] /= pointCount
        }

        blobColorRgba = convertScalarHsv2Rgba(blobColorHsv)

        logE"Touched rgba color: ($blobColorRgba.val[0], $blobColorRgba.val[1], $blobColorRgba.val[2], blobColorRgba[3])"()

        detector.setHsvColor(blobColorHsv)

        Imgproc.resize(detector.getSpectrum(), spectrum, SpectrumSize)

        isColorSelected = true

        touchedRegionRgba.release()
        touchedRegionHsv.release()

        false
    }

    def onCameraFrame(inputFrame : CvCameraViewFrame) = {
      rgba = inputFrame.rgba()

      if (isColorSelected) {
        detector.process(rgba)

        val contours = detector.getContours()

        logE"Contours count: $contours.size()"()

        Imgproc.drawContours(rgba, contours, -1, ContourColor)

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
}
