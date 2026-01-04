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
import org.tensorflow.lite.examples.objectdetection.detectors.GridLines
import java.util.LinkedList
import java.util.Locale
import kotlin.math.max

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: List<ObjectDetection> = LinkedList<ObjectDetection>()
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()

    // Grid visualization
    private var showGrid: Boolean = false
    private var gridLines: GridLines? = null
    private var gridPaint = Paint()
    private var gridTextPaint = Paint()
    private var horizonPaint = Paint()
    private var gridImageWidth: Int = 0
    private var gridImageHeight: Int = 0
    private var gridScaleFactor: Float = 1f

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

        // Grid paints
        gridPaint.style = Paint.Style.STROKE
        gridPaint.strokeWidth = 2f
        gridPaint.isAntiAlias = true

        gridTextPaint.color = Color.WHITE
        gridTextPaint.style = Paint.Style.FILL
        gridTextPaint.textSize = 32f
        gridTextPaint.isAntiAlias = true

        horizonPaint.color = Color.RED
        horizonPaint.style = Paint.Style.STROKE
        horizonPaint.strokeWidth = 3f
        horizonPaint.isAntiAlias = true
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        // Draw grid first (behind bounding boxes)
        if (showGrid && gridLines != null) {
            drawGrid(canvas)
        }

        // median threshold: 1.0 m/s = 3.6 km/h
        val MIN_SPEED_TO_SHOW = 1.0 // m/s (~3.6 km/h)
        val movingVehicles = results.filter { result ->
            val speed = result.speedMps
            speed != null && speed >= MIN_SPEED_TO_SHOW
        }

        for (result in movingVehicles) {
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

    fun setGridEnabled(enabled: Boolean) {
        showGrid = enabled
        invalidate()
    }

    fun setGridLines(grid: GridLines?, imageWidth: Int, imageHeight: Int) {
        gridLines = grid
        gridImageWidth = imageWidth
        gridImageHeight = imageHeight
        gridScaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)
        invalidate()
    }

    private fun drawGrid(canvas: Canvas) {
        val grid = gridLines ?: return

        // Color palette for horizontal distance lines (cycling through hues)
        val horizontalColors = listOf(
            Color.rgb(255, 255, 0),   // Cyan
            Color.rgb(255, 200, 0),   // Light blue
            Color.rgb(255, 150, 0),   // Blue
            Color.rgb(200, 100, 0),   // Dark blue
            Color.rgb(150, 50, 0),    // Navy
            Color.rgb(100, 0, 0),     // Dark navy
            Color.rgb(50, 0, 50),     // Very dark
            Color.rgb(100, 0, 100),   // Purple
            Color.rgb(150, 0, 150)    // Light purple
        )

        // Draw horizontal distance lines
        for ((index, line) in grid.horizontalLines.withIndex()) {
            val color = horizontalColors[index % horizontalColors.size]
            gridPaint.color = color

            val points = line.points
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                canvas.drawLine(
                    p1.x * gridScaleFactor,
                    p1.y * gridScaleFactor,
                    p2.x * gridScaleFactor,
                    p2.y * gridScaleFactor,
                    gridPaint
                )
            }

            // Draw distance label
            if (points.isNotEmpty()) {
                var labelPoint = points.firstOrNull { pt ->
                    val x = pt.x * gridScaleFactor
                    val y = pt.y * gridScaleFactor
                    x >= 0 && x < width && y >= 0 && y < height
                } ?: points.firstOrNull()

                if (labelPoint != null) {
                    val labelText = "${line.distanceMeters.toInt()}m"
                    val textX = (labelPoint.x * gridScaleFactor + 10f).coerceIn(10f, width.toFloat() - 100f)
                    val textY = (labelPoint.y * gridScaleFactor).coerceIn(30f, height.toFloat() - 10f)

                    val textBounds = android.graphics.Rect()
                    gridTextPaint.getTextBounds(labelText, 0, labelText.length, textBounds)

                    val bgPaint = Paint()
                    bgPaint.color = Color.argb(200, 0, 0, 0)
                    bgPaint.style = Paint.Style.FILL
                    canvas.drawRect(
                        textX - 4f,
                        textY + textBounds.top - 4f,
                        textX + textBounds.width() + 4f,
                        textY + textBounds.bottom + 4f,
                        bgPaint
                    )

                    gridTextPaint.color = Color.WHITE
                    canvas.drawText(labelText, textX, textY, gridTextPaint)
                }
            }
        }

        // Draw vertical lateral lines
        gridPaint.color = Color.GREEN
        for (line in grid.verticalLines) {
            val points = line.points
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                canvas.drawLine(
                    p1.x * gridScaleFactor,
                    p1.y * gridScaleFactor,
                    p2.x * gridScaleFactor,
                    p2.y * gridScaleFactor,
                    gridPaint
                )
            }
        }

        // Draw horizon line
        canvas.drawLine(
            0f,
            grid.horizonY * gridScaleFactor,
            width.toFloat(),
            grid.horizonY * gridScaleFactor,
            horizonPaint
        )

        // Draw horizon label
        gridTextPaint.color = Color.RED
        gridTextPaint.textSize = 40f
        canvas.drawText(
            "HORIZON",
            10f,
            grid.horizonY * gridScaleFactor - 10f,
            gridTextPaint
        )
        gridTextPaint.textSize = 32f // Reset
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
