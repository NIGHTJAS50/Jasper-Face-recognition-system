package com.jasper.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jasper.app.data.repository.FaceRepository
import com.jasper.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val repository: FaceRepository
) : ViewModel() {

    val threshold: StateFlow<Float> = settings.recognitionThreshold
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_THRESHOLD)

    val targetCaptures: StateFlow<Int> = settings.targetCaptures
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_TARGET_CAPTURES)

    val autoCaptureEnabled: StateFlow<Boolean> = settings.autoCaptureEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_AUTO_CAPTURE)

    val autoCaptureDelayMs: StateFlow<Long> = settings.autoCaptureDelayMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_AUTO_CAP_DELAY)

    fun setThreshold(value: Float)          = viewModelScope.launch { settings.setRecognitionThreshold(value) }
    fun setTargetCaptures(value: Int)       = viewModelScope.launch { settings.setTargetCaptures(value) }
    fun setAutoCaptureEnabled(value: Boolean) = viewModelScope.launch { settings.setAutoCaptureEnabled(value) }
    fun setAutoCaptureDelayMs(value: Long)  = viewModelScope.launch { settings.setAutoCaptureDelayMs(value) }

    companion object {
        fun factory(settings: SettingsRepository, repository: FaceRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(settings, repository) as T
            }
    }
}
