package com.jasper.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jasper.app.JasperApplication
import com.jasper.app.R
import com.jasper.app.ui.components.CameraPreview
import com.jasper.app.ui.state.RegistrationUiState
import com.jasper.app.viewmodel.RegistrationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    onNavigateBack: () -> Unit,
    existingUserId: Int? = null   // non-null = "add more captures" mode
) {
    val context = LocalContext.current
    val app = context.applicationContext as JasperApplication
    val vm: RegistrationViewModel = viewModel(
        factory = RegistrationViewModel.factory(app.repository, app.settingsRepository)
    )
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        vm.captureEvent.collect {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }
    var torchEnabled by remember { mutableStateOf(false) }

    // Keep ViewModel in sync with current camera facing
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

    val isCapturingMode = uiState is RegistrationUiState.Capturing ||
                          uiState is RegistrationUiState.LivenessCheck

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(
                    if (existingUserId != null) R.string.add_captures else R.string.register_face
                )) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (isCapturingMode) {
                        IconButton(onClick = { torchEnabled = !torchEnabled }) {
                            Icon(
                                if (torchEnabled) Icons.Filled.FlashOff else Icons.Filled.FlashOn,
                                contentDescription = stringResource(
                                    if (torchEnabled) R.string.torch_off else R.string.torch_on)
                            )
                        }
                        IconButton(onClick = {
                            torchEnabled = false
                            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                                CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                        }) {
                            Icon(Icons.Filled.FlipCameraAndroid,
                                contentDescription = stringResource(R.string.flip_camera))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                RegistrationUiState.NameInput -> {
                    if (existingUserId != null) {
                        // "Add more captures" — skip name entry, start immediately
                        LaunchedEffect(Unit) { vm.startCapture("", existingUserId) }
                        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        NameInputContent(
                            onStartCapture = { name ->
                                if (hasCameraPermission) vm.startCapture(name)
                                else permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        )
                    }
                }

                is RegistrationUiState.LivenessCheck -> {
                    if (!hasCameraPermission) {
                        PermissionDeniedContent { permissionLauncher.launch(Manifest.permission.CAMERA) }
                    } else {
                        LivenessContent(
                            state = state,
                            cameraSelector = cameraSelector,
                            torchEnabled = torchEnabled,
                            onAnalyzeFrame = { vm.processFrame(it) }
                        )
                    }
                }

                is RegistrationUiState.Capturing -> {
                    if (!hasCameraPermission) {
                        PermissionDeniedContent { permissionLauncher.launch(Manifest.permission.CAMERA) }
                    } else {
                        CapturingContent(
                            state = state,
                            cameraSelector = cameraSelector,
                            torchEnabled = torchEnabled,
                            onAnalyzeFrame = { vm.processFrame(it) },
                            onCapture = { vm.captureNow() }
                        )
                    }
                }

                RegistrationUiState.Saving ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

                RegistrationUiState.Success -> SuccessContent(
                    onDone = onNavigateBack,
                    onRegisterAnother = { vm.resetToNameInput() },
                    isAddMode = existingUserId != null
                )

                is RegistrationUiState.Error -> ErrorContent(
                    message = state.message,
                    onRetry = { vm.resetToNameInput() }
                )
            }
        }
    }
}

@Composable
private fun NameInputContent(onStartCapture: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    var name by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.enter_name_prompt), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text(stringResource(R.string.name_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { onStartCapture(name) }, enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.start_capture))
        }
    }
}

@Composable
private fun LivenessContent(
    state: RegistrationUiState.LivenessCheck,
    cameraSelector: CameraSelector,
    torchEnabled: Boolean,
    onAnalyzeFrame: (ImageProxy) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            cameraSelector = cameraSelector,
            torchEnabled = torchEnabled,
            imageAnalyzer = { onAnalyzeFrame(it) }
        )
        FaceGuideOverlay(detectedBox = state.currentBox, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.liveness_challenge),
                style = MaterialTheme.typography.titleMedium, color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.liveness_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun CapturingContent(
    state: RegistrationUiState.Capturing,
    cameraSelector: CameraSelector,
    torchEnabled: Boolean,
    onAnalyzeFrame: (ImageProxy) -> Unit,
    onCapture: () -> Unit
) {
    val faceDetected = state.currentBox != null
    Box(Modifier.fillMaxSize()) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            cameraSelector = cameraSelector,
            torchEnabled = torchEnabled,
            imageAnalyzer = { onAnalyzeFrame(it) }
        )
        FaceGuideOverlay(detectedBox = state.currentBox, modifier = Modifier.fillMaxSize())

        // Quality warning overlay
        if (state.qualityWarning != null) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    state.qualityWarning,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (faceDetected) stringResource(R.string.face_detected)
                else stringResource(R.string.no_face_detected),
                style = MaterialTheme.typography.bodyMedium,
                color = if (faceDetected) Color(0xFF4CAF50) else Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.captures_progress, state.count, state.target),
                style = MaterialTheme.typography.bodyLarge, color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.count.toFloat() / state.target },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Box(contentAlignment = Alignment.Center) {
                if (state.autoCapProgress > 0f) {
                    Canvas(modifier = Modifier.size(72.dp)) {
                        val stroke = 5.dp.toPx()
                        val inset  = stroke / 2f
                        drawArc(
                            color = Color(0xFF4CAF50),
                            startAngle = -90f,
                            sweepAngle = 360f * state.autoCapProgress,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                }
                Button(onClick = onCapture, enabled = faceDetected,
                    modifier = Modifier.fillMaxWidth(0.75f)) {
                    Text(stringResource(R.string.capture))
                }
            }
        }
    }
}

@Composable
private fun FaceGuideOverlay(detectedBox: RectF?, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx    = size.width / 2f
        val cy    = size.height * 0.38f
        val ovalW = size.width * 0.55f
        val ovalH = ovalW * 1.3f
        drawOval(color = Color.White.copy(alpha = 0.35f),
            topLeft = Offset(cx - ovalW / 2, cy - ovalH / 2),
            size = Size(ovalW, ovalH), style = Stroke(width = 3f))
        if (detectedBox != null) {
            drawRect(color = Color(0xFF4CAF50),
                topLeft = Offset(detectedBox.left * size.width, detectedBox.top * size.height),
                size = Size((detectedBox.right - detectedBox.left) * size.width,
                            (detectedBox.bottom - detectedBox.top) * size.height),
                style = Stroke(width = 4f))
        }
    }
}

@Composable
private fun PermissionDeniedContent(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.camera_permission_required))
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrant) { Text(stringResource(R.string.grant_permission)) }
    }
}

@Composable
private fun SuccessContent(onDone: () -> Unit, onRegisterAnother: () -> Unit, isAddMode: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.registration_success),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.done))
        }
        if (!isAddMode) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRegisterAnother, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.register_another))
            }
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}
