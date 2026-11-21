package com.example

import com.example.model.ChallengeDayLog
import com.example.model.ChallengePlan30Days
import com.example.model.CoachLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun challengePlan_has30DaysWithProgressiveRepsAndRestDays() {
        val days = ChallengePlan30Days.days
        assertEquals(30, days.size)
        assertEquals(10, days[0].targetReps)
        assertFalse(days[0].isRestDay)
        assertTrue(days[3].isRestDay) // Day 4 is rest/recovery
        assertEquals(0, days[3].targetReps)
        assertTrue(days[29].targetReps >= 45)
    }

    @Test
    fun comparisonMessage_highlightsRepImprovementInHinglish() {
        val prev = ChallengeDayLog(
            dayNumber = 1,
            targetReps = 10,
            completedReps = 10,
            formAccuracyPercent = 82,
            isPostureGood = true,
            completedAtMillis = 1000L
        )
        val today = ChallengeDayLog(
            dayNumber = 2,
            targetReps = 12,
            completedReps = 13,
            formAccuracyPercent = 88,
            isPostureGood = true,
            completedAtMillis = 2000L
        )
        val message = ChallengePlan30Days.buildComparisonMessage(today, prev, CoachLanguage.HINGLISH)
        assertTrue(message.contains("3 reps zyada"))
    }
}
