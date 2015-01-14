package ar.com.manas.giskard

import java.util.ArrayList

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.MatOfPoint
import org.opencv.imgproc.Imgproc

object ColorBlobDetector {
  var minContourArea = 0.1
}

class ColorBlobDetector extends NicerScalars {
  import ColorBlobDetector._

  val lowerBound = new Scalar(0)
  val upperBound = new Scalar(0)

  var colorRadius = new Scalar(25, 50, 50, 0)
  
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
    val minH = if ((hsvColor.value(0) >= colorRadius.value(0))) hsvColor.value(0) - colorRadius.value(0) else 0
    val maxH = if ((hsvColor.value(0) + colorRadius.value(0) <= 255)) hsvColor.value(0) + colorRadius.value(0) else 255

    lowerBound.value(0) = minH
    upperBound.value(0) = maxH
    lowerBound.value(1) = hsvColor.value(1) - colorRadius.value(1)
    upperBound.value(1) = hsvColor.value(1) + colorRadius.value(1)
    lowerBound.value(2) = hsvColor.value(2) - colorRadius.value(2)
    upperBound.value(2) = hsvColor.value(2) + colorRadius.value(2)
    lowerBound.value(3) = 0
    upperBound.value(3) = 255

    val spectrumHsv = new Mat(1, (maxH - minH).toInt, CvType.CV_8UC3)

    for (j <- 0 until (maxH - minH).toInt) {
      val tmp = Array((minH + j).toByte, 255.toByte, 255.toByte)
      spectrumHsv.put(0, j, tmp)
    }
    Imgproc.cvtColor(spectrumHsv, spectrum, Imgproc.COLOR_HSV2RGB_FULL, 4) 
  }

  def getSpectrum: Mat = {
    spectrum
  }

  def setMinContourArea(area: Float) = {
    ColorBlobDetector.minContourArea = area
  }

  def process(rgbaImage: Mat) = {
    Imgproc.pyrDown(rgbaImage, pyrDownMat)
    Imgproc.pyrDown(pyrDownMat, pyrDownMat)
    Imgproc.cvtColor(pyrDownMat, hsvMat, Imgproc.COLOR_RGB2HSV_FULL)

    Core.inRange(hsvMat, lowerBound, upperBound, mask)
    
    Imgproc.dilate(mask, dilatedMask, new Mat())
    
    val contours = new ArrayList[MatOfPoint]()
    
    Imgproc.findContours(dilatedMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
    
    var maxArea = 0.0
    var each = contours.iterator()
    
    while (each.hasNext) {
      val wrapper = each.next()
      val area = Imgproc.contourArea(wrapper)
      if (area > maxArea) maxArea = area
    }
    
    contours.clear()
    
    each = contours.iterator()
    while (each.hasNext) {
      val contour = each.next()
      if (Imgproc.contourArea(contour) > minContourArea * maxArea) {
        Core.multiply(contour, new Scalar(4, 4), contour)
        contours.add(contour)
      }
    }
  }

  def getContours(): java.util.List[MatOfPoint] = contours
}