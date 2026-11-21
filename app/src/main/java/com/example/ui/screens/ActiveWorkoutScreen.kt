package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.camera.CameraCoachPreview
import com.example.camera.FramingGuideOverlay
import com.example.camera.LiveSkeletonOverlay
import com.example.model.CameraCoachMode
import com.example.model.CoachLanguage
import com.example.model.FormFeedback
import com.example.model.FormStatus
import com.example.model.LightingStatus
import com.example.model.PoseSkeletonData
import com.example.model.WorkoutPhase
import kotlinx.coroutines.flow.StateFlow
import com.example.ui.theme.AdjustAmber
import com.example.ui.theme.AdjustAmberBg
import com.example.ui.theme.CharcoalBg
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CharcoalOutline
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.GoodFormBg
import com.example.ui.theme.GoodFormGreen
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RestCyan
import com.example.ui.theme.RestCyanBg
import com.example.ui.theme.TimerMonoFontFamily
import com.example.viewmodel.JoshFitUiState

@Composable
fun ActiveWorkoutScreen(
    uiState: JoshFitUiState,
    poseSkeletonFlow: StateFlow<PoseSkeletonData?>,
    onBackHome: () -> Unit,
    onToggleStartPause: () -> Unit,
    onResetTimer: () -> Unit,
    onNextSetOrSkipRest: () -> Unit,
    onIncrementRep: () -> Unit,
    onFinishWorkout: () -> Unit,
    onStartLiveCamera: () -> Unit,
    onStartDemoMode: (String?) -> Unit,
    onStopCameraCoach: () -> Unit,
    onTogglePauseGuidance: () -> Unit,
    onFlipCamera: () -> Unit,
    onManualFormCheck: () -> Unit,
    onPoseFrameCaptured: (android.graphics.Bitmap) -> Unit,
    onFrameCaptured: (android.graphics.Bitmap) -> Unit,
    onDemoSimulateForm: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hinglish = uiState.streakStats.language == CoachLanguage.HINGLISH
    val lang = uiState.streakStats.language

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedExplicitly by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            permissionDeniedExplicitly = false
            onStartLiveCamera()
        } else {
            permissionDeniedExplicitly = true
            val reason = if (hinglish) {
                "Camera permission nahi mili — Demo AI Mode chalu hai."
            } else {
                "Camera permission denied — switched to Demo AI Mode."
            }
            onStartDemoMode(reason)
        }
    }

    // If user entered this screen with "Start Camera AI Coach" directly, request permission automatically
    LaunchedEffect(uiState.cameraCoachMode) {
        if (uiState.cameraCoachMode == CameraCoachMode.LIVE_CAMERA && !hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val requestStartCameraCoach = {
        val currentlyGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        hasCameraPermission = currentlyGranted
        if (currentlyGranted) {
            onStartLiveCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Top Sub-header with Back + Exercise Title + Finish Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBackHome,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = CharcoalCard,
                        contentColor = PureWhite
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("workout_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to workouts"
                    )
                }

                Column {
                    Text(
                        text = uiState.selectedExercise.title(lang),
                        style = MaterialTheme.typography.headlineLarge,
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Set ${uiState.currentSet} / ${uiState.totalSets} • ${uiState.selectedExercise.subtitle(lang)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSilver,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            FilledTonalButton(
                onClick = onFinishWorkout,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = CharcoalElevated,
                    contentColor = PureWhite
                ),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("finish_workout_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = null,
                    tint = CrimsonBright,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (hinglish) "Finish" else "Finish")
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 720.dp

            if (isWideScreen) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.15f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CameraCoachSection(
                            uiState = uiState,
                            poseSkeletonFlow = poseSkeletonFlow,
                            hasCameraPermission = hasCameraPermission,
                            permissionDenied = permissionDeniedExplicitly,
                            onRequestStartCamera = requestStartCameraCoach,
                            onStartDemoMode = { onStartDemoMode(null) },
                            onStopCoach = onStopCameraCoach,
                            onTogglePauseGuidance = onTogglePauseGuidance,
                            onFlipCamera = onFlipCamera,
                            onManualFormCheck = onManualFormCheck,
                            onPoseFrameCaptured = onPoseFrameCaptured,
                            onFrameCaptured = onFrameCaptured,
                            onCameraUnavailable = { msg -> onStartDemoMode(msg) },
                            onDemoSimulateForm = onDemoSimulateForm
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.85f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        WorkoutTimerCard(
                            uiState = uiState,
                            onToggleStartPause = onToggleStartPause,
                            onResetTimer = onResetTimer,
                            onNextSetOrSkipRest = onNextSetOrSkipRest,
                            onIncrementRep = onIncrementRep
                        )
                        FormCheckpointsCard(uiState = uiState)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CameraCoachSection(
                        uiState = uiState,
                        poseSkeletonFlow = poseSkeletonFlow,
                        hasCameraPermission = hasCameraPermission,
                        permissionDenied = permissionDeniedExplicitly,
                        onRequestStartCamera = requestStartCameraCoach,
                        onStartDemoMode = { onStartDemoMode(null) },
                        onStopCoach = onStopCameraCoach,
                        onTogglePauseGuidance = onTogglePauseGuidance,
                        onFlipCamera = onFlipCamera,
                        onManualFormCheck = onManualFormCheck,
                        onPoseFrameCaptured = onPoseFrameCaptured,
                        onFrameCaptured = onFrameCaptured,
                        onCameraUnavailable = { msg -> onStartDemoMode(msg) },
                        onDemoSimulateForm = onDemoSimulateForm
                    )

                    WorkoutTimerCard(
                        uiState = uiState,
                        onToggleStartPause = onToggleStartPause,
                        onResetTimer = onResetTimer,
                        onNextSetOrSkipRest = onNextSetOrSkipRest,
                        onIncrementRep = onIncrementRep
                    )

                    FormCheckpointsCard(uiState = uiState)

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CameraCoachSection(
    uiState: JoshFitUiState,
    poseSkeletonFlow: StateFlow<PoseSkeletonData?>,
    hasCameraPermission: Boolean,
    permissionDenied: Boolean,
    onRequestStartCamera: () -> Unit,
    onStartDemoMode: () -> Unit,
    onStopCoach: () -> Unit,
    onTogglePauseGuidance: () -> Unit,
    onFlipCamera: () -> Unit,
    onManualFormCheck: () -> Unit,
    onPoseFrameCaptured: (android.graphics.Bitmap) -> Unit,
    onFrameCaptured: (android.graphics.Bitmap) -> Unit,
    onCameraUnavailable: (String) -> Unit,
    onDemoSimulateForm: (Boolean) -> Unit
) {
    val hinglish = uiState.streakStats.language == CoachLanguage.HINGLISH
    val lang = uiState.streakStats.language

    when (uiState.cameraCoachMode) {
        CameraCoachMode.OFF -> {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                border = BorderStroke(1.dp, CrimsonPrimary.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = CrimsonContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Videocam,
                                    contentDescription = null,
                                    tint = CrimsonBright
                                )
                            }
                        }
                        Column {
                            Text(
                                text = if (hinglish) "Live Camera AI Form Coach" else "Live Camera AI Form Coach",
                                style = MaterialTheme.typography.titleLarge,
                                color = PureWhite
                            )
                            Text(
                                text = if (hinglish) {
                                    "Real-time posture check + Hinglish voice tips"
                                } else {
                                    "Real-time posture feedback + voice cues"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSilver
                            )
                        }
                    }

                    Text(
                        text = uiState.selectedExercise.framingGuide(lang),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PureWhite.copy(alpha = 0.9f)
                    )

                    if (permissionDenied) {
                        Text(
                            text = if (hinglish) {
                                "Camera permission denied hai. Demo Mode try karein ya permission allow karein."
                            } else {
                                "Camera permission was denied. Try Demo Mode or grant camera access."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = AdjustAmber
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onRequestStartCamera,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrimsonPrimary,
                                contentColor = PureWhite
                            ),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp)
                                .testTag("start_camera_ai_coach_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Start Camera AI Coach",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = onStartDemoMode,
                            border = BorderStroke(1.dp, CharcoalOutline),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                            modifier = Modifier
                                .weight(0.9f)
                                .height(48.dp)
                                .testTag("start_demo_mode_button")
                        ) {
                            Text(
                                text = if (hinglish) "Demo Mode" else "Demo Mode",
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        CameraCoachMode.LIVE_CAMERA, CameraCoachMode.DEMO_MODE -> {
            val isDemo = uiState.cameraCoachMode == CameraCoachMode.DEMO_MODE
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                border = BorderStroke(
                    width = 1.5.dp,
                    color = when (uiState.formFeedback.status) {
                        FormStatus.GOOD_FORM -> GoodFormGreen
                        FormStatus.ADJUST_POSTURE -> AdjustAmber
                        FormStatus.ERROR -> CrimsonPrimary
                        else -> CharcoalOutline
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Camera or Demo Visual Stage
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(CharcoalBg)
                    ) {
                        if (!isDemo && hasCameraPermission) {
                            CameraCoachPreview(
                                useFrontCamera = uiState.useFrontCamera,
                                isAnalysisPaused = uiState.isCameraAnalysisPaused || !uiState.isTimerRunning,
                                isAiInFlight = uiState.isAiAnalyzing,
                                manualCaptureTrigger = uiState.manualCaptureTrigger,
                                geminiIntervalMs = 6000L,
                                poseSkeletonFlow = poseSkeletonFlow,
                                formStatus = uiState.formFeedback.status,
                                onPoseFrameCaptured = onPoseFrameCaptured,
                                onFrameCaptured = onFrameCaptured,
                                onCameraUnavailable = onCameraUnavailable,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            DemoCoachStage(
                                imageRes = uiState.selectedExercise.imageRes,
                                poseSkeletonFlow = poseSkeletonFlow,
                                formStatus = uiState.formFeedback.status,
                                isPaused = uiState.isCameraAnalysisPaused,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Top Overlay: Form Status Badge + AI Progress + Close Coach
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                FormStatusBadge(
                                    feedback = uiState.formFeedback,
                                    isAiAnalyzing = uiState.isAiAnalyzing,
                                    isPaused = uiState.isCameraAnalysisPaused
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val angleLabel = uiState.liveAngleLabel
                                    if (angleLabel.isNotBlank()) {
                                        Surface(
                                            color = CharcoalBg.copy(alpha = 0.85f),
                                            shape = RoundedCornerShape(50.dp),
                                            border = BorderStroke(1.dp, CharcoalOutline)
                                        ) {
                                            Text(
                                                text = angleLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = PureWhite,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    if (!isDemo && uiState.lightingStatus != LightingStatus.GOOD) {
                                        Surface(
                                            color = AdjustAmberBg.copy(alpha = 0.92f),
                                            shape = RoundedCornerShape(50.dp),
                                            border = BorderStroke(1.dp, AdjustAmber)
                                        ) {
                                            Text(
                                                text = if (hinglish) "Light Kam Hai" else "Low Light",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = AdjustAmber,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (!isDemo) {
                                    IconButton(
                                        onClick = onFlipCamera,
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = CharcoalBg.copy(alpha = 0.78f),
                                            contentColor = PureWhite
                                        ),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("flip_camera_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Cameraswitch,
                                            contentDescription = "Flip Camera"
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onStopCoach,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = CharcoalBg.copy(alpha = 0.78f),
                                        contentColor = PureWhite
                                    ),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("close_camera_coach_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Close AI Coach"
                                    )
                                }
                            }
                        }

                        // Bottom Framing Guide Banner inside Camera Stage
                        Surface(
                            color = CharcoalBg.copy(alpha = 0.82f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(10.dp)
                        ) {
                            Text(
                                text = uiState.cameraUnavailableReason
                                    ?: uiState.selectedExercise.framingGuide(lang),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PureWhite,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Live Form Cue & Tip Panel
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LiveFeedbackBanner(
                            feedback = uiState.formFeedback,
                            hinglish = hinglish,
                            isTimerRunning = uiState.isTimerRunning,
                            isDemo = isDemo
                        )

                        // Quick Coach Action Row (Pause/Resume AI, Check Now, Switch Live/Demo)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onTogglePauseGuidance,
                                border = BorderStroke(1.dp, CharcoalOutline),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("pause_resume_ai_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isCameraAnalysisPaused) {
                                        Icons.Filled.PlayArrow
                                    } else {
                                        Icons.Filled.Pause
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (uiState.isCameraAnalysisPaused) {
                                        if (hinglish) "Resume AI" else "Resume AI"
                                    } else {
                                        if (hinglish) "Pause AI" else "Pause AI"
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = onManualFormCheck,
                                enabled = !uiState.isAiAnalyzing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CrimsonPrimary,
                                    contentColor = PureWhite
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(48.dp)
                                    .testTag("check_form_now_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isDemo) "Gemini Check" else "Check Form",
                                    maxLines = 1
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    if (isDemo) onRequestStartCamera() else onStartDemoMode()
                                },
                                border = BorderStroke(1.dp, CharcoalOutline),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MutedSilver),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier
                                    .weight(0.85f)
                                    .height(48.dp)
                                    .testTag("switch_coach_mode_button")
                            ) {
                                Icon(
                                    imageVector = if (isDemo) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isDemo) "Live" else "Demo",
                                    maxLines = 1
                                )
                            }
                        }

                        if (isDemo) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { onDemoSimulateForm(true) },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = GoodFormBg,
                                        contentColor = GoodFormGreen
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("demo_good_form_button")
                                ) {
                                    Text(
                                        text = if (hinglish) "Demo: Good Form" else "Test Good Form",
                                        maxLines = 1
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { onDemoSimulateForm(false) },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = AdjustAmberBg,
                                        contentColor = AdjustAmber
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("demo_adjust_posture_button")
                                ) {
                                    Text(
                                        text = if (hinglish) "Demo: Fix Posture" else "Test Fix Posture",
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoCoachStage(
    imageRes: Int,
    poseSkeletonFlow: StateFlow<PoseSkeletonData?>,
    formStatus: FormStatus,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLine"
    )

    Box(modifier = modifier) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = "Demo posture guide",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CharcoalBg.copy(alpha = 0.25f))
        )
        FramingGuideOverlay(
            formStatus = formStatus,
            modifier = Modifier.fillMaxSize()
        )
        LiveSkeletonOverlay(
            skeletonFlow = poseSkeletonFlow,
            formStatus = formStatus,
            modifier = Modifier.fillMaxSize()
        )
        if (!isPaused) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val y = size.height * scanProgress
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, CrimsonBright, Color.Transparent)
                    ),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }
    }
}

@Composable
private fun FormStatusBadge(
    feedback: FormFeedback,
    isAiAnalyzing: Boolean,
    isPaused: Boolean
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isPaused -> CharcoalElevated.copy(alpha = 0.92f)
            feedback.status == FormStatus.GOOD_FORM -> GoodFormBg.copy(alpha = 0.95f)
            feedback.status == FormStatus.ADJUST_POSTURE -> AdjustAmberBg.copy(alpha = 0.95f)
            feedback.status == FormStatus.ERROR -> CrimsonContainer.copy(alpha = 0.95f)
            else -> CharcoalBg.copy(alpha = 0.90f)
        },
        label = "badgeBg"
    )

    val contentColor = when {
        isPaused -> MutedSilver
        feedback.status == FormStatus.GOOD_FORM -> GoodFormGreen
        feedback.status == FormStatus.ADJUST_POSTURE -> AdjustAmber
        feedback.status == FormStatus.ERROR -> CrimsonBright
        else -> PureWhite
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(50.dp),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.65f)),
        modifier = Modifier.testTag("form_status_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isAiAnalyzing) {
                CircularProgressIndicator(
                    color = CrimsonBright,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Icon(
                    imageVector = when (feedback.status) {
                        FormStatus.GOOD_FORM -> Icons.Filled.CheckCircle
                        FormStatus.ADJUST_POSTURE, FormStatus.ERROR -> Icons.Filled.Warning
                        else -> Icons.Filled.AutoAwesome
                    },
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = if (isPaused) "AI Paused" else feedback.badgeText,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
        }
    }
}

@Composable
private fun LiveFeedbackBanner(
    feedback: FormFeedback,
    hinglish: Boolean,
    isTimerRunning: Boolean,
    isDemo: Boolean
) {
    val cue = feedback.cueText.ifBlank {
        if (!isTimerRunning) {
            if (hinglish) "Timer Start dabao — AI Coach posture track karega!" else "Hit Start Timer to begin live posture tracking!"
        } else {
            if (hinglish) "Position hold karo, AI check kar raha hai..." else "Hold steady, AI Coach is analyzing..."
        }
    }

    Surface(
        color = CharcoalElevated,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isDemo) "AI FORM CUE (DEMO)" else "GEMINI LIVE FORM CUE",
                    style = MaterialTheme.typography.labelMedium,
                    color = CrimsonBright
                )
                if (feedback.repDetected) {
                    Text(
                        text = "+1 REP",
                        style = MaterialTheme.typography.labelMedium,
                        color = GoodFormGreen
                    )
                }
            }
            Text(
                text = cue,
                style = MaterialTheme.typography.titleMedium,
                color = PureWhite
            )
            if (feedback.postureTip.isNotBlank()) {
                Text(
                    text = feedback.postureTip,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSilver
                )
            }
        }
    }
}

@Composable
private fun WorkoutTimerCard(
    uiState: JoshFitUiState,
    onToggleStartPause: () -> Unit,
    onResetTimer: () -> Unit,
    onNextSetOrSkipRest: () -> Unit,
    onIncrementRep: () -> Unit
) {
    val hinglish = uiState.streakStats.language == CoachLanguage.HINGLISH
    val isRest = uiState.phase == WorkoutPhase.REST
    val maxSec = if (isRest) uiState.restDurationSec else uiState.workDurationSec
    val progress = if (maxSec > 0) uiState.remainingSeconds.toFloat() / maxSec.toFloat() else 0f

    val ringColor by animateColorAsState(
        targetValue = if (isRest) RestCyan else CrimsonPrimary,
        label = "timerRing"
    )
    val phaseLabel = when (uiState.phase) {
        WorkoutPhase.READY -> if (hinglish) "TAIYAAR • SET ${uiState.currentSet}/${uiState.totalSets}" else "READY • SET ${uiState.currentSet}/${uiState.totalSets}"
        WorkoutPhase.ACTIVE_WORK -> if (hinglish) "WORKOUT • SET ${uiState.currentSet}/${uiState.totalSets}" else "ACTIVE • SET ${uiState.currentSet}/${uiState.totalSets}"
        WorkoutPhase.REST -> if (hinglish) "REST TIME • PAANI PEELO" else "REST • RECOVER"
        WorkoutPhase.COMPLETED -> if (hinglish) "COMPLETE!" else "COMPLETE!"
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        border = BorderStroke(1.dp, if (isRest) RestCyan.copy(alpha = 0.45f) else CharcoalOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = if (isRest) RestCyanBg else CrimsonContainer,
                shape = RoundedCornerShape(50.dp)
            ) {
                Text(
                    text = phaseLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isRest) RestCyan else PureWhite,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            // Big Circular Progress + Readable Timer
            Box(
                modifier = Modifier.size(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 12.dp.toPx()
                    drawArc(
                        color = CharcoalElevated,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val mins = uiState.remainingSeconds / 60
                    val secs = uiState.remainingSeconds % 60
                    Text(
                        text = String.format("%02d:%02d", mins, secs),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontFamily = TimerMonoFontFamily
                        ),
                        color = PureWhite,
                        modifier = Modifier.testTag("workout_timer_text")
                    )
                    Text(
                        text = if (uiState.selectedExercise.isHoldExercise) {
                            if (hinglish) "Hold Timer" else "Hold Timer"
                        } else {
                            "${uiState.repsCompleted} Reps"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MutedSilver
                    )
                }
            }

            // Rep Quick Counter for non-hold exercises
            if (!uiState.selectedExercise.isHoldExercise) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hinglish) {
                            "Target: ${uiState.selectedExercise.targetRepsPerSet} reps / set"
                        } else {
                            "Target: ${uiState.selectedExercise.targetRepsPerSet} reps / set"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSilver
                    )
                    FilledTonalButton(
                        onClick = onIncrementRep,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CharcoalElevated,
                            contentColor = PureWhite
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("add_rep_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("1 Rep")
                    }
                }
            }

            // Primary Timer Controls (Start/Pause, Reset, Next Set / Skip Rest)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onResetTimer,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = CharcoalElevated,
                        contentColor = PureWhite
                    ),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("reset_timer_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Reset timer"
                    )
                }

                Button(
                    onClick = onToggleStartPause,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isTimerRunning) CharcoalElevated else CrimsonPrimary,
                        contentColor = PureWhite
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("start_pause_timer_button")
                ) {
                    Icon(
                        imageVector = if (uiState.isTimerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            uiState.isTimerRunning -> if (hinglish) "Pause" else "Pause"
                            uiState.phase == WorkoutPhase.READY -> if (hinglish) "Start" else "Start"
                            else -> if (hinglish) "Resume" else "Resume"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                IconButton(
                    onClick = onNextSetOrSkipRest,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = CharcoalElevated,
                        contentColor = PureWhite
                    ),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("next_set_skip_rest_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Complete set or skip rest"
                    )
                }
            }
        }
    }
}

@Composable
private fun FormCheckpointsCard(uiState: JoshFitUiState) {
    val lang = uiState.streakStats.language
    val hinglish = lang == CoachLanguage.HINGLISH

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        border = BorderStroke(1.dp, CharcoalOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (hinglish) "SAHI FORM CHECKLIST" else "FORM CHECKPOINTS",
                style = MaterialTheme.typography.labelLarge,
                color = CrimsonBright
            )
            uiState.selectedExercise.checkpoints(lang).forEachIndexed { idx, tip ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = CrimsonContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${idx + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = PureWhite
                            )
                        }
                    }
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PureWhite.copy(alpha = 0.92f)
                    )
                }
            }
        }
    }
}
