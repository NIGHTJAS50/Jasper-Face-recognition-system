package com.jasper.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jasper.app.JasperApplication
import com.jasper.app.R
import com.jasper.app.data.db.AttendanceSessionEntity
import com.jasper.app.data.repository.model.RegisteredUser
import com.jasper.app.ui.components.CameraPreview
import com.jasper.app.ui.state.AttendanceUiState
import com.jasper.app.viewmodel.AttendanceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as JasperApplication
    val vm: AttendanceViewModel = viewModel(
        factory = AttendanceViewModel.factory(app.repository, app.database, app.settingsRepository)
    )
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }
    SideEffect { vm.isFrontCamera = (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.attendance)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (uiState is AttendanceUiState.ActiveSession) {
                        IconButton(onClick = { vm.endSession() }) {
                            Icon(Icons.Default.Stop,
                                contentDescription = stringResource(R.string.end_session),
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                AttendanceUiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                is AttendanceUiState.Error ->
                    Text(state.message, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp))

                is AttendanceUiState.Idle ->
                    AttendanceIdleContent(
                        pastSessions = state.pastSessions,
                        onStartSession = { name -> vm.startSession(name) }
                    )

                is AttendanceUiState.ActiveSession -> {
                    if (!hasCameraPermission) {
                        Column(
                            Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(stringResource(R.string.camera_permission_required))
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text(stringResource(R.string.grant_permission))
                            }
                        }
                    } else {
                        AttendanceActiveContent(
                            state = state,
                            cameraSelector = cameraSelector,
                            onAnalyzeFrame = { vm.processFrame(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceIdleContent(
    pastSessions: List<AttendanceSessionEntity>,
    onStartSession: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var sessionName by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.new_session_label),
            style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = sessionName,
            onValueChange = { sessionName = it },
            label = { Text(stringResource(R.string.session_name_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onStartSession(sessionName); sessionName = "" },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Groups, contentDescription = null,
                modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.start_session))
        }

        if (pastSessions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.past_sessions_label),
                style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(pastSessions.reversed(), key = { it.id }) { session ->
                    SessionRow(session)
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: AttendanceSessionEntity) {
    val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(session.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                fmt.format(Date(session.startedAt)) +
                    if (session.endedAt != null) " → ${fmt.format(Date(session.endedAt))}" else " (open)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AttendanceActiveContent(
    state: AttendanceUiState.ActiveSession,
    cameraSelector: CameraSelector,
    onAnalyzeFrame: (ImageProxy) -> Unit
) {
    Row(Modifier.fillMaxSize()) {
        // Camera preview (left 55%)
        Box(Modifier.weight(0.55f).fillMaxSize()) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                cameraSelector = cameraSelector,
                torchEnabled = false,
                imageAnalyzer = { onAnalyzeFrame(it) }
            )
            // FPS counter
            if (state.fps > 0f) {
                Text(
                    "${"%.1f".format(state.fps)} fps",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                )
            }
        }

        // Roll call list (right 45%)
        Column(
            Modifier.weight(0.45f).fillMaxSize().padding(8.dp)
        ) {
            Text(
                state.sessionName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.present_count,
                    state.presentUserIds.size, state.allUsers.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            LazyColumn(
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val sorted = state.allUsers.sortedWith(
                    compareByDescending<RegisteredUser> { state.presentUserIds.contains(it.id) }
                        .thenBy { it.name }
                )
                items(sorted, key = { it.id }) { user ->
                    val present = state.presentUserIds.contains(user.id)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            imageVector = if (present) Icons.Default.CheckCircle
                                          else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (present) Color(0xFF4CAF50)
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(user.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (present) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
