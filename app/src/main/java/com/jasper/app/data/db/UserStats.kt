package com.jasper.app.data.db

import androidx.room.ColumnInfo

/** Aggregated recognition stats per user — returned by RecognitionEventDao. */
data class UserStats(
    @ColumnInfo(name = "userId") val userId: Int,
    @ColumnInfo(name = "recognitionCount") val recognitionCount: Int,
    @ColumnInfo(name = "lastSeenAt") val lastSeenAt: Long
)
