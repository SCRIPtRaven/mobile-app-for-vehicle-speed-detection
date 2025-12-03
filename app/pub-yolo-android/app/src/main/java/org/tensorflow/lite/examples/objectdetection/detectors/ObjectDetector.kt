package org.tensorflow.lite.examples.objectdetection.detectors

import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.support.image.TensorImage

class Category (
    val label: String,
    val confidence: Float
)

class ObjectDetection(
    val boundingBox: RectF,
    val category: Category,
    // Optional tracking id assigned by a tracker. Null if not tracked.
    var id: Int? = null,
    var distanceInMeters: Double? = null,
    // Average speed (meters/sec) computed from recent history. Null if not available.
    var speedMps: Double? = null
)

class DetectionResult(
    val image: Bitmap,
    val detections: List<ObjectDetection>,
    var info: Any?=null
)

interface ObjectDetector {
    fun detect(image: TensorImage, imageRotation: Int): DetectionResult
}
