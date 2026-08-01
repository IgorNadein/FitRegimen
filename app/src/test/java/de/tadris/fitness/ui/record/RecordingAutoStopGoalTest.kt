/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.ui.record

import de.tadris.fitness.data.CompletionRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingAutoStopGoalTest {

    @Test
    fun durationGoalUsesActiveTime() {
        val goal = RecordingAutoStopGoal(
            completionRule = CompletionRule.DURATION_REACHED,
            durationMillis = 60_000,
        )

        assertFalse(goal.isReached(59_999, 10_000.0, 100))
        assertTrue(goal.isReached(60_000, 0.0, 0))
    }

    @Test
    fun metricGoalUsesDistanceOrRepetitions() {
        val distance = RecordingAutoStopGoal(
            completionRule = CompletionRule.METRIC_TARGET_REACHED,
            distanceMeters = 1_000.0,
        )
        val repetitions = RecordingAutoStopGoal(
            completionRule = CompletionRule.METRIC_TARGET_REACHED,
            repetitions = 12,
        )

        assertTrue(distance.isReached(1, 1_000.0, 0))
        assertFalse(distance.isReached(1, 999.9, 100))
        assertTrue(repetitions.isReached(1, 0.0, 12))
    }

    @Test
    fun manualRuleNeverStopsRecorder() {
        val goal = RecordingAutoStopGoal(
            completionRule = CompletionRule.USER_CONFIRM,
            durationMillis = 1,
            distanceMeters = 1.0,
            repetitions = 1,
        )

        assertFalse(goal.isReached(100, 100.0, 100))
    }
}
