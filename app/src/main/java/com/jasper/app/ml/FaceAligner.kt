package com.jasper.app.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import com.google.mediapipe.tasks.components.containers.Detection
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Aligns and crops a detected face to a 112×112 bitmap suitable for MobileFaceNet input.
 *
 * Pipeline per face:
 *  1. Validate keypoints and bounding box (5-guard chain)
 *  2. Denormalize eye keypoints to pixel coordinates
 *  3. Compute roll angle from left-eye/right-eye vector
 *  4. Rotate full bitmap around its centre
 *  5. Build crop box from eye midpoint + eye distance (FACE_SCALE=3.0, CHIN_SHIFT=0.3)
 *  6. Add 20% padding, clamp to bitmap bounds
 *  7. Draw into pre-allocated 112×112 [outputBitmap] via [Canvas] — no GC allocation per frame
 *
 * NOT thread-safe — caller must use the returned bitmap synchronously before the next call.
 * The returned bitmap is the same pre-allocated instance every time.
 *
 * MediaPipe keypoint order: index 0 = left eye, index 1 = right eye (normalized [0,1]).
 */
class FaceAligner {

    companion object {
        const val OUTPUT_SIZE = 112
        private const val FACE_SCALE = 3.0f      // face crop = eyeDist * FACE_SCALE
        private const val CHIN_SHIFT = 0.3f      // shift centre down by eyeDist * CHIN_SHIFT
        private const val PADDING = 0.20f        // 20% padding around crop box
        private const val MIN_EYE_DIST_PX = 10f  // guard: eyes must be at least 10px apart
        private const val MIN_BBOX_FRACTION = 0.05f  // guard: bbox must be > 5% of frame
    }

    // Pre-allocated output resources — reused every frame to eliminate GC pressure
    private val outputBitmap: Bitmap =
        Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
    private val outputCanvas = Canvas(outputBitmap)
    private val dstRect = RectF(0f, 0f, OUTPUT_SIZE.toFloat(), OUTPUT_SIZE.toFloat())
    private val srcRect = Rect()
    private val rotationMatrix = Matrix()
    private val drawMatrix = Matrix()

    /**
     * Align and crop [sourceBitmap] for the given MediaPipe [detection].
     * Returns the pre-allocated 112×112 [outputBitmap], or null if any guard fails.
     */
    fun align(sourceBitmap: Bitmap, detection: Detection): Bitmap? {
        val w = sourceBitmap.width.toFloat()
        val h = sourceBitmap.height.toFloat()

        // Guard 1: need at least 2 keypoints (left eye + right eye)
        val keypoints = detection.keypoints().orElse(null) ?: return null
        if (keypoints.size < 2) return null

        // Guard 2: bounding box must have meaningful size
        val bbox = detection.boundingBox()
        if (bbox.width() < w * MIN_BBOX_FRACTION || bbox.height() < h * MIN_BBOX_FRACTION) {
            return null
        }

        // Denormalize eye coordinates
        val leftEyeX = keypoints[0].x() * w
        val leftEyeY = keypoints[0].y() * h
        val rightEyeX = keypoints[1].x() * w
        val rightEyeY = keypoints[1].y() * h

        // Guard 3: eyes must be meaningfully separated before rotation
        val dx = rightEyeX - leftEyeX
        val dy = rightEyeY - leftEyeY
        val eyeDist = sqrt(dx * dx + dy * dy)
        if (eyeDist < MIN_EYE_DIST_PX) return null

        // Compute roll angle and rotate the full bitmap
        val angleDeg = atan2(dy, dx) * (180.0 / Math.PI).toFloat()
        rotationMatrix.reset()
        rotationMatrix.postRotate(angleDeg, w / 2f, h / 2f)  // pivot at image centre, not (0,0)
        val rotated = Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, rotationMatrix, true)

        // Re-compute eye positions in rotated bitmap
        val rw = rotated.width.toFloat()
        val rh = rotated.height.toFloat()
        val rotMid = floatArrayOf((leftEyeX + rightEyeX) / 2f, (leftEyeY + rightEyeY) / 2f)
        rotationMatrix.mapPoints(rotMid)

        // Guard 4: eye distance should be preserved after rotation
        val leftEyeRot = floatArrayOf(leftEyeX, leftEyeY)
        val rightEyeRot = floatArrayOf(rightEyeX, rightEyeY)
        rotationMatrix.mapPoints(leftEyeRot)
        rotationMatrix.mapPoints(rightEyeRot)
        val rotEyeDist = sqrt(
            (rightEyeRot[0] - leftEyeRot[0]).let { it * it } +
            (rightEyeRot[1] - leftEyeRot[1]).let { it * it }
        )
        if (rotEyeDist < MIN_EYE_DIST_PX) {
            rotated.recycle()
            return null
        }

        // Build crop box centred on eye midpoint, shifted slightly toward chin
        val midX = rotMid[0]
        val midY = rotMid[1] + CHIN_SHIFT * rotEyeDist
        val halfSize = (rotEyeDist * FACE_SCALE / 2f) * (1f + PADDING)

        val left = (midX - halfSize).coerceAtLeast(0f)
        val top = (midY - halfSize).coerceAtLeast(0f)
        val right = (midX + halfSize).coerceAtMost(rw)
        val bottom = (midY + halfSize).coerceAtMost(rh)

        // Guard 5: crop region must be valid
        if (right <= left || bottom <= top) {
            rotated.recycle()
            return null
        }

        srcRect.set(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

        // Draw cropped+scaled region into pre-allocated 112×112 bitmap
        drawMatrix.reset()
        drawMatrix.setRectToRect(
            RectF(srcRect),
            dstRect,
            Matrix.ScaleToFit.FILL
        )
        outputBitmap.eraseColor(0)
        outputCanvas.drawBitmap(rotated, drawMatrix, null)

        rotated.recycle()
        return outputBitmap
    }
}
