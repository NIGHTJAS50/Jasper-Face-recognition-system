package com.jasper.app.ml

import android.graphics.RectF

enum class LivenessStatus { COLLECTING, LIVE, STATIC }

/**
 * Lightweight temporal anti-spoofing: tracks the last [BUFFER_SIZE] face-center positions
 * (normalized [0,1] coordinates) and computes positional variance.
 *
 * A static face (printed photo / screen) produces near-zero variance.
 * A live face always has micro-movements that push variance above [MIN_VARIANCE].
 *
 * Overhead: ~0.1 ms per frame (5 variance calculations on 5 floats each).
 * No allocations inside the hot path after warm-up.
 *
 * Memory: Stale buffers (not updated for 3+ seconds) are evicted to prevent unbounded growth.
 */
object LivenessChecker {

    private const val BUFFER_SIZE = 5
    private const val MIN_VARIANCE = 0.0003f
    private const val BUFFER_EVICTION_MS = 3000L

    // Keyed by a coarse hash of the face's location so multiple simultaneous faces
    // each maintain their own independent buffer.
    private val buffers = HashMap<Int, ArrayDeque<Pair<Float, Float>>>()
    private val bufferTimestamps = HashMap<Int, Long>()

    /**
     * Call once per detected face per frame.
     * @param box  Normalized bounding box [0,1] as returned by the face detector.
     * @return [LivenessStatus.COLLECTING] until the buffer is full,
     *         [LivenessStatus.LIVE] if variance is sufficient,
     *         [LivenessStatus.STATIC] if face appears frozen (spoof candidate).
     */
    fun check(box: RectF): LivenessStatus {
        val now = System.currentTimeMillis()

        // Evict stale buffers to prevent unbounded HashMap growth
        buffers.keys.removeAll { key ->
            (now - (bufferTimestamps[key] ?: 0L)) > BUFFER_EVICTION_MS
        }
        bufferTimestamps.keys.removeAll { key ->
            !buffers.containsKey(key)
        }

        val key  = boxKey(box)
        val buf  = buffers.getOrPut(key) { ArrayDeque(BUFFER_SIZE + 1) }
        bufferTimestamps[key] = now

        val cx   = (box.left + box.right)  / 2f
        val cy   = (box.top  + box.bottom) / 2f
        buf.addLast(Pair(cx, cy))
        if (buf.size > BUFFER_SIZE) buf.removeFirst()
        if (buf.size < BUFFER_SIZE) return LivenessStatus.COLLECTING
        val vx = variance(buf, selectX = true)
        val vy = variance(buf, selectX = false)
        return if (vx + vy >= MIN_VARIANCE) LivenessStatus.LIVE else LivenessStatus.STATIC
    }

    /** Call when the screen exits or a recognition session ends to avoid stale state. */
    fun reset() {
        buffers.clear()
        bufferTimestamps.clear()
    }

    // ── Internals ────────────────────────────────────────────────────────────

    /**
     * Grid hash: bucket the face centre to a 20×20 grid so that small
     * natural movements do NOT create a new buffer entry, but a completely
     * different face in frame DOES get a separate buffer. Finer grid reduces collisions.
     */
    private fun boxKey(b: RectF): Int {
        val gridX = ((b.left + b.right)  / 2f * 20f).toInt().coerceIn(0, 19)
        val gridY = ((b.top  + b.bottom) / 2f * 20f).toInt().coerceIn(0, 19)
        return gridX * 31 + gridY
    }

    private fun variance(buf: ArrayDeque<Pair<Float, Float>>, selectX: Boolean): Float {
        var sum = 0f
        for (p in buf) sum += if (selectX) p.first else p.second
        val mean = sum / buf.size
        var varSum = 0f
        for (p in buf) {
            val d = (if (selectX) p.first else p.second) - mean
            varSum += d * d
        }
        return varSum / buf.size
    }
}
