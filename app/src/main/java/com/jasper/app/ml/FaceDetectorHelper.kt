package com.jasper.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector as MediaPipeFaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult

/**
 * Thread-safe wrapper around MediaPipe FaceDetector.
 *
 * Model: face_detection_short_range.tflite must be in app/src/main/assets/
 * Download: https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/1/blaze_face_short_range.tflite
 *
 * Lifecycle: app-scoped. Never close from a ViewModel.
 */
class FaceDetectorHelper(private val context: Context) {

    companion object {
        private const val TAG = "FaceDetectorHelper"
        private const val MODEL_ASSET = "face_detection_short_range.tflite"
        private const val MIN_DETECTION_CONFIDENCE = 0.5f
    }

    @Volatile
    private var detector: MediaPipeFaceDetector? = null
    private val initLock = Any()

    val isInitialized: Boolean get() = detector != null

    fun ensureInitialized() { getOrCreateDetector() }

    fun detect(bitmap: Bitmap): FaceDetectorResult? {
        val det = getOrCreateDetector() ?: return null
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            det.detect(mpImage)
        } catch (e: Exception) {
            Log.e(TAG, "Detection failed", e)
            null
        }
    }

    private fun getOrCreateDetector(): MediaPipeFaceDetector? {
        detector?.let { return it }
        return synchronized(initLock) {
            detector ?: try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET)
                    .build()
                val options = MediaPipeFaceDetector.FaceDetectorOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.IMAGE)
                    .setMinDetectionConfidence(MIN_DETECTION_CONFIDENCE)
                    .build()
                MediaPipeFaceDetector.createFromOptions(context, options)
                    .also {
                        detector = it
                        Log.i(TAG, "FaceDetector initialized successfully")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize FaceDetector — is $MODEL_ASSET in assets?", e)
                null
            }
        }
    }

    fun close() {
        synchronized(initLock) {
            detector?.close()
            detector = null
        }
    }
}
