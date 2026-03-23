package com.jasper.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jasper.app.data.db.FaceDatabase
import com.jasper.app.ui.state.HistoryUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val db: FaceDatabase) : ViewModel() {

    val uiState = db.recognitionEventDao().getAllEvents()
        .map<_, HistoryUiState> { HistoryUiState.Ready(it) }
        .catch { e -> emit(HistoryUiState.Error(e.message ?: "Failed to load history")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState.Loading)

    fun clearHistory() {
        viewModelScope.launch {
            db.recognitionEventDao().clearAll()
        }
    }

    companion object {
        fun factory(db: FaceDatabase) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HistoryViewModel(db) as T
        }
    }
}
