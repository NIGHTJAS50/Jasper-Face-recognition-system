package com.jasper.app.ml

import android.graphics.RectF
import com.jasper.app.data.repository.model.RecognitionResult
import com.jasper.app.data.repository.model.RegisteredUser

/**
 * Strategy interface for embedding matching.
 *
 * The default implementation is [LinearFaceMatcher] (O(U×512) two-phase scan),
 * which is sufficient for up to ~500 registered users at real-time frame rates.
 *
 * For larger user bases, swap in an ANN (Approximate Nearest Neighbour) implementation
 * such as FAISS, ScaNN, or an on-device HNSW index — no changes to callers required.
 */
interface EmbeddingMatcher {

    /**
     * Find the best-matching user for [queryEmbedding] among [users].
     *
     * @param queryEmbedding L2-normalized 512-dim embedding from FaceEmbedder
     * @param users          current in-memory list of registered users
     * @param threshold      cosine similarity threshold in [0, 1]; default 0.6
     * @param boundingBox    face bounding box in normalized image coords [0, 1]
     * @return               [RecognitionResult] with label, scores, and bounding box
     */
    fun findBestMatch(
        queryEmbedding: FloatArray,
        users: List<RegisteredUser>,
        threshold: Float,
        boundingBox: RectF
    ): RecognitionResult
}
