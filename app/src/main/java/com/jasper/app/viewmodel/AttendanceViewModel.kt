package com.jasper.app.viewmodel

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jasper.app.data.db.AttendanceEntryEntity
import com.jasper.app.data.db.AttendanceSessionEntity
import com.jasper.app.data.db.FaceDatabase
import com.jasper.app.data.repository.FaceRepository
import com.jasper.app.data.repository.model.RegisteredUser
import com.jasper.app.data.settings.SettingsRepository
import com.jasper.app.ml.LivenessChecker
import com.jasper.app.ml.LivenessStatus
import com.jasper.app.ui.state.AttendanceUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class AttendanceViewModel(
    private val repository: FaceRepository,
    private val db: FaceDatabase,
    private val settings: SettingsRepository
) : ViewModel() {

    companion object {
        private const val FPS_WINDOW   = 10
        private const val COOLDOWN_MS  = 30_000L   // per-user DB write cooldown
        private const val ATTENDANCE_CONFIDENCE_THRESHOLD = 93f

        fun factory(repository: FaceRepository, db: FaceDatabase, settings: SettingsRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AttendanceViewModel(repository, db, settings) as T
            }
    }

    private val _uiState = MutableStateFlow<AttendanceUiState>(AttendanceUiState.Loading)
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    @Volatile var isFrontCamera: Boolean = true
    private val isProcessingFrame = AtomicBoolean(false)
    private val frameTimestamps   = ArrayDeque<Long>(FPS_WINDOW + 1)

    @Volatile private var registeredUsers: List<RegisteredUser> = emptyList()

    /** Per-user timestamp of last DB write — enforces COOLDOWN_MS between writes. */
    private val lastLoggedAt = HashMap<Int, Long>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                registeredUsers = repository.getAllRegisteredUsers()
                val sessions = db.attendanceDao().getAllSessions().first()
                _uiState.value = AttendanceUiState.Idle(sessions)
            } catch (e: Exception) {
                _uiState.value = AttendanceUiState.Error(e.message ?: "Failed to load")
            }
        }
    }

    fun startSession(sessionName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sessionId = db.attendanceDao().insertSession(
                    AttendanceSessionEntity(name = sessionName.ifBlank { "Session" })
                ).toInt()
                LivenessChecker.reset()
                lastLoggedAt.clear()
                _uiState.value = AttendanceUiState.ActiveSession(
                    sessionId   = sessionId,
                    sessionName = sessionName.ifBlank { "Session" },
                    allUsers    = registeredUsers
                )
            } catch (e: Exception) {
                _uiState.value = AttendanceUiState.Error(e.message ?: "Failed to start session")
            }
        }
    }

    fun endSession() {
        val state = _uiState.value as? AttendanceUiState.ActiveSession ?: return
        viewModelScope.launch(Dispatchers.IO) {
            db.attendanceDao().closeSession(state.sessionId, System.currentTimeMillis())
            val sessions = db.attendanceDao().getAllSessions().first()
            lastLoggedAt.clear()
            LivenessChecker.reset()
            _uiState.value = AttendanceUiState.Idle(sessions)
        }
    }

    fun processFrame(imageProxy: ImageProxy) {
        if (!isProcessingFrame.compareAndSet(false, true)) {
            imageProxy.close(); return
        }
        val state = _uiState.value as? AttendanceUiState.ActiveSession
        if (state == null) {
            imageProxy.close(); isProcessingFrame.set(false); return
        }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val bitmap    = prepareBitmap(imageProxy)
                val threshold = settings.recognitionThreshold.first()
                val results   = repository.recognizeFaces(bitmap, registeredUsers, threshold)

                val now = System.currentTimeMillis()
                frameTimestamps.addLast(now)
                while (frameTimestamps.size > FPS_WINDOW + 1) frameTimestamps.removeFirst()
                val fps = if (frameTimestamps.size >= 2) {
                    val elapsed = (frameTimestamps.last() - frameTimestamps.first()) / 1000f
                    if (elapsed > 0) (frameTimestamps.size - 1) / elapsed else 0f
                } else 0f

                // ── Liveness + confidence + cooldown filter ─────────────────────
                val liveKnown = results.filter { r ->
                    if (!r.isKnown) return@filter false
                    if (r.confidencePercent < ATTENDANCE_CONFIDENCE_THRESHOLD) return@filter false
                    // Anti-spoofing: must show movement across frames
                    val liveness = LivenessChecker.check(r.boundingBox)
                    liveness != LivenessStatus.STATIC
                }

                if (liveKnown.isNotEmpty()) {
                    val newlyPresent  = liveKnown.mapNotNull { r ->
                        registeredUsers.find { it.name == r.label }
                    }
                    val updatedPresent = state.presentUserIds.toMutableSet()

                    withIO {
                        for (user in newlyPresent) {
                            // Enforce cooldown — skip DB write if within window
                            if ((now - (lastLoggedAt[user.id] ?: 0L)) < COOLDOWN_MS) continue
                            lastLoggedAt[user.id] = now

                            if (updatedPresent.contains(user.id)) {
                                db.attendanceDao().updateEntry(state.sessionId, user.id, now)
                            } else {
                                db.attendanceDao().insertEntry(
                                    AttendanceEntryEntity(
                                        sessionId   = state.sessionId,
                                        userId      = user.id,
                                        userName    = user.name,
                                        firstSeenAt = now,
                                        lastSeenAt  = now
                                    )
                                )
                                updatedPresent.add(user.id)
                            }
                        }
                    }
                    _uiState.value = state.copy(presentUserIds = updatedPresent, fps = fps)
                } else {
                    _uiState.value = state.copy(fps = fps)
                }
            } catch (e: Exception) {
                // Don't crash the session on a single frame error
            } finally {
                imageProxy.close()
                isProcessingFrame.set(false)
            }
        }
    }

    private suspend fun withIO(block: suspend () -> Unit) =
        kotlinx.coroutines.withContext(Dispatchers.IO) { block() }

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
