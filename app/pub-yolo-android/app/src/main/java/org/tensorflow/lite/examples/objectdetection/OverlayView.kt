/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.objectdetection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import org.tensorflow.lite.examples.objectdetection.detectors.ObjectDetection
import java.util.LinkedList
import java.util.Locale
import kotlin.math.max

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: List<ObjectDetection> = LinkedList<ObjectDetection>()
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()

    // Palette of distinct colors to assign per tracking id
    private val palette = intArrayOf(
        Color.parseColor("#e6194b"), // red
        Color.parseColor("#3cb44b"), // green
        Color.parseColor("#ffe119"), // yellow
        Color.parseColor("#0082c8"), // blue
        Color.parseColor("#f58231"), // orange
        Color.parseColor("#911eb4"), // purple
        Color.parseColor("#46f0f0"), // cyan
        Color.parseColor("#f032e6"), // magenta
        Color.parseColor("#d2f53c"), // lime
        Color.parseColor("#fabebe"), // pink
    )

    private var scaleFactor: Float = 1f

    private var bounds = Rect()

    init {
        initPaints()
    }

    fun clear() {
        textPaint.reset()
        textBackgroundPaint.reset()
        boxPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 50f

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f

        boxPaint.color = ContextCompat.getColor(context!!, R.color.bounding_box_color)
        boxPaint.strokeWidth = 8F
        boxPaint.style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        for (result in results) {
            val boundingBox = result.boundingBox

            val top = boundingBox.top * scaleFactor
            val bottom = boundingBox.bottom * scaleFactor
            val left = boundingBox.left * scaleFactor
            val right = boundingBox.right * scaleFactor

            // Choose color: per id if available, otherwise default
            val color = result.id?.let { colorForId(it) } ?: ContextCompat.getColor(context!!, R.color.bounding_box_color)

            // Update paints for this detection
            boxPaint.color = color
            // Slightly translucent background for text so it's readable on top of camera
            textBackgroundPaint.color = color and 0x00FFFFFF or (0xAA shl 24)

            // Choose text color (black or white) for readability based on luminance
            textPaint.color = if (isColorDark(color)) Color.WHITE else Color.BLACK

            // Draw bounding box around detected objects
            val drawableRect = RectF(left, top, right, bottom)
            canvas.drawRect(drawableRect, boxPaint)

            // Create text to display alongside detected objects
            val idPart = result.id?.let { "#${it} " } ?: ""
            val meterPart = result.distanceInMeters?.let { String.format(Locale.US, "(%.2f m) ", it) } ?: ""
            val speedPart = result.speedMps?.let { String.format(Locale.US, "[%.1f km/h] ", it * 3.6) } ?: ""
            val drawableText = idPart + meterPart + speedPart + " ${(result.category.confidence * 100).toInt()}%"

            // Draw rect behind display text
            textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
            val textWidth = bounds.width()
            val textHeight = bounds.height()
            canvas.drawRect(
                left,
                top,
                left + textWidth + BOUNDING_RECT_TEXT_PADDING,
                top + textHeight + BOUNDING_RECT_TEXT_PADDING,
                textBackgroundPaint
            )

            // Draw text for detected object
            canvas.drawText(drawableText, left, top + bounds.height(), textPaint)
        }
    }

    fun setResults(
        detectionResults: List<ObjectDetection>,
        imageHeight: Int,
        imageWidth: Int,
    ) {
        results = detectionResults

        // PreviewView is in FILL_START mode. So we need to scale up the bounding box to match with
        // the size that the captured images will be displayed.
        scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)
    }

    private fun colorForId(id: Int): Int {
        val idx = ((id - 1) % palette.size + palette.size) % palette.size
        return palette[idx]
    }

    private fun isColorDark(color: Int): Boolean {
        // Perceived luminance
        val r = (color shr 16 and 0xFF)
        val g = (color shr 8 and 0xFF)
        val b = (color and 0xFF)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b)
        return luminance < 128
    }

    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}
