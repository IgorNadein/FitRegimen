/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data

import kotlin.math.max

/** Pure timing primitives shared by the persisted runner and local unit tests. */
object TrainingSessionTiming {

    fun deadline(startedAt: Long, durationMillis: Long): Long =
        startedAt + durationMillis.coerceAtLeast(0)

    fun remainingMillis(now: Long, deadlineAt: Long?): Long? =
        deadlineAt?.let { max(0L, it - now) }

    fun isDue(now: Long, deadlineAt: Long?): Boolean =
        deadlineAt != null && now >= deadlineAt

    fun adjustedDeadline(now: Long, deadlineAt: Long, deltaMillis: Long): Long =
        max(now, deadlineAt + deltaMillis)

    fun shiftPastPause(timestamp: Long, pausedAt: Long, resumedAt: Long): Long =
        timestamp + max(0L, resumedAt - pausedAt)

    fun accumulatePausedMillis(current: Long, pausedAt: Long, resumedAt: Long): Long {
        val safeCurrent = current.coerceAtLeast(0L)
        val delta = max(0L, resumedAt - pausedAt)
        return if (Long.MAX_VALUE - safeCurrent < delta) Long.MAX_VALUE else safeCurrent + delta
    }

    fun elapsedExcludingPauses(startedAt: Long, endedAt: Long, accumulatedPausedMillis: Long): Long =
        max(0L, endedAt - startedAt - accumulatedPausedMillis.coerceAtLeast(0L))

    fun dueTransition(
        phase: TrainingSessionPhase,
        now: Long,
        deadlineAt: Long?,
    ): TimedPhaseTransition {
        if (!isDue(now, deadlineAt)) return TimedPhaseTransition.NONE
        return when (phase) {
            TrainingSessionPhase.PREPARE -> TimedPhaseTransition.START_SET
            TrainingSessionPhase.ACTIVE_SET -> TimedPhaseTransition.COMPLETE_SET
            TrainingSessionPhase.REST -> TimedPhaseTransition.FINISH_REST
            else -> TimedPhaseTransition.NONE
        }
    }
}

enum class TimedPhaseTransition {
    NONE,
    START_SET,
    COMPLETE_SET,
    FINISH_REST,
}

data class SessionSetMetrics(
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationMillis: Long? = null,
    val distanceMeters: Double? = null,
)

data class TrainingSessionState(
    val session: TrainingSession,
    val exercise: SessionExercise?,
    val set: SessionSet?,
    val exerciseCount: Int,
    val setCount: Int,
    val remainingMillis: Long?,
)
