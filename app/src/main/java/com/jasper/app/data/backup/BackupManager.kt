package com.jasper.app.data.backup

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.jasper.app.data.db.FaceDatabase
import com.jasper.app.data.db.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class BackupUser(
    val id: Int,
    val name: String,
    val createdAt: Long,
    val embeddings: List<List<Float>>   // each inner list = one 128-float embedding
)

data class BackupFile(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val users: List<BackupUser>
)

object BackupManager {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun export(context: Context, db: FaceDatabase, uri: Uri): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val users = db.userDao().getAllUsersOnce()
                val backupUsers = users.map { user ->
                    val embeddings = db.embeddingDao()
                        .getEmbeddingsForUser(user.id)
                        .map { entity ->
                            val floats = FloatArray(entity.embedding.size / 4)
                            java.nio.ByteBuffer.wrap(entity.embedding)
                                .asFloatBuffer()
                                .get(floats)
                            floats.toList()
                        }
                    BackupUser(user.id, user.name, user.createdAt, embeddings)
                }
                val backup = BackupFile(users = backupUsers)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(gson.toJson(backup).toByteArray(Charsets.UTF_8))
                } ?: error("Cannot open output stream")
                backupUsers.size
            }
        }

    suspend fun import(context: Context, db: FaceDatabase, uri: Uri): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?.toString(Charsets.UTF_8) ?: error("Cannot open input stream")
                val backup = gson.fromJson(json, BackupFile::class.java)
                    ?: error("Invalid backup file")
                var imported = 0
                for (bu in backup.users) {
                    val existingUsers = db.userDao().getAllUsersOnce()
                    val exists = existingUsers.any { it.name == bu.name }
                    if (exists) continue
                    val newId = db.userDao().insertUser(
                        UserEntity(name = bu.name, createdAt = bu.createdAt)
                    ).toInt()
                    for (floats in bu.embeddings) {
                        val bytes = java.nio.ByteBuffer
                            .allocate(floats.size * 4)
                            .apply { floats.forEach { putFloat(it) } }
                            .array()
                        db.embeddingDao().insertEmbedding(
                            com.jasper.app.data.db.EmbeddingEntity(userId = newId, embedding = bytes)
                        )
                    }
                    imported++
                }
                imported
            }
        }
}
