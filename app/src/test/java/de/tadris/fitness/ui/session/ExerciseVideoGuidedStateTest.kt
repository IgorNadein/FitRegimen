/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.ui.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseVideoGuidedStateTest {

    @Test
    fun `opening video pauses only a running preparation or active set`() {
        assertTrue(
            shouldPauseGuidedWorkoutForVideo(GuidedWorkoutPhase.PREPARING, timerIsRunning = true)
        )
        assertTrue(
            shouldPauseGuidedWorkoutForVideo(GuidedWorkoutPhase.ACTIVE, timerIsRunning = true)
        )
        assertFalse(
            shouldPauseGuidedWorkoutForVideo(GuidedWorkoutPhase.ACTIVE, timerIsRunning = false)
        )
        assertFalse(
            shouldPauseGuidedWorkoutForVideo(GuidedWorkoutPhase.REST, timerIsRunning = true)
        )
        assertFalse(
            shouldPauseGuidedWorkoutForVideo(GuidedWorkoutPhase.PAUSED, timerIsRunning = false)
        )
    }

    @Test
    fun `closing video resumes only the pause created by video`() {
        assertTrue(
            shouldResumeGuidedWorkoutAfterVideo(
                pausedByVideo = true,
                currentPhase = GuidedWorkoutPhase.PAUSED,
            )
        )
        assertFalse(
            shouldResumeGuidedWorkoutAfterVideo(
                pausedByVideo = false,
                currentPhase = GuidedWorkoutPhase.PAUSED,
            )
        )
        assertFalse(
            shouldResumeGuidedWorkoutAfterVideo(
                pausedByVideo = true,
                currentPhase = GuidedWorkoutPhase.COMPLETE,
            )
        )
    }
}
