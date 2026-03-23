package com.jasper.app.viewmodel

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jasper.app.data.repository.FaceRepository
import com.jasper.app.data.settings.SettingsRepository
import com.jasper.app.ml.FaceQualityChecker
import com.jasper.app.ui.state.RegistrationUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class RegistrationViewModel(
    private val repository: FaceRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    companion object {
        /** How long the face bounding box stays visible after the face disappears (ms). */
        private const val BOX_PERSIST_MS = 500L

        /** Liveness check: minimum relative Y-range to pass (fraction of frame height). */
        private const val LIVENESS_Y_THRESHOLD = 0.07f

        /** Liveness check timeout — auto-fail if exceeded (ms). */
        private const val LIVENESS_TIMEOUT_MS = 8000L

        /** Progress animation step (ms). */
        private const val PROGRESS_STEP_MS = 50L

        fun factory(repository: FaceRepository, settings: SettingsRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RegistrationViewModel(repository, settings) as T
            }
    }

    private val _uiState = MutableStateFlow<RegistrationUiState>(RegistrationUiState.NameInput)
    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

    private val _captureEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val captureEvent: SharedFlow<Unit> = _captureEvent.asSharedFlow()

    private val isProcessingFrame = AtomicBoolean(false)
    private val isCapturing        = AtomicBoolean(false)

    @Volatile var isFrontCamera: Boolean = true
    @Volatile private var lastBitmap: Bitmap? = null
    @Volatile private var lastBoxSeenMs  = 0L
    @Volatile private var stableFaceStartMs = 0L

    // Liveness tracking
    @Volatile private var livenessMinY = Float.MAX_VALUE
    @Volatile private var livenessMaxY = -Float.MAX_VALUE
    @Volatile private var livenessStartMs = 0L

    private var autoCapJob: Job? = null

    private val capturedEmbeddings = mutableListOf<FloatArray>()
    private var currentUserId: Int = -1
    private var targetCaptures: Int = SettingsRepository.DEFAULT_TARGET_CAPTURES
    private var autoCaptureEnabled: Boolean = SettingsRepository.DEFAULT_AUTO_CAPTURE
    private var autoCaptureDelayMs: Long = SettingsRepository.DEFAULT_AUTO_CAP_DELAY

    fun startCapture(name: String, existingUserId: Int? = null) {
        if (name.isBlank() && existingUserId == null) {
            _uiState.value = RegistrationUiState.Error("Name cannot be empty")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            // Read settings once
            targetCaptures    = settings.targetCaptures.first()
            autoCaptureEnabled = settings.autoCaptureEnabled.first()
            autoCaptureDelayMs = settings.autoCaptureDelayMs.first()

            currentUserId = existingUserId ?: repository.registerUser(name.trim())
            capturedEmbeddings.clear()
            lastBitmap      = null
            lastBoxSeenMs   = 0L
            stableFaceStartMs = 0L

            // Begin with liveness check
            resetLiveness()
            _uiState.value = RegistrationUiState.LivenessCheck(challenge = "Slowly nod your head")
        }
    }

    // ── Frame processing ──────────────────────────────────────────────────────

    fun processFrame(imageProxy: ImageProxy) {
        if (!isProcessingFrame.compareAndSet(false, true)) {
            imageProxy.close(); return
        }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val bitmap = prepareBitmap(imageProxy)
                lastBitmap = bitmap
                val boxes  = repository.detectFaces(bitmap)
                val newBox = boxes.firstOrNull()
                val nowMs  = System.currentTimeMillis()

                when (val state = _uiState.value) {
                    is RegistrationUiState.LivenessCheck -> handleLivenessFrame(newBox, nowMs, state)
                    is RegistrationUiState.Capturing     -> handleCapturingFrame(newBox, nowMs, state)
                    else -> { /* no-op */ }
                }
            } finally {
                imageProxy.close()
                isProcessingFrame.set(false)
            }
        }
    }

    private fun handleLivenessFrame(newBox: RectF?, nowMs: Long, state: RegistrationUiState.LivenessCheck) {
        if (newBox == null) return
        if (livenessStartMs == 0L) livenessStartMs = nowMs

        // Track vertical range of face centre
        val centerY = (newBox.top + newBox.bottom) / 2f
        if (centerY < livenessMinY) livenessMinY = centerY
        if (centerY > livenessMaxY) livenessMaxY = centerY

        val yRange = livenessMaxY - livenessMinY
        val progress = (yRange / LIVENESS_Y_THRESHOLD).coerceIn(0f, 1f)
        _uiState.value = state.copy(currentBox = newBox, progress = progress)

        val timedOut = nowMs - livenessStartMs > LIVENESS_TIMEOUT_MS
        if (progress >= 1f || timedOut) {
            // Pass liveness (or timeout fallback) → go to capturing
            _uiState.value = RegistrationUiState.Capturing(
                count = capturedEmbeddings.size,
                target = targetCaptures
            )
        }
    }

    private fun handleCapturingFrame(newBox: RectF?, nowMs: Long, state: RegistrationUiState.Capturing) {
        if (newBox != null) {
            lastBoxSeenMs = nowMs
            if (stableFaceStartMs == 0L) {
                stableFaceStartMs = nowMs
                if (autoCaptureEnabled) startAutoCapCountdown()
            }
            val elapsed  = nowMs - stableFaceStartMs
            val progress = (elapsed.toFloat() / autoCaptureDelayMs).coerceIn(0f, 1f)
            _uiState.value = state.copy(
                currentBox    = newBox,
                autoCapProgress = if (autoCaptureEnabled) progress else 0f,
                qualityWarning  = null
            )
        } else {
            val timeSinceLast = nowMs - lastBoxSeenMs
            if (timeSinceLast > BOX_PERSIST_MS) {
                cancelAutoCapture()
                _uiState.value = state.copy(currentBox = null, autoCapProgress = 0f)
            }
        }
    }

    // ── Capture ───────────────────────────────────────────────────────────────

    fun captureNow() {
        val current = _uiState.value as? RegistrationUiState.Capturing ?: return
        if (current.currentBox == null) return
        if (!isCapturing.compareAndSet(false, true)) return
        val bitmap = lastBitmap ?: run { isCapturing.set(false); return }
        cancelAutoCapture()

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val embedResults = repository.detectAlignEmbed(bitmap)
                if (embedResults.isEmpty()) { isCapturing.set(false); return@launch }

                val (embedding, quality) = embedResults.first()
                if (!quality.isAcceptable) {
                    // Quality gate: show warning instead of storing bad embedding
                    val warning = when (quality.issue) {
                        FaceQualityChecker.QualityIssue.BLURRY     -> "Hold still — too blurry"
                        FaceQualityChecker.QualityIssue.TOO_DARK    -> "Too dark — find better lighting"
                        FaceQualityChecker.QualityIssue.OVEREXPOSED -> "Too bright — reduce light"
                        else -> null
                    }
                    _uiState.value = current.copy(qualityWarning = warning, autoCapProgress = 0f)
                    isCapturing.set(false)
                    return@launch
                }

                // Save photo only for the very first capture
                val photo = if (capturedEmbeddings.isEmpty()) bitmap else null
                repository.addEmbeddingForUser(currentUserId, embedding, photo)
                capturedEmbeddings.add(embedding)
                _captureEvent.tryEmit(Unit)

                val newCount = capturedEmbeddings.size
                _uiState.value = if (newCount >= targetCaptures) {
                    RegistrationUiState.Success
                } else {
                    RegistrationUiState.Capturing(
                        count = newCount,
                        target = targetCaptures,
                        currentBox = current.currentBox
                    )
                }
            } finally {
                isCapturing.set(false)
            }
        }
    }

    fun resetToNameInput() {
        cancelAutoCapture()
        capturedEmbeddings.clear()
        lastBitmap = null
        lastBoxSeenMs = 0L
        stableFaceStartMs = 0L
        currentUserId = -1
        resetLiveness()
        _uiState.value = RegistrationUiState.NameInput
    }

    // ── Auto-capture countdown ────────────────────────────────────────────────

    private fun startAutoCapCountdown() {
        autoCapJob?.cancel()
        autoCapJob = viewModelScope.launch(Dispatchers.Default) {
            val startMs = System.currentTimeMillis()
            while (isActive) {
                val elapsed  = System.currentTimeMillis() - startMs
                val progress = (elapsed.toFloat() / autoCaptureDelayMs).coerceIn(0f, 1f)
                val current  = _uiState.value as? RegistrationUiState.Capturing
                if (current != null) _uiState.value = current.copy(autoCapProgress = progress)
                if (elapsed >= autoCaptureDelayMs) { captureNow(); break }
                delay(PROGRESS_STEP_MS)
            }
        }
    }

    private fun cancelAutoCapture() {
        autoCapJob?.cancel(); autoCapJob = null; stableFaceStartMs = 0L
    }

    private fun resetLiveness() {
        livenessMinY = Float.MAX_VALUE; livenessMaxY = -Float.MAX_VALUE; livenessStartMs = 0L
    }

    // ── Image preparation ─────────────────────────────────────────────────────

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
        cancelAutoCapture()
        repository.closeEmbedderResources()
    }
}
