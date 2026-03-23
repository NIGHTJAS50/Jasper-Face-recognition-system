package com.jasper.app.ui.state

data class AnalyticsStats(
    val totalRecognitionsToday: Int,
    val uniqueUsersToday: Int,
    val avgConfidenceToday: Float,     // 0–100
    val uniqueAttendeesToday: Int
)

sealed class AnalyticsUiState {
    object Loading : AnalyticsUiState()
    data class Ready(val stats: AnalyticsStats) : AnalyticsUiState()
    data class Error(val message: String) : AnalyticsUiState()
}
