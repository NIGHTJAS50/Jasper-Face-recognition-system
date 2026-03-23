package com.jasper.app.ml

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * Wraps the MobileFaceNet TFLite model to produce 128-dim L2-normalized face embeddings.
 *
 * Model requirements:
 *   Input:  [1, 112, 112, 3] float32 — pixels normalized to [-1, 1] via (channel/128f) - 1f
 *   Output: [1, 128]         float32 — the model's Lambda layer already L2-normalizes;
 *                                      we normalize again here as a safety guard.
 *
 * Model file: app/src/main/assets/mobilefacenet.tflite (user must provide)
 *
 * Thread safety: [Interpreter] is NOT thread-safe. All inference is serialized via [mutex].
 * Buffers are pre-allocated once and reused per call (rewind() before each inference).
 */
class FaceEmbedder(context: Context) {

    companion object {
        private const val MODEL_ASSET = "mobilefacenet.tflite"
        private const val INPUT_SIZE = 112
        private const val EMBEDDING_SIZE = 128   // this model: 1×1×128 → Flatten → 128-dim
        private const val CHANNELS = 3
        // Bytes: 1 batch × 112 × 112 × 3 channels × 4 bytes/float
        private const val INPUT_BUFFER_SIZE = 1 * INPUT_SIZE * INPUT_SIZE * CHANNELS * Float.SIZE_BYTES
    }

    private val interpreter: Interpreter
    private val mutex = Mutex()

    // Pre-allocated buffers — reused every inference call to avoid GC pressure
    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(INPUT_BUFFER_SIZE).order(ByteOrder.nativeOrder())
    private val outputBuffer: Array<FloatArray> = Array(1) { FloatArray(EMBEDDING_SIZE) }

    @Volatile
    private var isClosed = false

    init {
        val assetFd = context.assets.openFd(MODEL_ASSET)
        try {
            val modelBuffer = assetFd.createInputStream().channel.use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, assetFd.startOffset, assetFd.declaredLength)
            }
            interpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
                numThreads = 2
            })
        } finally {
            assetFd.close()
        }
    }

    /**
     * Run inference on [bitmap] (must be 112×112 ARGB_8888).
     * Returns L2-normalized 128-dim embedding, or null if closed or normalization fails.
     */
    suspend fun embed(bitmap: Bitmap): FloatArray? {
        check(!isClosed) { "FaceEmbedder has been closed" }

        return mutex.withLock {
            if (isClosed) return@withLock null

            // Fill input buffer: NHWC layout, normalized to [-1, 1]
            inputBuffer.rewind()
            for (y in 0 until INPUT_SIZE) {
                for (x in 0 until INPUT_SIZE) {
                    val pixel = bitmap.getPixel(x, y)
                    inputBuffer.putFloat(((pixel shr 16 and 0xFF) / 128f) - 1f)  // R
                    inputBuffer.putFloat(((pixel shr 8 and 0xFF) / 128f) - 1f)   // G
                    inputBuffer.putFloat(((pixel and 0xFF) / 128f) - 1f)          // B
                }
            }

            // Run inference — output written into pre-allocated outputBuffer
            interpreter.run(inputBuffer, outputBuffer)

            val raw = outputBuffer[0]
            l2Normalize(raw)
        }
    }

    /**
     * L2-normalize [embedding] in-place. Returns null on degenerate input (zero/NaN/Inf norm).
     * Returns a copy so the pre-allocated buffer remains clean for next call.
     */
    private fun l2Normalize(embedding: FloatArray): FloatArray? {
        // Guard 1: check for NaN/Inf before computing norm
        for (v in embedding) {
            if (!v.isFinite()) return null
        }

        val norm = sqrt(embedding.sumOf { (it * it).toDouble() }).toFloat()

        // Guard 2: zero norm means no meaningful signal
        if (norm < 1e-10f) return null

        val normalized = FloatArray(embedding.size)
        for (i in embedding.indices) {
            normalized[i] = embedding[i] / norm
        }

        // Guard 3: verify result is finite after division
        for (v in normalized) {
            if (!v.isFinite()) return null
        }

        return normalized
    }

    /** Idempotent. Safe to call multiple times. */
    fun close() {
        if (!isClosed) {
            isClosed = true
            interpreter.close()
        }
    }
}
