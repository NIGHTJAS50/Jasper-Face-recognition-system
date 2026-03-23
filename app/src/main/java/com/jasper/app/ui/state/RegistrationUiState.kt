package com.jasper.app.ui.state

import android.graphics.RectF

sealed class RegistrationUiState {
    object NameInput : RegistrationUiState()

    /** Motion-based liveness challenge before captures begin. */
    data class LivenessCheck(
        val challenge: String,
        val currentBox: RectF? = null,
        val progress: Float = 0f   // 0→1 as required head movement is detected
    ) : RegistrationUiState()

    data class Capturing(
        val count: Int,
        val target: Int = 5,
        val currentBox: RectF? = null,
        val autoCapProgress: Float = 0f,    // 0→1 countdown arc
        val qualityWarning: String? = null  // non-null = show quality feedback overlay
    ) : RegistrationUiState()

    object Saving : RegistrationUiState()
    object Success : RegistrationUiState()
    data class Error(val message: String) : RegistrationUiState()
}
