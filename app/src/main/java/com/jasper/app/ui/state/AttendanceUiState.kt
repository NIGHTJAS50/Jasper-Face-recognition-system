package com.jasper.app.ui.state

import com.jasper.app.data.db.AttendanceSessionEntity
import com.jasper.app.data.repository.model.RegisteredUser

sealed class AttendanceUiState {
    object Loading : AttendanceUiState()

    /** Session not yet started — shows past sessions + Start button. */
    data class Idle(val pastSessions: List<AttendanceSessionEntity>) : AttendanceUiState()

    /** Active session: real-time recognition tracking presence. */
    data class ActiveSession(
        val sessionId: Int,
        val sessionName: String,
        val allUsers: List<RegisteredUser>,
        val presentUserIds: Set<Int> = emptySet(),
        val fps: Float = 0f
    ) : AttendanceUiState()

    data class Error(val message: String) : AttendanceUiState()
}
