package com.jasper.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserEntity::class,
        EmbeddingEntity::class,
        RecognitionEventEntity::class,
        AttendanceSessionEntity::class,
        AttendanceEntryEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class FaceDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun embeddingDao(): EmbeddingDao
    abstract fun recognitionEventDao(): RecognitionEventDao
    abstract fun attendanceDao(): AttendanceDao

    companion object {
        private const val DATABASE_NAME = "jasper_database"

        /** v1 → v2: add recognition_events table */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS recognition_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        userName TEXT NOT NULL,
                        confidencePercent REAL NOT NULL,
                        recognizedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        /** v2 → v3: add attendance tables */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS attendance_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        startedAt INTEGER NOT NULL,
                        endedAt INTEGER
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS attendance_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId INTEGER NOT NULL,
                        userId INTEGER NOT NULL,
                        userName TEXT NOT NULL,
                        firstSeenAt INTEGER NOT NULL,
                        lastSeenAt INTEGER NOT NULL,
                        scanCount INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY (sessionId) REFERENCES attendance_sessions(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_attendance_entries_sessionId ON attendance_entries(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_attendance_entries_userId ON attendance_entries(userId)")
            }
        }

        @Volatile
        private var instance: FaceDatabase? = null

        fun getInstance(context: Context): FaceDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): FaceDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FaceDatabase::class.java,
                DATABASE_NAME
            )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
    }
}
