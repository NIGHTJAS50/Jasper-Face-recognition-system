package com.jasper.app.viewmodel

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jasper.app.data.repository.FaceRepository
import com.jasper.app.data.repository.model.RecognitionResult
import com.jasper.app.data.settings.SettingsRepository
import com.jasper.app.ml.LivenessChecker
import com.jasper.app.ml.LivenessStatus
import com.jasper.app.ui.state.RecognitionUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class RecognitionViewModel(
    private val repository: FaceRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    companion object {
        private const val FPS_WINDOW            = 10
        private const val RESULT_PERSIST_MS     = 300L
        private const val UNKNOWN_ALERT_FRAMES  = 15   // ~1.5 s at ~10 fps

        fun factory(repository: FaceRepository, settings: SettingsRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RecognitionViewModel(repository, settings) as T
            }
    }

    private val _uiState = MutableStateFlow<RecognitionUiState>(RecognitionUiState.Loading)
    val uiState: StateFlow<RecognitionUiState> = _uiState.asStateFlow()

    /** Emits once when an unrecognised face has been present for UNKNOWN_ALERT_FRAMES frames. */
    private val _unknownAlert = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val unknownAlert: SharedFlow<Unit> = _unknownAlert.asSharedFlow()

    /** Live threshold from DataStore — changes are picked up on the next frame. */
    private val thresholdFlow = settings.recognitionThreshold.stateIn(
        viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_THRESHOLD
    )

    private val isProcessingFrame        = AtomicBoolean(false)
    private val frameTimestamps          = ArrayDeque<Long>(FPS_WINDOW + 1)
    private var consecutiveUnknownFrames = 0

    @Volatile var isFrontCamera: Boolean = true
    @Volatile private var registeredUsers = emptyList<com.jasper.app.data.repository.model.RegisteredUser>()
    @Volatile private var lastResults: List<RecognitionResult> = emptyList()
    @Volatile private var lastResultsMs = 0L

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                registeredUsers = repository.getAllRegisteredUsers()
                _uiState.value = RecognitionUiState.Scanning()
            } catch (e: Exception) {
                _uiState.value = RecognitionUiState.Error(e.message ?: "Failed to load users")
            }
        }
    }

    fun processFrame(imageProxy: ImageProxy) {
        if (!isProcessingFrame.compareAndSet(false, true)) {
            imageProxy.close(); return
        }
        if (_uiState.value is RecognitionUiState.Error) {
            imageProxy.close(); isProcessingFrame.set(false); return
        }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val bitmap    = prepareBitmap(imageProxy)
                val threshold = thresholdFlow.value
                val rawResults = repository.recognizeFaces(bitmap, registeredUsers, threshold)

                // ── Anti-spoofing: demote STATIC faces to "unknown" ───────────
                val results = rawResults.map { r ->
                    if (!r.isKnown) return@map r
                    val liveness = LivenessChecker.check(r.boundingBox)
                    if (liveness == LivenessStatus.STATIC) {
                        // Face is likely a photo — override label and isKnown flag
                        r.copy(label = "Spoof?", isKnown = false)
                    } else r
                }

                val now = System.currentTimeMillis()
                frameTimestamps.addLast(now)
                while (frameTimestamps.size > FPS_WINDOW + 1) frameTimestamps.removeFirst()
                val fps = if (frameTimestamps.size >= 2) {
                    val elapsed = (frameTimestamps.last() - frameTimestamps.first()) / 1000f
                    if (elapsed > 0) (frameTimestamps.size - 1) / elapsed else 0f
                } else 0f

                val displayResults = if (results.isNotEmpty()) {
                    lastResults = results; lastResultsMs = now; results
                } else if (now - lastResultsMs <= RESULT_PERSIST_MS) {
                    lastResults
                } else emptyList()

                // ── Unknown-person alert ──────────────────────────────────────
                val hasKnown = displayResults.any { it.isKnown }
                if (!hasKnown && displayResults.isNotEmpty()) {
                    consecutiveUnknownFrames++
                    if (consecutiveUnknownFrames == UNKNOWN_ALERT_FRAMES) {
                        _unknownAlert.tryEmit(Unit)
                    }
                } else {
                    consecutiveUnknownFrames = 0
                }

                _uiState.value = RecognitionUiState.Scanning(results = displayResults, fps = fps)
            } catch (e: Exception) {
                _uiState.value = RecognitionUiState.Error(e.message ?: "Recognition failed")
            } finally {
                imageProxy.close()
                isProcessingFrame.set(false)
            }
        }
    }

    private fun prepareBitmap(imageProxy: ImageProxy): Bitmap {
        val bitmap = imageProxy.toBitmap()
        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            if (isFrontCamera) preScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    override fun onCleared() {
        super.onCleared()
        LivenessChecker.reset()
        repository.closeEmbedderResources()
    }
}
