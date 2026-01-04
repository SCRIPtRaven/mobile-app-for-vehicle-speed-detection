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
    private var displayRotation: Int = 0

    fun getImageWidth(): Int = imageWidth
    fun getImageHeight(): Int = imageHeight

    fun calibrate(
        cameraHeight: Double = 1.2,
        tiltAngleDeg: Double = -6.0,
        focalLengthMm: Double = 26.0,
        sensorWidthMm: Double = 36.0,
        panAngleDeg: Double = 0.0,
        imageWidth: Int = 3072,
        imageHeight: Int = 4096,
        displayRotation: Int = 0
    ) {
        if (cameraHeight <= 0.0) throw IllegalArgumentException("cameraHeight must be positive, got $cameraHeight")
        if (tiltAngleDeg < -45.0 || tiltAngleDeg > 90.0) throw IllegalArgumentException("tiltAngle must be between -45 and 90 degrees, got $tiltAngleDeg")
        if (focalLengthMm <= 0.0) throw IllegalArgumentException("focalLength must be positive, got $focalLengthMm")
        if (sensorWidthMm <= 0.0) throw IllegalArgumentException("sensorWidth must be positive, got $sensorWidthMm")

        this.cameraHeight = cameraHeight
        this.panAngleRad = Math.toRadians(panAngleDeg)
        this.focalLengthMm = focalLengthMm
        this.sensorWidthMm = sensorWidthMm
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        this.displayRotation = displayRotation

        val isPortrait = (displayRotation == 0 || displayRotation == 180)
        val tiltCompensation = if (isPortrait) 0.75 else 1.0
        this.tiltAngleRad = Math.toRadians(tiltAngleDeg) * tiltCompensation

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
     * Returns horizon in sensor native coordinate space.
     */
    fun getHorizonLine(): Double {
        if (!isCalibrated) throw IllegalStateException("Transformer must be calibrated first")

        // At horizon, ray_elevation = 0 => beta = tiltAngleRad
        // -normY * (fovVertical / 2) = tiltAngleRad  => normY = -tiltAngleRad / (fovVertical / 2)
        val normY = -tiltAngleRad / (fovVertical / 2.0)
        val horizonY = normY * (imageHeight / 2.0) + imageHeight / 2.0
        return horizonY
    }

    fun getDisplayRotation(): Int = displayRotation

    /**
     * Inverse transform: Convert ground plane meters to pixel coordinates.
     *
     * @param xMeters Lateral distance in meters (negative=left, positive=right)
     * @param yMeters Distance from camera in meters (forward distance)
     * @return PointF with pixel coordinates, or null if point is outside valid range
     */
    fun inverseTransformPoint(xMeters: Double, yMeters: Double): PointF? {
        if (!isCalibrated) throw IllegalStateException("Transformer must be calibrated first")

        if (yMeters <= 0.0) return null

        // Calculate horizontal angle
        val alpha = atan(xMeters / yMeters) - panAngleRad

        // Calculate ray elevation from distance
        val rayElevation = -atan(cameraHeight / yMeters)

        // Calculate beta (vertical view angle)
        val beta = rayElevation + tiltAngleRad

        // Check if point is below horizon (ray_elevation must be negative)
        if (rayElevation >= 0.0) return null

        // Convert angles to normalized coordinates
        val normX = alpha / (fovHorizontal / 2.0)
        val normY = -beta / (fovVertical / 2.0)

        // Check if within field of view
        if (abs(normX) > 1.0 || abs(normY) > 1.0) return null

        // Convert to pixel coordinates
        val xPx = (normX * (imageWidth / 2.0) + imageWidth / 2.0).toFloat()
        val yPx = (normY * (imageHeight / 2.0) + imageHeight / 2.0).toFloat()

        // Verify within image bounds
        if (xPx >= 0 && xPx < imageWidth && yPx >= 0 && yPx < imageHeight) {
            return PointF(xPx, yPx)
        }

        return null
    }

    /**
     * Get grid line points for visualization.
     * Returns a data structure containing all points needed to draw the perspective grid.
     */
    fun getGridLines(
        distances: List<Float> = listOf(1f, 2f, 3f, 5f, 10f, 15f, 20f, 30f, 50f, 75f, 100f, 150f, 200f),
        lateralDistances: List<Float> = listOf(-15f, -10f, -5f, -2f, 0f, 2f, 5f, 10f, 15f)
    ): GridLines {
        if (!isCalibrated) throw IllegalStateException("Transformer must be calibrated before drawing grid")

        val horizonY = getHorizonLine().toFloat()
        val horizontalLines = mutableListOf<HorizontalLine>()
        val verticalLines = mutableListOf<VerticalLine>()

        // Generate horizontal distance lines
        for (distance in distances) {
            val points = mutableListOf<PointF>()

            // Sample across width
            for (xPx in 0 until imageWidth step max(1, imageWidth / 15)) {
                val yMin = horizonY.toInt() + 1
                val yMax = imageHeight - 1

                if (yMin >= yMax) continue

                var bestY: Int? = null
                var bestDiff = Float.MAX_VALUE

                // Search vertically for point at this distance
                for (yPx in yMin until yMax step max(1, (yMax - yMin) / 30)) {
                    try {
                        val (xM, yM) = transformPoint(xPx.toFloat(), yPx.toFloat())
                        val diff = abs(yM - distance.toDouble())
                        if (diff < bestDiff) {
                            bestDiff = diff.toFloat()
                            bestY = yPx
                        }
                    } catch (e: Exception) {
                        // Point above horizon or invalid
                    }
                }

                if (bestY != null && bestDiff < distance * 0.1f) {
                    points.add(PointF(xPx.toFloat(), bestY.toFloat()))
                }
            }

            if (points.size > 1) {
                horizontalLines.add(HorizontalLine(distance, points))
            }
        }

        // Generate vertical lateral lines
        for (lateralDist in lateralDistances) {
            val points = mutableListOf<PointF>()

            val minDepth = 0.1f
            val maxDepth = 500f

            // Sample depths from near to far
            val numSamples = 50
            for (i in 0..numSamples) {
                val depth = minDepth + (maxDepth - minDepth) * i / numSamples
                val pixelCoord = inverseTransformPoint(lateralDist.toDouble(), depth.toDouble())
                if (pixelCoord != null) {
                    points.add(pixelCoord)
                }
            }

            if (points.size > 1) {
                verticalLines.add(VerticalLine(lateralDist, points))
            }
        }

        return GridLines(horizonY, horizontalLines, verticalLines)
    }
}

/**
 * Data classes for grid visualization
 */
data class GridLines(
    val horizonY: Float,
    val horizontalLines: List<HorizontalLine>,
    val verticalLines: List<VerticalLine>
)

data class HorizontalLine(
    val distanceMeters: Float,
    val points: List<PointF>
)

data class VerticalLine(
    val lateralMeters: Float,
    val points: List<PointF>
)
