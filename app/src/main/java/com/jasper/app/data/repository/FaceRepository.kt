package com.jasper.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.jasper.app.data.db.Converters
import com.jasper.app.data.db.EmbeddingEntity
import com.jasper.app.data.db.FaceDatabase
import com.jasper.app.data.db.RecognitionEventEntity
import com.jasper.app.data.db.UserEntity
import com.jasper.app.data.db.UserStats
import com.jasper.app.data.repository.model.RecognitionResult
import com.jasper.app.data.repository.model.RegisteredUser
import com.jasper.app.ml.EmbeddingMatcher
import com.jasper.app.ml.FaceAligner
import com.jasper.app.ml.FaceDetectorHelper
import com.jasper.app.ml.FaceEmbedder
import com.jasper.app.ml.FaceQualityChecker
import com.jasper.app.ml.LinearFaceMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

/**
 * Single source of truth for face registration and recognition.
 *
 * ML lifecycle:
 *  - [detector] is app-scoped: initialized once, never closed from ViewModels.
 *  - [embedder] is session-scoped: closed by each ViewModel.onCleared() via [closeEmbedderResources].
 *
 * In-memory session state ([userStates]) is loaded from DB once per app session via
 * [bootstrapSession], then kept current by incremental mutations — no hot-path DB reads.
 */
class FaceRepository(
    private val context: Context,
    private val db: FaceDatabase,
    private val matcher: EmbeddingMatcher = LinearFaceMatcher()
) {

    companion object {
        private const val TAG = "FaceRepository"
        const val DEFAULT_THRESHOLD = 0.6f

        fun l2NormalizeStatic(vec: FloatArray): FloatArray? {
            val norm = sqrt(vec.sumOf { (it * it).toDouble() }).toFloat()
            if (norm < 1e-10f) return null
            return FloatArray(vec.size) { vec[it] / norm }
        }
    }

    // ─── App-scoped ML resources ──────────────────────────────────────────────

    private val detector = FaceDetectorHelper(context)
    private val aligner  = FaceAligner()

    // ─── Session-scoped ML resources ─────────────────────────────────────────

    @Volatile private var embedder: FaceEmbedder? = null
    private val embedderLock = Any()

    private fun getOrCreateEmbedder(): FaceEmbedder =
        embedder ?: synchronized(embedderLock) {
            embedder ?: FaceEmbedder(context).also { embedder = it }
        }

    // ─── In-memory session state ──────────────────────────────────────────────

    private class UserSessionState(val userId: Int, var name: String) {
        val embeddingSum = FloatArray(128)
        var embeddingCount = 0
        var templateEmbedding = FloatArray(128)
        val individualEmbeddings = mutableListOf<FloatArray>()

        fun addEmbedding(embedding: FloatArray) {
            for (i in embeddingSum.indices) embeddingSum[i] += embedding[i]
            embeddingCount++
            individualEmbeddings.add(embedding)
            templateEmbedding = l2NormalizeStatic(embeddingSum) ?: embedding.copyOf()
        }

        fun toRegisteredUser(): RegisteredUser = RegisteredUser(
            id = userId,
            name = name,
            embeddings = individualEmbeddings.toList(),
            templateEmbedding = templateEmbedding.copyOf()
        )
    }

    private val userStates = HashMap<Int, UserSessionState>()
    private val stateLock  = Any()

    @Volatile private var sessionLoaded = false

    // ─── DB-facing Flows ──────────────────────────────────────────────────────

    val allUsers: Flow<List<UserEntity>> = db.userDao().getAllUsers()
    val userStats: Flow<List<UserStats>> = db.recognitionEventDao().getUserStats()

    // ─── Session bootstrap ────────────────────────────────────────────────────

    suspend fun bootstrapSession() = withContext(Dispatchers.IO) {
        if (sessionLoaded) return@withContext
        val users = db.userDao().getAllUsersOnce()
        val allEmbeddings = db.embeddingDao().getAllEmbeddings()
        val embeddingsByUser = allEmbeddings.groupBy { it.userId }

        synchronized(stateLock) {
            if (sessionLoaded) return@synchronized
            userStates.clear()
            for (user in users) {
                val state = UserSessionState(user.id, user.name)
                embeddingsByUser[user.id]?.forEach { entity ->
                    state.addEmbedding(Converters.byteArrayToFloatArray(entity.embedding))
                }
                userStates[user.id] = state
            }
            sessionLoaded = true
        }
    }

    // ─── Registration ─────────────────────────────────────────────────────────

    suspend fun detectFaces(bitmap: Bitmap): List<RectF> = withContext(Dispatchers.Default) {
        val result = detector.detect(bitmap) ?: return@withContext emptyList()
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        result.detections().map { d ->
            val b = d.boundingBox()
            RectF(b.left / w, b.top / h, b.right / w, b.bottom / h)
        }
    }

    /**
     * Detect + align + embed all faces in [bitmap].
     * Returns a list of (embedding, qualityResult) pairs — one per detected face.
     */
    suspend fun detectAlignEmbed(bitmap: Bitmap): List<Pair<FloatArray, FaceQualityChecker.QualityResult>> =
        withContext(Dispatchers.Default) {
            val detectionResult = detector.detect(bitmap) ?: return@withContext emptyList()
            val embedderRef = getOrCreateEmbedder()
            val results = mutableListOf<Pair<FloatArray, FaceQualityChecker.QualityResult>>()

            for (detection in detectionResult.detections()) {
                val aligned = aligner.align(bitmap, detection) ?: continue
                val quality = FaceQualityChecker.check(aligned)
                val embedding = embedderRef.embed(aligned) ?: continue
                results.add(embedding to quality)
            }
            results
        }

    /** Legacy: detect + embed, ignoring quality. */
    suspend fun detectAndEmbed(bitmap: Bitmap): List<FloatArray> =
        detectAlignEmbed(bitmap).map { it.first }

    suspend fun registerUser(name: String): Int = withContext(Dispatchers.IO) {
        val userId = db.userDao().insertUser(UserEntity(name = name)).toInt()
        synchronized(stateLock) {
            userStates[userId] = UserSessionState(userId, name)
        }
        userId
    }

    suspend fun addEmbeddingForUser(userId: Int, embedding: FloatArray, photo: Bitmap? = null) =
        withContext(Dispatchers.IO) {
            val bytes = Converters.floatArrayToByteArray(embedding)
            db.embeddingDao().insertEmbedding(EmbeddingEntity(userId = userId, embedding = bytes))

            // Save profile photo for first embedding (or whenever photo is provided)
            if (photo != null) {
                saveProfilePhoto(userId, photo)
            }

            synchronized(stateLock) {
                userStates.getOrPut(userId) {
                    UserSessionState(userId, "")
                }.addEmbedding(embedding)
            }
        }

    fun profilePhotoFile(userId: Int): File =
        File(context.filesDir, "faces/$userId.jpg")

    private fun saveProfilePhoto(userId: Int, bitmap: Bitmap) {
        try {
            val dir = File(context.filesDir, "faces")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "$userId.jpg")
            // Only save if no photo exists yet (first capture wins)
            if (!file.exists()) {
                FileOutputStream(file).use { out ->
                    // Copy bitmap to avoid using the pre-allocated aligner buffer
                    val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    copy.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    copy.recycle()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save profile photo for user $userId", e)
        }
    }

    // ─── Recognition ──────────────────────────────────────────────────────────

    suspend fun getAllRegisteredUsers(): List<RegisteredUser> {
        bootstrapSession()
        return synchronized(stateLock) {
            userStates.values
                .filter { it.individualEmbeddings.isNotEmpty() }
                .map { it.toRegisteredUser() }
        }
    }

    /**
     * Detect, align, embed, and classify all faces in [bitmap].
     * Logs successful recognitions to the recognition_events table.
     */
    suspend fun recognizeFaces(
        bitmap: Bitmap,
        users: List<RegisteredUser>,
        threshold: Float = DEFAULT_THRESHOLD
    ): List<RecognitionResult> = withContext(Dispatchers.Default) {
        val detectionResult = detector.detect(bitmap) ?: return@withContext emptyList()
        if (detectionResult.detections().isEmpty()) return@withContext emptyList()

        val embedderRef = getOrCreateEmbedder()
        val bitmapW = bitmap.width.toFloat()
        val bitmapH = bitmap.height.toFloat()
        val results = mutableListOf<RecognitionResult>()

        for (detection in detectionResult.detections()) {
            val aligned = aligner.align(bitmap, detection) ?: continue
            val embedding = embedderRef.embed(aligned) ?: continue

            val bbox = detection.boundingBox()
            val normalizedBox = RectF(
                bbox.left / bitmapW, bbox.top / bitmapH,
                bbox.right / bitmapW, bbox.bottom / bitmapH
            )

            val result = if (users.isEmpty()) {
                RecognitionResult("Unknown", -1f, 0f, normalizedBox, false)
            } else {
                matcher.findBestMatch(embedding, users, threshold, normalizedBox)
            }
            results.add(result)

            // Log successful recognition to history
            if (result.isKnown) {
                withContext(Dispatchers.IO) {
                    try {
                        db.recognitionEventDao().insertEvent(
                            RecognitionEventEntity(
                                userId = users.first { it.name == result.label }.id,
                                userName = result.label,
                                confidencePercent = result.confidencePercent
                            )
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to log recognition event", e)
                    }
                }
            }
        }
        results
    }

    // ─── User management ──────────────────────────────────────────────────────

    suspend fun deleteUser(userId: Int): Unit = withContext(Dispatchers.IO) {
        db.userDao().deleteUser(userId)
        db.recognitionEventDao().deleteEventsForUser(userId)
        profilePhotoFile(userId).delete()
        synchronized(stateLock) { userStates.remove(userId) }
    }

    suspend fun renameUser(userId: Int, newName: String): Unit = withContext(Dispatchers.IO) {
        db.userDao().updateUserName(userId, newName)
        synchronized(stateLock) {
            userStates[userId]?.name = newName
        }
    }

    // ─── Resource management ──────────────────────────────────────────────────

    fun closeEmbedderResources() {
        synchronized(embedderLock) {
            embedder?.close()
            embedder = null
        }
    }

    fun closeAllResources() {
        closeEmbedderResources()
        detector.close()
    }
}
