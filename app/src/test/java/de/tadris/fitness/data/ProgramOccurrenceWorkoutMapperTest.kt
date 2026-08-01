/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgramOccurrenceWorkoutMapperTest {

    @Test
    fun `no completed exercise produces no workout`() {
        val result = map(
            results = listOf(
                result(
                    completion = completion(id = 1, status = PROGRAM_COMPLETION_STATUS_SKIPPED),
                    plans = listOf(plan(reps = 12)),
                )
            )
        )

        assertNull(result)
    }

    @Test
    fun `quick completion uses planned set targets`() {
        val result = map(
            results = listOf(
                result(
                    completion = completion(id = 1),
                    plans = listOf(
                        plan(reps = 8, repsMax = 12, duration = 30_000, distance = 500.0),
                        plan(reps = 7, duration = 20_000, distance = 250.0),
                    ),
                )
            )
        )!!

        assertEquals(19, result.repetitions)
        assertEquals(50_000L, result.activeDuration)
        assertEquals(750.0, result.distance, 0.0)
        assertEquals(OCCURRENCE + 50_000L, result.end)
    }

    @Test
    fun `actual logs replace planned values`() {
        val result = map(
            results = listOf(
                result(
                    completion = completion(id = 1),
                    plans = listOf(plan(reps = 20, duration = 60_000, distance = 1_000.0)),
                    logs = listOf(log(reps = 9, duration = 15_000, distance = 125.0)),
                )
            )
        )!!

        assertEquals(9, result.repetitions)
        assertEquals(15_000L, result.activeDuration)
        assertEquals(125.0, result.distance, 0.0)
    }

    @Test
    fun `linked workout replaces plan and is counted once`() {
        val linked = workout(
            id = 91,
            uuid = "recorded-91",
            duration = 75_000,
            repetitions = 32,
            distance = 1_250.0,
            energy = 5_000.0,
        )
        val result = map(
            results = listOf(
                result(
                    completion = completion(id = 1, workoutId = linked.id),
                    plans = listOf(plan(reps = 100, duration = 600_000, distance = 10_000.0)),
                ),
                result(
                    completion = completion(id = 2, workoutId = linked.id),
                    plans = listOf(plan(reps = 100, duration = 600_000, distance = 10_000.0)),
                ),
            ),
            linked = listOf(linked, linked),
        )!!

        assertEquals(32, result.repetitions)
        assertEquals(75_000L, result.activeDuration)
        assertEquals(1_250.0, result.distance, 0.0)
        assertEquals(5_000.0, result.energy, 0.0)
    }

    @Test
    fun `logs own metrics while linked workout owns measured energy`() {
        val linked = workout(
            id = 91,
            uuid = "recorded-91",
            duration = 75_000,
            repetitions = 32,
            distance = 1_250.0,
            energy = 5_000.0,
        )
        val result = map(
            results = listOf(
                result(
                    completion = completion(id = 1, workoutId = linked.id),
                    logs = listOf(log(reps = 5, duration = 10_000, distance = 50.0)),
                )
            ),
            linked = listOf(linked),
        )!!

        assertEquals(5, result.repetitions)
        assertEquals(10_000L, result.activeDuration)
        assertEquals(50.0, result.distance, 0.0)
        assertEquals(5_000.0, result.energy, 0.0)
    }

    @Test
    fun `generated guided workout is never counted as a source`() {
        val generated = workout(
            id = 91,
            uuid = "guided-session:old-session",
            duration = 75_000,
            repetitions = 32,
            distance = 1_250.0,
            energy = 5_000.0,
        )
        val result = map(
            results = listOf(
                result(completion = completion(id = 1, workoutId = generated.id))
            ),
            linked = listOf(generated),
        )!!

        assertEquals(0, result.repetitions)
        assertEquals(0L, result.activeDuration)
        assertEquals(0.0, result.distance, 0.0)
        assertEquals(0.0, result.energy, 0.0)
    }

    @Test
    fun `identity and occurrence timestamp stay deterministic`() {
        val result = map(
            workoutId = 77,
            results = listOf(result(completion = completion(id = 1))),
        )!!

        assertEquals(77L, result.id)
        assertEquals("program-occurrence:$DAY_ID:$OCCURRENCE", result.uuid)
        assertEquals(OCCURRENCE, result.start)
        assertEquals("Program · Day", result.comment)
        assertEquals(GUIDED_WORKOUT_TYPE_ID, result.workoutTypeId)
    }

    @Test
    fun `program identity and icon are copied into the history snapshot`() {
        val result = map(
            programUuid = "program-uuid",
            programName = "Return to form",
            iconOverride = "hiking",
            results = listOf(result(completion = completion(id = 1))),
        )!!

        assertEquals("program-uuid", result.programUuid)
        assertEquals("Return to form", result.programName)
        assertEquals("hiking", result.iconOverride)
    }

    @Test
    fun `rep-only exercise derives duration and energy from explicit intensity`() {
        val result = map(
            userWeightKg = 80.0,
            results = listOf(
                result(
                    completion = completion(id = 1),
                    exercise = exercise(met = 6.0, secondsPerRep = 3.0),
                    plans = listOf(plan(reps = 20)),
                )
            ),
        )!!

        assertEquals(60_000L, result.activeDuration)
        assertEquals(35_169.12, result.energy, 0.001)
    }

    @Test
    fun `old rep exercise receives safe duration and MET fallbacks`() {
        val result = map(
            userWeightKg = 80.0,
            results = listOf(
                result(
                    completion = completion(id = 1),
                    exercise = exercise(),
                    plans = listOf(plan(reps = 12)),
                )
            ),
        )!!

        // REPS defaults to 2 seconds/rep and 5 MET.
        assertEquals(24_000L, result.activeDuration)
        assertEquals(11_723.04, result.energy, 0.001)
    }

    @Test
    fun `each completion keeps its own historical weight for energy`() {
        val results = listOf(
            result(
                completion = completion(
                    id = 1,
                    profileUuidSnapshot = "profile-a",
                    userWeightKgSnapshot = 60.0,
                ),
                exercise = exercise(met = 6.0, secondsPerRep = 3.0),
                plans = listOf(plan(reps = 20)),
            ),
            result(
                completion = completion(
                    id = 2,
                    profileUuidSnapshot = "profile-b",
                    userWeightKgSnapshot = 100.0,
                ),
                exercise = exercise(met = 6.0, secondsPerRep = 3.0),
                plans = listOf(plan(reps = 20)),
            ),
        )

        val lightFallback = map(results = results, userWeightKg = 40.0)!!
        val heavyFallback = map(results = results, userWeightKg = 180.0)!!

        assertEquals(70_338.24, lightFallback.energy, 0.001)
        assertEquals(lightFallback.energy, heavyFallback.energy, 0.0)
        assertNull(lightFallback.profileUuid)
        assertNull(lightFallback.userWeightKgSnapshot)
    }

    @Test
    fun `single profile snapshot is copied to occurrence workout`() {
        val result = map(
            userWeightKg = 180.0,
            results = listOf(
                result(
                    completion = completion(
                        id = 1,
                        profileUuidSnapshot = "profile-exact",
                        userWeightKgSnapshot = 71.25,
                    ),
                    exercise = exercise(met = 6.0, secondsPerRep = 3.0),
                    plans = listOf(plan(reps = 20)),
                )
            ),
        )!!

        assertEquals("profile-exact", result.profileUuid)
        assertEquals(71.25, result.userWeightKgSnapshot!!, 0.0)
        assertEquals(31_322.4975, result.energy, 0.001)
    }

    @Test
    fun `linked source snapshot fills legacy completion identity`() {
        val linked = workout(
            id = 91,
            uuid = "recorded-91",
            duration = 60_000,
            repetitions = 10,
            distance = 100.0,
            energy = 4_000.0,
            profileUuid = "linked-profile",
            userWeightKgSnapshot = 68.75,
        )

        val result = map(
            results = listOf(
                result(completion = completion(id = 1, workoutId = linked.id))
            ),
            linked = listOf(linked),
        )!!

        assertEquals("linked-profile", result.profileUuid)
        assertEquals(68.75, result.userWeightKgSnapshot!!, 0.0)
    }

    private fun map(
        workoutId: Long = 44,
        results: List<ProgramOccurrenceExerciseResult>,
        linked: List<Workout> = emptyList(),
        userWeightKg: Double = DEFAULT_PROGRAM_USER_WEIGHT_KG,
        programUuid: String? = null,
        programName: String? = null,
        iconOverride: String? = null,
    ): Workout? = ProgramOccurrenceWorkoutMapper.toWorkout(
        dayId = DAY_ID,
        occurrenceDate = OCCURRENCE,
        title = "Program · Day",
        exerciseResults = results,
        linkedWorkouts = linked,
        workoutId = workoutId,
        userWeightKg = userWeightKg,
        programUuid = programUuid,
        programName = programName,
        iconOverride = iconOverride,
    )

    private fun result(
        completion: ProgramCompletion,
        exercise: Exercise = exercise(),
        plans: List<ProgramSet> = emptyList(),
        logs: List<SetLog> = emptyList(),
    ) = ProgramOccurrenceExerciseResult(completion, exercise, plans, logs)

    private fun exercise(
        met: Double? = null,
        secondsPerRep: Double? = null,
        metricKind: MetricKind = MetricKind.REPS,
        workoutTypeId: String? = null,
    ) = Exercise(
        id = 10,
        uuid = "exercise-10",
        name = "Exercise",
        metricKind = metricKind,
        workoutTypeId = workoutTypeId,
        met = met,
        secondsPerRep = secondsPerRep,
    )

    private fun completion(
        id: Long,
        status: Int = PROGRAM_COMPLETION_STATUS_COMPLETED,
        workoutId: Long? = null,
        profileUuidSnapshot: String? = null,
        userWeightKgSnapshot: Double? = null,
    ) = ProgramCompletion(
        programExerciseId = id,
        id = id,
        occurrenceDate = OCCURRENCE,
        status = status,
        workoutId = workoutId,
        completedAt = OCCURRENCE,
        profileUuidSnapshot = profileUuidSnapshot,
        userWeightKgSnapshot = userWeightKgSnapshot,
    )

    private fun plan(
        reps: Int? = null,
        repsMax: Int? = null,
        duration: Long? = null,
        distance: Double? = null,
    ) = ProgramSet(
        programExerciseId = 1,
        orderIndex = 0,
        reps = reps,
        repsMax = repsMax,
        durationMillis = duration,
        distanceMeters = distance,
    )

    private fun log(
        reps: Int? = null,
        duration: Long? = null,
        distance: Double? = null,
    ) = SetLog(
        completionId = 1,
        orderIndex = 0,
        reps = reps,
        durationMillis = duration,
        distanceMeters = distance,
    )

    private fun workout(
        id: Long,
        uuid: String,
        duration: Long,
        repetitions: Int,
        distance: Double,
        energy: Double,
        profileUuid: String? = null,
        userWeightKgSnapshot: Double? = null,
    ) = Workout(
        id = id,
        uuid = uuid,
        start = OCCURRENCE,
        end = OCCURRENCE + duration,
        activeDuration = duration,
        distance = distance,
        repetitions = repetitions,
        energy = energy,
        workoutTypeId = "running",
        intervalSetUsedId = -1,
        profileUuid = profileUuid,
        userWeightKgSnapshot = userWeightKgSnapshot,
    )

    private companion object {
        const val DAY_ID = 7L
        const val OCCURRENCE = 1_700_000_000_000L
    }
}
