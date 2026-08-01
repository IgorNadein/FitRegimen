/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data

import kotlin.math.max

internal const val GUIDED_WORKOUT_TYPE_ID = "program_training"
private const val GUIDED_WORKOUT_UUID_PREFIX = "guided-session:"

/**
 * Pure projection from a terminal guided-session snapshot to the legacy Workout
 * read model used by the dashboard and statistics screens.
 *
 * Recorder workouts remain the authoritative owners of their samples. They are
 * used only for energy here, once per distinct workout id; performed set metrics
 * are already snapshotted on [SessionSet] and are therefore safe to aggregate
 * across manual, timer and recorder execution modes alike.
 */
internal object GuidedWorkoutHistoryMapper {

    fun summaryUuid(sessionUuid: String): String = GUIDED_WORKOUT_UUID_PREFIX + sessionUuid

    fun toWorkout(
        session: TrainingSession,
        exercises: List<SessionExercise>,
        setsByExerciseId: Map<Long, List<SessionSet>>,
        linkedWorkoutsById: Map<Long, Workout>,
        workoutId: Long,
    ): Workout? {
        if (session.status != TrainingSessionStatus.COMPLETED &&
            session.status != TrainingSessionStatus.ABORTED
        ) {
            return null
        }

        val completedSets = exercises
            .sortedBy { it.orderIndex }
            .flatMap { exercise ->
                setsByExerciseId[exercise.id]
                    .orEmpty()
                    .sortedBy { it.orderIndex }
            }
            .filter { it.status == SessionSetStatus.COMPLETED }
        if (completedSets.isEmpty()) return null

        val activeDuration = completedSets.sumNonNegativeLong { it.actualDurationMillis }
        val repetitions = completedSets
            .sumOf { it.actualReps?.coerceAtLeast(0)?.toLong() ?: 0L }
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val distance = completedSets.sumOf { set ->
            set.actualDistanceMeters
                ?.takeIf(Double::isFinite)
                ?.coerceAtLeast(0.0)
                ?: 0.0
        }
        val linkedWorkoutIds = completedSets.mapNotNull { it.linkedWorkoutId }.toSet()
        val energy = linkedWorkoutIds.sumOf { linkedId ->
            linkedWorkoutsById[linkedId]
                ?.energy
                ?.takeIf(Double::isFinite)
                ?.coerceAtLeast(0.0)
                ?: 0.0
        }

        val terminalAt = session.completedAt
            ?: completedSets.mapNotNull { it.completedAt }.maxOrNull()
            ?: session.updatedAt
        // Workout.pauseDuration assumes totalDuration >= activeDuration.
        val end = max(max(session.startedAt, terminalAt), session.startedAt + activeDuration)

        return Workout(
            id = workoutId,
            uuid = summaryUuid(session.uuid),
            start = session.startedAt,
            end = end,
            activeDuration = activeDuration,
            comment = session.nameSnapshot,
            distance = distance,
            repetitions = repetitions,
            energy = energy,
            workoutTypeId = GUIDED_WORKOUT_TYPE_ID,
            intervalSetUsedId = -1L,
            profileUuid = session.profileUuidSnapshot,
            userWeightKgSnapshot = session.userWeightKgSnapshot,
            stepLengthMetersSnapshot = session.stepLengthMetersSnapshot,
        )
    }

    private inline fun List<SessionSet>.sumNonNegativeLong(value: (SessionSet) -> Long?): Long {
        var total = 0L
        for (set in this) {
            val next = value(set)?.coerceAtLeast(0L) ?: 0L
            total = if (Long.MAX_VALUE - total < next) Long.MAX_VALUE else total + next
        }
        return total
    }
}
