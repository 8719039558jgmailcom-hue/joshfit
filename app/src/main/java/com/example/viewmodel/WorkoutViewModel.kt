package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiFormCoachService
import com.example.audio.WorkoutAudioEngine
import com.example.data.ChallengeRepository
import com.example.data.StreakRepository
import com.example.model.CameraCoachMode
import com.example.model.ChallengePlan30Days
import com.example.model.ChallengeProgressState
import com.example.model.CoachLanguage
import com.example.model.ExerciseSpec
import com.example.model.ExerciseType
import com.example.model.FormFeedback
import com.example.model.FormStatus
import com.example.model.LightingStatus
import com.example.model.PoseSkeletonData
import com.example.model.RoomSetupGuidance
import com.example.model.SessionSummaryState
import com.example.model.StarterWorkouts
import com.example.model.StreakStats
import com.example.model.WorkoutPhase
import com.example.pose.PoseFormEvaluator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val TAG = "WorkoutViewModel"

enum class AppDestination {
    HOME,
    CHALLENGE,
    ROOM_SETUP,
    WORKOUT,
    SUMMARY
}

data class JoshFitUiState(
    val destination: AppDestination = AppDestination.HOME,
    val workouts: List<ExerciseSpec> = StarterWorkouts.all,
    val selectedExercise: ExerciseSpec = StarterWorkouts.all.first(),
    val totalSets: Int = StarterWorkouts.all.first().defaultSets,
    val workDurationSec: Int = StarterWorkouts.all.first().defaultWorkSeconds,
    val restDurationSec: Int = StarterWorkouts.all.first().defaultRestSeconds,
    val currentSet: Int = 1,
    val phase: WorkoutPhase = WorkoutPhase.READY,
    val remainingSeconds: Int = StarterWorkouts.all.first().defaultWorkSeconds,
    val totalElapsedWorkSeconds: Int = 0,
    val isTimerRunning: Boolean = false,
    val cameraCoachMode: CameraCoachMode = CameraCoachMode.OFF,
    val isCameraAnalysisPaused: Boolean = false,
    val useFrontCamera: Boolean = true,
    val isAiAnalyzing: Boolean = false,
    val manualCaptureTrigger: Int = 0,
    val cameraUnavailableReason: String? = null,
    val formFeedback: FormFeedback = FormFeedback(),
    val liveAngleLabel: String = "",
    val isPoseFullBodyVisible: Boolean = false,
    val lightingStatus: LightingStatus = LightingStatus.GOOD,
    val roomSetupGuidance: RoomSetupGuidance = RoomSetupGuidance(),
    val roomScanTrigger: Int = 0,
    val isRoomAutoScanPaused: Boolean = false,
    val verifyFramingPending: Boolean = false,
    val repsCompleted: Int = 0,
    val goodFormChecks: Int = 0,
    val totalFormChecks: Int = 0,
    val streakStats: StreakStats = StreakStats(),
    val challengeProgress: ChallengeProgressState = ChallengeProgressState(),
    val activeChallengeDay: Int? = null,
    val challengeDayTargetReps: Int? = null,
    val sessionSummary: SessionSummaryState? = null,
    val showPolicyDialog: Boolean = false
)

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {

    private val streakRepository = StreakRepository(application)
    private val challengeRepository = ChallengeRepository(application)
    private val aiCoachService = AiFormCoachService()
    private val poseEvaluator = PoseFormEvaluator()
    private val audioEngine = WorkoutAudioEngine(application)

    private val _uiState = MutableStateFlow(JoshFitUiState())
    val uiState: StateFlow<JoshFitUiState> = _uiState.asStateFlow()

    // Dedicated high-frequency flow for the lightweight Canvas skeleton overlay so the full UI tree never recomposes every frame
    private val _poseSkeleton = MutableStateFlow<PoseSkeletonData?>(null)
    val poseSkeletonFlow: StateFlow<PoseSkeletonData?> = _poseSkeleton.asStateFlow()

    private var timerJob: Job? = null
    private var demoLoopJob: Job? = null
    private var demoCueIndex = 0
    private var lastRoomVoiceSpoken = ""
    private var lastEmittedAngleDeg: Int? = null
    private var lastAngleUiUpdateMs = 0L

    init {
        viewModelScope.launch {
            streakRepository.statsFlow.collect { stats ->
                _uiState.update { it.copy(streakStats = stats) }
                audioEngine.setCoachLanguage(stats.language)
                syncBeatPlayback()
            }
        }
        viewModelScope.launch {
            challengeRepository.progressFlow.collect { progress ->
                _uiState.update { current ->
                    val preservedSelected = current.challengeProgress.selectedDayNumber
                        .takeIf { it in 1..30 } ?: progress.activeDayNumber
                    current.copy(
                        challengeProgress = progress.copy(selectedDayNumber = preservedSelected)
                    )
                }
            }
        }
    }

    fun selectMainTab(destination: AppDestination) {
        if (destination != AppDestination.HOME && destination != AppDestination.CHALLENGE) return
        stopTimer()
        stopDemoLoop()
        audioEngine.setBeatPlaying(false)
        audioEngine.stopSpeaking()
        _poseSkeleton.value = null
        _uiState.update {
            it.copy(
                destination = destination,
                isTimerRunning = false,
                cameraCoachMode = CameraCoachMode.OFF
            )
        }
    }

    fun setPolicyDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showPolicyDialog = visible) }
    }

    fun selectChallengeDay(dayNumber: Int) {
        _uiState.update {
            it.copy(
                challengeProgress = it.challengeProgress.copy(
                    selectedDayNumber = dayNumber.coerceIn(1, 30)
                )
            )
        }
    }

    fun completeRestChallengeDay(dayNumber: Int) {
        val plan = ChallengePlan30Days.days.getOrNull(dayNumber - 1) ?: return
        if (!plan.isRestDay) return
        viewModelScope.launch {
            challengeRepository.saveDayWorkoutLog(
                dayNumber = dayNumber,
                completedReps = 0,
                formAccuracyPercent = 100,
                isRestCompleted = true
            )
            audioEngine.playSetCompleteChime()
            if (_uiState.value.streakStats.voiceEnabled) {
                val cue = if (_uiState.value.streakStats.language == CoachLanguage.HINGLISH) {
                    "Day $dayNumber recovery complete! Kal naye josh ke saath milte hain!"
                } else {
                    "Day $dayNumber recovery complete! Ready for tomorrow's push-ups!"
                }
                audioEngine.speak(cue, force = true)
            }
        }
    }

    fun startChallengeDay(dayNumber: Int, useRoomSetup: Boolean = true) {
        val plan = ChallengePlan30Days.days.getOrNull(dayNumber - 1) ?: return
        if (plan.isRestDay) {
            completeRestChallengeDay(dayNumber)
            return
        }
        val pushUps = StarterWorkouts.all.first { it.type == ExerciseType.PUSH_UPS }
        val suggestedSets = when {
            plan.targetReps <= 15 -> 2
            plan.targetReps <= 30 -> 3
            else -> 4
        }
        val repsPerSet = maxOf(5, (plan.targetReps + suggestedSets - 1) / suggestedSets)
        val challengePushUps = pushUps.copy(
            defaultSets = suggestedSets,
            targetRepsPerSet = repsPerSet
        )
        _uiState.update {
            it.copy(
                selectedExercise = challengePushUps,
                totalSets = suggestedSets,
                workDurationSec = 45,
                restDurationSec = 20,
                activeChallengeDay = dayNumber,
                challengeDayTargetReps = plan.targetReps
            )
        }
        if (useRoomSetup) {
            openRoomSetup(challengePushUps)
        } else {
            openWorkout(challengePushUps, launchCameraCoach = false)
        }
    }

    fun selectExercise(exercise: ExerciseSpec) {
        if (_uiState.value.destination == AppDestination.WORKOUT) return
        _uiState.update {
            it.copy(
                selectedExercise = exercise,
                totalSets = exercise.defaultSets,
                workDurationSec = exercise.defaultWorkSeconds,
                restDurationSec = exercise.defaultRestSeconds,
                remainingSeconds = exercise.defaultWorkSeconds,
                activeChallengeDay = null,
                challengeDayTargetReps = null
            )
        }
    }

    fun adjustWorkDuration(deltaSeconds: Int) {
        _uiState.update { state ->
            val updated = (state.workDurationSec + deltaSeconds).coerceIn(15, 120)
            state.copy(
                workDurationSec = updated,
                remainingSeconds = if (state.phase == WorkoutPhase.READY) updated else state.remainingSeconds
            )
        }
    }

    fun adjustRestDuration(deltaSeconds: Int) {
        _uiState.update { state ->
            val updated = (state.restDurationSec + deltaSeconds).coerceIn(10, 60)
            state.copy(restDurationSec = updated)
        }
    }

    fun adjustSets(delta: Int) {
        _uiState.update { state ->
            state.copy(totalSets = (state.totalSets + delta).coerceIn(1, 5))
        }
    }

    // --- Step 1: AI Room & Camera Setup Assistant ---

    fun openRoomSetup(exercise: ExerciseSpec) {
        stopTimer()
        stopDemoLoop()
        audioEngine.stopSpeaking()
        poseEvaluator.resetRepState()
        lastRoomVoiceSpoken = ""
        _poseSkeleton.value = null

        val lang = _uiState.value.streakStats.language
        val introGuidance = RoomSetupGuidance(
            bestWorkoutSpot = if (lang == CoachLanguage.HINGLISH) {
                "Apne kamre aur floor space ko dheere se camera mein dikhayein..."
            } else {
                "Slowly pan your camera across your room and floor space..."
            },
            exactCameraPlacement = exercise.defaultPlacementTip(lang),
            spotReady = false,
            fullBodyVerified = false,
            isScanning = false,
            isDemo = false
        )

        _uiState.update { state ->
            state.copy(
                destination = AppDestination.ROOM_SETUP,
                selectedExercise = exercise,
                totalSets = if (exercise == state.selectedExercise) state.totalSets else exercise.defaultSets,
                workDurationSec = if (exercise == state.selectedExercise) state.workDurationSec else exercise.defaultWorkSeconds,
                restDurationSec = if (exercise == state.selectedExercise) state.restDurationSec else exercise.defaultRestSeconds,
                cameraCoachMode = CameraCoachMode.LIVE_CAMERA,
                useFrontCamera = false,
                isCameraAnalysisPaused = false,
                cameraUnavailableReason = null,
                isPoseFullBodyVisible = false,
                liveAngleLabel = "",
                lightingStatus = LightingStatus.GOOD,
                roomSetupGuidance = introGuidance,
                isRoomAutoScanPaused = false,
                verifyFramingPending = false
            )
        }

        if (_uiState.value.streakStats.voiceEnabled) {
            val promptVoice = if (lang == CoachLanguage.HINGLISH) {
                "Apna kamra aur floor dheere se dikhayein, main best workout spot aur phone placement bataunga."
            } else {
                "Slowly show your room and floor space so I can suggest the best spot and camera angle."
            }
            audioEngine.speak(promptVoice, force = true)
        }
    }

    fun triggerRoomScanNow() {
        _uiState.update {
            it.copy(
                roomScanTrigger = it.roomScanTrigger + 1,
                isRoomAutoScanPaused = false,
                roomSetupGuidance = it.roomSetupGuidance.copy(isScanning = true, errorMessage = null)
            )
        }
    }

    fun confirmPlacementAndCheckFullBody() {
        val state = _uiState.value
        if (state.cameraCoachMode == CameraCoachMode.DEMO_MODE) {
            val lang = state.streakStats.language
            val verifiedMsg = if (lang == CoachLanguage.HINGLISH) {
                "Demo Setup Verified! Poori body frame mein hai — Start Workout dabao!"
            } else {
                "Demo Setup Verified! Full body framed — tap Start Workout!"
            }
            _poseSkeleton.value = PoseFormEvaluator.buildDemoSkeleton(state.selectedExercise.type, true)
            _uiState.update {
                it.copy(
                    isPoseFullBodyVisible = true,
                    roomSetupGuidance = it.roomSetupGuidance.copy(
                        fullBodyVerified = true,
                        verificationMessage = verifiedMsg,
                        isVerifying = false
                    )
                )
            }
            audioEngine.playSetCompleteChime()
            if (state.streakStats.voiceEnabled) {
                audioEngine.speak(verifiedMsg, force = true)
            }
            return
        }

        _uiState.update {
            it.copy(
                verifyFramingPending = true,
                roomScanTrigger = it.roomScanTrigger + 1,
                roomSetupGuidance = it.roomSetupGuidance.copy(
                    isVerifying = true,
                    verificationMessage = if (state.streakStats.language == CoachLanguage.HINGLISH) {
                        "Full body framing check ho raha hai..."
                    } else {
                        "Checking your full body framing..."
                    }
                )
            )
        }
    }

    fun replayRoomGuidanceVoice() {
        val guidance = _uiState.value.roomSetupGuidance
        val msg = listOf(guidance.bestWorkoutSpot, guidance.exactCameraPlacement)
            .filter { it.isNotBlank() }
            .joinToString(". ")
        if (msg.isNotBlank()) {
            audioEngine.speak(msg, force = true)
        }
    }

    fun switchRoomSetupToDemo(reason: String? = null) {
        val state = _uiState.value
        val lang = state.streakStats.language
        val demoSpot = if (lang == CoachLanguage.HINGLISH) {
            "Saaf 6x4 feet floor space chunein jahan light acchi ho aur furniture door ho."
        } else {
            "Pick a clear 6x4 ft floor space with bright overhead or window light."
        }
        val demoPlacement = state.selectedExercise.defaultPlacementTip(lang)
        _poseSkeleton.value = PoseFormEvaluator.buildDemoSkeleton(state.selectedExercise.type, true)

        _uiState.update {
            it.copy(
                cameraCoachMode = CameraCoachMode.DEMO_MODE,
                cameraUnavailableReason = reason,
                isPoseFullBodyVisible = true,
                roomSetupGuidance = RoomSetupGuidance(
                    lightingStatus = LightingStatus.GOOD,
                    bestWorkoutSpot = demoSpot,
                    exactCameraPlacement = demoPlacement,
                    spotReady = true,
                    fullBodyVerified = false,
                    verificationMessage = if (lang == CoachLanguage.HINGLISH) {
                        "Demo Mode active hai — 'Confirm Placement' dabakar Start Workout unlock karein."
                    } else {
                        "Demo Mode active — tap 'Confirm Placement' to verify and start."
                    },
                    isDemo = true
                )
            )
        }
    }

    fun onRoomSetupGeminiFrame(bitmap: Bitmap) {
        val state = _uiState.value
        if (state.destination != AppDestination.ROOM_SETUP || state.isAiAnalyzing) {
            bitmap.recycle()
            return
        }

        val isVerifying = state.verifyFramingPending
        viewModelScope.launch {
            _uiState.update { it.copy(isAiAnalyzing = true) }
            try {
                val lighting = poseEvaluator.estimateLighting(bitmap)
                if (isVerifying) {
                    val poseFull = _poseSkeleton.value?.fullBodyVisible == true
                    val (verified, message) = aiCoachService.verifyFullBodyFraming(
                        bitmap = bitmap,
                        exercise = state.selectedExercise,
                        language = state.streakStats.language,
                        poseFullBodyDetected = poseFull,
                        localLighting = lighting
                    )
                    _uiState.update { current ->
                        current.copy(
                            verifyFramingPending = false,
                            lightingStatus = lighting,
                            isRoomAutoScanPaused = current.isRoomAutoScanPaused || verified,
                            roomSetupGuidance = current.roomSetupGuidance.copy(
                                lightingStatus = lighting,
                                fullBodyVerified = verified,
                                verificationMessage = message,
                                isVerifying = false
                            )
                        )
                    }
                    if (verified) audioEngine.playSetCompleteChime() else audioEngine.playBeep(false)
                    if (_uiState.value.streakStats.voiceEnabled) {
                        audioEngine.speak(message, force = true)
                    }
                } else {
                    val guidance = aiCoachService.analyzeRoomSetup(
                        bitmap = bitmap,
                        exercise = state.selectedExercise,
                        language = state.streakStats.language,
                        localLighting = lighting
                    )
                    val shouldPauseAutoScan = guidance.spotReady || guidance.errorMessage != null
                    _uiState.update { current ->
                        current.copy(
                            lightingStatus = guidance.lightingStatus,
                            isRoomAutoScanPaused = shouldPauseAutoScan,
                            roomSetupGuidance = guidance.copy(
                                fullBodyVerified = current.roomSetupGuidance.fullBodyVerified,
                                verificationMessage = current.roomSetupGuidance.verificationMessage
                            )
                        )
                    }
                    val spoken = "${guidance.bestWorkoutSpot}. ${guidance.exactCameraPlacement}"
                    if (_uiState.value.streakStats.voiceEnabled &&
                        guidance.errorMessage == null &&
                        guidance.bestWorkoutSpot.isNotBlank() &&
                        spoken != lastRoomVoiceSpoken
                    ) {
                        lastRoomVoiceSpoken = spoken
                        audioEngine.speak(spoken)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
                _uiState.update { it.copy(isAiAnalyzing = false, verifyFramingPending = false) }
            }
        }
    }

    // --- Step 2: Workout Screen & Live Form Guidance ---

    fun openWorkout(
        exercise: ExerciseSpec,
        launchCameraCoach: Boolean = false,
        useDemoMode: Boolean = false,
        autoStartTimer: Boolean = false
    ) {
        stopTimer()
        stopDemoLoop()
        audioEngine.stopSpeaking()
        poseEvaluator.resetRepState()
        lastEmittedAngleDeg = null

        val mode = when {
            useDemoMode -> CameraCoachMode.DEMO_MODE
            launchCameraCoach -> CameraCoachMode.LIVE_CAMERA
            else -> CameraCoachMode.OFF
        }
        _poseSkeleton.value = if (useDemoMode) {
            PoseFormEvaluator.buildDemoSkeleton(exercise.type, true)
        } else null

        _uiState.update { state ->
            val workSec = if (exercise == state.selectedExercise) state.workDurationSec else exercise.defaultWorkSeconds
            state.copy(
                destination = AppDestination.WORKOUT,
                selectedExercise = exercise,
                totalSets = if (exercise == state.selectedExercise) state.totalSets else exercise.defaultSets,
                workDurationSec = workSec,
                restDurationSec = if (exercise == state.selectedExercise) state.restDurationSec else exercise.defaultRestSeconds,
                currentSet = 1,
                phase = WorkoutPhase.READY,
                remainingSeconds = workSec,
                totalElapsedWorkSeconds = 0,
                isTimerRunning = false,
                cameraCoachMode = mode,
                useFrontCamera = true,
                isCameraAnalysisPaused = false,
                cameraUnavailableReason = if (useDemoMode) state.cameraUnavailableReason else null,
                liveAngleLabel = "",
                formFeedback = FormFeedback(
                    status = if (useDemoMode) FormStatus.GOOD_FORM else FormStatus.IDLE,
                    badgeText = if (state.streakStats.language == CoachLanguage.HINGLISH) {
                        "AI Coach Taiyaar"
                    } else {
                        "AI Coach Ready"
                    },
                    cueText = exercise.framingGuide(state.streakStats.language)
                ),
                repsCompleted = 0,
                goodFormChecks = 0,
                totalFormChecks = 0,
                sessionSummary = null
            )
        }
        syncBeatPlayback()
        if (autoStartTimer) {
            startOrResumeTimer()
        }
    }

    fun navigateHome() {
        stopTimer()
        stopDemoLoop()
        audioEngine.setBeatPlaying(false)
        audioEngine.stopSpeaking()
        _poseSkeleton.value = null
        _uiState.update {
            it.copy(
                destination = AppDestination.HOME,
                isTimerRunning = false,
                cameraCoachMode = CameraCoachMode.OFF,
                phase = WorkoutPhase.READY,
                activeChallengeDay = null,
                challengeDayTargetReps = null
            )
        }
    }

    fun toggleStartPauseTimer() {
        val state = _uiState.value
        if (state.phase == WorkoutPhase.COMPLETED) return
        if (state.isTimerRunning) pauseTimer() else startOrResumeTimer()
    }

    private fun startOrResumeTimer() {
        val state = _uiState.value
        val startingFromReady = state.phase == WorkoutPhase.READY
        val nextPhase = if (startingFromReady) WorkoutPhase.ACTIVE_WORK else state.phase
        val nextRemaining = if (startingFromReady) state.workDurationSec else state.remainingSeconds

        _uiState.update {
            it.copy(
                phase = nextPhase,
                remainingSeconds = nextRemaining,
                isTimerRunning = true
            )
        }

        if (startingFromReady && state.streakStats.voiceEnabled) {
            val cue = if (state.streakStats.language == CoachLanguage.HINGLISH) {
                "Chalo! ${state.selectedExercise.titleEn} Set 1 shuru!"
            } else {
                "Let's go! ${state.selectedExercise.titleEn} Set 1!"
            }
            audioEngine.speak(cue, force = true)
        }

        syncBeatPlayback()
        startTicker()
        if (_uiState.value.cameraCoachMode == CameraCoachMode.DEMO_MODE && !_uiState.value.isCameraAnalysisPaused) {
            startDemoLoop()
        }
    }

    private fun pauseTimer() {
        stopTimer()
        stopDemoLoop()
        _uiState.update { it.copy(isTimerRunning = false) }
        syncBeatPlayback()
    }

    fun resetWorkoutTimer() {
        stopTimer()
        stopDemoLoop()
        audioEngine.stopSpeaking()
        poseEvaluator.resetRepState()
        _uiState.update { state ->
            state.copy(
                phase = WorkoutPhase.READY,
                currentSet = 1,
                remainingSeconds = state.workDurationSec,
                totalElapsedWorkSeconds = 0,
                isTimerRunning = false,
                repsCompleted = 0,
                goodFormChecks = 0,
                totalFormChecks = 0
            )
        }
        syncBeatPlayback()
    }

    fun completeSetEarlyOrSkipRest() {
        when (_uiState.value.phase) {
            WorkoutPhase.READY -> startOrResumeTimer()
            WorkoutPhase.ACTIVE_WORK -> handleWorkIntervalFinished()
            WorkoutPhase.REST -> handleRestIntervalFinished()
            WorkoutPhase.COMPLETED -> Unit
        }
    }

    fun incrementManualRep() {
        _uiState.update { it.copy(repsCompleted = it.repsCompleted + 1) }
        audioEngine.playBeep(highPitch = true)
    }

    fun finishWorkoutNow() {
        finishSession()
    }

    private fun startTicker() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val current = _uiState.value
                if (!current.isTimerRunning) break

                val newRemaining = current.remainingSeconds - 1
                val addedWork = if (current.phase == WorkoutPhase.ACTIVE_WORK) 1 else 0

                if (newRemaining in 1..3) {
                    audioEngine.playBeep(highPitch = newRemaining == 1)
                }

                if (newRemaining <= 0) {
                    _uiState.update {
                        it.copy(
                            remainingSeconds = 0,
                            totalElapsedWorkSeconds = it.totalElapsedWorkSeconds + addedWork
                        )
                    }
                    when (current.phase) {
                        WorkoutPhase.ACTIVE_WORK -> handleWorkIntervalFinished()
                        WorkoutPhase.REST -> handleRestIntervalFinished()
                        else -> Unit
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            remainingSeconds = newRemaining,
                            totalElapsedWorkSeconds = it.totalElapsedWorkSeconds + addedWork
                        )
                    }
                }
            }
        }
    }

    private fun handleWorkIntervalFinished() {
        val state = _uiState.value
        audioEngine.playSetCompleteChime()

        val expectedMinReps = if (state.selectedExercise.isHoldExercise) 0 else state.currentSet * 5
        val updatedReps = if (state.repsCompleted < expectedMinReps && state.cameraCoachMode == CameraCoachMode.OFF) {
            state.currentSet * state.selectedExercise.targetRepsPerSet
        } else {
            state.repsCompleted
        }

        if (state.currentSet >= state.totalSets) {
            _uiState.update { it.copy(repsCompleted = updatedReps) }
            finishSession()
        } else {
            _uiState.update {
                it.copy(
                    phase = WorkoutPhase.REST,
                    remainingSeconds = it.restDurationSec,
                    repsCompleted = updatedReps
                )
            }
            if (state.streakStats.voiceEnabled) {
                val restCue = if (state.streakStats.language == CoachLanguage.HINGLISH) {
                    "Set ${state.currentSet} complete! Thoda rest karo, paani peelo."
                } else {
                    "Set ${state.currentSet} complete! Take a short rest."
                }
                audioEngine.speak(restCue, force = true)
            }
            syncBeatPlayback()
        }
    }

    private fun handleRestIntervalFinished() {
        val state = _uiState.value
        val nextSet = state.currentSet + 1
        _uiState.update {
            it.copy(
                phase = WorkoutPhase.ACTIVE_WORK,
                currentSet = nextSet,
                remainingSeconds = it.workDurationSec
            )
        }
        audioEngine.playBeep(highPitch = true)
        if (state.streakStats.voiceEnabled) {
            val cue = if (state.streakStats.language == CoachLanguage.HINGLISH) {
                "Set $nextSet shuru! Full Josh!"
            } else {
                "Set $nextSet! Let's go!"
            }
            audioEngine.speak(cue, force = true)
        }
        syncBeatPlayback()
    }

    private fun finishSession() {
        stopTimer()
        stopDemoLoop()
        audioEngine.setBeatPlaying(false)
        audioEngine.stopSpeaking()
        audioEngine.playSetCompleteChime()
        _poseSkeleton.value = null

        val snapshot = _uiState.value
        val setsDone = when (snapshot.phase) {
            WorkoutPhase.READY -> 0
            WorkoutPhase.ACTIVE_WORK -> maxOf(1, snapshot.currentSet)
            WorkoutPhase.REST -> snapshot.currentSet
            WorkoutPhase.COMPLETED -> snapshot.totalSets
        }.coerceAtLeast(1)

        val effectiveWorkSeconds = maxOf(snapshot.totalElapsedWorkSeconds, 15)
        val calories = (setsDone * snapshot.selectedExercise.caloriesPerSet) +
            (snapshot.repsCompleted / 4)

        val initialSummary = SessionSummaryState(
            exercise = snapshot.selectedExercise,
            setsCompleted = setsDone,
            repsCompleted = snapshot.repsCompleted,
            totalWorkSeconds = effectiveWorkSeconds,
            estimatedCalories = calories,
            goodFormChecks = snapshot.goodFormChecks,
            totalFormChecks = snapshot.totalFormChecks,
            updatedStreakDays = maxOf(1, snapshot.streakStats.streakDays),
            isAiTipLoading = true
        )

        _uiState.update {
            it.copy(
                destination = AppDestination.SUMMARY,
                phase = WorkoutPhase.COMPLETED,
                isTimerRunning = false,
                cameraCoachMode = CameraCoachMode.OFF,
                sessionSummary = initialSummary
            )
        }

        if (snapshot.streakStats.voiceEnabled) {
            val doneCue = if (snapshot.streakStats.language == CoachLanguage.HINGLISH) {
                "Kamaal kar diya! Workout complete!"
            } else {
                "Awesome job! Workout complete!"
            }
            audioEngine.speak(doneCue, force = true)
        }

        viewModelScope.launch {
            val newStreak = streakRepository.recordCompletedWorkout(effectiveWorkSeconds)

            // Automatically log Push-Up workouts into the 30-Day Challenge active day
            if (snapshot.selectedExercise.type == ExerciseType.PUSH_UPS) {
                val targetDay = snapshot.activeChallengeDay ?: snapshot.challengeProgress.activeDayNumber
                challengeRepository.saveDayWorkoutLog(
                    dayNumber = targetDay,
                    completedReps = snapshot.repsCompleted,
                    formAccuracyPercent = initialSummary.formAccuracyPercent,
                    isRestCompleted = false
                )
            }

            _uiState.update { state ->
                state.copy(
                    sessionSummary = state.sessionSummary?.copy(updatedStreakDays = newStreak)
                )
            }

            try {
                val tip = aiCoachService.generateSessionSummaryTip(
                    exercise = snapshot.selectedExercise,
                    setsCompleted = setsDone,
                    repsCompleted = snapshot.repsCompleted,
                    durationSeconds = effectiveWorkSeconds,
                    formScorePercent = initialSummary.formAccuracyPercent,
                    streakDays = newStreak,
                    language = snapshot.streakStats.language
                )
                _uiState.update { state ->
                    state.copy(
                        sessionSummary = state.sessionSummary?.copy(
                            aiTip = tip,
                            isAiTipLoading = false,
                            aiTipError = null
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate AI session summary tip", e)
                _uiState.update { state ->
                    state.copy(
                        sessionSummary = state.sessionSummary?.copy(
                            isAiTipLoading = false,
                            aiTipError = e.localizedMessage ?: "Unable to fetch AI recovery tip."
                        )
                    )
                }
            }
        }
    }

    // --- Real-Time ML Kit Pose + Gemini Frame Processing (Lag-Free Delta Updates) ---

    fun onPoseFrameCaptured(bitmap: Bitmap) {
        val state = _uiState.value
        val isRoomSetup = state.destination == AppDestination.ROOM_SETUP
        val isWorkout = state.destination == AppDestination.WORKOUT &&
            state.cameraCoachMode == CameraCoachMode.LIVE_CAMERA

        if (!isRoomSetup && !isWorkout) {
            bitmap.recycle()
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val eval = poseEvaluator.processFrame(
                    bitmap = bitmap,
                    exerciseType = state.selectedExercise.type,
                    language = state.streakStats.language,
                    evaluateFormAndReps = isWorkout && state.isTimerRunning && !state.isCameraAnalysisPaused
                )

                // Always update lightweight skeleton StateFlow for Canvas only (no full UI tree re-render!)
                _poseSkeleton.value = eval.skeleton

                val now = System.currentTimeMillis()
                val newAngle = eval.skeleton.primaryAngleDeg
                val angleChangedSignificantly = newAngle != null &&
                    (lastEmittedAngleDeg == null || abs(newAngle - lastEmittedAngleDeg!!) >= 5) &&
                    (now - lastAngleUiUpdateMs >= 250L)

                if (angleChangedSignificantly) {
                    lastEmittedAngleDeg = newAngle
                    lastAngleUiUpdateMs = now
                }

                val currentUi = _uiState.value
                val shouldUpdateMainState = eval.repIncremented ||
                    eval.lightingStatus != currentUi.lightingStatus ||
                    eval.skeleton.fullBodyVisible != currentUi.isPoseFullBodyVisible ||
                    (angleChangedSignificantly && eval.skeleton.primaryAngleLabel != currentUi.liveAngleLabel)

                if (shouldUpdateMainState) {
                    _uiState.update { current ->
                        current.copy(
                            lightingStatus = eval.lightingStatus,
                            isPoseFullBodyVisible = eval.skeleton.fullBodyVisible,
                            liveAngleLabel = if (angleChangedSignificantly) eval.skeleton.primaryAngleLabel else current.liveAngleLabel,
                            repsCompleted = if (eval.repIncremented) current.repsCompleted + 1 else current.repsCompleted
                        )
                    }
                }

                if (eval.repIncremented) {
                    audioEngine.playBeep(highPitch = true)
                }

                eval.feedback?.let { poseFeedback ->
                    val prev = _uiState.value.formFeedback
                    if (eval.repIncremented ||
                        poseFeedback.status != prev.status ||
                        poseFeedback.cueText != prev.cueText
                    ) {
                        applyFormFeedback(poseFeedback, countRepFromFeedback = false)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Pose evaluation error", e)
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }

    fun startLiveCameraCoach() {
        stopDemoLoop()
        poseEvaluator.resetRepState()
        _poseSkeleton.value = null
        _uiState.update { state ->
            state.copy(
                cameraCoachMode = CameraCoachMode.LIVE_CAMERA,
                isCameraAnalysisPaused = false,
                cameraUnavailableReason = null,
                liveAngleLabel = "",
                formFeedback = FormFeedback(
                    status = FormStatus.IDLE,
                    badgeText = if (state.streakStats.language == CoachLanguage.HINGLISH) {
                        "Pose + AI Active"
                    } else {
                        "Pose + AI Active"
                    },
                    cueText = state.selectedExercise.framingGuide(state.streakStats.language)
                )
            )
        }
    }

    fun startDemoCoachMode(reason: String? = null) {
        val state = _uiState.value
        _poseSkeleton.value = PoseFormEvaluator.buildDemoSkeleton(state.selectedExercise.type, true)
        _uiState.update {
            it.copy(
                cameraCoachMode = CameraCoachMode.DEMO_MODE,
                isCameraAnalysisPaused = false,
                cameraUnavailableReason = reason,
                formFeedback = buildDemoFeedback(
                    exercise = state.selectedExercise,
                    language = state.streakStats.language,
                    index = 0
                )
            )
        }
        startDemoLoop()
    }

    fun stopCameraCoach() {
        stopDemoLoop()
        audioEngine.stopSpeaking()
        _poseSkeleton.value = null
        _uiState.update {
            it.copy(
                cameraCoachMode = CameraCoachMode.OFF,
                isAiAnalyzing = false,
                liveAngleLabel = ""
            )
        }
    }

    fun togglePauseCameraGuidance() {
        val nextPaused = !_uiState.value.isCameraAnalysisPaused
        if (nextPaused) audioEngine.stopSpeaking()
        _uiState.update { it.copy(isCameraAnalysisPaused = nextPaused) }
        if (_uiState.value.cameraCoachMode == CameraCoachMode.DEMO_MODE) {
            if (nextPaused) stopDemoLoop() else startDemoLoop()
        }
    }

    fun flipCamera() {
        _uiState.update { it.copy(useFrontCamera = !it.useFrontCamera) }
    }

    fun triggerManualFormCheck() {
        val mode = _uiState.value.cameraCoachMode
        if (mode == CameraCoachMode.LIVE_CAMERA) {
            _uiState.update { it.copy(manualCaptureTrigger = it.manualCaptureTrigger + 1) }
        } else if (mode == CameraCoachMode.DEMO_MODE) {
            analyzeBundledDemoPoseWithGemini()
        }
    }

    fun onLiveCameraFrameCaptured(bitmap: Bitmap) {
        val state = _uiState.value
        if (state.isAiAnalyzing || state.cameraCoachMode != CameraCoachMode.LIVE_CAMERA) {
            bitmap.recycle()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAiAnalyzing = true) }
            try {
                val feedback = aiCoachService.analyzeFrame(
                    bitmap = bitmap,
                    exercise = state.selectedExercise,
                    language = state.streakStats.language
                )
                applyFormFeedback(feedback, countRepFromFeedback = false)
            } catch (e: CancellationException) {
                throw e
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
                _uiState.update { it.copy(isAiAnalyzing = false) }
            }
        }
    }

    private fun analyzeBundledDemoPoseWithGemini() {
        val state = _uiState.value
        if (state.isAiAnalyzing) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAiAnalyzing = true,
                    formFeedback = it.formFeedback.copy(
                        status = FormStatus.ANALYZING,
                        badgeText = "Gemini AI Analyzing..."
                    )
                )
            }
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeResource(
                        getApplication<Application>().resources,
                        state.selectedExercise.imageRes
                    )
                }
                if (bitmap != null) {
                    val feedback = aiCoachService.analyzeFrame(
                        bitmap = bitmap,
                        exercise = state.selectedExercise,
                        language = state.streakStats.language
                    )
                    bitmap.recycle()
                    applyFormFeedback(feedback.copy(isDemoFeedback = true), countRepFromFeedback = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Demo Gemini analysis failed", e)
            } finally {
                _uiState.update { it.copy(isAiAnalyzing = false) }
            }
        }
    }

    fun triggerDemoPostureSimulation(goodForm: Boolean) {
        val state = _uiState.value
        val feedback = if (goodForm) {
            buildDemoFeedback(state.selectedExercise, state.streakStats.language, 0)
        } else {
            buildDemoFeedback(state.selectedExercise, state.streakStats.language, 1)
        }
        _poseSkeleton.value = PoseFormEvaluator.buildDemoSkeleton(state.selectedExercise.type, goodForm)
        applyFormFeedback(feedback, countRepFromFeedback = true)
    }

    private fun applyFormFeedback(feedback: FormFeedback, countRepFromFeedback: Boolean) {
        _uiState.update { state ->
            val countedCheck = feedback.status == FormStatus.GOOD_FORM ||
                feedback.status == FormStatus.ADJUST_POSTURE
            val goodInc = if (feedback.status == FormStatus.GOOD_FORM) 1 else 0
            val totalInc = if (countedCheck) 1 else 0
            val repInc = if (countRepFromFeedback && feedback.repDetected && !state.selectedExercise.isHoldExercise) 1 else 0

            state.copy(
                formFeedback = feedback,
                goodFormChecks = state.goodFormChecks + goodInc,
                totalFormChecks = state.totalFormChecks + totalInc,
                repsCompleted = state.repsCompleted + repInc
            )
        }

        val current = _uiState.value
        if (current.streakStats.voiceEnabled &&
            (feedback.status == FormStatus.GOOD_FORM || feedback.status == FormStatus.ADJUST_POSTURE)
        ) {
            audioEngine.speak(feedback.cueText)
        }
    }

    private fun startDemoLoop() {
        demoLoopJob?.cancel()
        demoLoopJob = viewModelScope.launch {
            while (isActive) {
                delay(4200L)
                val state = _uiState.value
                if (state.cameraCoachMode != CameraCoachMode.DEMO_MODE || state.isCameraAnalysisPaused) {
                    break
                }
                if (state.isTimerRunning && !state.isAiAnalyzing) {
                    demoCueIndex = (demoCueIndex + 1) % 3
                    val demoFeedback = buildDemoFeedback(
                        exercise = state.selectedExercise,
                        language = state.streakStats.language,
                        index = demoCueIndex
                    )
                    _poseSkeleton.value = PoseFormEvaluator.buildDemoSkeleton(
                        state.selectedExercise.type,
                        demoFeedback.status == FormStatus.GOOD_FORM
                    )
                    applyFormFeedback(demoFeedback, countRepFromFeedback = true)
                }
            }
        }
    }

    private fun stopDemoLoop() {
        demoLoopJob?.cancel()
        demoLoopJob = null
    }

    private fun buildDemoFeedback(
        exercise: ExerciseSpec,
        language: CoachLanguage,
        index: Int
    ): FormFeedback {
        val hinglish = language == CoachLanguage.HINGLISH
        return when (exercise.type) {
            ExerciseType.PUSH_UPS -> when (index % 3) {
                0 -> FormFeedback(
                    status = FormStatus.GOOD_FORM,
                    badgeText = if (hinglish) "Sahi Form (Demo)" else "Good Form (Demo)",
                    cueText = if (hinglish) "Bahut badhiya! Kamar seedhi hai, rep count hua." else "Great form! Back flat, rep counted.",
                    postureTip = if (hinglish) "Elbows 45° angle par • Smooth tempo" else "Elbows at 45° • Controlled tempo",
                    repDetected = true,
                    timestampMs = System.currentTimeMillis(),
                    isDemoFeedback = true
                )
                1 -> FormFeedback(
                    status = FormStatus.ADJUST_POSTURE,
                    badgeText = if (hinglish) "Form Sudharein (Demo)" else "Adjust Posture (Demo)",
                    cueText = if (hinglish) "Kamar seedhi rakho, hips neeche mat girao!" else "Keep back straight — don't let hips sag!",
                    postureTip = if (hinglish) "Head se heels tak ek seedhi line banao" else "Maintain a straight plank line head-to-heels",
                    repDetected = false,
                    timestampMs = System.currentTimeMillis(),
                    isDemoFeedback = true
                )
                else -> FormFeedback(
                    status = FormStatus.GOOD_FORM,
                    badgeText = if (hinglish) "Sahi Form (Demo)" else "Good Form (Demo)",
                    cueText = if (hinglish) "Thoda aur neeche jao aur full push karo!" else "Lower chest steadily and push cleanly!",
                    postureTip = if (hinglish) "Shoulders stable aur hands shoulder-width" else "Shoulders stacked over wrists",
                    repDetected = true,
                    timestampMs = System.currentTimeMillis(),
                    isDemoFeedback = true
                )
            }

            ExerciseType.SQUATS -> when (index % 3) {
                0 -> FormFeedback(
                    status = FormStatus.GOOD_FORM,
                    badgeText = if (hinglish) "Sahi Form (Demo)" else "Good Form (Demo)",
                    cueText = if (hinglish) "Bahut badhiya, rep count hua! Heels se push karo." else "Awesome squat, rep counted! Drive through heels.",
                    postureTip = if (hinglish) "Thighs parallel • Seena upar" else "Thighs parallel • Chest proud",
                    repDetected = true,
                    timestampMs = System.currentTimeMillis(),
                    isDemoFeedback = true
                )
                1 -> FormFeedback(
                    status = FormStatus.ADJUST_POSTURE,
                    badgeText = if (hinglish) "Form Sudharein (Demo)" else "Adjust Posture (Demo)",
                    cueText = if (hinglish) "Thoda aur neeche jao aur kamar seedhi rakho!" else "Go lower and keep your chest upright!",
                    postureTip = if (hinglish) "Ghutne toes ki disha mein rakho" else "Track knees over toes, keep chest up",
                    repDetected = false,
                    timestampMs = System.currentTimeMillis(),
                    isDemoFeedback = true
                )
                else -> FormFeedback(
                    status = FormStatus.GOOD_FORM,
                    badgeText = if (hinglish) "Sahi Form (Demo)" else "Good Form (Demo)",
                    cueText = if (hinglish) "Zabardast control! Rep count hua!" else "Solid rhythm and neutral spine!",
                    postureTip = if (hinglish) "Weight heels aur mid-foot par" else "Balanced mid-foot weight distribution",
                    repDetected = true,
                    timestampMs = System.currentTimeMillis(),
                    isDemoFeedback = true
                )
            }

            ExerciseType.PLANK -> when (index % 3) {
                0 -> FormFeedback(
                    status = FormStatus.GOOD_FORM,
                    badgeText = if (hinglish) "Sahi Form (Demo)" else "Good Form (Demo)",
                    cueText = if (hinglish) "Bahut badhiya plank! Pet aur glutes tight rakho." else "Rock-solid plank! Keep core and glutes braced.",
                    postureTip = if (hinglish) "Neck neutral • Forearms flat" else "Neutral neck • Elbows under shoulders",
                    repDetected = false,
                    timestampMs = System.currentTimeMillis(),
                    isDemoFeedback = true
                )
                1 -> FormFeedback(
                    status = FormStatus.ADJUST_POSTURE,
                    badgeText = if (hinglish) "Form Sudharein (Demo)" else "Adjust Posture (Demo)",
                    cueText = if (hinglish) "Kamar seedhi rakho — hips thode neeche laao!" else "Keep your back straight — lower hips into line!",
                    postureTip = if (hinglish) "Kamar ko neutral rakho, breathe steady" else "Tuck pelvis slightly and breathe steadily",
                    repDetected = false,
                    timestampMs = System.currentTimeMillis(),
                    isDemoFeedback = true
                )
                else -> FormFeedback(
                    status = FormStatus.GOOD_FORM,
                    badgeText = if (hinglish) "Sahi Form (Demo)" else "Good Form (Demo)",
                    cueText = if (hinglish) "Bas hold karo, bilkul sahi posture!" else "Hold steady — strong alignment!",
                    postureTip = if (hinglish) "Shoulders ko kaanon se door rakho" else "Press forearms down, shoulders away from ears",
                    repDetected = false,
                    timestampMs = System.currentTimeMillis(),
                    isDemoFeedback = true
                )
            }
        }
    }

    // --- Audio & Language Toggles ---

    fun toggleBeat() {
        val next = !_uiState.value.streakStats.beatEnabled
        viewModelScope.launch { streakRepository.setBeatEnabled(next) }
    }

    fun toggleVoice() {
        val next = !_uiState.value.streakStats.voiceEnabled
        if (!next) audioEngine.stopSpeaking()
        viewModelScope.launch { streakRepository.setVoiceEnabled(next) }
    }

    fun toggleLanguage() {
        val next = if (_uiState.value.streakStats.language == CoachLanguage.HINGLISH) {
            CoachLanguage.ENGLISH
        } else {
            CoachLanguage.HINGLISH
        }
        viewModelScope.launch { streakRepository.setLanguage(next) }
    }

    private fun syncBeatPlayback() {
        val state = _uiState.value
        val shouldPlayBeat = state.destination == AppDestination.WORKOUT &&
            state.isTimerRunning &&
            state.phase == WorkoutPhase.ACTIVE_WORK &&
            state.streakStats.beatEnabled
        audioEngine.setBeatPlaying(shouldPlayBeat)
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
        stopDemoLoop()
        poseEvaluator.close()
        audioEngine.release()
    }
}
