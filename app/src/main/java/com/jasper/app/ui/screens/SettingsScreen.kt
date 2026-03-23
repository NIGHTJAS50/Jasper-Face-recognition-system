package com.jasper.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jasper.app.JasperApplication
import com.jasper.app.R
import com.jasper.app.data.backup.BackupManager
import com.jasper.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as JasperApplication
    val vm: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(app.settingsRepository, app.repository)
    )
    val scope = rememberCoroutineScope()

    val threshold by vm.threshold.collectAsStateWithLifecycle()
    val targetCaptures by vm.targetCaptures.collectAsStateWithLifecycle()
    val autoCaptureEnabled by vm.autoCaptureEnabled.collectAsStateWithLifecycle()
    val autoCaptureDelayMs by vm.autoCaptureDelayMs.collectAsStateWithLifecycle()

    // Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            BackupManager.export(context, app.database, uri).fold(
                onSuccess = { count ->
                    Toast.makeText(context,
                        context.getString(R.string.export_success, count), Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    Toast.makeText(context,
                        context.getString(R.string.export_failed, e.message), Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // Import launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            BackupManager.import(context, app.database, uri).fold(
                onSuccess = { count ->
                    Toast.makeText(context,
                        context.getString(R.string.import_success, count), Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    Toast.makeText(context,
                        context.getString(R.string.import_failed, e.message), Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Recognition ─────────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.settings_section_recognition))

            Text(
                stringResource(R.string.threshold_label, "%.0f".format(threshold * 100)),
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = threshold,
                onValueChange = { vm.setThreshold(it) },
                valueRange = 0.40f..0.90f,
                steps = 9   // 0.05 steps
            )
            Text(
                stringResource(R.string.threshold_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.target_captures_label),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 5, 8, 10).forEach { n ->
                    if (n == targetCaptures) {
                        Button(onClick = {}) { Text("$n") }
                    } else {
                        OutlinedButton(onClick = { vm.setTargetCaptures(n) }) { Text("$n") }
                    }
                }
            }

            // ── Auto-capture ─────────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            SectionHeader(stringResource(R.string.settings_section_capture))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.auto_capture_label),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = autoCaptureEnabled,
                    onCheckedChange = { vm.setAutoCaptureEnabled(it) })
            }

            if (autoCaptureEnabled) {
                Text(
                    stringResource(R.string.auto_capture_delay_label),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1000L to "1s", 1500L to "1.5s", 2000L to "2s", 3000L to "3s").forEach { (ms, label) ->
                        if (ms == autoCaptureDelayMs) {
                            Button(onClick = {}) { Text(label) }
                        } else {
                            OutlinedButton(onClick = { vm.setAutoCaptureDelayMs(ms) }) { Text(label) }
                        }
                    }
                }
            }

            // ── Backup ──────────────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            SectionHeader(stringResource(R.string.settings_section_backup))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                        exportLauncher.launch("jasper_backup_$date.json")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.export_backup))
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.import_backup))
                }
            }
            Text(
                stringResource(R.string.backup_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary)
    HorizontalDivider(Modifier.padding(bottom = 4.dp))
}
