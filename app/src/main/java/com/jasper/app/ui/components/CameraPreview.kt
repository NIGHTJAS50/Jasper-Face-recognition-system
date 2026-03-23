package com.jasper.app.ui.components

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "CameraPreview"

/**
 * Composable that hosts a CameraX [PreviewView].
 *
 * Camera is bound once per [cameraSelector] change (not on every recomposition).
 * The [imageAnalyzer] lambda is wrapped in a stable proxy so swapping it never
 * triggers a camera rebind — only [cameraSelector] changes cause a rebind.
 *
 * @param modifier            Layout modifier
 * @param cameraSelector      [CameraSelector.DEFAULT_FRONT_CAMERA] or BACK
 * @param torchEnabled        Enable/disable torch (flash). Silently ignored on front camera.
 * @param imageAnalyzer       Optional [ImageAnalysis.Analyzer] for frame processing.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA,
    torchEnabled: Boolean = false,
    imageAnalyzer: ImageAnalysis.Analyzer? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val view = LocalView.current

    // rememberUpdatedState: always holds the latest analyzer without causing re-binds
    val currentAnalyzer = rememberUpdatedState(imageAnalyzer)

    // AtomicReference so the torch LaunchedEffect can reach the bound Camera
    val cameraRef = remember { AtomicReference<Camera?>(null) }

    // Toggle torch whenever torchEnabled flips — fire-and-forget, safe to ignore failure
    LaunchedEffect(torchEnabled) {
        cameraRef.get()?.cameraControl?.enableTorch(torchEnabled)
    }

    // Keep screen on while camera composable is alive
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            analysisExecutor.shutdown()
        }
    }

    // key(cameraSelector) recreates the AndroidView only when the camera side changes.
    // Torch and analyzer updates never cause a rebind.
    key(cameraSelector) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }

                // Stable wrapper — reads currentAnalyzer.value at analysis time, not at bind time.
                // This means we never need to rebind just because the lambda reference changed.
                val analyzerProxy = ImageAnalysis.Analyzer { proxy ->
                    currentAnalyzer.value?.analyze(proxy) ?: proxy.close()
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cam = bindCamera(
                        context = ctx,
                        cameraProvider = cameraProviderFuture.get(),
                        previewView = previewView,
                        cameraSelector = cameraSelector,
                        analyzer = analyzerProxy,
                        analysisExecutor = analysisExecutor,
                        lifecycleOwner = lifecycleOwner
                    )
                    cameraRef.set(cam)
                    // Apply initial torch state after bind
                    cam?.cameraControl?.enableTorch(torchEnabled)
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            update = { /* no-op: everything is driven by factory + LaunchedEffect */ }
        )
    }
}

private fun bindCamera(
    context: Context,
    cameraProvider: ProcessCameraProvider,
    previewView: PreviewView,
    cameraSelector: CameraSelector,
    analyzer: ImageAnalysis.Analyzer,
    analysisExecutor: java.util.concurrent.ExecutorService,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner
): Camera? {
    return try {
        cameraProvider.unbindAll()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(analysisExecutor, analyzer) }

        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis
        )
    } catch (e: Exception) {
        Log.e(TAG, "CameraX bind failed", e)
        null
    }
}
