package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RoomPreferences
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.camera.CameraCoachPreview
import com.example.camera.FramingGuideOverlay
import com.example.camera.LiveSkeletonOverlay
import com.example.model.CameraCoachMode
import com.example.model.CoachLanguage
import com.example.model.FormStatus
import com.example.model.LightingStatus
import com.example.model.PoseSkeletonData
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
import com.example.viewmodel.JoshFitUiState

@Composable
fun RoomSetupScreen(
    uiState: JoshFitUiState,
    poseSkeletonFlow: StateFlow<PoseSkeletonData?>,
    onBackHome: () -> Unit,
    onFlipCamera: () -> Unit,
    onScanRoomNow: () -> Unit,
    onReplayVoice: () -> Unit,
    onConfirmPlacement: () -> Unit,
    onStartWorkout: () -> Unit,
    onSwitchToDemo: (String?) -> Unit,
    onPoseFrameCaptured: (Bitmap) -> Unit,
    onGeminiRoomFrame: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hinglish = uiState.streakStats.language == CoachLanguage.HINGLISH
    val guidance = uiState.roomSetupGuidance

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            val msg = if (hinglish) {
                "Camera access block hai — Demo Setup Mode chalu kiya gaya hai."
            } else {
                "Camera access was blocked — switched to Demo Setup Mode."
            }
            onSwitchToDemo(msg)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && uiState.cameraCoachMode == CameraCoachMode.LIVE_CAMERA) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Header
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
                        .testTag("room_setup_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to home"
                    )
                }

                Column {
                    Text(
                        text = if (hinglish) "STEP 1 • ROOM & CAMERA SETUP" else "STEP 1 • ROOM & CAMERA SETUP",
                        style = MaterialTheme.typography.labelMedium,
                        color = CrimsonBright
                    )
                    Text(
                        text = uiState.selectedExercise.title(uiState.streakStats.language),
                        style = MaterialTheme.typography.headlineLarge,
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onReplayVoice,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = CharcoalCard,
                    contentColor = PureWhite
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("replay_room_voice_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.VolumeUp,
                    contentDescription = "Replay voice guidance"
                )
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 720.dp

            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.05f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RoomCameraPreviewCard(
                            uiState = uiState,
                            poseSkeletonFlow = poseSkeletonFlow,
                            hasCameraPermission = hasCameraPermission,
                            hinglish = hinglish,
                            onFlipCamera = onFlipCamera,
                            onScanRoomNow = onScanRoomNow,
                            onSwitchToDemo = { onSwitchToDemo(null) },
                            onRequestCamera = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            onPoseFrameCaptured = onPoseFrameCaptured,
                            onGeminiRoomFrame = onGeminiRoomFrame,
                            onCameraUnavailable = { msg -> onSwitchToDemo(msg) }
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(0.95f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RoomGuidanceCards(
                            uiState = uiState,
                            hinglish = hinglish,
                            onConfirmPlacement = onConfirmPlacement,
                            onStartWorkout = onStartWorkout
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RoomCameraPreviewCard(
                        uiState = uiState,
                        poseSkeletonFlow = poseSkeletonFlow,
                        hasCameraPermission = hasCameraPermission,
                        hinglish = hinglish,
                        onFlipCamera = onFlipCamera,
                        onScanRoomNow = onScanRoomNow,
                        onSwitchToDemo = { onSwitchToDemo(null) },
                        onRequestCamera = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onPoseFrameCaptured = onPoseFrameCaptured,
                        onGeminiRoomFrame = onGeminiRoomFrame,
                        onCameraUnavailable = { msg -> onSwitchToDemo(msg) }
                    )
                    RoomGuidanceCards(
                        uiState = uiState,
                        hinglish = hinglish,
                        onConfirmPlacement = onConfirmPlacement,
                        onStartWorkout = onStartWorkout
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun RoomCameraPreviewCard(
    uiState: JoshFitUiState,
    poseSkeletonFlow: StateFlow<PoseSkeletonData?>,
    hasCameraPermission: Boolean,
    hinglish: Boolean,
    onFlipCamera: () -> Unit,
    onScanRoomNow: () -> Unit,
    onSwitchToDemo: () -> Unit,
    onRequestCamera: () -> Unit,
    onPoseFrameCaptured: (Bitmap) -> Unit,
    onGeminiRoomFrame: (Bitmap) -> Unit,
    onCameraUnavailable: (String) -> Unit
) {
    val isDemo = uiState.cameraCoachMode == CameraCoachMode.DEMO_MODE || !hasCameraPermission
    val guidance = uiState.roomSetupGuidance
    val bodyReady = uiState.isPoseFullBodyVisible || guidance.fullBodyVerified

    val borderColor by animateColorAsState(
        targetValue = when {
            guidance.fullBodyVerified -> GoodFormGreen
            uiState.lightingStatus == LightingStatus.TOO_DARK -> AdjustAmber
            else -> CrimsonOutlineColor(isDemo)
        },
        label = "setupBorder"
    )

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        border = BorderStroke(1.5.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(275.dp)
                    .background(CharcoalBg)
            ) {
                if (!isDemo) {
                    CameraCoachPreview(
                        useFrontCamera = uiState.useFrontCamera,
                        isAnalysisPaused = false,
                        isAiInFlight = uiState.isAiAnalyzing,
                        manualCaptureTrigger = uiState.roomScanTrigger,
                        geminiIntervalMs = 8000L,
                        isAutoGeminiPaused = uiState.isRoomAutoScanPaused,
                        poseSkeletonFlow = poseSkeletonFlow,
                        formStatus = if (bodyReady) FormStatus.GOOD_FORM else FormStatus.IDLE,
                        onPoseFrameCaptured = onPoseFrameCaptured,
                        onFrameCaptured = onGeminiRoomFrame,
                        onCameraUnavailable = onCameraUnavailable,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = uiState.selectedExercise.imageRes),
                        contentDescription = "Demo room setup guide",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CharcoalBg.copy(alpha = 0.35f))
                    )
                    FramingGuideOverlay(
                        formStatus = if (guidance.fullBodyVerified) FormStatus.GOOD_FORM else FormStatus.IDLE,
                        modifier = Modifier.fillMaxSize()
                    )
                    LiveSkeletonOverlay(
                        skeletonFlow = poseSkeletonFlow,
                        formStatus = if (guidance.fullBodyVerified) FormStatus.GOOD_FORM else FormStatus.IDLE,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Top Status Pills (Lighting + Pose Full Body indicator + Flip Camera)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LightingPill(status = uiState.lightingStatus, hinglish = hinglish)

                        Surface(
                            color = if (bodyReady) GoodFormBg.copy(alpha = 0.92f) else CharcoalBg.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(50.dp),
                            border = BorderStroke(1.dp, if (bodyReady) GoodFormGreen else CharcoalOutline)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (uiState.isAiAnalyzing || guidance.isVerifying) {
                                    CircularProgressIndicator(
                                        color = CrimsonBright,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(13.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (bodyReady) Icons.Filled.CheckCircle else Icons.Filled.Radar,
                                        contentDescription = null,
                                        tint = if (bodyReady) GoodFormGreen else RestCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = when {
                                        guidance.fullBodyVerified -> if (hinglish) "Full Body Verified" else "Full Body Verified"
                                        uiState.isPoseFullBodyVisible -> if (hinglish) "Body Frame Mein Hai" else "Body in Frame"
                                        else -> if (hinglish) "Room Scan Active" else "Room Scan Active"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (bodyReady) GoodFormGreen else PureWhite
                                )
                            }
                        }
                    }

                    if (!isDemo) {
                        IconButton(
                            onClick = onFlipCamera,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = CharcoalBg.copy(alpha = 0.8f),
                                contentColor = PureWhite
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("room_flip_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Cameraswitch,
                                contentDescription = "Switch front or back camera"
                            )
                        }
                    }
                }

                // Bottom Camera Instruction Strip
                Surface(
                    color = CharcoalBg.copy(alpha = 0.84f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    Text(
                        text = uiState.cameraUnavailableReason ?: if (hinglish) {
                            "Dheere se kamra dikhayein • Phir phone rakh ke peeche khade hon"
                        } else {
                            "Pan your room slowly • Then place phone & step back into frame"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = PureWhite,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = if (isDemo) onRequestCamera else onScanRoomNow,
                    enabled = !uiState.isAiAnalyzing,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = CharcoalElevated,
                        contentColor = PureWhite
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("scan_room_now_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = CrimsonBright,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isDemo) {
                            if (hinglish) "Camera On Karein" else "Enable Camera"
                        } else {
                            if (hinglish) "Room Scan Karein" else "Scan Room Now"
                        },
                        maxLines = 1
                    )
                }

                if (!isDemo) {
                    OutlinedButton(
                        onClick = onSwitchToDemo,
                        border = BorderStroke(1.dp, CharcoalOutline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MutedSilver),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("room_demo_fallback_button")
                    ) {
                        Text("Demo")
                    }
                }
            }
        }
    }
}

@Composable
private fun LightingPill(status: LightingStatus, hinglish: Boolean) {
    val (bg, fg, label) = when (status) {
        LightingStatus.GOOD -> Triple(
            GoodFormBg.copy(alpha = 0.9f),
            GoodFormGreen,
            if (hinglish) "Light Acchi Hai" else "Good Lighting"
        )
        LightingStatus.LOW -> Triple(
            AdjustAmberBg.copy(alpha = 0.95f),
            AdjustAmber,
            if (hinglish) "Light Thodi Kam Hai" else "Low Lighting"
        )
        LightingStatus.TOO_DARK -> Triple(
            CrimsonContainer.copy(alpha = 0.95f),
            CrimsonBright,
            if (hinglish) "Andhera Zyada • Light On Karein" else "Too Dark • Turn On Lights"
        )
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(50.dp),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = if (status == LightingStatus.GOOD) Icons.Filled.WbSunny else Icons.Filled.Warning,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = fg
            )
        }
    }
}

@Composable
private fun RoomGuidanceCards(
    uiState: JoshFitUiState,
    hinglish: Boolean,
    onConfirmPlacement: () -> Unit,
    onStartWorkout: () -> Unit
) {
    val guidance = uiState.roomSetupGuidance

    // 1. Best Workout Spot Card
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        border = BorderStroke(1.dp, CharcoalOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = CrimsonContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.RoomPreferences,
                            contentDescription = null,
                            tint = CrimsonBright,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = if (hinglish) "1. BEST WORKOUT SPOT" else "1. BEST WORKOUT SPOT",
                    style = MaterialTheme.typography.labelLarge,
                    color = CrimsonBright
                )
            }
            Text(
                text = guidance.bestWorkoutSpot.ifBlank {
                    if (hinglish) "Apne room ka clear floor area camera mein dikhayein." else "Show your clear floor area to the camera."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = PureWhite
            )
            if (guidance.lightingHint.isNotBlank()) {
                Text(
                    text = guidance.lightingHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (guidance.lightingStatus == LightingStatus.GOOD) GoodFormGreen else AdjustAmber
                )
            }
        }
    }

    // 2. Exact Camera Placement Card
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        border = BorderStroke(1.dp, CharcoalOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = CharcoalElevated,
                    shape = CircleShape,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PhoneAndroid,
                            contentDescription = null,
                            tint = RestCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = if (hinglish) "2. EXACT CAMERA PLACEMENT" else "2. EXACT CAMERA PLACEMENT",
                    style = MaterialTheme.typography.labelLarge,
                    color = RestCyan
                )
            }
            Text(
                text = guidance.exactCameraPlacement.ifBlank {
                    uiState.selectedExercise.defaultPlacementTip(uiState.streakStats.language)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = PureWhite
            )
        }
    }

    if (guidance.errorMessage != null) {
        Text(
            text = guidance.errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = AdjustAmber,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }

    // Verification Feedback Banner
    if (guidance.verificationMessage.isNotBlank()) {
        Surface(
            color = if (guidance.fullBodyVerified) GoodFormBg else CharcoalElevated,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                1.dp,
                if (guidance.fullBodyVerified) GoodFormGreen else CharcoalOutline
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (guidance.fullBodyVerified) Icons.Filled.CheckCircle else Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = if (guidance.fullBodyVerified) GoodFormGreen else CrimsonBright
                )
                Text(
                    text = guidance.verificationMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureWhite
                )
            }
        }
    }

    // Action Buttons: Confirm Placement & Check Full Body -> Unlocks Start Workout!
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = onConfirmPlacement,
            enabled = !guidance.isVerifying,
            border = BorderStroke(1.5.dp, if (guidance.fullBodyVerified) GoodFormGreen else CrimsonPrimary),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (guidance.fullBodyVerified) GoodFormGreen else PureWhite
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("confirm_placement_check_button")
        ) {
            if (guidance.isVerifying) {
                CircularProgressIndicator(
                    color = CrimsonBright,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "Confirm Placement & Check Full Body",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Button(
            onClick = onStartWorkout,
            enabled = guidance.fullBodyVerified,
            colors = ButtonDefaults.buttonColors(
                containerColor = CrimsonPrimary,
                contentColor = PureWhite,
                disabledContainerColor = CharcoalElevated,
                disabledContentColor = MutedSilver
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("start_workout_unlocked_button")
        ) {
            Icon(
                imageVector = if (guidance.fullBodyVerified) Icons.Filled.PlayArrow else Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (guidance.fullBodyVerified) {
                    if (hinglish) "Start Workout • AI Coach Ready" else "Start Workout • AI Coach Ready"
                } else {
                    if (hinglish) "Pehle Full Body Verify Karein" else "Verify Full Body to Start Workout"
                },
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

private fun CrimsonOutlineColor(isDemo: Boolean) =
    if (isDemo) CharcoalOutline else CrimsonPrimary.copy(alpha = 0.65f)
