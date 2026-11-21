package com.example.model

import androidx.annotation.DrawableRes
import com.example.R

enum class CoachLanguage {
    HINGLISH,
    ENGLISH
}

enum class ExerciseType {
    PUSH_UPS,
    SQUATS,
    PLANK
}

enum class WorkoutPhase {
    READY,
    ACTIVE_WORK,
    REST,
    COMPLETED
}

enum class CameraCoachMode {
    OFF,
    LIVE_CAMERA,
    DEMO_MODE
}

enum class FormStatus {
    IDLE,
    ANALYZING,
    GOOD_FORM,
    ADJUST_POSTURE,
    GET_IN_FRAME,
    ERROR
}

enum class LightingStatus {
    GOOD,
    LOW,
    TOO_DARK
}

data class NormalizedPoint(
    val x: Float,
    val y: Float,
    val likelihood: Float
)

data class PoseSkeletonData(
    val landmarks: Map<Int, NormalizedPoint> = emptyMap(),
    val connections: List<Pair<Int, Int>> = emptyList(),
    val primaryAngleDeg: Int? = null,
    val primaryAngleLabel: String = "",
    val fullBodyVisible: Boolean = false,
    val upperBodyVisible: Boolean = false
)

data class RoomSetupGuidance(
    val lightingStatus: LightingStatus = LightingStatus.GOOD,
    val lightingHint: String = "",
    val bestWorkoutSpot: String = "",
    val exactCameraPlacement: String = "",
    val spotReady: Boolean = false,
    val fullBodyVerified: Boolean = false,
    val verificationMessage: String = "",
    val isScanning: Boolean = false,
    val isVerifying: Boolean = false,
    val errorMessage: String? = null,
    val isDemo: Boolean = false
)

data class ExerciseSpec(
    val type: ExerciseType,
    val titleEn: String,
    val titleHinglish: String,
    val subtitleEn: String,
    val subtitleHinglish: String,
    val difficultyLabel: String,
    @DrawableRes val imageRes: Int,
    val defaultSets: Int,
    val defaultWorkSeconds: Int,
    val defaultRestSeconds: Int,
    val targetRepsPerSet: Int,
    val isHoldExercise: Boolean,
    val framingGuideEn: String,
    val framingGuideHinglish: String,
    val defaultPlacementTipEn: String,
    val defaultPlacementTipHinglish: String,
    val checkpointsEn: List<String>,
    val checkpointsHinglish: List<String>,
    val caloriesPerSet: Int
) {
    fun title(lang: CoachLanguage): String =
        if (lang == CoachLanguage.HINGLISH) titleHinglish else titleEn

    fun subtitle(lang: CoachLanguage): String =
        if (lang == CoachLanguage.HINGLISH) subtitleHinglish else subtitleEn

    fun framingGuide(lang: CoachLanguage): String =
        if (lang == CoachLanguage.HINGLISH) framingGuideHinglish else framingGuideEn

    fun defaultPlacementTip(lang: CoachLanguage): String =
        if (lang == CoachLanguage.HINGLISH) defaultPlacementTipHinglish else defaultPlacementTipEn

    fun checkpoints(lang: CoachLanguage): List<String> =
        if (lang == CoachLanguage.HINGLISH) checkpointsHinglish else checkpointsEn
}

data class FormFeedback(
    val status: FormStatus = FormStatus.IDLE,
    val badgeText: String = "AI Coach Ready",
    val cueText: String = "",
    val postureTip: String = "",
    val repDetected: Boolean = false,
    val isLowLight: Boolean = false,
    val timestampMs: Long = 0L,
    val isDemoFeedback: Boolean = false
)

data class StreakStats(
    val streakDays: Int = 0,
    val totalWorkouts: Int = 0,
    val totalMinutes: Int = 0,
    val lastWorkoutEpochDay: Long = -1L,
    val beatEnabled: Boolean = true,
    val voiceEnabled: Boolean = true,
    val language: CoachLanguage = CoachLanguage.HINGLISH
)

data class SessionSummaryState(
    val exercise: ExerciseSpec,
    val setsCompleted: Int,
    val repsCompleted: Int,
    val totalWorkSeconds: Int,
    val estimatedCalories: Int,
    val goodFormChecks: Int,
    val totalFormChecks: Int,
    val updatedStreakDays: Int,
    val aiTip: String = "",
    val isAiTipLoading: Boolean = false,
    val aiTipError: String? = null
) {
    val formAccuracyPercent: Int
        get() = if (totalFormChecks == 0) 100 else ((goodFormChecks * 100f) / totalFormChecks).toInt()
}

object StarterWorkouts {
    val all: List<ExerciseSpec> = listOf(
        ExerciseSpec(
            type = ExerciseType.PUSH_UPS,
            titleEn = "Push-Ups",
            titleHinglish = "Push-Ups (Dand)",
            subtitleEn = "Chest, Shoulders & Core Power",
            subtitleHinglish = "Chest, Shoulders aur Core Strength",
            difficultyLabel = "Intermediate",
            imageRes = R.drawable.img_workout_pushups,
            defaultSets = 3,
            defaultWorkSeconds = 45,
            defaultRestSeconds = 20,
            targetRepsPerSet = 12,
            isHoldExercise = false,
            framingGuideEn = "Place camera at side angle so upper & full body is visible",
            framingGuideHinglish = "Camera side mein rakhein ki poori body aur kamar saaf dikhe",
            defaultPlacementTipEn = "Place phone 1-2 ft high against a wall/bottle, 6 ft away in side profile.",
            defaultPlacementTipHinglish = "Phone ko zameen se 1.5 feet ooncha diwar ya bottle ke sahare rakhein aur 6 feet peeche side angle par aayein.",
            checkpointsEn = listOf(
                "Keep spine straight — don't let hips sag",
                "Lower chest close to floor, elbows ~45°",
                "Push through palms, exhale on the way up"
            ),
            checkpointsHinglish = listOf(
                "Kamar bilkul seedhi rakho — hips neeche mat girao",
                "Chest floor ke paas le jao, elbows 45° pe",
                "Upar aate waqt saans chhodo, full lockout karo"
            ),
            caloriesPerSet = 14
        ),
        ExerciseSpec(
            type = ExerciseType.SQUATS,
            titleEn = "Bodyweight Squats",
            titleHinglish = "Squats (Uthak-Baithak)",
            subtitleEn = "Quads, Glutes & Lower Body",
            subtitleHinglish = "Legs, Glutes aur Stamina Booster",
            difficultyLabel = "Beginner",
            imageRes = R.drawable.img_workout_squats,
            defaultSets = 3,
            defaultWorkSeconds = 45,
            defaultRestSeconds = 20,
            targetRepsPerSet = 15,
            isHoldExercise = false,
            framingGuideEn = "Place camera 6-7 ft away so full body from head to feet is visible",
            framingGuideHinglish = "Camera 6-7 feet door rakhein ki sir se pair tak poori body dikhe",
            defaultPlacementTipEn = "Prop phone 2-3 ft high on a table or shelf, step back 6-7 ft facing 45°.",
            defaultPlacementTipHinglish = "Phone ko zameen se 2-3 feet ooncha table ya chair par rakhein aur 6-7 feet peeche jayein taaki sir se pair tak dikhe.",
            checkpointsEn = listOf(
                "Keep chest proud and back neutral",
                "Push hips back until thighs are parallel",
                "Knees track over toes, heels stay planted"
            ),
            checkpointsHinglish = listOf(
                "Seena upar aur kamar seedhi rakho",
                "Hips peeche le jao jab tak thighs parallel na hon",
                "Ghutne toes ki line mein, heels zameen par rakho"
            ),
            caloriesPerSet = 16
        ),
        ExerciseSpec(
            type = ExerciseType.PLANK,
            titleEn = "Forearm Plank",
            titleHinglish = "Plank Hold",
            subtitleEn = "Rock-Solid Core & Posture",
            subtitleHinglish = "Core Stability aur Flat Belly",
            difficultyLabel = "All Levels",
            imageRes = R.drawable.img_workout_plank,
            defaultSets = 3,
            defaultWorkSeconds = 30,
            defaultRestSeconds = 15,
            targetRepsPerSet = 1,
            isHoldExercise = true,
            framingGuideEn = "Place camera in side profile so full body from head to heels is visible",
            framingGuideHinglish = "Side angle pe camera rakhein ki head se heels tak poori body dikhe",
            defaultPlacementTipEn = "Place phone low (1-1.5 ft high) 6-7 ft away in full side profile.",
            defaultPlacementTipHinglish = "Phone ko zameen se 1-1.5 feet ooncha side mein 6-7 feet door rakhein taaki head se heels tak seedhi line dikhe.",
            checkpointsEn = listOf(
                "Elbows directly beneath your shoulders",
                "Engage core & glutes — straight line head to heels",
                "Avoid raising hips too high or dropping lower back"
            ),
            checkpointsHinglish = listOf(
                "Kohni (elbows) bilkul shoulders ke neeche rakho",
                "Pet tight rakho — head se heels tak seedhi line",
                "Hips na zyada upar uthao, na kamar neeche jhukao"
            ),
            caloriesPerSet = 10
        )
    )
}
