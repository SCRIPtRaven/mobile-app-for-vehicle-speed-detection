package org.tensorflow.lite.examples.objectdetection.detectors

import android.graphics.PointF
import kotlin.math.*
import kotlin.jvm.JvmName
import android.util.Log

/**
 * Transformer that uses camera parameters to map pixels to real-world meters.
 * Assumes a flat ground plane and uses a pinhole camera geometry approximation.
 *
 * Ported from a Python implementation; angles are provided in degrees.
 */
class CameraCalibrationTransformer(

) {
    private var cameraHeight: Double = 0.0
    private var tiltAngleRad: Double = 0.0
    private var panAngleRad: Double = 0.0
    private var focalLengthMm: Double = 0.0
    private var sensorWidthMm: Double = 0.0
    private var fovHorizontal: Double = 0.0
    private var sensorHeightMm: Double = 0.0
    private var fovVertical: Double = 0.0
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    private var isCalibrated = false

    fun getImageWidth(): Int = imageWidth
    fun getImageHeight(): Int = imageHeight

    fun calibrate(
        cameraHeight: Double = 1.2,
        tiltAngleDeg: Double = -6.0,
        focalLengthMm: Double = 26.0,
        sensorWidthMm: Double = 36.0,
        panAngleDeg: Double = 0.0,
        imageWidth: Int = 3072,
        imageHeight: Int = 4096
    ) {
        if (cameraHeight <= 0.0) throw IllegalArgumentException("cameraHeight must be positive, got $cameraHeight")
        if (tiltAngleDeg < -45.0 || tiltAngleDeg > 90.0) throw IllegalArgumentException("tiltAngle must be between -45 and 90 degrees, got $tiltAngleDeg")
        if (focalLengthMm <= 0.0) throw IllegalArgumentException("focalLength must be positive, got $focalLengthMm")
        if (sensorWidthMm <= 0.0) throw IllegalArgumentException("sensorWidth must be positive, got $sensorWidthMm")

        this.cameraHeight = cameraHeight
        this.tiltAngleRad = Math.toRadians(tiltAngleDeg)
        this.panAngleRad = Math.toRadians(panAngleDeg)
        this.focalLengthMm = focalLengthMm
        this.sensorWidthMm = sensorWidthMm
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight

        // horizontal field of view (radians)
        this.fovHorizontal = 2.0 * atan(sensorWidthMm / (2.0 * focalLengthMm))

        // compute sensor height from image aspect ratio
        val aspectRatio = imageHeight.toDouble() / imageWidth.toDouble()
        this.sensorHeightMm = sensorWidthMm * aspectRatio
        this.fovVertical = 2.0 * atan(sensorHeightMm / (2.0 * focalLengthMm))

        isCalibrated = true
    }

    fun isCalibrated(): Boolean = isCalibrated

    /**
     * Transform a single pixel point (px, py) to ground-plane coordinates in meters.
     * Returns Pair(xMeters, yMeters) where yMeters is the distance forward from camera
     * and xMeters is the lateral offset (positive = right).
     * Throws IllegalStateException if not calibrated and IllegalArgumentException if
     * the ray points above the horizon.
     */
    fun transformPoint(point: Pair<Double, Double>): Pair<Double, Double> {
        if (!isCalibrated) throw IllegalStateException("Transformer must be calibrated before transforming points")

        val px = point.first
        val py = point.second

        // normalize x and y into [-1, 1] where center of image is (0,0)
        val normX = (px - imageWidth / 2.0) / (imageWidth / 2.0)
        val normY = (py - imageHeight / 2.0) / (imageHeight / 2.0)

        // angular offsets from center
        val alpha = normX * (fovHorizontal / 2.0)
        val beta = -normY * (fovVertical / 2.0)

        // elevation of the ray relative to camera horizontal plane
        val rayElevation = beta - tiltAngleRad
        if (rayElevation >= 0.0) {
            throw IllegalArgumentException("Point ($px, $py) corresponds to a ray above the horizon")
        }

        // distance along the ground plane from camera to intersection
        val groundDistance = cameraHeight / tan(abs(rayElevation))

        val xMeters = groundDistance * tan(alpha + panAngleRad)
        val yMeters = groundDistance

        return Pair(xMeters, yMeters)
    }

    // Convenience overloads for common Android types and float inputs
    fun transformPoint(px: Float, py: Float): Pair<Double, Double> = transformPoint(Pair(px.toDouble(), py.toDouble()))
    fun transformPoint(point: PointF): Pair<Double, Double> = transformPoint(Pair(point.x.toDouble(), point.y.toDouble()))
    @JvmName("transformPointFloatPair")
    fun transformPoint(point: Pair<Float, Float>): Pair<Double, Double> = transformPoint(Pair(point.first.toDouble(), point.second.toDouble()))

    /**
     * Transform a list of pixel points to a list of ground-plane coordinates.
     */
    fun transformPoints(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (!isCalibrated) throw IllegalStateException("Transformer must be calibrated before transforming points")
        return points.map { transformPoint(it) }
    }

    fun transformPointsFloat(points: List<Pair<Float, Float>>): List<Pair<Double, Double>> {
        if (!isCalibrated) throw IllegalStateException("Transformer must be calibrated before transforming points")
        return points.map { transformPoint(it) }
    }

    fun transformPointsPointF(points: List<PointF>): List<Pair<Double, Double>> {
        if (!isCalibrated) throw IllegalStateException("Transformer must be calibrated before transforming points")
        return points.map { transformPoint(it) }
    }

    /**
     * Calculate the euclidean distance (meters) between two pixel points on the ground plane.
     */
    fun calculateDistance(p1: Pair<Double, Double>, p2: Pair<Double, Double>): Double {
        if (!isCalibrated) throw IllegalStateException("Transformer must be calibrated before calculating distances")
        val t1 = transformPoint(p1)
        val t2 = transformPoint(p2)
        val dx = t2.first - t1.first
        val dy = t2.second - t1.second
        return sqrt(dx * dx + dy * dy)
    }

    // Overloads for float/PointF inputs
    @JvmName("calculateDistanceFloatPair")
    fun calculateDistance(p1: Pair<Float, Float>, p2: Pair<Float, Float>): Double = calculateDistance(Pair(p1.first.toDouble(), p1.second.toDouble()), Pair(p2.first.toDouble(), p2.second.toDouble()))
    fun calculateDistance(p1: PointF, p2: PointF): Double = calculateDistance(Pair(p1.x.toDouble(), p1.y.toDouble()), Pair(p2.x.toDouble(), p2.y.toDouble()))

    /**
     * Compute the y pixel coordinate of the horizon line. Points above this y are above the horizon
     * and won't intersect the ground plane.
     */
    fun getHorizonLine(): Double {
        if (!isCalibrated) throw IllegalStateException("Transformer must be calibrated first")

        // At horizon, ray_elevation = 0 => beta = tiltAngleRad
        // -normY * (fovVertical / 2) = tiltAngleRad  => normY = -tiltAngleRad / (fovVertical / 2)
        val normY = -tiltAngleRad / (fovVertical / 2.0)
        val horizonY = normY * (imageHeight / 2.0) + imageHeight / 2.0
        return horizonY
    }
}
