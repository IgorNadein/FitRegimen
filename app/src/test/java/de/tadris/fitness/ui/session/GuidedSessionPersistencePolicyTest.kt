/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.ui.session

import de.tadris.fitness.data.TrainingSessionPhase
import de.tadris.fitness.data.TrainingSessionStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedSessionPersistencePolicyTest {

    @Test
    fun brandNewReadyRouteIsNotPausedOnFirstOpen() {
        assertFalse(
            GuidedSessionPersistencePolicy.shouldPauseOnOpen(
                pauseOnOpen = false,
                status = TrainingSessionStatus.ACTIVE,
                phase = TrainingSessionPhase.READY,
                hasPersistedProgress = false,
            )
        )
    }

    @Test
    fun recoveredActiveRouteIsPausedBeforeTimeReconciliation() {
        assertTrue(
            GuidedSessionPersistencePolicy.shouldPauseOnOpen(
                pauseOnOpen = true,
                status = TrainingSessionStatus.ACTIVE,
                phase = TrainingSessionPhase.READY,
                hasPersistedProgress = false,
            )
        )
    }

    @Test
    fun restoredProgressIsPausedEvenWhenSerializedRouteSaysItWasNew() {
        listOf(
            TrainingSessionPhase.PREPARE,
            TrainingSessionPhase.ACTIVE_SET,
            TrainingSessionPhase.REST,
        ).forEach { phase ->
            assertTrue(
                GuidedSessionPersistencePolicy.shouldPauseOnOpen(
                    pauseOnOpen = false,
                    status = TrainingSessionStatus.ACTIVE,
                    phase = phase,
                    hasPersistedProgress = false,
                )
            )
        }
        assertTrue(
            GuidedSessionPersistencePolicy.shouldPauseOnOpen(
                pauseOnOpen = false,
                status = TrainingSessionStatus.ACTIVE,
                phase = TrainingSessionPhase.READY,
                hasPersistedProgress = true,
            )
        )
    }

    @Test
    fun recoveredPausedOrTerminalRouteIsNotMutatedAgain() {
        listOf(
            TrainingSessionStatus.PAUSED,
            TrainingSessionStatus.COMPLETED,
            TrainingSessionStatus.ABORTED,
        ).forEach { status ->
            assertFalse(
                GuidedSessionPersistencePolicy.shouldPauseOnOpen(
                    pauseOnOpen = true,
                    status = status,
                    phase = TrainingSessionPhase.ACTIVE_SET,
                    hasPersistedProgress = true,
                )
            )
        }
    }

    @Test
    fun ordinaryExitPausesOnlyAnActiveSession() {
        assertTrue(GuidedSessionPersistencePolicy.shouldPauseOnExit(TrainingSessionStatus.ACTIVE))
        assertFalse(GuidedSessionPersistencePolicy.shouldPauseOnExit(TrainingSessionStatus.PAUSED))
        assertFalse(GuidedSessionPersistencePolicy.shouldPauseOnExit(TrainingSessionStatus.COMPLETED))
        assertFalse(GuidedSessionPersistencePolicy.shouldPauseOnExit(TrainingSessionStatus.ABORTED))
    }

    @Test
    fun recorderNavigationDoesNotTriggerLifecyclePause() {
        assertFalse(
            GuidedSessionPersistencePolicy.shouldPauseForLifecycleStop(
                navigatingToRecorder = true,
            )
        )
        assertTrue(
            GuidedSessionPersistencePolicy.shouldPauseForLifecycleStop(
                navigatingToRecorder = false,
            )
        )
        assertFalse(
            GuidedSessionPersistencePolicy.shouldPauseForLifecycleStop(
                navigatingToRecorder = false,
                isChangingConfigurations = true,
            )
        )
    }
}
