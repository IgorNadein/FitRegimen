/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.ui.session

import de.tadris.fitness.data.TrainingSessionPhase
import de.tadris.fitness.data.TrainingSessionStatus

/**
 * Pure policy for route/session lifetime decisions.
 *
 * An existing route is opened in a paused state so a wall-clock timer can never
 * silently continue after process death. A brand-new session is the only route
 * that may initially remain active (it is still in READY and has no running timer).
 */
internal object GuidedSessionPersistencePolicy {

    fun shouldPauseOnOpen(
        pauseOnOpen: Boolean,
        status: TrainingSessionStatus,
        phase: TrainingSessionPhase,
        hasPersistedProgress: Boolean,
    ): Boolean = status == TrainingSessionStatus.ACTIVE && (
        pauseOnOpen ||
            phase != TrainingSessionPhase.READY ||
            hasPersistedProgress
        )

    fun shouldPauseOnExit(status: TrainingSessionStatus): Boolean =
        status == TrainingSessionStatus.ACTIVE

    /** A child recorder owns the active set and must not trigger route-lifetime pausing. */
    fun shouldPauseForLifecycleStop(
        navigatingToRecorder: Boolean,
        isChangingConfigurations: Boolean = false,
    ): Boolean = !navigatingToRecorder && !isChangingConfigurations
}
