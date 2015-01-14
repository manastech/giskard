package ar.com.manas.giskard

import org.opencv.core.Scalar

class ScalaFriendlyScalar(wrapped: Scalar) {
  def value = wrapped.`val`
}

trait NicerScalars {
  implicit def makeScalarScalaFriendly(scalar: Scalar): ScalaFriendlyScalar = new ScalaFriendlyScalar(scalar)
}
