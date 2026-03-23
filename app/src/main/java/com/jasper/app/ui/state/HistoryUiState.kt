package com.jasper.app.ui.state

import com.jasper.app.data.db.RecognitionEventEntity

sealed class HistoryUiState {
    object Loading : HistoryUiState()
    data class Ready(val events: List<RecognitionEventEntity>) : HistoryUiState()
    data class Error(val message: String) : HistoryUiState()
}
