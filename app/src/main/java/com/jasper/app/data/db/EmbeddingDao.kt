package com.jasper.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EmbeddingDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEmbedding(embedding: EmbeddingEntity): Long

    @Query("SELECT * FROM face_embeddings ORDER BY id ASC")
    suspend fun getAllEmbeddings(): List<EmbeddingEntity>

    @Query("SELECT * FROM face_embeddings WHERE userId = :userId ORDER BY id ASC")
    suspend fun getEmbeddingsForUser(userId: Int): List<EmbeddingEntity>

    @Query("SELECT COUNT(*) FROM face_embeddings WHERE userId = :userId")
    suspend fun getEmbeddingCountForUser(userId: Int): Int
}
