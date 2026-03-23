package com.jasper.app.data.repository.model

/**
 * In-memory domain model for a registered user.
 *
 * [templateEmbedding] is the L2-normalized average of all [embeddings] for that user.
 * It is used in Phase 1 of the two-phase matching (fast O(U×512) path).
 * Individual [embeddings] are only scanned in Phase 2 when the template score
 * falls within NEAR_MARGIN of the threshold.
 */
data class RegisteredUser(
    val id: Int,
    val name: String,
    val embeddings: List<FloatArray>,
    val templateEmbedding: FloatArray
) {
    // FloatArray equality must be structural
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RegisteredUser) return false
        return id == other.id &&
            name == other.name &&
            templateEmbedding.contentEquals(other.templateEmbedding)
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + name.hashCode()
        result = 31 * result + templateEmbedding.contentHashCode()
        return result
    }
}
