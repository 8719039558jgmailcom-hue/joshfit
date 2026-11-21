package com.example.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.FormStatus
import com.example.model.PoseSkeletonData
import com.example.ui.theme.AdjustAmber
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.GoodFormGreen
import com.example.ui.theme.PureWhite
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "CameraCoachPreview"
private const val POSE_FRAME_INTERVAL_MS = 85L // ~12 FPS throttle to keep CPU/GPU smooth

@Composable
fun CameraCoachPreview(
    useFrontCamera: Boolean,
    isAnalysisPaused: Boolean,
    isAiInFlight: Boolean,
    manualCaptureTrigger: Int,
    geminiIntervalMs: Long = 5000L,
    isAutoGeminiPaused: Boolean = false,
    poseSkeletonFlow: StateFlow<PoseSkeletonData?>? = null,
    formStatus: FormStatus = FormStatus.IDLE,
    onPoseFrameCaptured: (Bitmap) -> Unit = {},
    onFrameCaptured: (Bitmap) -> Unit,
    onCameraUnavailable: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentOnPoseFrame by rememberUpdatedState(onPoseFrameCaptured)
    val currentOnFrame by rememberUpdatedState(onFrameCaptured)
    val currentOnUnavailable by rememberUpdatedState(onCameraUnavailable)
    val currentPaused by rememberUpdatedState(isAnalysisPaused)
    val currentAutoGeminiPaused by rememberUpdatedState(isAutoGeminiPaused)
    val currentInFlight by rememberUpdatedState(isAiInFlight)
    val currentIntervalMs by rememberUpdatedState(geminiIntervalMs)

    // Brief 1.2s camera warmup before first auto Gemini scan so exposure settles
    val lastGeminiAnalyzedAtMs = remember {
        AtomicLong(System.currentTimeMillis() - maxOf(0L, geminiIntervalMs - 1200L))
    }
    val lastPoseAnalyzedAtMs = remember { AtomicLong(0L) }
    val isPoseBusy = remember { AtomicBoolean(false) }
    val pendingManualCapture = remember { mutableStateOf(false) }

    // Ensure cooldown interval is measured AFTER an AI request finishes, never back-to-back
    LaunchedEffect(isAiInFlight) {
        if (!isAiInFlight) {
            lastGeminiAnalyzedAtMs.set(System.currentTimeMillis())
        }
    }

    LaunchedEffect(manualCaptureTrigger) {
        if (manualCaptureTrigger > 0) {
            pendingManualCapture.value = true
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val executor = remember { Executors.newSingleThreadExecutor() }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProvider?.unbindAll()
            } catch (_: Exception) {
            }
            executor.shutdownNow()
        }
    }

    DisposableEffect(lifecycleOwner, useFrontCamera) {
        val future = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider

                val desiredSelector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                val selector = when {
                    provider.hasCamera(desiredSelector) -> desiredSelector
                    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> {
                        currentOnUnavailable("No camera hardware detected on this device.")
                        return@addListener
                    }
                }

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                // Lightweight 480x360 analysis resolution to prevent CPU/GPU lag
                val analysisResolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(480, 360),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                        )
                    )
                    .build()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(analysisResolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    try {
                        val now = System.currentTimeMillis()
                        val manual = pendingManualCapture.value
                        val geminiElapsed = (now - lastGeminiAnalyzedAtMs.get()) >= currentIntervalMs
                        val poseElapsed = (now - lastPoseAnalyzedAtMs.get()) >= POSE_FRAME_INTERVAL_MS

                        val needGemini = (!currentPaused && !currentAutoGeminiPaused && geminiElapsed && !currentInFlight) ||
                            (manual && !currentInFlight)
                        val needPose = !currentPaused && poseElapsed && !isPoseBusy.get()

                        if (needGemini || needPose) {
                            val upright = imageProxy.toUprightBitmap(
                                mirrorHorizontally = useFrontCamera,
                                maxDim = if (needGemini) 384 else 360
                            )
                            if (upright != null) {
                                if (needGemini) {
                                    pendingManualCapture.value = false
                                    lastGeminiAnalyzedAtMs.set(now)
                                    val geminiBmp = if (needPose) {
                                        upright.copy(Bitmap.Config.ARGB_8888, false)
                                    } else {
                                        upright
                                    }
                                    currentOnFrame(geminiBmp)
                                }
                                if (needPose) {
                                    lastPoseAnalyzedAtMs.set(now)
                                    isPoseBusy.set(true)
                                    try {
                                        currentOnPoseFrame(upright)
                                    } finally {
                                        isPoseBusy.set(false)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Frame capture conversion skipped", e)
                    } finally {
                        imageProxy.close()
                    }
                }

                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
            } catch (e: Exception) {
                Log.e(TAG, "CameraX binding failed", e)
                currentOnUnavailable(e.localizedMessage ?: "Camera unavailable.")
            }
        }, mainExecutor)

        onDispose {
            try {
                cameraProvider?.unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        FramingGuideOverlay(
            formStatus = formStatus,
            modifier = Modifier.fillMaxSize()
        )
        if (poseSkeletonFlow != null) {
            LiveSkeletonOverlay(
                skeletonFlow = poseSkeletonFlow,
                formStatus = formStatus,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun LiveSkeletonOverlay(
    skeletonFlow: StateFlow<PoseSkeletonData?>,
    formStatus: FormStatus,
    modifier: Modifier = Modifier
) {
    val skeleton by skeletonFlow.collectAsStateWithLifecycle()
    SkeletonOverlay(
        skeleton = skeleton,
        formStatus = formStatus,
        modifier = modifier
    )
}

@Composable
fun SkeletonOverlay(
    skeleton: PoseSkeletonData?,
    formStatus: FormStatus,
    modifier: Modifier = Modifier
) {
    if (skeleton == null || skeleton.landmarks.isEmpty()) return

    val boneColor = when (formStatus) {
        FormStatus.GOOD_FORM -> GoodFormGreen
        FormStatus.ADJUST_POSTURE -> AdjustAmber
        FormStatus.ERROR -> CrimsonBright
        else -> Color(0xFF38BDF8)
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokePx = 3.5.dp.toPx()

        for ((startId, endId) in skeleton.connections) {
            val a = skeleton.landmarks[startId]
            val b = skeleton.landmarks[endId]
            if (a != null && b != null && a.likelihood >= 0.4f && b.likelihood >= 0.4f) {
                drawLine(
                    color = boneColor.copy(alpha = 0.9f),
                    start = Offset(a.x * w, a.y * h),
                    end = Offset(b.x * w, b.y * h),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }
        }

        skeleton.landmarks.values.forEach { pt ->
            if (pt.likelihood >= 0.4f) {
                val center = Offset(pt.x * w, pt.y * h)
                drawCircle(
                    color = boneColor,
                    radius = 6.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = PureWhite,
                    radius = 3.dp.toPx(),
                    center = center
                )
            }
        }
    }
}

@Composable
fun FramingGuideOverlay(
    formStatus: FormStatus,
    modifier: Modifier = Modifier
) {
    val reticleColor = when (formStatus) {
        FormStatus.GOOD_FORM -> GoodFormGreen
        FormStatus.ADJUST_POSTURE -> AdjustAmber
        FormStatus.ERROR -> CrimsonPrimary
        else -> Color.White.copy(alpha = 0.78f)
    }

    Canvas(modifier = modifier) {
        val marginX = size.width * 0.08f
        val marginY = size.height * 0.12f
        val cornerLen = minOf(size.width, size.height) * 0.11f
        val strokePx = 3.dp.toPx()

        drawLine(reticleColor, Offset(marginX, marginY), Offset(marginX + cornerLen, marginY), strokePx, StrokeCap.Round)
        drawLine(reticleColor, Offset(marginX, marginY), Offset(marginX, marginY + cornerLen), strokePx, StrokeCap.Round)

        drawLine(reticleColor, Offset(size.width - marginX, marginY), Offset(size.width - marginX - cornerLen, marginY), strokePx, StrokeCap.Round)
        drawLine(reticleColor, Offset(size.width - marginX, marginY), Offset(size.width - marginX, marginY + cornerLen), strokePx, StrokeCap.Round)

        drawLine(reticleColor, Offset(marginX, size.height - marginY), Offset(marginX + cornerLen, size.height - marginY), strokePx, StrokeCap.Round)
        drawLine(reticleColor, Offset(marginX, size.height - marginY), Offset(marginX, size.height - marginY - cornerLen), strokePx, StrokeCap.Round)

        drawLine(reticleColor, Offset(size.width - marginX, size.height - marginY), Offset(size.width - marginX - cornerLen, size.height - marginY), strokePx, StrokeCap.Round)
        drawLine(reticleColor, Offset(size.width - marginX, size.height - marginY), Offset(size.width - marginX, size.height - marginY - cornerLen), strokePx, StrokeCap.Round)

        drawLine(
            color = reticleColor.copy(alpha = 0.25f),
            start = Offset(marginX, size.height * 0.5f),
            end = Offset(size.width - marginX, size.height * 0.5f),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
        )
    }
}

private fun ImageProxy.toUprightBitmap(mirrorHorizontally: Boolean, maxDim: Int): Bitmap? {
    val raw = toBitmap()
    val rotation = imageInfo.rotationDegrees.toFloat()
    val matrix = Matrix().apply {
        if (rotation != 0f) postRotate(rotation)
        if (mirrorHorizontally) postScale(-1f, 1f)
    }
    val rotated = if (rotation != 0f || mirrorHorizontally) {
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    } else {
        raw
    }
    if (rotated !== raw) raw.recycle()

    if (rotated.width <= maxDim && rotated.height <= maxDim) return rotated
    val ratio = maxDim.toFloat() / maxOf(rotated.width, rotated.height).toFloat()
    val w = maxOf(1, (rotated.width * ratio).toInt())
    val h = maxOf(1, (rotated.height * ratio).toInt())
    val scaled = Bitmap.createScaledBitmap(rotated, w, h, true)
    rotated.recycle()
    return scaled
}
