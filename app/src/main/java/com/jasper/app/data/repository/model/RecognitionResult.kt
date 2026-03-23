package com.jasper.app.data.repository.model

import android.graphics.RectF

/**
 * Result of one face recognition attempt.
 *
 * [label]           — display name ("Unknown" if below threshold)
 * [similarityScore] — raw cosine similarity in [-1, 1]; internal use only
 * [confidencePercent] — mapped to [0, 100] for display:
 *                       ((similarityScore + 1) / 2) * 100
 *                       This is NOT a probability — a 60% here does NOT mean
 *                       "60% chance this is the person". It is purely a display-friendly
 *                       rescaling of the raw dot-product similarity.
 * [boundingBox]     — face region in normalized image coordinates [0, 1]
 * [isKnown]         — true iff similarityScore >= threshold at match time
 */
data class RecognitionResult(
    val label: String,
    val similarityScore: Float,
    val confidencePercent: Float,
    val boundingBox: RectF,
    val isKnown: Boolean
)
