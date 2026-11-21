package com.example.model

data class ChallengeDayPlan(
    val dayNumber: Int,
    val targetReps: Int,
    val isRestDay: Boolean,
    val titleEn: String,
    val titleHinglish: String,
    val focusHintEn: String,
    val focusHintHinglish: String
)

data class ChallengeDayLog(
    val dayNumber: Int,
    val targetReps: Int,
    val completedReps: Int,
    val formAccuracyPercent: Int,
    val isPostureGood: Boolean,
    val completedAtMillis: Long,
    val isRestCompleted: Boolean = false
) {
    fun postureLabel(language: CoachLanguage): String = when {
        isRestCompleted -> if (language == CoachLanguage.HINGLISH) "Recovery Done" else "Recovery Done"
        isPostureGood -> if (language == CoachLanguage.HINGLISH) "Sahi Posture" else "Good Posture"
        else -> if (language == CoachLanguage.HINGLISH) "Sudhar Zaroori" else "Needs Adjustment"
    }
}

data class ChallengeProgressState(
    val plans: List<ChallengeDayPlan> = ChallengePlan30Days.days,
    val logs: Map<Int, ChallengeDayLog> = emptyMap(),
    val activeDayNumber: Int = 1,
    val selectedDayNumber: Int = 1
) {
    val completedDaysCount: Int
        get() = logs.size

    val totalPushUpsLogged: Int
        get() = logs.values.sumOf { it.completedReps }

    val averageFormAccuracy: Int
        get() {
            val workoutLogs = logs.values.filter { !it.isRestCompleted }
            if (workoutLogs.isEmpty()) return 0
            return workoutLogs.map { it.formAccuracyPercent }.average().toInt()
        }
}

object ChallengePlan30Days {
    val days: List<ChallengeDayPlan> = (1..30).map { day ->
        val isRest = day % 4 == 0 && day < 30
        if (isRest) {
            ChallengeDayPlan(
                dayNumber = day,
                targetReps = 0,
                isRestDay = true,
                titleEn = "Rest & Mobility Day",
                titleHinglish = "Rest & Recovery Din",
                focusHintEn = "Light shoulder & chest stretching + hydration",
                focusHintHinglish = "Halki shoulder stretching karo aur paani peeyo"
            )
        } else {
            val workoutIndex = day - (day / 4)
            val targetReps = when {
                day == 1 -> 10
                day == 2 -> 12
                day == 3 -> 15
                day == 30 -> 50
                else -> (10 + (workoutIndex * 2)).coerceAtMost(50)
            }
            ChallengeDayPlan(
                dayNumber = day,
                targetReps = targetReps,
                isRestDay = false,
                titleEn = "Day $day: $targetReps Push-Ups",
                titleHinglish = "Day $day: $targetReps Push-Ups",
                focusHintEn = if (day <= 10) "Focus on flat back & controlled descent" else "Full chest-to-floor depth & steady tempo",
                focusHintHinglish = if (day <= 10) "Kamar seedhi rakho aur control se neeche jao" else "Full depth aur tight core ke saath reps poore karo"
            )
        }
    }

    fun buildComparisonMessage(
        latestLog: ChallengeDayLog?,
        previousWorkoutLog: ChallengeDayLog?,
        language: CoachLanguage
    ): String {
        val hinglish = language == CoachLanguage.HINGLISH
        if (latestLog == null) {
            return if (hinglish) {
                "Day 1 se shuruat karo! Roz Push-Ups aur sahi posture track karke 30 din mein tagda stamina banao."
            } else {
                "Start Day 1 today! Track daily Push-Ups and posture accuracy across your 30-day transformation."
            }
        }

        if (latestLog.isRestCompleted) {
            return if (hinglish) {
                "Recovery day poora hua! Muscles fresh hain — agle Push-Up day ke liye taiyaar raho."
            } else {
                "Recovery day complete! Your muscles are primed for the next Push-Up challenge."
            }
        }

        if (previousWorkoutLog == null) {
            return if (latestLog.isPostureGood) {
                if (hinglish) {
                    "Day ${latestLog.dayNumber} zabardast shuruat! ${latestLog.completedReps} reps aur ${latestLog.formAccuracyPercent}% Sahi Posture."
                } else {
                    "Awesome start on Day ${latestLog.dayNumber}! ${latestLog.completedReps} reps with ${latestLog.formAccuracyPercent}% Good Form."
                }
            } else {
                if (hinglish) {
                    "Aaj ${latestLog.completedReps} reps poore hue, par kamar aur elbows pe thoda aur dhyaan do."
                } else {
                    "Completed ${latestLog.completedReps} reps today — focus on keeping your back straight next round."
                }
            }
        }

        val repDiff = latestLog.completedReps - previousWorkoutLog.completedReps
        val formDiff = latestLog.formAccuracyPercent - previousWorkoutLog.formAccuracyPercent

        return when {
            repDiff > 0 && latestLog.isPostureGood -> if (hinglish) {
                "Kal se $repDiff reps zyada kiye, shabaash! Form bhi ${latestLog.formAccuracyPercent}% zabardast raha."
            } else {
                "Awesome! $repDiff more reps than your last workout with ${latestLog.formAccuracyPercent}% Good Form!"
            }

            repDiff > 0 && !latestLog.isPostureGood -> if (hinglish) {
                "Kal se $repDiff reps zyada kiye, par kamar pe thoda aur dhyaan do (${latestLog.formAccuracyPercent}% form)."
            } else {
                "$repDiff more reps than last session, but tighten your core for cleaner posture (${latestLog.formAccuracyPercent}%)."
            }

            latestLog.completedReps >= latestLog.targetReps && !latestLog.isPostureGood -> if (hinglish) {
                "Aaj reps poore hue (${latestLog.completedReps}), par kamar aur depth pe thoda aur dhyaan do!"
            } else {
                "Target reps hit (${latestLog.completedReps}), now focus on keeping your back flat!"
            }

            formDiff > 0 -> if (hinglish) {
                "Pichle din se form accuracy +$formDiff% behtar hui! Bahut badhiya discipline."
            } else {
                "Form accuracy improved by +$formDiff% compared to your previous workout!"
            }

            else -> if (hinglish) {
                "Day ${latestLog.dayNumber} complete! Roz thoda push karo, consistency hi asli taakat hai."
            } else {
                "Day ${latestLog.dayNumber} logged! Stay consistent and aim to beat today's reps tomorrow."
            }
        }
    }
}
