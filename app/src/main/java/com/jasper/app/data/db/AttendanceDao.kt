package com.jasper.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    // ── Sessions ──────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: AttendanceSessionEntity): Long

    @Query("UPDATE attendance_sessions SET endedAt = :endedAt WHERE id = :sessionId")
    suspend fun closeSession(sessionId: Int, endedAt: Long)

    @Query("SELECT * FROM attendance_sessions ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<AttendanceSessionEntity>>

    @Query("SELECT * FROM attendance_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Int): AttendanceSessionEntity?

    // ── Entries ───────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(entry: AttendanceEntryEntity): Long

    @Query("""
        UPDATE attendance_entries
        SET lastSeenAt = :lastSeenAt, scanCount = scanCount + 1
        WHERE sessionId = :sessionId AND userId = :userId
    """)
    suspend fun updateEntry(sessionId: Int, userId: Int, lastSeenAt: Long)

    @Query("SELECT * FROM attendance_entries WHERE sessionId = :sessionId ORDER BY firstSeenAt ASC")
    fun getEntriesForSession(sessionId: Int): Flow<List<AttendanceEntryEntity>>

    @Query("SELECT userId FROM attendance_entries WHERE sessionId = :sessionId")
    suspend fun getPresentUserIds(sessionId: Int): List<Int>

    // ── Date-range / today queries ────────────────────────────────────────────

    @Query("SELECT * FROM attendance_sessions WHERE startedAt >= :startOfDay ORDER BY startedAt DESC")
    fun getSessionsToday(startOfDay: Long): Flow<List<AttendanceSessionEntity>>

    @Query("SELECT * FROM attendance_sessions WHERE startedAt >= :from AND startedAt < :to ORDER BY startedAt DESC")
    suspend fun getSessionsInRange(from: Long, to: Long): List<AttendanceSessionEntity>

    @Query("SELECT * FROM attendance_entries WHERE sessionId = :sessionId ORDER BY firstSeenAt ASC")
    suspend fun getEntriesForSessionOnce(sessionId: Int): List<AttendanceEntryEntity>

    @Query("SELECT COUNT(DISTINCT userId) FROM attendance_entries WHERE sessionId IN (SELECT id FROM attendance_sessions WHERE startedAt >= :startOfDay)")
    suspend fun countUniqueAttendeesToday(startOfDay: Long): Int
}
