package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ChallengeDayLog
import com.example.model.ChallengePlan30Days
import com.example.model.ChallengeProgressState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.challengeDataStore by preferencesDataStore(name = "joshfit_challenge_30d")

class ChallengeRepository(private val context: Context) {

    private object Keys {
        val LOGS_JSON = stringPreferencesKey("challenge_logs_json")
        val ACTIVE_DAY = intPreferencesKey("active_challenge_day")
    }

    val progressFlow: Flow<ChallengeProgressState> = context.challengeDataStore.data.map { prefs ->
        val logsJson = prefs[Keys.LOGS_JSON] ?: "[]"
        val logsMap = parseLogs(logsJson)
        val storedActive = prefs[Keys.ACTIVE_DAY] ?: computeNextActiveDay(logsMap)
        val activeDay = storedActive.coerceIn(1, 30)
        ChallengeProgressState(
            plans = ChallengePlan30Days.days,
            logs = logsMap,
            activeDayNumber = activeDay,
            selectedDayNumber = activeDay
        )
    }

    suspend fun saveDayWorkoutLog(
        dayNumber: Int,
        completedReps: Int,
        formAccuracyPercent: Int,
        isRestCompleted: Boolean = false
    ) {
        val clampedDay = dayNumber.coerceIn(1, 30)
        val plan = ChallengePlan30Days.days[clampedDay - 1]
        val clampedAccuracy = formAccuracyPercent.coerceIn(0, 100)
        val entry = ChallengeDayLog(
            dayNumber = clampedDay,
            targetReps = plan.targetReps,
            completedReps = completedReps.coerceAtLeast(0),
            formAccuracyPercent = clampedAccuracy,
            isPostureGood = clampedAccuracy >= 75 || isRestCompleted,
            completedAtMillis = System.currentTimeMillis(),
            isRestCompleted = isRestCompleted
        )

        context.challengeDataStore.edit { prefs ->
            val currentMap = parseLogs(prefs[Keys.LOGS_JSON] ?: "[]").toMutableMap()
            currentMap[clampedDay] = entry
            prefs[Keys.LOGS_JSON] = serializeLogs(currentMap)
            prefs[Keys.ACTIVE_DAY] = computeNextActiveDay(currentMap)
        }
    }

    suspend fun setActiveDay(dayNumber: Int) {
        context.challengeDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_DAY] = dayNumber.coerceIn(1, 30)
        }
    }

    private fun computeNextActiveDay(logs: Map<Int, ChallengeDayLog>): Int {
        for (day in 1..30) {
            if (!logs.containsKey(day)) return day
        }
        return 30
    }

    private fun parseLogs(raw: String): Map<Int, ChallengeDayLog> {
        return try {
            val array = JSONArray(raw)
            val result = mutableMapOf<Int, ChallengeDayLog>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val day = obj.getInt("dayNumber")
                result[day] = ChallengeDayLog(
                    dayNumber = day,
                    targetReps = obj.optInt("targetReps", 10),
                    completedReps = obj.optInt("completedReps", 0),
                    formAccuracyPercent = obj.optInt("formAccuracyPercent", 100),
                    isPostureGood = obj.optBoolean("isPostureGood", true),
                    completedAtMillis = obj.optLong("completedAtMillis", System.currentTimeMillis()),
                    isRestCompleted = obj.optBoolean("isRestCompleted", false)
                )
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun serializeLogs(map: Map<Int, ChallengeDayLog>): String {
        val array = JSONArray()
        map.values.sortedBy { it.dayNumber }.forEach { log ->
            val obj = JSONObject().apply {
                put("dayNumber", log.dayNumber)
                put("targetReps", log.targetReps)
                put("completedReps", log.completedReps)
                put("formAccuracyPercent", log.formAccuracyPercent)
                put("isPostureGood", log.isPostureGood)
                put("completedAtMillis", log.completedAtMillis)
                put("isRestCompleted", log.isRestCompleted)
            }
            array.put(obj)
        }
        return array.toString()
    }
}
