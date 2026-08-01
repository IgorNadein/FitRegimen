/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingSessionTimingTest {

    @Test
    fun deadlineAndRemainingAreStable() {
        val deadline = TrainingSessionTiming.deadline(startedAt = 10_000, durationMillis = 30_000)

        assertEquals(40_000, deadline)
        assertEquals(12_500L, TrainingSessionTiming.remainingMillis(now = 27_500, deadlineAt = deadline))
        assertEquals(0L, TrainingSessionTiming.remainingMillis(now = 41_000, deadlineAt = deadline))
        assertNull(TrainingSessionTiming.remainingMillis(now = 27_500, deadlineAt = null))
    }

    @Test
    fun dueOnlyAfterDeadline() {
        assertFalse(TrainingSessionTiming.isDue(now = 999, deadlineAt = 1_000))
        assertTrue(TrainingSessionTiming.isDue(now = 1_000, deadlineAt = 1_000))
        assertTrue(TrainingSessionTiming.isDue(now = 1_001, deadlineAt = 1_000))
        assertFalse(TrainingSessionTiming.isDue(now = 1_001, deadlineAt = null))
    }

    @Test
    fun negativeRestAdjustmentNeverMovesDeadlineIntoPast() {
        assertEquals(
            20_000,
            TrainingSessionTiming.adjustedDeadline(
                now = 20_000,
                deadlineAt = 30_000,
                deltaMillis = -15_000,
            ),
        )
        assertEquals(
            35_000,
            TrainingSessionTiming.adjustedDeadline(
                now = 20_000,
                deadlineAt = 30_000,
                deltaMillis = 5_000,
            ),
        )
    }

    @Test
    fun dueTransitionMapsPersistedPhasesDeterministically() {
        assertEquals(
            TimedPhaseTransition.START_SET,
            TrainingSessionTiming.dueTransition(TrainingSessionPhase.PREPARE, 10_000, 10_000),
        )
        assertEquals(
            TimedPhaseTransition.COMPLETE_SET,
            TrainingSessionTiming.dueTransition(TrainingSessionPhase.ACTIVE_SET, 10_000, 9_000),
        )
        assertEquals(
            TimedPhaseTransition.FINISH_REST,
            TrainingSessionTiming.dueTransition(TrainingSessionPhase.REST, 10_000, 8_000),
        )
        assertEquals(
            TimedPhaseTransition.NONE,
            TrainingSessionTiming.dueTransition(TrainingSessionPhase.READY, 10_000, 8_000),
        )
        assertEquals(
            TimedPhaseTransition.NONE,
            TrainingSessionTiming.dueTransition(TrainingSessionPhase.PREPARE, 9_999, 10_000),
        )
    }

    @Test
    fun pausedRemainingTimeDoesNotIncludeTimeSpentOutsideTheApp() {
        val originalDeadline = TrainingSessionTiming.deadline(
            startedAt = 10_000,
            durationMillis = 30_000,
        )
        val remainingAtExit = TrainingSessionTiming.remainingMillis(
            now = 25_000,
            deadlineAt = originalDeadline,
        )

        // The app is reopened much later. Only an explicit resume creates a new
        // deadline from the persisted remainder.
        val resumedAt = 1_000_000L
        val resumedDeadline = TrainingSessionTiming.deadline(resumedAt, remainingAtExit!!)

        assertEquals(15_000L, remainingAtExit)
        assertEquals(1_015_000L, resumedDeadline)
        assertEquals(
            15_000L,
            TrainingSessionTiming.remainingMillis(resumedAt, resumedDeadline),
        )
    }

    @Test
    fun activeElapsedTimestampMovesPastPause() {
        assertEquals(
            80_000L,
            TrainingSessionTiming.shiftPastPause(
                timestamp = 10_000,
                pausedAt = 20_000,
                resumedAt = 90_000,
            ),
        )
        assertEquals(
            10_000L,
            TrainingSessionTiming.shiftPastPause(
                timestamp = 10_000,
                pausedAt = 90_000,
                resumedAt = 80_000,
            ),
        )
    }

    @Test
    fun elapsedAndAccumulatedPauseKeepOriginalCalendarStart() {
        val accumulated = TrainingSessionTiming.accumulatePausedMillis(
            current = 5_000,
            pausedAt = 20_000,
            resumedAt = 90_000,
        )

        assertEquals(75_000L, accumulated)
        assertEquals(
            20_000L,
            TrainingSessionTiming.elapsedExcludingPauses(
                startedAt = 10_000,
                endedAt = 105_000,
                accumulatedPausedMillis = accumulated,
            )
        )
    }
}
