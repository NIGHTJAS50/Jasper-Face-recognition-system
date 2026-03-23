package com.jasper.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recognition_events")
data class RecognitionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val userName: String,
    val confidencePercent: Float,
    val recognizedAt: Long = System.currentTimeMillis()
)
