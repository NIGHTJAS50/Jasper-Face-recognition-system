package com.jasper.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jasper.app.data.db.FaceDatabase
import com.jasper.app.ui.state.AnalyticsStats
import com.jasper.app.ui.state.AnalyticsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class AnalyticsViewModel(private val db: FaceDatabase) : ViewModel() {

    private val _uiState = MutableStateFlow<AnalyticsUiState>(AnalyticsUiState.Loading)
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = AnalyticsUiState.Loading
            try {
                val startOfDay = startOfTodayMs()
                val total      = db.recognitionEventDao().countTodayRecognitions(startOfDay)
                val unique     = db.recognitionEventDao().countUniqueUsersToday(startOfDay)
                val avgConf    = db.recognitionEventDao().avgConfidenceToday(startOfDay) ?: 0f
                val attendees  = db.attendanceDao().countUniqueAttendeesToday(startOfDay)
                _uiState.value = AnalyticsUiState.Ready(
                    AnalyticsStats(
                        totalRecognitionsToday = total,
                        uniqueUsersToday       = unique,
                        avgConfidenceToday     = avgConf,
                        uniqueAttendeesToday   = attendees
                    )
                )
            } catch (e: Exception) {
                _uiState.value = AnalyticsUiState.Error(e.message ?: "Failed to load analytics")
            }
        }
    }

    companion object {
        fun startOfTodayMs(): Long {
            return Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        fun factory(db: FaceDatabase) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AnalyticsViewModel(db) as T
        }
    }
}
