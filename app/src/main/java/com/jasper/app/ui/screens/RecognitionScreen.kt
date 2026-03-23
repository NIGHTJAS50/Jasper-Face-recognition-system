package com.jasper.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageProxy
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jasper.app.JasperApplication
import com.jasper.app.R
import com.jasper.app.ui.components.CameraPreview
import com.jasper.app.ui.components.FaceOverlay
import com.jasper.app.ui.state.RecognitionUiState
import com.jasper.app.viewmodel.RecognitionViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WELCOME_DISPLAY_MS = 4000L
private const val HIGH_CONFIDENCE_THRESHOLD = 93f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionScreen(onNavigateBack: () -> Unit, onNavigateToKiosk: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as JasperApplication
    val vm: RecognitionViewModel = viewModel(
        factory = RecognitionViewModel.factory(app.repository, app.settingsRepository)
    )
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    // ── Camera & torch state ──────────────────────────────────────────────────
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }
    var torchEnabled by remember { mutableStateOf(false) }

    // Keep ViewModel in sync with current camera facing
    SideEffect { vm.isFrontCamera = (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) }

    // ── Snackbar for unknown-person alert ────────────────────────────────────
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val unknownAlertMsg = stringResource(R.string.unknown_person_alert)
    LaunchedEffect(Unit) {
        vm.unknownAlert.collect {
            scope.launch { snackbarHostState.showSnackbar(unknownAlertMsg) }
        }
    }

    // ── Text-to-Speech ────────────────────────────────────────────────────────
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember {
        TextToSpeech(context) { status ->
            ttsReady = (status == TextToSpeech.SUCCESS)
        }
    }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }

    // ── Welcome / pause state ─────────────────────────────────────────────────
    // Non-null while the welcome card is showing; recognition is paused during this time.
    var welcomeName by remember { mutableStateOf<String?>(null) }

    // Fire when a face hits ≥ 95% confidence
    LaunchedEffect(uiState) {
        val state = uiState as? RecognitionUiState.Scanning ?: return@LaunchedEffect
        // Only trigger if not already showing a welcome
        if (welcomeName != null) return@LaunchedEffect
        val highConf = state.results.firstOrNull {
            it.isKnown && it.confidencePercent >= HIGH_CONFIDENCE_THRESHOLD
        } ?: return@LaunchedEffect

        welcomeName = highConf.label   // pauses frame analysis (analyzer → null below)

        if (ttsReady) {
            tts.speak(
                "Welcome, ${highConf.label}",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "welcome_utterance"
            )
        }

        delay(WELCOME_DISPLAY_MS)
        welcomeName = null              // resumes recognition
    }

    // ── Runtime camera permission ─────────────────────────────────────────────
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    // ─────────────────────────────────────────────────────────────────────────

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recognize)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToKiosk) {
                        Icon(Icons.Filled.Security,
                            contentDescription = stringResource(R.string.kiosk_mode))
                    }
                    if (uiState is RecognitionUiState.Scanning && welcomeName == null) {
                        IconButton(onClick = { torchEnabled = !torchEnabled }) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Filled.FlashOff
                                              else Icons.Filled.FlashOn,
                                contentDescription = stringResource(
                                    if (torchEnabled) R.string.torch_off else R.string.torch_on
                                )
                            )
                        }
                        IconButton(onClick = {
                            torchEnabled = false
                            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                                CameraSelector.DEFAULT_BACK_CAMERA
                            else
                                CameraSelector.DEFAULT_FRONT_CAMERA
                        }) {
                            Icon(
                                Icons.Filled.FlipCameraAndroid,
                                contentDescription = stringResource(R.string.flip_camera)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!hasCameraPermission) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.camera_permission_required),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.grant_permission))
                    }
                }
                return@Scaffold
            }

            when (val state = uiState) {
                RecognitionUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is RecognitionUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }

                is RecognitionUiState.Scanning -> {
                    // Camera preview stays live at all times.
                    // When welcomeName != null, imageAnalyzer is null → frames are dropped
                    // at the CameraPreview proxy level, so the ViewModel gets no new data.
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        cameraSelector = cameraSelector,
                        torchEnabled = torchEnabled,
                        imageAnalyzer = if (welcomeName == null) {
                            { imageProxy: ImageProxy -> vm.processFrame(imageProxy) }
                        } else null
                    )

                    // Bounding boxes — only shown while actively scanning
                    if (welcomeName == null) {
                        FaceOverlay(
                            results = state.results,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (state.fps > 0f) {
                            Text(
                                text = "${"%.1f".format(state.fps)} fps",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                            )
                        }
                    }

                    // Welcome card — slides up from bottom, tap to dismiss early
                    AnimatedVisibility(
                        visible = welcomeName != null,
                        enter = slideInVertically { it / 2 } + fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 40.dp, start = 20.dp, end = 20.dp)
                    ) {
                        WelcomeCard(
                            name = welcomeName ?: "",
                            onDismiss = { welcomeName = null }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeCard(name: String, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xEE0A1744),    // deep navy, nearly opaque
        shadowElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDismiss)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF69F0AE),    // bright mint green
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.welcome_user),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.80f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.tap_to_dismiss),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.45f),
                textAlign = TextAlign.Center
            )
        }
    }
}
