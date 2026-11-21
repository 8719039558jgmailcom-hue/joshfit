package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.CoachLanguage
import com.example.model.StreakStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

private val Context.joshFitDataStore by preferencesDataStore(name = "joshfit_prefs")

class StreakRepository(private val context: Context) {

    private object Keys {
        val STREAK_DAYS = intPreferencesKey("streak_days")
        val TOTAL_WORKOUTS = intPreferencesKey("total_workouts")
        val TOTAL_MINUTES = intPreferencesKey("total_minutes")
        val LAST_WORKOUT_EPOCH_DAY = longPreferencesKey("last_workout_epoch_day")
        val BEAT_ENABLED = booleanPreferencesKey("beat_enabled")
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val LANGUAGE = stringPreferencesKey("coach_language")
    }

    val statsFlow: Flow<StreakStats> = context.joshFitDataStore.data.map { prefs ->
        val langName = prefs[Keys.LANGUAGE] ?: CoachLanguage.HINGLISH.name
        val language = runCatching { CoachLanguage.valueOf(langName) }
            .getOrDefault(CoachLanguage.HINGLISH)
        StreakStats(
            streakDays = prefs[Keys.STREAK_DAYS] ?: 0,
            totalWorkouts = prefs[Keys.TOTAL_WORKOUTS] ?: 0,
            totalMinutes = prefs[Keys.TOTAL_MINUTES] ?: 0,
            lastWorkoutEpochDay = prefs[Keys.LAST_WORKOUT_EPOCH_DAY] ?: -1L,
            beatEnabled = prefs[Keys.BEAT_ENABLED] ?: true,
            voiceEnabled = prefs[Keys.VOICE_ENABLED] ?: true,
            language = language
        )
    }

    suspend fun recordCompletedWorkout(activeSeconds: Int): Int {
        val todayEpochDay = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
        var updatedStreak = 1
        context.joshFitDataStore.edit { prefs ->
            val currentStreak = prefs[Keys.STREAK_DAYS] ?: 0
            val lastDay = prefs[Keys.LAST_WORKOUT_EPOCH_DAY] ?: -1L

            updatedStreak = when {
                lastDay == todayEpochDay -> maxOf(1, currentStreak)
                lastDay == todayEpochDay - 1L -> currentStreak + 1
                else -> 1
            }

            val addedMinutes = maxOf(1, (activeSeconds + 30) / 60)
            prefs[Keys.STREAK_DAYS] = updatedStreak
            prefs[Keys.LAST_WORKOUT_EPOCH_DAY] = todayEpochDay
            prefs[Keys.TOTAL_WORKOUTS] = (prefs[Keys.TOTAL_WORKOUTS] ?: 0) + 1
            prefs[Keys.TOTAL_MINUTES] = (prefs[Keys.TOTAL_MINUTES] ?: 0) + addedMinutes
        }
        return updatedStreak
    }

    suspend fun setBeatEnabled(enabled: Boolean) {
        context.joshFitDataStore.edit { it[Keys.BEAT_ENABLED] = enabled }
    }

    suspend fun setVoiceEnabled(enabled: Boolean) {
        context.joshFitDataStore.edit { it[Keys.VOICE_ENABLED] = enabled }
    }

    suspend fun setLanguage(language: CoachLanguage) {
        context.joshFitDataStore.edit { it[Keys.LANGUAGE] = language.name }
    }
}
