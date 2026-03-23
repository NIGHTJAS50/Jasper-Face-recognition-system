package com.jasper.app.ml

import android.graphics.Bitmap

/**
 * Lightweight face quality assessment — runs entirely on the 112×112 aligned crop,
 * no ML inference required.
 *
 * Checks:
 *  - Blur (Laplacian variance): low variance = blurry
 *  - Brightness (mean luminance): too dark or overexposed
 */
object FaceQualityChecker {

    private const val BLUR_THRESHOLD       = 60.0    // variance below this = blurry
    private const val DARK_THRESHOLD       = 40.0    // mean luminance below = too dark
    private const val BRIGHT_THRESHOLD     = 220.0   // mean luminance above = overexposed

    enum class QualityIssue { OK, BLURRY, TOO_DARK, OVEREXPOSED }

    data class QualityResult(
        val issue: QualityIssue,
        val blurVariance: Double,
        val meanBrightness: Double
    ) {
        val isAcceptable: Boolean get() = issue == QualityIssue.OK
    }

    fun check(bitmap: Bitmap): QualityResult {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Compute grayscale array
        val gray = DoubleArray(w * h) { i ->
            val px = pixels[i]
            0.299 * ((px shr 16) and 0xFF) +
            0.587 * ((px shr 8)  and 0xFF) +
            0.114 * (px and 0xFF)
        }

        // Mean brightness
        val meanBrightness = gray.average()

        // Laplacian variance (Laplacian kernel: center -4, 4 neighbours +1)
        var lapSum   = 0.0
        var lapSumSq = 0.0
        var count    = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val lap = (gray[y * w + x + 1] + gray[y * w + x - 1] +
                           gray[(y - 1) * w + x] + gray[(y + 1) * w + x] -
                           4.0 * gray[y * w + x])
                lapSum   += lap
                lapSumSq += lap * lap
                count++
            }
        }
        val mean        = if (count > 0) lapSum / count else 0.0
        val blurVariance = if (count > 0) lapSumSq / count - mean * mean else 0.0

        val issue = when {
            meanBrightness < DARK_THRESHOLD    -> QualityIssue.TOO_DARK
            meanBrightness > BRIGHT_THRESHOLD  -> QualityIssue.OVEREXPOSED
            blurVariance   < BLUR_THRESHOLD    -> QualityIssue.BLURRY
            else                               -> QualityIssue.OK
        }

        return QualityResult(issue, blurVariance, meanBrightness)
    }
}
