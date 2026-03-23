package com.jasper.app.ui.state

import com.jasper.app.data.repository.model.RecognitionResult

sealed class RecognitionUiState {
    object Loading : RecognitionUiState()
    data class Scanning(
        val results: List<RecognitionResult> = emptyList(),
        val fps: Float = 0f
    ) : RecognitionUiState()
    data class Error(val message: String) : RecognitionUiState()
}
