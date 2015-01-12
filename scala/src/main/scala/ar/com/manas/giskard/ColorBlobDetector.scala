package ar.com.manas.giskard

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

object ColorBlobDetector {
  val MinContourArea = 0.1
}

class ColorBlobDetector {
  import ColorBlobDetector._

  val lowerBound = new Scalar(0)
  val upperBound = new Scalar(0)

  val colorRadius = new Scalar(25, 50, 50, 0)
  val spectrum = new Mat

  val contours = new ArrayList[MatOfPoint]

  val pyrDownMat = new Mat
  val hsvMat = new Mat
  val mask = new Mat
  val dilatedMask = new Mat
  val hierarchy = new Mat

  def setColorRadius(radius : Scalar) = {
    colorRadius = radius
  }

  def setHsvColor(hsvColor : Scalar) = {
    val minH = if ((hsvColor.val(0) >= colorRadius.val(0))) hsvColor.val(0) - colorRadius.val(0) else 0
    val maxH = if ((hsvColor.val(0) + colorRadius.val(0) <= 255)) hsvColor.val(0) + colorRadius.val(0) else 255

    lowerBound.val(0) = minH
    upperBound.val(0) = maxH
    lowerBound.val(1) = hsvColor.val(1) - colorRadius.val(1)
    upperBound.val(1) = hsvColor.val(1) + colorRadius.val(1)
    lowerBound.val(2) = hsvColor.val(2) - colorRadius.val(2)
    upperBound.val(2) = hsvColor.val(2) + colorRadius.val(2)
    lowerBound.val(3) = 0
    upperBound.val(3) = 255

    val spectrumHsv = new Mat(1, (maxH - minH).toInt, CvType.CV_8UC3)

    for (j <- 0 until maxH - minH) {
      val tmp = Array((minH + j).toByte, 255.toByte, 255.toByte)
      spectrumHsv.put(0, j, tmp)
    }
    Imgproc.cvtColor(spectrumHsv, spectrum, Imgproc.COLOR_HSV2RGB_FULL, 4) 
  }

  def getSpectrum: Mat = {
    spectrum
  }

  def minContourArea(area) = {
    minContourArea = area
  }

  def process(rgbaImage: Mat) = {
    Imgproc.pyrDown(rgbaImage, pyrDownMat)
    Imgproc.pyrDown(pyrDownMat, pyrDownMat)
    Imgproc.cvtColor(pyrDownMat, hsvMat, Imgproc.COLOR_RGB2HSV_FULL)
    Core.inRange(hsvMat, lowerBound, mUpperBound, mMask)
    Imgproc.dilate(mMask, mDilatedMask, new Mat())
    val contours = new ArrayList[MatOfPoint]()
    Imgproc.findContours(mDilatedMask, contours, mHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
    var maxArea = 0
    var each = contours.iterator()
    while (each.hasNext) {
      val wrapper = each.next()
      val area = Imgproc.contourArea(wrapper)
      if (area > maxArea) maxArea = area
    }
    mContours.clear()
    each = contours.iterator()
    while (each.hasNext) {
      val contour = each.next()
      if (Imgproc.contourArea(contour) > mMinContourArea * maxArea) {
        Core.multiply(contour, new Scalar(4, 4), contour)
        mContours.add(contour)
      }
    }
  }

  def getContours(): List[MatOfPoint] = contours
}