package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VoiceOverOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.model.CameraCoachMode
import com.example.model.CoachLanguage
import com.example.ui.theme.CharcoalBg
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CharcoalOutline
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.PureWhite
import com.example.viewmodel.AppDestination
import com.example.viewmodel.WorkoutViewModel

@Composable
fun JoshFitApp(
    viewModel: WorkoutViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = uiState.destination != AppDestination.HOME) {
        viewModel.navigateHome()
    }

    val showBottomBar = uiState.destination == AppDestination.HOME ||
        uiState.destination == AppDestination.CHALLENGE

    Scaffold(
        containerColor = CharcoalBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            JoshFitTopBar(
                streakDays = uiState.streakStats.streakDays,
                beatEnabled = uiState.streakStats.beatEnabled,
                voiceEnabled = uiState.streakStats.voiceEnabled,
                language = uiState.streakStats.language,
                onToggleBeat = viewModel::toggleBeat,
                onToggleVoice = viewModel::toggleVoice,
                onToggleLanguage = viewModel::toggleLanguage,
                onOpenPolicy = { viewModel.setPolicyDialogVisible(true) }
            )
        },
        bottomBar = {
            if (showBottomBar) {
                JoshFitBottomBar(
                    currentDestination = uiState.destination,
                    hinglish = uiState.streakStats.language == CoachLanguage.HINGLISH,
                    onSelectTab = viewModel::selectMainTab
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.destination) {
                AppDestination.HOME -> {
                    HomeSelectionScreen(
                        uiState = uiState,
                        onSelectExercise = viewModel::selectExercise,
                        onScanRoomSetup = viewModel::openRoomSetup,
                        onStartWorkout = { exercise, launchCam ->
                            viewModel.openWorkout(
                                exercise = exercise,
                                launchCameraCoach = launchCam
                            )
                        },
                        onAdjustSets = viewModel::adjustSets,
                        onAdjustWorkSec = viewModel::adjustWorkDuration,
                        onAdjustRestSec = viewModel::adjustRestDuration
                    )
                }

                AppDestination.CHALLENGE -> {
                    ChallengeScreen(
                        uiState = uiState,
                        onSelectDay = viewModel::selectChallengeDay,
                        onStartDayWithCameraSetup = { day ->
                            viewModel.startChallengeDay(day, useRoomSetup = true)
                        },
                        onStartDayTimerOnly = { day ->
                            viewModel.startChallengeDay(day, useRoomSetup = false)
                        },
                        onMarkRestCompleted = viewModel::completeRestChallengeDay
                    )
                }

                AppDestination.ROOM_SETUP -> {
                    RoomSetupScreen(
                        uiState = uiState,
                        poseSkeletonFlow = viewModel.poseSkeletonFlow,
                        onBackHome = viewModel::navigateHome,
                        onFlipCamera = viewModel::flipCamera,
                        onScanRoomNow = viewModel::triggerRoomScanNow,
                        onReplayVoice = viewModel::replayRoomGuidanceVoice,
                        onConfirmPlacement = viewModel::confirmPlacementAndCheckFullBody,
                        onStartWorkout = {
                            val isDemo = uiState.cameraCoachMode == CameraCoachMode.DEMO_MODE
                            viewModel.openWorkout(
                                exercise = uiState.selectedExercise,
                                launchCameraCoach = !isDemo,
                                useDemoMode = isDemo,
                                autoStartTimer = true
                            )
                        },
                        onSwitchToDemo = viewModel::switchRoomSetupToDemo,
                        onPoseFrameCaptured = viewModel::onPoseFrameCaptured,
                        onGeminiRoomFrame = viewModel::onRoomSetupGeminiFrame
                    )
                }

                AppDestination.WORKOUT -> {
                    ActiveWorkoutScreen(
                        uiState = uiState,
                        poseSkeletonFlow = viewModel.poseSkeletonFlow,
                        onBackHome = viewModel::navigateHome,
                        onToggleStartPause = viewModel::toggleStartPauseTimer,
                        onResetTimer = viewModel::resetWorkoutTimer,
                        onNextSetOrSkipRest = viewModel::completeSetEarlyOrSkipRest,
                        onIncrementRep = viewModel::incrementManualRep,
                        onFinishWorkout = viewModel::finishWorkoutNow,
                        onStartLiveCamera = viewModel::startLiveCameraCoach,
                        onStartDemoMode = viewModel::startDemoCoachMode,
                        onStopCameraCoach = viewModel::stopCameraCoach,
                        onTogglePauseGuidance = viewModel::togglePauseCameraGuidance,
                        onFlipCamera = viewModel::flipCamera,
                        onManualFormCheck = viewModel::triggerManualFormCheck,
                        onPoseFrameCaptured = viewModel::onPoseFrameCaptured,
                        onFrameCaptured = viewModel::onLiveCameraFrameCaptured,
                        onDemoSimulateForm = viewModel::triggerDemoPostureSimulation
                    )
                }

                AppDestination.SUMMARY -> {
                    val summary = uiState.sessionSummary
                    if (summary != null) {
                        SessionSummaryScreen(
                            summary = summary,
                            language = uiState.streakStats.language,
                            onRestartWorkout = {
                                viewModel.openWorkout(summary.exercise, launchCameraCoach = false)
                            },
                            onBackToHome = viewModel::navigateHome,
                            onOpenChallenge = if (summary.exercise.type == com.example.model.ExerciseType.PUSH_UPS) {
                                { viewModel.selectMainTab(AppDestination.CHALLENGE) }
                            } else null
                        )
                    }
                }
            }
        }

        if (uiState.showPolicyDialog) {
            PrivacyAndSafetyDialog(
                hinglish = uiState.streakStats.language == CoachLanguage.HINGLISH,
                onDismiss = { viewModel.setPolicyDialogVisible(false) }
            )
        }
    }
}

@Composable
private fun JoshFitBottomBar(
    currentDestination: AppDestination,
    hinglish: Boolean,
    onSelectTab: (AppDestination) -> Unit
) {
    NavigationBar(
        containerColor = CharcoalSurface,
        contentColor = PureWhite,
        windowInsets = WindowInsets.navigationBars
    ) {
        NavigationBarItem(
            selected = currentDestination == AppDestination.HOME,
            onClick = { onSelectTab(AppDestination.HOME) },
            icon = {
                Icon(
                    imageVector = Icons.Filled.FitnessCenter,
                    contentDescription = "Workouts"
                )
            },
            label = {
                Text(if (hinglish) "Workouts" else "Workouts")
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PureWhite,
                selectedTextColor = PureWhite,
                indicatorColor = CrimsonPrimary,
                unselectedIconColor = MutedSilver,
                unselectedTextColor = MutedSilver
            ),
            modifier = Modifier.testTag("tab_workouts")
        )

        NavigationBarItem(
            selected = currentDestination == AppDestination.CHALLENGE,
            onClick = { onSelectTab(AppDestination.CHALLENGE) },
            icon = {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = "30-Day Challenge"
                )
            },
            label = {
                Text(if (hinglish) "30-Day Challenge" else "30-Day Challenge")
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PureWhite,
                selectedTextColor = PureWhite,
                indicatorColor = CrimsonPrimary,
                unselectedIconColor = MutedSilver,
                unselectedTextColor = MutedSilver
            ),
            modifier = Modifier.testTag("tab_challenge_30d")
        )
    }
}

@Composable
private fun JoshFitTopBar(
    streakDays: Int,
    beatEnabled: Boolean,
    voiceEnabled: Boolean,
    language: CoachLanguage,
    onToggleBeat: () -> Unit,
    onToggleVoice: () -> Unit,
    onToggleLanguage: () -> Unit,
    onOpenPolicy: () -> Unit
) {
    Surface(
        color = CharcoalBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_joshfit_logo),
                    contentDescription = "JoshFit logo",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                )
                Text(
                    text = "JOSHFIT",
                    style = MaterialTheme.typography.headlineLarge,
                    color = PureWhite
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    color = CharcoalCard,
                    shape = RoundedCornerShape(50.dp),
                    border = BorderStroke(1.dp, FlameOrange.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalFireDepartment,
                            contentDescription = "Workout Streak",
                            tint = FlameOrange,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "$streakDays",
                            style = MaterialTheme.typography.labelLarge,
                            color = PureWhite
                        )
                    }
                }

                IconButton(
                    onClick = onToggleBeat,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (beatEnabled) CrimsonContainer else CharcoalCard,
                        contentColor = if (beatEnabled) CrimsonBright else MutedSilver
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("toggle_beat_button")
                ) {
                    Icon(
                        imageVector = if (beatEnabled) Icons.Filled.MusicNote else Icons.Filled.MusicOff,
                        contentDescription = if (beatEnabled) "Mute workout beat" else "Enable workout beat",
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(
                    onClick = onToggleVoice,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (voiceEnabled) CharcoalElevated else CharcoalCard,
                        contentColor = if (voiceEnabled) PureWhite else MutedSilver
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("toggle_voice_button")
                ) {
                    Icon(
                        imageVector = if (voiceEnabled) Icons.Filled.RecordVoiceOver else Icons.Filled.VoiceOverOff,
                        contentDescription = if (voiceEnabled) "Mute voice cues" else "Enable voice cues",
                        modifier = Modifier.size(19.dp)
                    )
                }

                Surface(
                    color = CharcoalCard,
                    shape = RoundedCornerShape(50.dp),
                    border = BorderStroke(1.dp, CharcoalOutline),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .clickable(onClick = onToggleLanguage)
                        .testTag("toggle_language_button")
                ) {
                    Text(
                        text = if (language == CoachLanguage.HINGLISH) "हिंग्लिश" else "EN",
                        style = MaterialTheme.typography.labelMedium,
                        color = PureWhite,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                    )
                }

                IconButton(
                    onClick = onOpenPolicy,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = CharcoalCard,
                        contentColor = MutedSilver
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("policy_disclaimer_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = "Privacy & Safety Disclaimer",
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyAndSafetyDialog(
    hinglish: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CharcoalCard,
        titleContentColor = PureWhite,
        textContentColor = MutedSilver,
        title = {
            Text(
                text = if (hinglish) "Privacy, Camera & Fitness Disclaimer" else "Privacy, Camera & Fitness Disclaimer",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (hinglish) {
                        "• Camera Privacy: Aapki live camera feed sirf real-time posture analysis ke liye use hoti hai. Koi video ya photo phone par save nahi hota."
                    } else {
                        "• Camera Privacy: Live camera frames are used strictly for real-time posture analysis. No video or photos are saved on your device."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (hinglish) {
                        "• Fitness Disclaimer: Exercise apni capacity ke hisaab se karein. Kisi bhi dard ya chakkar aane par turant ruk jayein aur doctor se salah lein."
                    } else {
                        "• Fitness Disclaimer: Exercise within your personal limits. Stop immediately if you experience pain or dizziness and consult a physician before intense training."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (hinglish) {
                        "• Local Progress: Aapka 30-Day Challenge log aur streak sirf aapke device par store hota hai."
                    } else {
                        "• Local Progress: Your 30-Day Challenge log and workout streak are stored locally on your device."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrimsonPrimary,
                    contentColor = PureWhite
                )
            ) {
                Text(if (hinglish) "Samajh Gaya" else "Got It")
            }
        }
    )
}
