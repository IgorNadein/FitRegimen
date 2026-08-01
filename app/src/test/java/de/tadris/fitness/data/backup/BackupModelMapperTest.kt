/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data.backup

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import de.tadris.fitness.data.CompletionRule
import de.tadris.fitness.data.HeartRateSample
import de.tadris.fitness.data.MeasurementSource
import de.tadris.fitness.data.MetricKind
import de.tadris.fitness.data.RepetitionSample
import de.tadris.fitness.data.RouteSample
import de.tadris.fitness.data.SessionExercise
import de.tadris.fitness.data.SessionExerciseStatus
import de.tadris.fitness.data.SessionSet
import de.tadris.fitness.data.SessionSetStatus
import de.tadris.fitness.data.SetType
import de.tadris.fitness.data.TrainingSession
import de.tadris.fitness.data.TrainingSessionPhase
import de.tadris.fitness.data.TrainingSessionStatus
import de.tadris.fitness.data.Workout
import de.tadris.fitness.data.WorkoutData
import de.tadris.fitness.data.WorkoutEvent
import de.tadris.fitness.data.WorkoutPause
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.data.backup.model.CompletionExport
import de.tadris.fitness.data.backup.model.SetLogExport
import de.tadris.fitness.data.backup.model.TrainingSessionExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupModelMapperTest {

    @Test
    fun `guided session preserves snapshots runtime state results and workout uuid`() {
        val session = TrainingSession(
            id = 7,
            uuid = "session-uuid",
            sourceProgramId = 2,
            sourceDayId = 3,
            occurrenceDate = 1_700_000_000_000,
            zoneId = "Europe/Moscow",
            nameSnapshot = "Leg day",
            descriptionSnapshot = "Snapshot description",
            profileUuidSnapshot = "profile-uuid",
            userWeightKgSnapshot = 82.5,
            stepLengthMetersSnapshot = 0.79,
            status = TrainingSessionStatus.PAUSED,
            phase = TrainingSessionPhase.REST,
            currentExerciseIndex = 1,
            currentSetIndex = 2,
            startedAt = 100,
            updatedAt = 200,
            completedAt = null,
            phaseStartedAt = 150,
            phaseDeadlineAt = 10_000,
            pausedRemainingMillis = 9_850,
            accumulatedPausedMillis = 1_250,
        )
        val exercise = SessionExercise(
            sessionId = session.id,
            id = 8,
            orderIndex = 0,
            sourceProgramExerciseId = 4,
            sourceExerciseId = 5,
            exerciseUuidSnapshot = "exercise-uuid",
            nameSnapshot = "Run",
            metricKindSnapshot = MetricKind.DISTANCE_DURATION,
            workoutTypeIdSnapshot = "running",
            descriptionSnapshot = "Description",
            tipsSnapshot = "Tips",
            videoUrlSnapshot = "https://example.invalid/run",
            imagePathSnapshot = "exercise_media/run.gif",
            noteSnapshot = "Easy pace",
            measurementSource = MeasurementSource.RECORDER,
            completionRule = CompletionRule.METRIC_TARGET_REACHED,
            prepareSeconds = 5,
            restSeconds = 30,
            transitionSeconds = 60,
            status = SessionExerciseStatus.ACTIVE,
            startedAt = 120,
        )
        val set = SessionSet(
            sessionExerciseId = exercise.id,
            id = 9,
            orderIndex = 0,
            sourceProgramSetId = 6,
            type = SetType.NORMAL,
            status = SessionSetStatus.COMPLETED,
            completionSource = MeasurementSource.RECORDER,
            targetDurationMillis = 300_000,
            targetDistanceMeters = 1_000.0,
            actualDurationMillis = 290_000,
            actualDistanceMeters = 1_005.5,
            linkedWorkoutId = 42,
            startedAt = 130,
            completedAt = 420,
        )

        val export = session.toBackupExport(
            exercises = listOf(SessionExerciseWithSets(exercise, listOf(set))),
            workoutUuidForId = { id -> if (id == 42L) "workout-uuid" else null },
            sourceProgramUuidForId = { id -> if (id == 2L) "program-uuid" else null },
            sourceDayIndexForId = { id -> if (id == 3L) 4 else null },
            sourceProgramExerciseIndexForId = { id -> if (id == 4L) 5 else null },
            sourceProgramSetIndexForId = { id -> if (id == 6L) 6 else null },
            imageArchivePathForExercise = { "exercise_media/run.gif" },
        )
        val mapper = jsonMapper {
            addModule(kotlinModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
        val serializedExport = mapper.readValue(
            mapper.writeValueAsBytes(export),
            TrainingSessionExport::class.java,
        )
        val restoredSession = serializedExport.toEntity(sourceProgramId = 20, sourceDayId = 30)
        val restoredExercise = serializedExport.exercises.single().toEntity(
            sessionId = restoredSession.id,
            orderIndex = 0,
            sourceProgramExerciseId = 40,
            sourceExerciseId = 50,
            restoredImagePath = "exercise_images/restored/run.gif",
        )
        val restoredSet = serializedExport.exercises.single().sets.single().toEntity(
            sessionExerciseId = 99,
            orderIndex = 0,
            workoutIdForUuid = { uuid -> if (uuid == "workout-uuid") 123L else null },
            sourceProgramSetId = 60,
        )

        assertEquals("Europe/Moscow", export.zoneId)
        assertEquals(TrainingSessionPhase.REST, restoredSession.phase)
        assertEquals(TrainingSessionStatus.PAUSED, restoredSession.status)
        assertEquals(10_000L, restoredSession.phaseDeadlineAt)
        assertEquals(9_850L, restoredSession.pausedRemainingMillis)
        assertEquals(1_250L, restoredSession.accumulatedPausedMillis)
        assertEquals("profile-uuid", restoredSession.profileUuidSnapshot)
        assertEquals(82.5, restoredSession.userWeightKgSnapshot!!, 0.0)
        assertEquals(0.79, restoredSession.stepLengthMetersSnapshot!!, 0.0)
        assertEquals("program-uuid", export.sourceProgramUuid)
        assertEquals(4, export.sourceDayIndex)
        assertEquals(5, export.exercises.single().sourceProgramExerciseIndex)
        assertEquals(6, export.exercises.single().sets.single().sourceProgramSetIndex)
        assertEquals(20L, restoredSession.sourceProgramId)
        assertEquals(30L, restoredSession.sourceDayId)
        assertNull(export.exercises.single().imagePathSnapshot)
        assertEquals("exercise_media/run.gif", export.exercises.single().imageArchivePathSnapshot)
        assertEquals("exercise_images/restored/run.gif", restoredExercise.imagePathSnapshot)
        assertEquals(MeasurementSource.RECORDER, restoredExercise.measurementSource)
        assertEquals(50L, restoredExercise.sourceExerciseId)
        assertEquals(40L, restoredExercise.sourceProgramExerciseId)
        assertEquals(1_005.5, restoredSet.actualDistanceMeters!!, 0.0)
        assertEquals(123L, restoredSet.linkedWorkoutId)
        assertEquals(60L, restoredSet.sourceProgramSetId)
    }

    @Test
    fun `workout remap changes parent and every child foreign key while clearing child ids`() {
        val sourceId = 42L
        val targetId = 99L
        val data = WorkoutData(
            workout = Workout(
                id = sourceId,
                uuid = "workout-uuid",
                start = 1,
                end = 2,
                activeDuration = 1,
                distance = 10.0,
                repetitions = 3,
                energy = 4.0,
                workoutTypeId = "running",
                intervalSetUsedId = 0,
                programUuid = "program-uuid",
                programName = "Four-week plan",
                iconOverride = "fitness_center",
            ),
            samples = listOf(
                RouteSample(sourceId, 11, 1, 0, 1.0, 2.0, 3.0, 4.0, 5.0, 6f)
            ),
            pauses = listOf(WorkoutPause(sourceId, 12, 1, 2, true)),
            events = listOf(WorkoutEvent(sourceId, 13, 1, 0, "interval")),
            repetitionSamples = listOf(
                RepetitionSample(sourceId, 14, 1, 2, 0, 1, 3, 4.0, 5.0)
            ),
            heartRateSamples = listOf(HeartRateSample(sourceId, 15, 1, 0, 120)),
        )

        val remapped = data.remapForBackupImport(targetId)

        assertEquals(targetId, remapped.workout.id)
        val childWorkoutIds = listOf(
            remapped.samples.single().workoutId,
            remapped.pauses.single().workoutId,
            remapped.events.single().workoutId,
            remapped.repetitionSamples.single().workoutId,
            remapped.heartRateSamples.single().workoutId,
        )
        val childIds = listOf(
            remapped.samples.single().id,
            remapped.pauses.single().id,
            remapped.events.single().id,
            remapped.repetitionSamples.single().id,
            remapped.heartRateSamples.single().id,
        )
        assertEquals(listOf(targetId, targetId, targetId, targetId, targetId), childWorkoutIds)
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L), childIds)
        assertEquals("program-uuid", remapped.workout.programUuid)
        assertEquals("Four-week plan", remapped.workout.programName)
        assertEquals("fitness_center", remapped.workout.iconOverride)
    }

    @Test
    fun `workout backup round trip preserves program and profile snapshots`() {
        val mapper = jsonMapper {
            addModule(kotlinModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
        val source = WorkoutData(
            workout = Workout(
                id = 7,
                uuid = "workout-uuid",
                start = 1,
                end = 2,
                activeDuration = 1,
                distance = 0.0,
                repetitions = 0,
                energy = 0.0,
                workoutTypeId = "program_training",
                intervalSetUsedId = 0,
                programUuid = "program-uuid",
                programName = "Strength plan",
                iconOverride = "exercise",
                profileUuid = "profile-uuid",
                userWeightKgSnapshot = 82.5,
                stepLengthMetersSnapshot = 0.79,
            ),
            samples = emptyList(),
            pauses = emptyList(),
            events = emptyList(),
            repetitionSamples = emptyList(),
            heartRateSamples = emptyList(),
        )

        val restored = mapper.readValue(
            mapper.writeValueAsBytes(source),
            WorkoutData::class.java,
        )

        assertEquals("program-uuid", restored.workout.programUuid)
        assertEquals("Strength plan", restored.workout.programName)
        assertEquals("exercise", restored.workout.iconOverride)
        assertEquals("profile-uuid", restored.workout.profileUuid)
        assertEquals(82.5, restored.workout.userWeightKgSnapshot!!, 0.0)
        assertEquals(0.79, restored.workout.stepLengthMetersSnapshot!!, 0.0)
    }

    @Test
    fun `numeric collision chooses first free id`() {
        val occupied = setOf(100L, 101L, 102L)

        assertEquals(103L, firstAvailableWorkoutId(100L, occupied::contains))
    }

    @Test
    fun `workout import remaps its interval set together with every child`() {
        val source = WorkoutData(
            workout = Workout(
                id = 7,
                uuid = "workout-uuid",
                start = 1,
                end = 2,
                activeDuration = 1,
                distance = 0.0,
                repetitions = 0,
                energy = 0.0,
                workoutTypeId = "running",
                intervalSetUsedId = 42,
            ),
            samples = emptyList(),
            pauses = emptyList(),
            events = emptyList(),
            repetitionSamples = emptyList(),
            heartRateSamples = emptyList(),
        )

        val restored = source.remapForBackupImport(
            newWorkoutId = 9,
            intervalSetIdForImportedId = { importedId ->
                if (importedId == 42L) 77L else importedId
            },
        )

        assertEquals(9L, restored.workout.id)
        assertEquals(77L, restored.workout.intervalSetUsedId)
    }

    @Test
    fun `session exporter emits portable source only as one complete atomic tree`() {
        val session = TrainingSession(
            uuid = "session",
            sourceProgramId = 10,
            sourceDayId = null,
            nameSnapshot = "Snapshot",
            startedAt = 1,
            updatedAt = 2,
        )
        val exercise = SessionExercise(
            sessionId = 1,
            orderIndex = 0,
            sourceProgramExerciseId = 20,
            exerciseUuidSnapshot = "exercise",
            nameSnapshot = "Exercise",
            metricKindSnapshot = MetricKind.REPS,
            measurementSource = MeasurementSource.USER_INPUT,
            completionRule = CompletionRule.USER_CONFIRM,
        )
        val set = SessionSet(
            sessionExerciseId = 1,
            orderIndex = 0,
            sourceProgramSetId = 30,
        )

        val export = session.toBackupExport(
            exercises = listOf(SessionExerciseWithSets(exercise, listOf(set))),
            workoutUuidForId = { null },
            sourceProgramUuidForId = { "program" },
            sourceDayIndexForId = { 0 },
            sourceProgramExerciseIndexForId = { 0 },
            sourceProgramSetIndexForId = { 0 },
        )

        assertNull(export.sourceProgramUuid)
        assertNull(export.sourceDayIndex)
        assertNull(export.exercises.single().sourceProgramExerciseIndex)
        assertNull(export.exercises.single().sets.single().sourceProgramSetIndex)
    }

    @Test
    fun `completion merge accepts newer history and fills missing equal-time logs`() {
        val newer = CompletionExport(
            occurrenceDate = 1,
            status = 1,
            completedAt = 20,
        )
        val sameWithLogs = newer.copy(
            completedAt = 10,
            setLogs = listOf(SetLogExport(reps = 8)),
        )

        assertTrue(shouldApplyImportedCompletion(10, existingHasLogs = true, newer))
        assertTrue(shouldApplyImportedCompletion(10, existingHasLogs = false, sameWithLogs))
        assertFalse(shouldApplyImportedCompletion(10, existingHasLogs = true, sameWithLogs))
        assertFalse(shouldApplyImportedCompletion(30, existingHasLogs = false, newer))
    }

    @Test
    fun `custom workout type collision receives deterministic unused portable id`() {
        val occupied = setOf("strength_imported", "strength_imported_2")

        assertEquals(
            "strength_imported_3",
            uniqueImportedWorkoutTypeId("strength", occupied::contains),
        )
    }

    @Test
    fun `repeated backup restore reuses equivalent derived workout type`() {
        val incoming = WorkoutType(
            "strength",
            "Strength",
            0,
            0xff8844,
            "fitness_center",
            6,
            "INDOOR",
        )
        val stored = mutableMapOf(
            "strength" to WorkoutType(
                "strength",
                "Unrelated local type",
                5,
                0,
                "other",
                2,
                "OUTDOOR",
            )
        )

        val firstTarget = resolveBackupWorkoutTypeId(
            incoming = incoming,
            reservedIds = BUILT_IN_WORKOUT_TYPE_IDS,
            findById = stored::get,
        )
        stored[firstTarget] = copyWorkoutTypeWithId(incoming, firstTarget)
        val repeatedTarget = resolveBackupWorkoutTypeId(
            incoming = incoming,
            reservedIds = BUILT_IN_WORKOUT_TYPE_IDS,
            findById = stored::get,
        )

        assertEquals("strength_imported", firstTarget)
        assertEquals(firstTarget, repeatedTarget)
        assertEquals(2, stored.size)
    }
}
