/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data.backup

import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import de.tadris.fitness.data.Exercise
import de.tadris.fitness.data.MetricKind
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.data.backup.model.DataContainer
import de.tadris.fitness.data.backup.model.ExerciseCatalogExport
import de.tadris.fitness.data.backup.model.FitnessProfileExport
import de.tadris.fitness.data.backup.model.ProgramContainer
import de.tadris.fitness.data.backup.model.ProgramExport
import de.tadris.fitness.data.backup.model.DayExport
import de.tadris.fitness.data.backup.model.ExerciseEntryExport
import de.tadris.fitness.data.backup.model.ExerciseExport
import de.tadris.fitness.data.backup.model.CompletionExport
import de.tadris.fitness.data.backup.model.SessionExerciseExport
import de.tadris.fitness.data.backup.model.SessionSetExport
import de.tadris.fitness.data.backup.model.TrainingSessionExport
import de.tadris.fitness.data.backup.model.toCatalogBackupExport
import de.tadris.fitness.data.backup.model.toEntity
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupValidationTest {

    @Test
    fun `modern manifest requires one version and one data entry`() {
        val valid = validateModernBackupManifest(
            entryNames = listOf("version.txt", "data.json", "workout_0.json"),
            versionText = "8\n",
        )

        assertEquals(8, valid.version)
        expectThrows<IOException> {
            validateModernBackupManifest(listOf("version.txt"), "7")
        }
        expectThrows<IOException> {
            validateModernBackupManifest(listOf("version.txt", "version.txt", "data.json"), "7")
        }
    }

    @Test
    fun `modern manifest rejects unsafe paths and unsupported versions`() {
        expectThrows<IOException> {
            validateModernBackupManifest(listOf("version.txt", "data.json", "../private"), "7")
        }
        expectThrows<BackupReader.UnsupportedVersionException> {
            validateModernBackupManifest(listOf("version.txt", "data.json"), "3")
        }
        expectThrows<BackupReader.UnsupportedVersionException> {
            validateModernBackupManifest(listOf("version.txt", "data.json"), "9")
        }
    }

    @Test
    fun `writer UUID guard and custom workout type preflight reject ambiguous identity`() {
        requireUniqueBackupUuids("workout", listOf("one", "two"))
        expectThrows<IOException> {
            requireUniqueBackupUuids("workout", listOf("same", "same"))
        }
        val type = WorkoutType(
            "custom",
            "Strength",
            1,
            0,
            "activity",
            -1,
            "indoor",
        )
        validateBackupWorkoutTypes(8, listOf(type))
        expectThrows<IOException> { validateBackupWorkoutTypes(8, listOf(type, type)) }
        expectThrows<IOException> {
            validateBackupWorkoutTypes(8, listOf(copyWorkoutTypeWithId(type, "")))
        }
        expectThrows<IOException> {
            validateBackupWorkoutTypes(
                8,
                listOf(
                    WorkoutType("bad", "Bad", 1, 0, "missing", 1, "indoor"),
                ),
            )
        }
    }

    @Test
    fun `v7 profiles require unique identities valid data and exactly one active`() {
        val active = profile(uuid = "one", active = true)
        val inactive = profile(uuid = "two", active = false)

        assertEquals(2, validateBackupProfiles(7, listOf(active, inactive)).size)
        assertTrue(validateBackupProfiles(6, emptyList()).isEmpty())
        assertTrue(validateBackupProfiles(7, emptyList()).isEmpty())
        expectThrows<IOException> { validateBackupProfiles(7, listOf(active, active)) }
        expectThrows<IOException> {
            validateBackupProfiles(7, listOf(active.copy(isActive = false), inactive))
        }
        expectThrows<IOException> {
            validateBackupProfiles(7, listOf(active.copy(weightKg = Double.NaN)))
        }
    }

    @Test
    fun `catalog requires safe present media and known metric`() {
        val mediaPath = "exercise_media/abc.gif"
        val catalog = listOf(
            ExerciseCatalogExport(
                uuid = "exercise-1",
                name = "Jump",
                metricKind = "REPS",
                imageArchivePath = mediaPath,
            ),
        )

        validateExerciseCatalog(catalog, setOf("version.txt", "data.json", mediaPath))
        expectThrows<IOException> { validateExerciseCatalog(catalog, setOf("version.txt", "data.json")) }
        expectThrows<IOException> {
            validateExerciseCatalog(catalog.map { it.copy(metricKind = "mystery") }, setOf(mediaPath))
        }
        expectThrows<IOException> {
            validateExerciseCatalog(catalog.map { it.copy(imageArchivePath = "exercise_media/../secret") }, setOf())
        }
    }

    @Test
    fun `catalog mapper round trip preserves all exercise fields without local path`() {
        val source = Exercise(
            uuid = "exercise-1",
            name = "Row",
            metricKind = MetricKind.WEIGHT_REPS,
            workoutTypeId = "rowing",
            description = "Description",
            tips = "Tips",
            videoUrl = "https://example.test/row.mp4",
            imagePath = "exercise_images/private.gif",
            hidden = true,
            met = 7.2,
            secondsPerRep = 2.5,
        )
        val archivePath = exerciseMediaArchivePath(source.uuid, source.imagePath!!, 0)
        val export = source.toCatalogBackupExport(archivePath)
        val restored = export.toEntity("exercise_images/restored/media.gif")

        assertTrue(isSafeBackupArchivePath(archivePath))
        assertTrue(archivePath.startsWith(EXERCISE_MEDIA_ARCHIVE_PREFIX))
        assertEquals(source.copy(id = 0, imagePath = restored.imagePath), restored)
        assertEquals("exercise_images/restored/media.gif", restored.imagePath)
    }

    @Test
    fun `full backup carries catalog while standalone program has no private profile or media fields`() {
        val mapper = jsonMapper { addModule(kotlinModule()) }
        val catalogEntry = ExerciseCatalogExport(
            uuid = "exercise-1",
            name = "Plank",
            metricKind = "DURATION",
            imageArchivePath = "exercise_media/plank.webp",
        )
        val dataJson = mapper.writeValueAsString(
            DataContainer(
                intervalSets = emptyList(),
                workoutTypes = emptyList(),
                exerciseCatalog = listOf(catalogEntry),
            ),
        )
        val restoredData = mapper.readValue(dataJson, DataContainer::class.java)
        val programJson = mapper.writeValueAsString(
            ProgramContainer(program = ProgramExport(name = "Plan")),
        )

        assertEquals(catalogEntry, restoredData.exerciseCatalog.single())
        assertTrue(dataJson.contains("exerciseCatalog"))
        assertFalse(programJson.contains("profiles"))
        assertFalse(programJson.contains("profileUuidSnapshot"))
        assertFalse(programJson.contains("userWeightKgSnapshot"))
        assertFalse(programJson.contains("imageArchivePath"))
        assertFalse(programJson.contains("imagePath"))
    }

    @Test
    fun `older data without catalog and profiles remains readable`() {
        val mapper = jsonMapper { addModule(kotlinModule()) }
        val restored = mapper.readValue(
            """{"intervalSets":[],"workoutTypes":[],"programs":[]}""",
            DataContainer::class.java,
        )

        assertTrue(restored.exerciseCatalog.isEmpty())
        assertTrue(restored.profiles.isEmpty())
        assertNull(restored.sessions.firstOrNull())
    }

    @Test
    fun `v8 session validates portable source and archived media`() {
        val media = "exercise_media/session.gif"
        val session = TrainingSessionExport(
            uuid = "session-1",
            sourceProgramUuid = "program-1",
            sourceDayIndex = 2,
            nameSnapshot = "Workout",
            startedAt = 10,
            updatedAt = 20,
            exercises = listOf(
                SessionExerciseExport(
                    sourceProgramExerciseIndex = 1,
                    exerciseUuidSnapshot = "exercise-1",
                    nameSnapshot = "Squat",
                    imageArchivePathSnapshot = media,
                    sets = listOf(SessionSetExport(sourceProgramSetIndex = 0)),
                ),
            ),
        )

        validateBackupTrainingSessions(8, listOf(session), setOf(media))
        expectThrows<IOException> {
            validateBackupTrainingSessions(8, listOf(session.copy(sourceDayIndex = null)), setOf(media))
        }
        expectThrows<IOException> {
            validateBackupTrainingSessions(8, listOf(session), emptySet())
        }
        expectThrows<IOException> {
            validateBackupTrainingSessions(
                8,
                listOf(
                    session.copy(
                        exercises = session.exercises.map {
                            it.copy(imageArchivePathSnapshot = null, imagePathSnapshot = "/private/source.gif")
                        },
                    ),
                ),
                emptySet(),
            )
        }
    }

    @Test
    fun `v8 program validation protects portable identities and history positions`() {
        val valid = ProgramContainer(
            program = ProgramExport(uuid = "program-1", name = "Plan"),
            exercises = listOf(
                ExerciseExport(
                    uuid = "exercise-1",
                    name = "Squat",
                    metricKind = "WEIGHT_REPS",
                ),
            ),
            days = listOf(
                DayExport(
                    exercises = listOf(ExerciseEntryExport(exerciseUuid = "exercise-1")),
                ),
            ),
            completions = listOf(
                CompletionExport(
                    dayIndex = 0,
                    exerciseIndex = 0,
                    occurrenceDate = 1,
                    status = 1,
                ),
            ),
        )

        validateBackupPrograms(8, listOf(valid))
        expectThrows<IOException> {
            validateBackupPrograms(8, listOf(valid, valid))
        }
        expectThrows<IOException> {
            validateBackupPrograms(
                8,
                listOf(
                    valid.copy(
                        days = listOf(
                            DayExport(
                                exercises = listOf(ExerciseEntryExport(exerciseUuid = "missing")),
                            ),
                        ),
                    ),
                ),
            )
        }
        expectThrows<IOException> {
            validateBackupPrograms(
                8,
                listOf(valid.copy(completions = valid.completions.map { it.copy(dayIndex = 4) })),
            )
        }
    }

    @Test
    fun `v7 session remains readable but private image path is never rebound directly`() {
        val legacy = TrainingSessionExport(
            uuid = "legacy-session",
            nameSnapshot = "Legacy",
            startedAt = 1,
            updatedAt = 2,
            exercises = listOf(
                SessionExerciseExport(
                    exerciseUuidSnapshot = "exercise-1",
                    nameSnapshot = "Plank",
                    imagePathSnapshot = "exercise_images/source-only.gif",
                ),
            ),
        )

        validateBackupTrainingSessions(7, listOf(legacy), emptySet())
        val restored = legacy.exercises.single().toEntity(
            sessionId = 1,
            orderIndex = 0,
            restoredImagePath = null,
        )
        assertNull(restored.imagePathSnapshot)
    }

    private fun profile(uuid: String, active: Boolean) = FitnessProfileExport(
        uuid = uuid,
        name = uuid,
        isActive = active,
        weightKg = 80.0,
        stepLengthCm = 79.0,
        createdAt = 1,
        updatedAt = 1,
    )

    private inline fun <reified T : Throwable> expectThrows(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw AssertionError("Expected ${T::class.java.name}, got ${error::class.java.name}", error)
        }
        fail("Expected ${T::class.java.name}")
        error("unreachable")
    }
}
