/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data.backup

import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import de.tadris.fitness.data.CompletionRule
import de.tadris.fitness.data.Exercise
import de.tadris.fitness.data.MeasurementSource
import de.tadris.fitness.data.MetricKind
import de.tadris.fitness.data.backup.model.DataContainer
import de.tadris.fitness.data.backup.model.CompletionExport
import de.tadris.fitness.data.backup.model.DayExport
import de.tadris.fitness.data.backup.model.ExerciseEntryExport
import de.tadris.fitness.data.backup.model.ExerciseExport
import de.tadris.fitness.data.backup.model.ProgramContainer
import de.tadris.fitness.data.backup.model.ProgramExport
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramFormatCompatibilityTest {

    @Test
    fun `existing catalog exercise receives only its missing imported metadata`() {
        val existing = Exercise(
            id = 17,
            uuid = "push-up",
            name = "Push-up",
            metricKind = MetricKind.REPS,
            workoutTypeId = " ",
            description = null,
            tips = "",
            videoUrl = null,
        )
        val imported = Exercise(
            uuid = "push-up",
            name = "Push-up",
            metricKind = MetricKind.REPS,
            workoutTypeId = "push-ups",
            description = "Keep the body in one line.",
            tips = "Use an incline when needed.",
            videoUrl = "https://cdn.example.org/push-up.mp4",
            imagePath = "exercise_images/restored/push-up.gif",
            met = 6.0,
            secondsPerRep = 3.0,
        )

        val enriched = ProgramImporter.enrichExistingExercise(existing, imported)

        assertEquals("push-ups", enriched.workoutTypeId)
        assertEquals("Keep the body in one line.", enriched.description)
        assertEquals("Use an incline when needed.", enriched.tips)
        assertEquals("https://cdn.example.org/push-up.mp4", enriched.videoUrl)
        assertEquals("exercise_images/restored/push-up.gif", enriched.imagePath)
        assertEquals(6.0, enriched.met!!, 0.0)
        assertEquals(3.0, enriched.secondsPerRep!!, 0.0)
        assertEquals(existing.id, enriched.id)
        assertEquals(existing.uuid, enriched.uuid)
    }

    @Test
    fun `existing catalog exercise preserves all user supplied metadata`() {
        val existing = Exercise(
            id = 19,
            uuid = "squat",
            name = "My squat",
            metricKind = MetricKind.WEIGHT_REPS,
            workoutTypeId = "my-recorder",
            description = "My technique note",
            tips = "My safety cue",
            videoUrl = "https://media.example.org/my-squat.webm",
            imagePath = "exercise_images/user.gif",
            hidden = true,
            met = 5.5,
            secondsPerRep = 4.0,
        )
        val imported = Exercise(
            uuid = "different-import-identity",
            name = "Imported squat",
            metricKind = MetricKind.REPS,
            workoutTypeId = "running",
            description = "Imported description",
            tips = "Imported tips",
            videoUrl = "https://cdn.example.org/imported.mp4",
            met = 8.0,
            secondsPerRep = 2.0,
        )

        assertEquals(existing, ProgramImporter.enrichExistingExercise(existing, imported))
    }

    @Test
    fun `format v1 flat tasks become days exercises sets and completion positions`() {
        val json = """
            {
              "type": "fitregimen-program",
              "formatVersion": 1,
              "program": {
                "uuid": "old-plan",
                "name": "Old plan",
                "cycleLengthDays": 3,
                "startDate": 1700000000000,
                "createdAt": 1600000000000
              },
              "tasks": [
                {
                  "id": 42,
                  "dayIndex": 1,
                  "dayTitle": "Cardio",
                  "orderIndex": 0,
                  "workoutTypeId": "running",
                  "targetSets": 2,
                  "targetDurationMillis": 60000,
                  "targetDistanceMeters": 1000.0
                }
              ],
              "completions": [
                {
                  "programTaskId": 42,
                  "occurrenceDate": 1700000000000,
                  "workoutId": 99,
                  "status": 1
                }
              ]
            }
        """.trimIndent()

        val converted = ProgramImporter.read(ByteArrayInputStream(json.toByteArray()))

        assertEquals(3, converted.days.size)
        assertTrue(converted.days[0].isRest)
        assertEquals("Cardio", converted.days[1].name)
        assertEquals(2, converted.days[1].exercises.single().sets.size)
        assertEquals("DISTANCE_DURATION", converted.exercises.single().metricKind)
        assertEquals(1, converted.completions.single().dayIndex)
        assertEquals(0, converted.completions.single().exerciseIndex)
        assertEquals(99L, converted.completions.single().workoutId)
        assertEquals(1_600_000_000_000L, converted.program.createdAt)
    }

    @Test
    fun `format v2 remains readable and missing policy can be derived`() {
        val json = """
            {
              "type": "fitregimen-program",
              "formatVersion": 2,
              "program": { "name": "Legacy plan" },
              "exercises": [
                { "uuid": "plank", "name": "Plank", "metricKind": "DURATION" }
              ],
              "days": [
                {
                  "exercises": [
                    { "exerciseUuid": "plank", "sets": [{ "durationMillis": 30000 }] }
                  ]
                }
              ]
            }
        """.trimIndent()

        val container = ProgramImporter.read(ByteArrayInputStream(json.toByteArray()))
        val entry = container.days.single().exercises.single()

        assertEquals(2, container.formatVersion)
        assertNull(entry.measurementSource)
        assertNull(entry.completionRule)
        assertEquals(
            ProgramImporter.ExecutionPolicy(
                MeasurementSource.CLOCK,
                CompletionRule.DURATION_REACHED,
            ),
            ProgramImporter.executionPolicyFor(entry, MetricKind.DURATION, workoutTypeId = null),
        )
    }

    @Test
    fun `format v3 writer includes guided execution policy`() {
        val container = ProgramContainer(
            program = ProgramExport(name = "Guided plan", icon = "fitness_center"),
            exercises = listOf(
                ExerciseExport(
                    uuid = "run",
                    name = "Run",
                    metricKind = "DISTANCE_DURATION",
                    met = 8.3,
                    secondsPerRep = 1.5,
                )
            ),
            days = listOf(
                DayExport(
                    exercises = listOf(
                        ExerciseEntryExport(
                            exerciseUuid = "run",
                            measurementSource = "RECORDER",
                            completionRule = "METRIC_TARGET_REACHED",
                            prepareSeconds = 10,
                            transitionSeconds = 45,
                        )
                    )
                )
            ),
            completions = listOf(
                CompletionExport(
                    dayIndex = 0,
                    exerciseIndex = 0,
                    occurrenceDate = 1_700_000_000_000,
                    status = 1,
                    profileUuidSnapshot = "profile-uuid",
                    userWeightKgSnapshot = 82.5,
                ),
            ),
        )
        val output = ByteArrayOutputStream()

        ProgramExporter.write(container, output)
        val restored = ProgramImporter.read(ByteArrayInputStream(output.toByteArray()))
        val entry = restored.days.single().exercises.single()

        assertEquals(3, restored.formatVersion)
        assertEquals("fitness_center", restored.program.icon)
        assertEquals("RECORDER", entry.measurementSource)
        assertEquals("METRIC_TARGET_REACHED", entry.completionRule)
        assertEquals(10, entry.prepareSeconds)
        assertEquals(45, entry.transitionSeconds)
        assertEquals(8.3, restored.exercises.single().met!!, 0.0)
        assertEquals(1.5, restored.exercises.single().secondsPerRep!!, 0.0)
        assertEquals("profile-uuid", restored.completions.single().profileUuidSnapshot)
        assertEquals(82.5, restored.completions.single().userWeightKgSnapshot!!, 0.0)
    }

    @Test
    fun `backup v5 data without sessions deserializes as empty guided history`() {
        val mapper = jsonMapper { addModule(kotlinModule()) }
        val restored = mapper.readValue(
            """{"intervalSets":[],"workoutTypes":[],"programs":[]}""",
            DataContainer::class.java,
        )

        assertTrue(restored.sessions.isEmpty())
    }

    @Test
    fun `older history json defaults missing profile snapshots to null`() {
        val mapper = jsonMapper { addModule(kotlinModule()) }
        val restored = mapper.readValue(
            """
                {
                  "intervalSets": [],
                  "workoutTypes": [],
                  "programs": [{
                    "program": {"name": "Legacy plan"},
                    "completions": [{
                      "dayIndex": 0,
                      "exerciseIndex": 0,
                      "occurrenceDate": 1700000000000,
                      "status": 1
                    }]
                  }],
                  "sessions": [{
                    "uuid": "legacy-session",
                    "nameSnapshot": "Legacy workout",
                    "startedAt": 100,
                    "updatedAt": 200
                  }]
                }
            """.trimIndent(),
            DataContainer::class.java,
        )

        val session = restored.sessions.single()
        val completion = restored.programs.single().completions.single()
        assertNull(session.profileUuidSnapshot)
        assertNull(session.userWeightKgSnapshot)
        assertNull(session.stepLengthMetersSnapshot)
        assertNull(completion.profileUuidSnapshot)
        assertNull(completion.userWeightKgSnapshot)
    }
}
