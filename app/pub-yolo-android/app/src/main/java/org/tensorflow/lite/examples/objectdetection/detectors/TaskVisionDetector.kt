package org.tensorflow.lite.examples.objectdetection.detectors

import android.content.Context
import org.tensorflow.lite.examples.objectdetection.ObjectDetectorHelper.Companion.MODEL_EFFICIENTDETV0
import org.tensorflow.lite.examples.objectdetection.ObjectDetectorHelper.Companion.MODEL_EFFICIENTDETV1
import org.tensorflow.lite.examples.objectdetection.ObjectDetectorHelper.Companion.MODEL_EFFICIENTDETV2
import org.tensorflow.lite.examples.objectdetection.ObjectDetectorHelper.Companion.MODEL_MOBILENETV1
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.util.LinkedList

class TaskVisionDetector(
    var options: ObjectDetector.ObjectDetectorOptions,
    var currentModel: Int = 0,
    var maxResults: Int = 50,
    val context: Context,

    ): org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetector {

    private var objectDetector: ObjectDetector

    init {

        val modelName =
            when (currentModel) {
                MODEL_MOBILENETV1 -> "mobilenetv1.tflite"
                MODEL_EFFICIENTDETV0 -> "efficientdet-lite0.tflite"
                MODEL_EFFICIENTDETV1 -> "efficientdet-lite1.tflite"
                MODEL_EFFICIENTDETV2 -> "efficientdet-lite2.tflite"
                else -> "mobilenetv1.tflite"
            }

        objectDetector = ObjectDetector.createFromFileAndOptions(context, modelName, options)

    }

    override fun detect(image: TensorImage, imageRotation: Int): DetectionResult {

        val tvDetections = objectDetector.detect(image)

        // Only allow these vehicle classes (case-insensitive).
        // We treat these classes as a single merged category "vehicle" for the UI/output.
        val allowedLabels = setOf(
            "car", "truck", "bus", "motorcycle", "motorbike", "van", "lorry", "suv", "pickup", "minivan"
        )

        // Convert task view detections to common interface
        val detections = LinkedList<ObjectDetection>()
        for (tvDetection: Detection in tvDetections) {

            // Respect configured maxResults
            if (detections.size >= maxResults) break

            // Skip if no categories available
            if (tvDetection.categories.isEmpty()) continue

            val cat = tvDetection.categories[0]
            val label = cat.label?.lowercase() ?: continue

            // Filter by allowlist
            if (!allowedLabels.contains(label)) continue

            val objDet = ObjectDetection(
                boundingBox = tvDetection.boundingBox,
                // Merge all allowed vehicle labels into a single label "vehicle"
                category = Category(
                    "vehicle",
                    cat.score
                )
            )
            detections.add(objDet)
        }
        val results = DetectionResult(
            image.bitmap,
            detections
        )

        return results

    }
}