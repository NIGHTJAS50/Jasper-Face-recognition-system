package com.jasper.app.ml

import android.graphics.RectF
import com.jasper.app.data.repository.model.RecognitionResult
import com.jasper.app.data.repository.model.RegisteredUser

/**
 * Two-phase linear embedding matcher.
 *
 * Phase 1 — Template scan O(U × D):
 *   Compare query against each user's L2-normalized template (average embedding).
 *   Dot product of two L2-normalized vectors = cosine similarity.
 *
 * Phase 2 — Individual scan O(K × D), only for near-threshold users:
 *   For users whose template score >= threshold - NEAR_MARGIN, scan every individual
 *   embedding. Catches cases where the template drifted but a specific capture is close.
 *
 * Swap this class for an ANN implementation via [EmbeddingMatcher] for >500 users.
 */
class LinearFaceMatcher : EmbeddingMatcher {

    companion object {
        private const val NEAR_MARGIN = 0.05f
    }

    override fun findBestMatch(
        queryEmbedding: FloatArray,
        users: List<RegisteredUser>,
        threshold: Float,
        boundingBox: RectF
    ): RecognitionResult {
        if (users.isEmpty()) return unknown(boundingBox)

        // Phase 1: template comparison
        var bestTemplateSim = -1f
        var bestUser: RegisteredUser? = null
        for (user in users) {
            if (user.templateEmbedding.size != queryEmbedding.size) continue  // dimension guard
            val sim = dotProduct(queryEmbedding, user.templateEmbedding)
            if (sim > bestTemplateSim) {
                bestTemplateSim = sim
                bestUser = user
            }
        }

        if (bestTemplateSim < threshold - NEAR_MARGIN || bestUser == null) {
            return unknown(boundingBox, bestTemplateSim)
        }

        // Phase 2: individual embeddings for near-threshold candidates
        var bestSim = bestTemplateSim
        var bestMatchUser = bestUser
        for (user in users) {
            if (user.templateEmbedding.size != queryEmbedding.size) continue
            if (dotProduct(queryEmbedding, user.templateEmbedding) < threshold - NEAR_MARGIN) continue
            for (emb in user.embeddings) {
                if (emb.size != queryEmbedding.size) continue
                val sim = dotProduct(queryEmbedding, emb)
                if (sim > bestSim) {
                    bestSim = sim
                    bestMatchUser = user
                }
            }
        }

        val isKnown = bestSim >= threshold
        return RecognitionResult(
            label = if (isKnown) bestMatchUser!!.name else "Unknown",
            similarityScore = bestSim,
            confidencePercent = ((bestSim + 1f) / 2f) * 100f,
            boundingBox = boundingBox,
            isKnown = isKnown
        )
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        val len = minOf(a.size, b.size)
        var sum = 0f
        for (i in 0 until len) sum += a[i] * b[i]
        return sum
    }

    private fun unknown(box: RectF, sim: Float = -1f) = RecognitionResult(
        label = "Unknown",
        similarityScore = sim,
        confidencePercent = ((sim + 1f) / 2f) * 100f,
        boundingBox = box,
        isKnown = false
    )
}
