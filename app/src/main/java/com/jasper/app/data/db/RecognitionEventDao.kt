package com.jasper.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecognitionEventDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: RecognitionEventEntity)

    @Query("SELECT * FROM recognition_events ORDER BY recognizedAt DESC")
    fun getAllEvents(): Flow<List<RecognitionEventEntity>>

    @Query("SELECT * FROM recognition_events WHERE userId = :userId ORDER BY recognizedAt DESC")
    fun getEventsForUser(userId: Int): Flow<List<RecognitionEventEntity>>

    @Query("""
        SELECT userId, COUNT(*) as recognitionCount, MAX(recognizedAt) as lastSeenAt
        FROM recognition_events
        GROUP BY userId
    """)
    fun getUserStats(): Flow<List<UserStats>>

    @Query("DELETE FROM recognition_events WHERE userId = :userId")
    suspend fun deleteEventsForUser(userId: Int)

    @Query("DELETE FROM recognition_events")
    suspend fun clearAll()

    // ── Analytics aggregates ──────────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM recognition_events WHERE recognizedAt >= :startOfDay")
    suspend fun countTodayRecognitions(startOfDay: Long): Int

    @Query("SELECT COUNT(DISTINCT userId) FROM recognition_events WHERE recognizedAt >= :startOfDay")
    suspend fun countUniqueUsersToday(startOfDay: Long): Int

    @Query("SELECT AVG(confidencePercent) FROM recognition_events WHERE recognizedAt >= :startOfDay")
    suspend fun avgConfidenceToday(startOfDay: Long): Float?

    @Query("SELECT COUNT(*) FROM recognition_events WHERE recognizedAt >= :from AND recognizedAt < :to")
    suspend fun countInRange(from: Long, to: Long): Int
}
