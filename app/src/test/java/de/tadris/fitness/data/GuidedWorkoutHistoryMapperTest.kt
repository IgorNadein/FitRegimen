/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuidedWorkoutHistoryMapperTest {

    @Test
    fun `terminal session aggregates manual timer and distinct recorder results`() {
        val session = session(status = TrainingSessionStatus.COMPLETED, completedAt = 20_000L)
        val manual = exercise(id = 11L, orderIndex = 0)
        val tracker = exercise(id = 12L, orderIndex = 1)
        val sets = mapOf(
            manual.id to listOf(
                completedSet(manual.id, 0, duration = 2_000L, reps = 8),
                completedSet(manual.id, 1, duration = 3_000L, reps = 7),
            ),
            tracker.id to listOf(
                completedSet(tracker.id, 0, duration = 4_000L, distance = 900.5, linkedWorkoutId = 91L),
                completedSet(tracker.id, 1, duration = 5_000L, distance = 1_100.5, linkedWorkoutId = 91L),
            ),
        )

        val result = GuidedWorkoutHistoryMapper.toWorkout(
            session = session,
            exercises = listOf(tracker, manual),
            setsByExerciseId = sets,
            linkedWorkoutsById = mapOf(91L to linkedWorkout(91L, energy = 12_345.0)),
            workoutId = 77L,
        )!!

        assertEquals(77L, result.id)
        assertEquals("guided-session:session-uuid", result.uuid)
        assertEquals(14_000L, result.activeDuration)
        assertEquals(15, result.repetitions)
        assertEquals(2_001.0, result.distance, 0.0)
        assertEquals(12_345.0, result.energy, 0.0)
        assertEquals(GUIDED_WORKOUT_TYPE_ID, result.workoutTypeId)
        assertEquals("Full body", result.comment)
        assertEquals(20_000L, result.end)
    }

    @Test
    fun `same recorder workout contributes energy once`() {
        val exercise = exercise(id = 11L, orderIndex = 0)
        val result = GuidedWorkoutHistoryMapper.toWorkout(
            session = session(status = TrainingSessionStatus.ABORTED, completedAt = 5_000L),
            exercises = listOf(exercise),
            setsByExerciseId = mapOf(
                exercise.id to listOf(
                    completedSet(exercise.id, 0, linkedWorkoutId = 5L),
                    completedSet(exercise.id, 1, linkedWorkoutId = 5L),
                )
            ),
            linkedWorkoutsById = mapOf(5L to linkedWorkout(5L, energy = 900.0)),
            workoutId = 1L,
        )!!

        assertEquals(900.0, result.energy, 0.0)
    }

    @Test
    fun `abort without completed sets does not create history`() {
        val exercise = exercise(id = 11L, orderIndex = 0)
        val pending = SessionSet(sessionExerciseId = exercise.id, id = 1L, orderIndex = 0)

        val result = GuidedWorkoutHistoryMapper.toWorkout(
            session = session(status = TrainingSessionStatus.ABORTED, completedAt = 2_000L),
            exercises = listOf(exercise),
            setsByExerciseId = mapOf(exercise.id to listOf(pending)),
            linkedWorkoutsById = emptyMap(),
            workoutId = 1L,
        )

        assertNull(result)
    }

    @Test
    fun `active session never leaks into legacy history`() {
        val exercise = exercise(id = 11L, orderIndex = 0)

        val result = GuidedWorkoutHistoryMapper.toWorkout(
            session = session(status = TrainingSessionStatus.ACTIVE, completedAt = null),
            exercises = listOf(exercise),
            setsByExerciseId = mapOf(exercise.id to listOf(completedSet(exercise.id, 0, reps = 5))),
            linkedWorkoutsById = emptyMap(),
            workoutId = 1L,
        )

        assertNull(result)
    }

    @Test
    fun `end is extended when performed duration exceeds wall clock`() {
        val exercise = exercise(id = 11L, orderIndex = 0)
        val result = GuidedWorkoutHistoryMapper.toWorkout(
            session = session(status = TrainingSessionStatus.COMPLETED, completedAt = 2_000L),
            exercises = listOf(exercise),
            setsByExerciseId = mapOf(
                exercise.id to listOf(completedSet(exercise.id, 0, duration = 5_000L))
            ),
            linkedWorkoutsById = emptyMap(),
            workoutId = 1L,
        )!!

        assertEquals(6_000L, result.end)
        assertEquals(0L, result.pauseDuration)
    }

    @Test
    fun `guided workout copies profile and measurement snapshots`() {
        val exercise = exercise(id = 11L, orderIndex = 0)
        val result = GuidedWorkoutHistoryMapper.toWorkout(
            session = session(
                status = TrainingSessionStatus.COMPLETED,
                completedAt = 2_000L,
            ).copy(
                profileUuidSnapshot = "profile-uuid",
                userWeightKgSnapshot = 72.4,
                stepLengthMetersSnapshot = 0.73,
            ),
            exercises = listOf(exercise),
            setsByExerciseId = mapOf(
                exercise.id to listOf(completedSet(exercise.id, 0, duration = 500L))
            ),
            linkedWorkoutsById = emptyMap(),
            workoutId = 1L,
        )!!

        assertEquals("profile-uuid", result.profileUuid)
        assertEquals(72.4, result.userWeightKgSnapshot!!, 0.0)
        assertEquals(0.73, result.stepLengthMetersSnapshot!!, 0.0)
    }

    private fun session(
        status: TrainingSessionStatus,
        completedAt: Long?,
    ) = TrainingSession(
        id = 7L,
        uuid = "session-uuid",
        nameSnapshot = "Full body",
        status = status,
        phase = if (completedAt == null) TrainingSessionPhase.ACTIVE_SET else TrainingSessionPhase.FINISHED,
        startedAt = 1_000L,
        updatedAt = completedAt ?: 1_500L,
        completedAt = completedAt,
    )

    private fun exercise(id: Long, orderIndex: Int) = SessionExercise(
        sessionId = 7L,
        id = id,
        orderIndex = orderIndex,
        exerciseUuidSnapshot = "exercise-$id",
        nameSnapshot = "Exercise $id",
        metricKindSnapshot = MetricKind.REPS,
        measurementSource = MeasurementSource.USER_INPUT,
        completionRule = CompletionRule.USER_CONFIRM,
        status = SessionExerciseStatus.COMPLETED,
    )

    private fun completedSet(
        exerciseId: Long,
        orderIndex: Int,
        duration: Long? = null,
        reps: Int? = null,
        distance: Double? = null,
        linkedWorkoutId: Long? = null,
    ) = SessionSet(
        sessionExerciseId = exerciseId,
        id = exerciseId * 10 + orderIndex,
        orderIndex = orderIndex,
        status = SessionSetStatus.COMPLETED,
        actualDurationMillis = duration,
        actualReps = reps,
        actualDistanceMeters = distance,
        linkedWorkoutId = linkedWorkoutId,
        startedAt = 1_000L,
        completedAt = 2_000L,
    )

    private fun linkedWorkout(id: Long, energy: Double) = Workout(
        id = id,
        uuid = "linked-$id",
        start = 1_000L,
        end = 2_000L,
        activeDuration = 1_000L,
        distance = 0.0,
        repetitions = 0,
        energy = energy,
        workoutTypeId = "running",
        intervalSetUsedId = -1L,
    )
}
