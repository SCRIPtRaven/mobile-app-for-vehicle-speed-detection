/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tensorflow.lite.examples.objectdetection

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.examples.objectdetection.detectors.CameraCalibrationTransformer
import org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetection
import org.tensorflow.lite.task.core.BaseOptions

import org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetector

import org.tensorflow.lite.examples.objectdetection.detectors.TaskVisionDetector
import org.tensorflow.lite.examples.objectdetection.detectors.YoloDetector
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions
import org.tensorflow.lite.examples.objectdetection.detectors.SimpleTracker
import java.util.Locale
import kotlin.math.sqrt
import java.util.ArrayDeque


class ObjectDetectorHelper(
  var threshold: Float = 0.3f,
  var numThreads: Int = 2,
  var maxResults: Int = 10,
  var currentDelegate: Int = 0,
  var currentModel: Int = 4,
  // Enable simple object tracking that will add an `id` to detections.
  val context: Context,
  val objectDetectorListener: DetectorListener?
) {

    // For this example this needs to be a var so it can be reset on changes. If the ObjectDetector
    // will not change, a lazy val would be preferable.
    private var objectDetector: ObjectDetector? = null
    private val tracker = SimpleTracker()
    private val transformer = CameraCalibrationTransformer()

    // Per-track recent positions (meters) with timestamps (ms) to compute speed.
    // Map of track id -> deque of Pair(timestampMs, Pair(xMeters, yMeters))
    // private val trackPositionHistory = mutableMapOf<Int, ArrayDeque<Pair<Long, Pair<Double, Double>>>>()
    // private val HISTORY_MAX = 10
    // Keep only the last observed ground position per track (timestamp, (xMeters,yMeters))
    private val lastPosition = mutableMapOf<Int, Pair<Long, Pair<Double, Double>>>()
    // Keep a deque of recent instantaneous speed samples (m/s) per track to average (last N speeds)
    private val trackSpeedHistory = mutableMapOf<Int, ArrayDeque<Double>>()
    private val HISTORY_MAX = 10

    init {
        setupObjectDetector()
    }

    fun clearObjectDetector() {
        objectDetector = null
    }

    // Initialize the object detector using current settings on the
    // thread that is using it. CPU and NNAPI delegates can be used with detectors
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the detector
    fun setupObjectDetector() {

        try {

            if (currentModel == MODEL_YOLO) {

                objectDetector = YoloDetector(

                    threshold,
                    0.3f,
                    numThreads,
                    maxResults,
                    currentDelegate,
                    currentModel,
                    context,

                )

            }
            else {

                // Create the base options for the detector using specifies max results and score threshold
                val optionsBuilder =
                    ObjectDetectorOptions.builder()
                        .setScoreThreshold(threshold)
                        .setMaxResults(maxResults)

                // Set general detection options, including number of used threads
                val baseOptionsBuilder = BaseOptions.builder().setNumThreads(numThreads)

                // Use the specified hardware for running the model. Default to CPU
                when (currentDelegate) {
                    DELEGATE_CPU -> {
                        // Default
                    }
                    DELEGATE_GPU -> {
//                        if (CompatibilityList().isDelegateSupportedOnThisDevice) {
//                            baseOptionsBuilder.useGpu()
//                        } else {
//                            objectDetectorListener?.onError("GPU is not supported on this device")
//                        }
                        // for some reason CompatibilityList().isDelegateSupportedOnThisDevice
                        // returns False in my Motorola Edge 30 Ultra, but GPU works :/
                        baseOptionsBuilder.useGpu()
                    }
                    DELEGATE_NNAPI -> {
                        baseOptionsBuilder.useNnapi()
                    }
                }

                optionsBuilder.setBaseOptions(baseOptionsBuilder.build())
                val options = optionsBuilder.build()

                objectDetector = TaskVisionDetector(
                    options,
                    currentModel,
                    maxResults,
                    context,

                )

            }


        }
        catch (e : Exception) {

            objectDetectorListener?.onError(e.toString())

        }


    }


    fun detect(image: Bitmap, imageRotation: Int) {

        if (objectDetector == null) {
            setupObjectDetector()
        }

        // Create preprocessor for the image.
        // See https://www.tensorflow.org/lite/inference_with_metadata/lite_support#imageprocessor_architecture

        val imageProcessor =
            ImageProcessor.Builder()
                .add(Rot90Op(-imageRotation / 90))
                .build()

        // Preprocess the image and convert it into a TensorImage for detection.
        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(image))

        // Inference time is the difference between the system time at the start and finish of the
        // process
        var inferenceTime = SystemClock.uptimeMillis()

        val results = objectDetector?.detect(tensorImage, imageRotation)

        inferenceTime = SystemClock.uptimeMillis() - inferenceTime

        if (results != null) {
            // Run tracker to assign persistent ids if enabled
            try {
                tracker.update(results.detections)
            } catch (e: Exception) {
                // If tracker throws for any reason, reset it and continue
                tracker.reset()
            }

            // Compute scaling factors to map model output coordinates (results.image) to the
            // original camera image coordinates (image). TaskVisionDetector returns boxes in a
            // fixed internal resolution (e.g. 640x480), while the camera frame may be larger.
            val scaleX = transformer.getImageWidth() / results.image.width
            val scaleY = transformer.getImageHeight() / results.image.height

            val nowMs = SystemClock.uptimeMillis()

            for (detection in results.detections) {
                // Scale the bounding box in-place to original image coordinates for UI and
                // for correct pixel->meter projection. Tracker has already consumed the
                // pre-scaled boxes, so scaling now keeps tracker coordinate space stable.
                try {
                    val origLeft = detection.boundingBox.left * scaleX
                    val origRight = detection.boundingBox.right * scaleX
                    val origBottom = detection.boundingBox.bottom * scaleY

                    val px = (origLeft + origRight) / 2.0f
                    // use bottom of bbox as the contact point with ground
                    val py = origBottom
                    val metersPair = transformer.transformPoint(px, py)
                    val dx = metersPair.first
                    val dy = metersPair.second
                    val dist = sqrt(dx * dx + dy * dy)
                    Log.e("ObjectDetectorHelper", String.format(Locale.US, "dx: %.2f m, dy: %.2f m, dist: %.2f m", dx, dy, dist))
                    detection.distanceInMeters = dist

                    // Compute speed using lastPosition + a short deque of recent speeds (last N samples)
                    val id = detection.id
                    if (id != null) {
                        val prev = lastPosition[id]
                        if (prev != null) {
                            val prevT = prev.first.toDouble()
                            val prevX = prev.second.first
                            val prevY = prev.second.second
                            val dtMs = (nowMs.toDouble() - prevT)
                            if (dtMs > 0.0) {
                                val ddx = dx - prevX
                                val ddy = dy - prevY
                                val distMeters = sqrt(ddx * ddx + ddy * ddy)
                                val instSpeed = distMeters / (dtMs / 1000.0) // m/s

                                val speedHistory = trackSpeedHistory.getOrPut(id) { ArrayDeque() }
                                speedHistory.addLast(instSpeed)
                                while (speedHistory.size > HISTORY_MAX) speedHistory.removeFirst()

                                val validSpeeds = speedHistory.filter { it.isFinite() && !it.isNaN() }
                                detection.speedMps = if (validSpeeds.isNotEmpty()) validSpeeds.sum() / validSpeeds.size else null
                            } else {
                                // non-positive time delta -> cannot compute speed
                                detection.speedMps = null
                            }
                        } else {
                            // No previous sample yet; init speed history but cannot compute speed
                            trackSpeedHistory.getOrPut(id) { ArrayDeque() }
                            detection.speedMps = null
                        }

                        // Update lastPosition for next frame
                        lastPosition[id] = Pair(nowMs, Pair(dx, dy))
                    } else {
                        detection.speedMps = null
                    }

                } catch (e: Exception) {
                    Log.e("ObjectDetectorHelper", "Error computing distance/speed: ${e.message}")
                    detection.distanceInMeters = null
                    detection.speedMps = null
                }
            }

            objectDetectorListener?.onResults(
                results.detections,
                inferenceTime,
                results.image.height,
                results.image.width
            )
        }

    }

    /**
     * Calibrate the internal CameraCalibrationTransformer using CameraX Camera intrinsics
     * obtained via Camera2 interop. Requires the bound Camera instance and the frame
     * image dimensions (pixels) that detections will be reported in.
     *
     * If any intrinsics are unavailable, reasonable defaults are used.
     */
    fun calibrateFromCamera(
        imageWidth: Int,
        imageHeight: Int,
        cameraHeightMeters: Double = 1.2,
        tiltAngleDeg: Double = -6.0,
        panAngleDeg: Double = 0.0,
        focalLengthMm: Double? = null,
        sensorWidthMm: Double? = null
    ) {
        try {
            // Use provided intrinsics if present, otherwise fall back to sensible defaults.
            val focal = focalLengthMm ?: 26.0
            val sensorW = sensorWidthMm ?: 36.0

            transformer.calibrate(
                cameraHeight = cameraHeightMeters,
                tiltAngleDeg = tiltAngleDeg,
                focalLengthMm = focal,
                sensorWidthMm = sensorW,
                panAngleDeg = panAngleDeg,
                imageWidth = imageWidth,
                imageHeight = imageHeight
            )

            Log.i("ObjectDetectorHelper", "Transformer calibrated: focal=${focal}mm, sensorW=${sensorW}mm, img=${imageWidth}x${imageHeight}")
        } catch (e: Exception) {
            Log.e("ObjectDetectorHelper", "Error calibrating transformer from camera: ${e.message}")
        }
    }

    interface DetectorListener {
        fun onError(error: String)
        fun onResults(
            results: List<ObjectDetection>,
            inferenceTime: Long,
            imageHeight: Int,
            imageWidth: Int
        )
    }

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DELEGATE_NNAPI = 2
        const val MODEL_MOBILENETV1 = 0
        const val MODEL_EFFICIENTDETV0 = 1
        const val MODEL_EFFICIENTDETV1 = 2
        const val MODEL_EFFICIENTDETV2 = 3
        const val MODEL_YOLO = 4
    }
}
