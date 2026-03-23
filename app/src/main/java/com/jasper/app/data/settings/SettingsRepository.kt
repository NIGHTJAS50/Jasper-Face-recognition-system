package com.jasper.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jasper_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val KEY_THRESHOLD         = floatPreferencesKey("recognition_threshold")
        val KEY_TARGET_CAPTURES   = intPreferencesKey("target_captures")
        val KEY_AUTO_CAPTURE      = booleanPreferencesKey("auto_capture_enabled")
        val KEY_AUTO_CAP_DELAY_MS = longPreferencesKey("auto_capture_delay_ms")

        const val DEFAULT_THRESHOLD       = 0.60f
        const val DEFAULT_TARGET_CAPTURES = 5
        const val DEFAULT_AUTO_CAPTURE    = true
        const val DEFAULT_AUTO_CAP_DELAY  = 1500L
    }

    val recognitionThreshold: Flow<Float> = context.dataStore.data
        .map { it[KEY_THRESHOLD] ?: DEFAULT_THRESHOLD }

    val targetCaptures: Flow<Int> = context.dataStore.data
        .map { it[KEY_TARGET_CAPTURES] ?: DEFAULT_TARGET_CAPTURES }

    val autoCaptureEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_AUTO_CAPTURE] ?: DEFAULT_AUTO_CAPTURE }

    val autoCaptureDelayMs: Flow<Long> = context.dataStore.data
        .map { it[KEY_AUTO_CAP_DELAY_MS] ?: DEFAULT_AUTO_CAP_DELAY }

    suspend fun setRecognitionThreshold(value: Float) {
        context.dataStore.edit { it[KEY_THRESHOLD] = value }
    }

    suspend fun setTargetCaptures(value: Int) {
        context.dataStore.edit { it[KEY_TARGET_CAPTURES] = value }
    }

    suspend fun setAutoCaptureEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_CAPTURE] = value }
    }

    suspend fun setAutoCaptureDelayMs(value: Long) {
        context.dataStore.edit { it[KEY_AUTO_CAP_DELAY_MS] = value }
    }
}
