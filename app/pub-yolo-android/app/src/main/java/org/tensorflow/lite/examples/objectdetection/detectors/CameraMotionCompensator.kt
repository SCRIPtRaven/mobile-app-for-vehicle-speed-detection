/*
 * Copyright 2024 The TensorFlow Authors. All Rights Reserved.
 *
 * Camera Motion Compensator - Optical Flow Global Motion Estimation
 *
 * This class handles camera ego-motion compensation for handheld/moving cameras.
 * When the camera moves (panning, shaking, or tilting), it detects this global
 * motion and provides a motion vector that can be subtracted from object tracking
 * to get accurate vehicle speed measurements.
 *
 * Algorithm:
 * 1. Detect features in static regions (frame borders, avoiding center where vehicles are)
 * 2. Track these features between frames using Lucas-Kanade optical flow
 * 3. Calculate median motion vector (robust to outliers)
 * 4. Return global camera motion if significant (> threshold)
 *
 * Usage:
 *   val compensator = CameraMotionCompensator()
 *   compensator.estimateMotion(previousFrame, currentFrame)
 *   val motion = compensator.getGlobalMotion() // Returns PointF or null
 *   // Subtract motion.x and motion.y from tracked object displacements
 */

package org.tensorflow.lite.examples.objectdetection.detectors

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import kotlin.math.min
import kotlin.math.sqrt

/**
 * CameraMotionCompensator - Estimates global camera motion using optical flow
 *
 * This class uses Lucas-Kanade optical flow to track static features in frame borders
 * and estimates the global camera motion vector. This motion can be subtracted from
 * object tracking to compensate for camera shake/movement.
 */
class CameraMotionCompensator(
    /** Minimum number of features required for reliable estimation */
    private val minFeatures: Int = 10,

    /** Maximum number of features to track in static regions */
    private val maxFeatures: Int = 100,

    /** Minimum motion magnitude (pixels) to consider significant */
    private val motionThreshold: Float = 2.0f,

    /** Border region size as fraction of frame (0.15 = outer 15% on each side) */
    private val borderFraction: Float = 0.15f,

    /** Enable debug logging */
    private val debugMode: Boolean = false
) {

    // Previous frame data
    private var prevGray: Mat? = null
    private var prevFeatures: MatOfPoint2f? = null

    // Current global motion estimate
    private var globalMotion: PointF? = null

    // Statistics for monitoring
    private var motionEstimatesCount = 0
    private var significantMotionCount = 0

    companion object {
        private const val TAG = "CameraMotionCompensator"

        // Feature detection parameters
        private const val QUALITY_LEVEL = 0.01
        private const val MIN_DISTANCE = 30.0

        // Optical flow parameters
        private val WIN_SIZE = Size(21.0, 21.0)
        private const val MAX_LEVEL = 3
        private val CRITERIA = TermCriteria(
            TermCriteria.EPS + TermCriteria.COUNT,
            10,
            0.03
        )
    }

    /**
     * Estimate global camera motion between two frames
     *
     * @param currentBitmap Current frame as Bitmap
     * @return True if motion was successfully estimated
     */
    fun estimateMotion(currentBitmap: Bitmap): Boolean {
        return try {
            // Convert bitmap to OpenCV Mat (grayscale)
            val currGray = Mat()
            val bitmapMat = Mat()
            Utils.bitmapToMat(currentBitmap, bitmapMat)
            Imgproc.cvtColor(bitmapMat, currGray, Imgproc.COLOR_RGBA2GRAY)

            // First frame - just store it
            if (prevGray == null) {
                prevGray = currGray.clone()
                globalMotion = null
                return false
            }

            // Estimate motion between frames
            globalMotion = estimateGlobalMotion(prevGray!!, currGray)

            // Update previous frame
            prevGray?.release()
            prevGray = currGray.clone()

            // Cleanup
            currGray.release()
            bitmapMat.release()

            motionEstimatesCount++
            if (globalMotion != null) {
                significantMotionCount++
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error estimating motion: ${e.message}")
            globalMotion = null
            false
        }
    }

    /**
     * Get the current global motion estimate
     *
     * @return PointF with (dx, dy) motion in pixels, or null if no significant motion
     */
    fun getGlobalMotion(): PointF? = globalMotion

    /**
     * Check if camera is currently moving significantly
     */
    fun isMoving(): Boolean = globalMotion != null

    /**
     * Get motion magnitude in pixels
     */
    fun getMotionMagnitude(): Float {
        return globalMotion?.let {
            sqrt(it.x * it.x + it.y * it.y)
        } ?: 0f
    }

    /**
     * Reset the compensator state (call when tracking is reset)
     */
    fun reset() {
        prevGray?.release()
        prevGray = null
        prevFeatures?.release()
        prevFeatures = null
        globalMotion = null

        if (debugMode) {
            Log.d(TAG, "Reset - Motion estimates: $motionEstimatesCount, " +
                    "Significant: $significantMotionCount " +
                    "(${if (motionEstimatesCount > 0)
                        (significantMotionCount * 100 / motionEstimatesCount) else 0}%)")
        }
    }

    /**
     * Core algorithm: Estimate global motion between two frames
     *
     * This detects features in static regions (frame borders) and tracks them
     * using optical flow to estimate camera motion.
     */
    private fun estimateGlobalMotion(prevFrame: Mat, currFrame: Mat): PointF? {
        try {
            val height = prevFrame.rows()
            val width = prevFrame.cols()

            // Create mask for static regions (outer borders only)
            val mask = Mat.zeros(height, width, CvType.CV_8UC1)
            val borderSize = (min(height, width) * borderFraction).toInt()

            // Mark border regions as areas to detect features
            // Top border
            mask.submat(0, borderSize, 0, width).setTo(Scalar(255.0))
            // Bottom border
            mask.submat(height - borderSize, height, 0, width).setTo(Scalar(255.0))
            // Left border
            mask.submat(0, height, 0, borderSize).setTo(Scalar(255.0))
            // Right border
            mask.submat(0, height, width - borderSize, width).setTo(Scalar(255.0))

            // Detect good features to track in static regions
            val staticFeatures = MatOfPoint2f()
            Imgproc.goodFeaturesToTrack(
                prevFrame,
                staticFeatures,
                maxFeatures,
                QUALITY_LEVEL,
                MIN_DISTANCE,
                mask
            )

            mask.release()

            // Check if we have enough features
            if (staticFeatures.empty() || staticFeatures.rows() < minFeatures) {
                if (debugMode) {
                    Log.d(TAG, "Not enough static features detected: ${staticFeatures.rows()}")
                }
                staticFeatures.release()
                return null
            }

            // Track features using Lucas-Kanade optical flow
            val nextFeatures = MatOfPoint2f()
            val status = MatOfByte()
            val err = MatOfFloat()

            Video.calcOpticalFlowPyrLK(
                prevFrame,
                currFrame,
                staticFeatures,
                nextFeatures,
                status,
                err,
                WIN_SIZE,
                MAX_LEVEL,
                CRITERIA
            )

            // Filter good matches (where tracking succeeded)
            val statusArray = status.toArray()
            val prevPoints = staticFeatures.toArray()
            val nextPoints = nextFeatures.toArray()

            val goodPrevPoints = mutableListOf<Point>()
            val goodNextPoints = mutableListOf<Point>()

            for (i in statusArray.indices) {
                if (statusArray[i].toInt() == 1) {
                    goodPrevPoints.add(prevPoints[i])
                    goodNextPoints.add(nextPoints[i])
                }
            }

            // Cleanup
            staticFeatures.release()
            nextFeatures.release()
            status.release()
            err.release()

            // Check if we have enough good matches
            if (goodPrevPoints.size < minFeatures) {
                if (debugMode) {
                    Log.d(TAG, "Not enough good matches: ${goodPrevPoints.size}")
                }
                return null
            }

            // Calculate motion vectors
            val motionVectors = mutableListOf<PointF>()
            for (i in goodPrevPoints.indices) {
                val dx = (goodNextPoints[i].x - goodPrevPoints[i].x).toFloat()
                val dy = (goodNextPoints[i].y - goodPrevPoints[i].y).toFloat()
                motionVectors.add(PointF(dx, dy))
            }

            // Calculate median motion (robust to outliers)
            val medianMotion = calculateMedianMotion(motionVectors)

            // Only return if motion is significant
            val magnitude = sqrt(medianMotion.x * medianMotion.x +
                                medianMotion.y * medianMotion.y)

            if (magnitude > motionThreshold) {
                if (debugMode) {
                    Log.d(TAG, "Global motion detected: dx=${medianMotion.x}, " +
                            "dy=${medianMotion.y}, magnitude=$magnitude, " +
                            "features=${goodPrevPoints.size}")
                }
                return medianMotion
            }

            return null

        } catch (e: Exception) {
            Log.e(TAG, "Error in estimateGlobalMotion: ${e.message}")
            return null
        }
    }

    /**
     * Calculate median motion vector (robust to outliers)
     */
    private fun calculateMedianMotion(motionVectors: List<PointF>): PointF {
        if (motionVectors.isEmpty()) {
            return PointF(0f, 0f)
        }

        val dxList = motionVectors.map { it.x }.sorted()
        val dyList = motionVectors.map { it.y }.sorted()

        val medianDx = if (dxList.size % 2 == 0) {
            (dxList[dxList.size / 2 - 1] + dxList[dxList.size / 2]) / 2f
        } else {
            dxList[dxList.size / 2]
        }

        val medianDy = if (dyList.size % 2 == 0) {
            (dyList[dyList.size / 2 - 1] + dyList[dyList.size / 2]) / 2f
        } else {
            dyList[dyList.size / 2]
        }

        return PointF(medianDx, medianDy)
    }

    /**
     * Get statistics about motion estimation
     */
    fun getStats(): String {
        val percentage = if (motionEstimatesCount > 0) {
            (significantMotionCount * 100 / motionEstimatesCount)
        } else {
            0
        }
        return "Estimates: $motionEstimatesCount, Significant: $significantMotionCount ($percentage%)"
    }
}
