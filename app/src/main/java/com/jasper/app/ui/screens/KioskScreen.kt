package com.jasper.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageProxy
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private const val RESULT_DISPLAY_MS = 2500L
private const val KIOSK_CONFIDENCE_THRESHOLD = 93f
private const val AUTHORIZED_DISPLAY_MS = 4000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KioskScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as JasperApplication
    val vm: RecognitionViewModel = viewModel(
        factory = RecognitionViewModel.factory(app.repository, app.settingsRepository)
    )
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }
    var torchEnabled by remember { mutableStateOf(false) }
    SideEffect { vm.isFrontCamera = (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) }

    // Kiosk overlay state
    var kioskResult by remember { mutableStateOf<KioskResult?>(null) }

    // ToneGenerator for beeps
    val toneGen = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull()
    }
    DisposableEffect(Unit) { onDispose { toneGen?.release() } }

    // TextToSpeech for access announcements
    val tts = remember { TextToSpeech(context, null) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }

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

    // Watch recognition results and trigger kiosk overlay
    LaunchedEffect(uiState) {
        val state = uiState as? RecognitionUiState.Scanning ?: return@LaunchedEffect
        val knownFace = state.results.firstOrNull {
            it.isKnown && it.confidencePercent >= KIOSK_CONFIDENCE_THRESHOLD
        }
        if (knownFace != null && kioskResult?.name != knownFace.label) {
            kioskResult = KioskResult(authorized = true, name = knownFace.label)
            tts.speak("Access authorized. Welcome, ${knownFace.label}", TextToSpeech.QUEUE_FLUSH, null, "kiosk_welcome")
            delay(AUTHORIZED_DISPLAY_MS)
            kioskResult = null
        } else if (state.results.isNotEmpty() && state.results.all { !it.isKnown }
            && kioskResult == null) {
            kioskResult = KioskResult(authorized = false, name = null)
            toneGen?.startTone(ToneGenerator.TONE_PROP_NACK, 500)
            delay(RESULT_DISPLAY_MS)
            kioskResult = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (!hasCameraPermission) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.camera_permission_required))
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.grant_permission))
                }
            }
        } else {
            when (val state = uiState) {
                RecognitionUiState.Loading -> { /* wait */ }
                is RecognitionUiState.Error -> {
                    Text(state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp))
                }
                is RecognitionUiState.Scanning -> {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        cameraSelector = cameraSelector,
                        torchEnabled = torchEnabled,
                        imageAnalyzer = if (kioskResult == null) {
                            { proxy: ImageProxy -> vm.processFrame(proxy) }
                        } else null
                    )
                    FaceOverlay(results = if (kioskResult == null) state.results else emptyList(), modifier = Modifier.fillMaxSize())

                    // FPS
                    if (state.fps > 0f) {
                        Text(
                            "${"%.1f".format(state.fps)} fps",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        )
                    }
                }
            }
        }

        // Back button (top-left)
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.navigate_back),
                tint = Color.White)
        }

        // Top-right controls: torch + camera flip
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) {
            // Torch toggle (hidden when front camera — front cameras have no torch)
            if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                IconButton(onClick = { torchEnabled = !torchEnabled }) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Default.FlashOff else Icons.Default.FlashOn,
                        contentDescription = "Toggle flashlight",
                        tint = Color.White
                    )
                }
            }
            // Camera flip
            IconButton(onClick = {
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                    CameraSelector.DEFAULT_BACK_CAMERA
                else {
                    torchEnabled = false   // reset torch when returning to front
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
            }) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Flip camera",
                    tint = Color.White
                )
            }
        }

        // Kiosk result overlay
        AnimatedVisibility(
            visible = kioskResult != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            val result = kioskResult
            if (result != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            if (result.authorized) Color(0xCC1B5E20) else Color(0xCCB71C1C)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (result.authorized) Icons.Default.CheckCircle
                                          else Icons.Default.Error,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = if (result.authorized)
                                stringResource(R.string.kiosk_authorized)
                            else
                                stringResource(R.string.kiosk_denied),
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (result.authorized && result.name != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = result.name,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Screen title
        Text(
            text = stringResource(R.string.kiosk_mode),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .fillMaxWidth()
                .padding(horizontal = 56.dp)
        )
    }
}

private data class KioskResult(val authorized: Boolean, val name: String?)
