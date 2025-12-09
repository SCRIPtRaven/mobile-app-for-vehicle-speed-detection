package org.tensorflow.lite.examples.objectdetection.detectors

import android.graphics.RectF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Improved IoU-based tracker using global greedy matching.
 * Matches tracks to detections by sorting all Track-Detection IoU pairs and
 * assigning the highest IoU pairs first (ensuring one-to-one matches).
 * Tracks that are not matched increment a miss counter and are removed after
 * exceeding maxMisses. New detections create new tracks.
 *
 * Also applies simple exponential smoothing to track boxes to reduce jitter.
 */
class SimpleTracker(
    private val iouThreshold: Float = 0.3f,
    private val maxMisses: Int = 5,
    private val smoothing: Float = 0.6f // weight given to the detection when updating track box
) {
    private data class Track(
        var id: Int,
        var box: RectF,
        var lastSeenFrame: Int,
        var misses: Int = 0,
        var prevBox: RectF? = null // previous box for simple linear motion prediction
    )

    private val tracks = mutableListOf<Track>()
    // Start IDs at 0 and always increment to ensure uniqueness
    private var nextId = 0
    private var frameCounter = 0

    fun reset() {
        tracks.clear()
        // Do NOT reset nextId here so ids are never reused across resets
        frameCounter = 0
    }

    /**
     * Update tracker with detections for the current frame. This mutates
     * ObjectDetection.id for assigned ids.
     */
    fun update(detections: List<ObjectDetection>) {
        frameCounter++

        if (tracks.isEmpty()) {
            // Initialize tracks from detections
            for (det in detections) {
                val id = nextId++
                det.id = id
                tracks.add(Track(id, RectF(det.boundingBox), frameCounter, 0))
            }
            return
        }

        val nTracks = tracks.size
        val nDets = detections.size

        // Build cost matrix and use Hungarian algorithm for global optimal assignment.
        // Cost combines (1 - IoU) with a normalized center-distance penalty so that
        // assignments favor both high overlap and spatial proximity.
        val N = max(nTracks, nDets)
        val costMatrix = Array(N) { DoubleArray(N) { 1.0 } }

        // keep an IoU matrix to validate matches (cost includes distance term)
        val iouMatrix = Array(nTracks) { FloatArray(nDets) { 0f } }

        // weight for normalized center distance contribution in cost
        val distanceWeight = 0.5

        for (i in 0 until nTracks) {
            val track = tracks[i]

            // compute predicted box using simple linear motion from prevBox (if available)
            val predictedBox = track.prevBox?.let { prev ->
                // use centers and width/height for prediction
                val prevCx = (prev.left + prev.right) / 2f
                val prevCy = (prev.top + prev.bottom) / 2f
                val curCx = (track.box.left + track.box.right) / 2f
                val curCy = (track.box.top + track.box.bottom) / 2f

                val vx = curCx - prevCx
                val vy = curCy - prevCy
                val gap = (frameCounter - track.lastSeenFrame).coerceAtLeast(1)
                val predCx = curCx + vx * gap
                val predCy = curCy + vy * gap

                val halfW = (track.box.right - track.box.left) / 2f
                val halfH = (track.box.bottom - track.box.top) / 2f
                RectF(predCx - halfW, predCy - halfH, predCx + halfW, predCy + halfH)
            } ?: RectF(track.box)

            // caching predicted center and diagonal for normalization
            val predCx = (predictedBox.left + predictedBox.right) / 2f
            val predCy = (predictedBox.top + predictedBox.bottom) / 2f
            val predW = predictedBox.right - predictedBox.left
            val predH = predictedBox.bottom - predictedBox.top
            val diag = hypot(predW.toDouble(), predH.toDouble()).toFloat().coerceAtLeast(1e-3f)

            for (j in 0 until nDets) {
                val dbox = detections[j].boundingBox
                val iou = iouRect(predictedBox, dbox)
                iouMatrix[i][j] = iou

                // normalized center distance (distance / diagonal)
                val dCx = (dbox.left + dbox.right) / 2f
                val dCy = (dbox.top + dbox.bottom) / 2f
                val centerDist = hypot((predCx - dCx).toDouble(), (predCy - dCy).toDouble()).toFloat()
                val distNorm = (centerDist / diag).coerceAtMost(10f)

                // combined cost: prefer high IoU and small distance
                val cost = (1.0 - iou.toDouble()) + distanceWeight * distNorm.toDouble()
                // ensure cost is non-negative and reasonable
                costMatrix[i][j] = cost.coerceAtLeast(0.0)
            }
        }

        val assignment = hungarian(costMatrix)

        val trackAssigned = BooleanArray(nTracks)
        val detAssigned = BooleanArray(nDets)

        for (ti in 0 until nTracks) {
            val assignedJ = if (ti < assignment.size) assignment[ti] else -1
            if (assignedJ >= 0 && assignedJ < nDets) {
                val iou = iouMatrix[ti][assignedJ]
                if (iou >= iouThreshold) {
                    val track = tracks[ti]
                    val det = detections[assignedJ]
                    det.id = track.id
                    // store previous box then update with smoothing
                    track.prevBox = RectF(track.box)
                    track.box = smoothRect(track.box, det.boundingBox, smoothing)
                    track.lastSeenFrame = frameCounter
                    track.misses = 0
                    trackAssigned[ti] = true
                    detAssigned[assignedJ] = true
                }
            }
        }

        // Create new tracks for unmatched detections
        for (di in 0 until nDets) {
            if (!detAssigned[di]) {
                val det = detections[di]
                val id = nextId++
                det.id = id
                tracks.add(Track(id, RectF(det.boundingBox), frameCounter, 0, null))
            }
        }

        // Increment misses for unmatched tracks and remove stale ones
        val it = tracks.iterator()
        var idx = 0
        while (it.hasNext()) {
            val tr = it.next()
            if (idx < trackAssigned.size && !trackAssigned[idx]) {
                tr.misses++
                // allow a slightly longer grace period for larger boxes (they're often slower)
                val area = (tr.box.right - tr.box.left) * (tr.box.bottom - tr.box.top)
                val extraGrace = if (area > 10000f) 1 else 0
                if (tr.misses > maxMisses + extraGrace) {
                    it.remove()
                    idx++
                    continue
                }
            }
            idx++
        }
    }

    private fun iouRect(a: RectF, b: RectF): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        if (right <= left || bottom <= top) return 0f
        val inter = (right - left) * (bottom - top)
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        if (areaA <= 0f || areaB <= 0f) return 0f
        return inter / (areaA + areaB - inter)
    }

    private fun smoothRect(oldBox: RectF, newBox: RectF, alpha: Float): RectF {
        // alpha in (0..1) weight for the newBox
        val a = alpha.coerceIn(0f, 1f)
        val left = a * newBox.left + (1 - a) * oldBox.left
        val top = a * newBox.top + (1 - a) * oldBox.top
        val right = a * newBox.right + (1 - a) * oldBox.right
        val bottom = a * newBox.bottom + (1 - a) * oldBox.bottom
        return RectF(left, top, right, bottom)
    }

    // Hungarian algorithm (Munkres) for square cost matrix. Returns array of assigned column index per row, -1 if none.
    private fun hungarian(cost: Array<DoubleArray>): IntArray {
        val n = cost.size
        val u = DoubleArray(n + 1)
        val v = DoubleArray(n + 1)
        val p = IntArray(n + 1)
        val way = IntArray(n + 1)

        for (i in 1..n) {
            p[0] = i
            var j0 = 0
            val minv = DoubleArray(n + 1) { Double.POSITIVE_INFINITY }
            val used = BooleanArray(n + 1)
            do {
                used[j0] = true
                val i0 = p[j0]
                var delta = Double.POSITIVE_INFINITY
                var j1 = 0
                for (j in 1..n) if (!used[j]) {
                    val cur = cost[i0 - 1][j - 1] - u[i0] - v[j]
                    if (cur < minv[j]) {
                        minv[j] = cur
                        way[j] = j0
                    }
                    if (minv[j] < delta) {
                        delta = minv[j]
                        j1 = j
                    }
                }
                for (j in 0..n) {
                    if (used[j]) {
                        u[p[j]] += delta
                        v[j] -= delta
                    } else {
                        minv[j] -= delta
                    }
                }
                j0 = j1
            } while (p[j0] != 0)
            do {
                val j1 = way[j0]
                p[j0] = p[j1]
                j0 = j1
            } while (j0 != 0)
        }

        val assignment = IntArray(n) { -1 }
        for (j in 1..n) if (p[j] > 0 && p[j] <= n) {
            assignment[p[j] - 1] = j - 1
        }
        return assignment
    }
}