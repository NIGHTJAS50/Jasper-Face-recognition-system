package com.jasper.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "face_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId")]
)
data class EmbeddingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: Int,
    val embedding: ByteArray,   // FloatArray serialized via Converters
    val capturedAt: Long = System.currentTimeMillis()
) {
    // ByteArray equality must be structural, not referential
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddingEntity) return false
        return id == other.id &&
            userId == other.userId &&
            embedding.contentEquals(other.embedding) &&
            capturedAt == other.capturedAt
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + userId
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + capturedAt.hashCode()
        return result
    }
}
