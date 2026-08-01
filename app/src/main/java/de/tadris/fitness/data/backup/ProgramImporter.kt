/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack
 *
 * FitoTrack is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     FitoTrack is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.data.backup

import android.content.Context
import com.fasterxml.jackson.core.StreamReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import de.tadris.fitness.Instance
import de.tadris.fitness.data.CompletionRule
import de.tadris.fitness.data.Exercise
import de.tadris.fitness.data.MeasurementSource
import de.tadris.fitness.data.MetricKind
import de.tadris.fitness.data.ProgramDay
import de.tadris.fitness.data.ProgramExercise
import de.tadris.fitness.data.ProgramSet
import de.tadris.fitness.data.SetType
import de.tadris.fitness.data.TrainingProgram
import de.tadris.fitness.data.isSupportedExerciseVideoUrl
import de.tadris.fitness.data.backup.model.PROGRAM_CONTAINER_FORMAT_VERSION
import de.tadris.fitness.data.backup.model.PROGRAM_CONTAINER_MIN_SUPPORTED_VERSION
import de.tadris.fitness.data.backup.model.PROGRAM_CONTAINER_TYPE
import de.tadris.fitness.data.backup.model.ExerciseEntryExport
import de.tadris.fitness.data.backup.model.ProgramContainer
import de.tadris.fitness.data.backup.model.ProgramExport
import de.tadris.fitness.data.backup.model.DayExport
import de.tadris.fitness.data.backup.model.ExerciseExport
import de.tadris.fitness.data.backup.model.SetExport
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Reads a standalone program JSON file (format v2 or v3, as written by [ProgramExporter]
 * or produced by an AI from the generated prompt) and imports it as a new program.
 *
 * Tolerance rules (AI-generated files are sloppy): missing/blank uuids are generated,
 * unknown metricKind/setType spellings fall back to safe defaults, unknown
 * workoutTypeId references are dropped. Structural problems (no name, empty days,
 * entry referencing an exercise that is neither bundled nor already in the catalog)
 * fail with a message specific enough to feed back to the AI.
 */
object ProgramImporter {

    private val mapper = jsonMapper {
        addModule(kotlinModule())
        configure(StreamReadFeature.IGNORE_UNDEFINED, true)
        configure(StreamReadFeature.AUTO_CLOSE_SOURCE, false)
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    }

    class InvalidProgramFileException(message: String) : IOException(message)

    fun read(inputStream: InputStream): ProgramContainer {
        val parsed = try {
            mapper.readValue(inputStream, ProgramContainer::class.java)
        } catch (e: Exception) {
            throw InvalidProgramFileException("Not valid JSON: ${e.message}")
        }
        if (parsed.type != PROGRAM_CONTAINER_TYPE) {
            throw InvalidProgramFileException(
                "Not a FitRegimen program file (expected type \"$PROGRAM_CONTAINER_TYPE\")"
            )
        }
        if (parsed.formatVersion !in
            PROGRAM_CONTAINER_MIN_SUPPORTED_VERSION..PROGRAM_CONTAINER_FORMAT_VERSION
        ) {
            throw InvalidProgramFileException(
                "Unsupported formatVersion ${parsed.formatVersion}, expected " +
                    "$PROGRAM_CONTAINER_MIN_SUPPORTED_VERSION..$PROGRAM_CONTAINER_FORMAT_VERSION"
            )
        }
        val container = normalizeForImport(parsed)
        if (container.program.name.isBlank()) {
            throw InvalidProgramFileException("Program name is missing")
        }
        if (container.days.isEmpty()) {
            throw InvalidProgramFileException("Program has no days")
        }
        return container
    }

    /**
     * Converts the original flat v1 program format (also nested in full backup v5)
     * to the id-free day/exercise tree. Keeping this explicit avoids silently
     * importing a legacy program as an empty plan.
     */
    internal fun normalizeForImport(container: ProgramContainer): ProgramContainer {
        if (container.formatVersion != 1 && container.tasks.isEmpty()) return container

        val sortedTasks = container.tasks.sortedWith(
            compareBy({ it.dayIndex }, { it.orderIndex }, { it.id })
        )
        val programKey = container.program.uuid?.takeIf { it.isNotBlank() }
            ?: container.program.name.trim().ifBlank { "legacy" }
        val exerciseUuidByTask = sortedTasks.associate { task ->
            task.id to "legacy-$programKey-task-${task.id}"
        }
        val exercisePositionByTask = mutableMapOf<Long, Pair<Int, Int>>()
        val lastTaskDay = sortedTasks.maxOfOrNull { it.dayIndex + 1 } ?: 0
        val dayCount = maxOf(container.program.cycleLengthDays ?: 0, lastTaskDay).coerceAtLeast(1)
        val typeTitles = container.customWorkoutTypes.associate { it.id to it.title }

        val exercises = sortedTasks.map { task ->
            val metricKind = when {
                task.targetDistanceMeters != null -> MetricKind.DISTANCE_DURATION
                task.targetDurationMillis != null && task.targetReps == null -> MetricKind.DURATION
                else -> MetricKind.REPS
            }
            ExerciseExport(
                uuid = exerciseUuidByTask.getValue(task.id),
                name = typeTitles[task.workoutTypeId] ?: task.workoutTypeId,
                metricKind = metricKind.name,
                workoutTypeId = task.workoutTypeId,
            )
        }

        val days = (0 until dayCount).map { dayIndex ->
            val dayTasks = sortedTasks.filter { it.dayIndex == dayIndex }
            DayExport(
                name = dayTasks.firstNotNullOfOrNull { it.dayTitle?.takeIf(String::isNotBlank) },
                isRest = dayTasks.isEmpty(),
                exercises = dayTasks.mapIndexed { exerciseIndex, task ->
                    exercisePositionByTask[task.id] = dayIndex to exerciseIndex
                    val setCount = (task.targetSets ?: 1).coerceAtLeast(1)
                    ExerciseEntryExport(
                        exerciseUuid = exerciseUuidByTask.getValue(task.id),
                        note = task.note,
                        sets = List(setCount) {
                            SetExport(
                                reps = task.targetReps,
                                durationMillis = task.targetDurationMillis,
                                distanceMeters = task.targetDistanceMeters,
                            )
                        },
                    )
                },
            )
        }

        val completions = container.completions.mapNotNull { completion ->
            if (completion.dayIndex >= 0 && completion.exerciseIndex >= 0) return@mapNotNull completion
            val position = completion.programTaskId?.let(exercisePositionByTask::get)
                ?: return@mapNotNull null
            completion.copy(
                dayIndex = position.first,
                exerciseIndex = position.second,
                programTaskId = null,
            )
        }

        return container.copy(
            formatVersion = PROGRAM_CONTAINER_FORMAT_VERSION,
            program = ProgramExport(
                uuid = container.program.uuid,
                name = container.program.name,
                description = container.program.description,
                icon = container.program.icon,
                startDate = container.program.startDate,
                createdAt = container.program.createdAt,
            ),
            days = days,
            exercises = exercises,
            completions = completions,
            tasks = emptyList(),
        )
    }

    /**
     * Inserts [container] as a brand-new program, creating any bundled exercises and
     * custom workout types that don't exist yet. Returns the new program id.
     */
    fun import(context: Context, container: ProgramContainer): Long {
        val db = Instance.getInstance(context).db
        return db.runInTransaction<Long> {
            val dao = db.trainingProgramDao()
            val exerciseDao = db.exerciseDao()

            val workoutTypeIdMap = mutableMapOf<String, String>()
            for (type in container.customWorkoutTypes) {
                val existing = db.workoutTypeDao().findById(type.id)
                val targetId = when {
                    existing == null && type.id !in BUILT_IN_WORKOUT_TYPE_IDS -> type.id
                    workoutTypesEquivalent(existing, type) -> type.id
                    else -> uniqueImportedWorkoutTypeId(type.id) { candidate ->
                        db.workoutTypeDao().findById(candidate) != null ||
                            candidate in BUILT_IN_WORKOUT_TYPE_IDS
                    }
                }
                if (db.workoutTypeDao().findById(targetId) == null) {
                    db.workoutTypeDao().insert(copyWorkoutTypeWithId(type, targetId))
                }
                workoutTypeIdMap[type.id] = targetId
            }

            // Resolve every bundled exercise to a catalog row: by uuid, then by
            // (name, kind) to avoid duplicate "Bench Press" rows on re-imports,
            // else insert fresh.
            val exerciseIdByFileUuid = mutableMapOf<String, Long>()
            for (bundled in container.exercises) {
                if (bundled.name.isBlank()) {
                    throw InvalidProgramFileException("An exercise in the file has no name")
                }
                val kind = MetricKind.findByName(bundled.metricKind.trim().uppercase())
                val fileUuid = bundled.uuid?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
                val importedMet = bundled.met.validEstimateOrNull(min = 0.5, max = 25.0)
                val importedSecondsPerRep = bundled.secondsPerRep
                    .validEstimateOrNull(min = 0.1, max = 60.0)
                val importedExercise = Exercise(
                    uuid = fileUuid,
                    name = bundled.name.trim(),
                    metricKind = kind,
                    workoutTypeId = resolveWorkoutTypeId(
                        context,
                        bundled.workoutTypeId?.let { workoutTypeIdMap[it] ?: it },
                    ),
                    description = bundled.description?.trim()?.takeIf(String::isNotEmpty),
                    tips = bundled.tips?.trim()?.takeIf(String::isNotEmpty),
                    videoUrl = bundled.videoUrl
                        ?.trim()
                        ?.takeIf(::isSupportedExerciseVideoUrl),
                    met = importedMet,
                    secondsPerRep = importedSecondsPerRep,
                )
                val existing = exerciseDao.findByUuid(fileUuid)
                    ?: exerciseDao.findByNameAndKind(bundled.name.trim(), kind.name)
                val id = if (existing == null) {
                    exerciseDao.insert(importedExercise)
                } else {
                    val enriched = enrichExistingExercise(existing, importedExercise)
                    if (enriched != existing) exerciseDao.update(enriched)
                    existing.id
                }
                exerciseIdByFileUuid[fileUuid] = id
                // The file may reference the exercise by its original uuid even if we
                // matched an existing row by name - map both spellings.
                bundled.uuid?.takeIf { it.isNotBlank() }?.let { exerciseIdByFileUuid[it] = id }
            }

            val programId = dao.insertProgram(
                TrainingProgram(
                    uuid = UUID.randomUUID().toString(),
                    name = container.program.name.trim(),
                    description = container.program.description?.takeIf { it.isNotBlank() },
                    icon = container.program.icon?.trim()?.takeIf { it.isNotEmpty() },
                    startDate = null,
                    createdAt = container.program.createdAt
                        ?.takeIf { it > 0L }
                        ?: System.currentTimeMillis(),
                )
            )

            container.days.forEachIndexed { dayIndex, day ->
                val dayId = dao.insertDay(
                    ProgramDay(
                        programId = programId,
                        orderIndex = dayIndex,
                        name = day.name?.takeIf { it.isNotBlank() },
                        isRest = day.isRest,
                    )
                )
                day.exercises.forEachIndexed { entryIndex, entry ->
                    val exerciseId = exerciseIdByFileUuid[entry.exerciseUuid]
                        ?: exerciseDao.findByUuid(entry.exerciseUuid.trim())?.id
                        ?: throw InvalidProgramFileException(
                            "Day ${dayIndex + 1} references exercise uuid \"${entry.exerciseUuid}\" " +
                                "that is not defined in the file's \"exercises\" array"
                        )
                    val resolvedExercise = exerciseDao.findById(exerciseId)
                        ?: throw InvalidProgramFileException(
                            "Day ${dayIndex + 1} references an exercise that could not be restored"
                        )
                    val executionPolicy = executionPolicyFor(
                        entry = entry,
                        metricKind = resolvedExercise.metricKind,
                        workoutTypeId = resolvedExercise.workoutTypeId,
                    )
                    val programExerciseId = dao.insertProgramExercise(
                        ProgramExercise(
                            dayId = dayId,
                            orderIndex = entryIndex,
                            exerciseId = exerciseId,
                            note = entry.note?.takeIf { it.isNotBlank() },
                            restSeconds = entry.restSeconds?.coerceAtLeast(0),
                            supersetGroup = entry.supersetGroup,
                            measurementSource = executionPolicy.measurementSource,
                            completionRule = executionPolicy.completionRule,
                            prepareSeconds = entry.prepareSeconds?.coerceAtLeast(0) ?: 0,
                            transitionSeconds = entry.transitionSeconds?.coerceAtLeast(0),
                        )
                    )
                    entry.sets.forEachIndexed { setIndex, set ->
                        dao.insertSet(
                            ProgramSet(
                                programExerciseId = programExerciseId,
                                orderIndex = setIndex,
                                type = SetType.findByName(set.type.trim().uppercase()),
                                reps = set.reps,
                                repsMax = set.repsMax,
                                weightKg = set.weightKg,
                                durationMillis = set.durationMillis,
                                distanceMeters = set.distanceMeters,
                            )
                        )
                    }
                }
            }

            programId
        }
    }

    private fun Double?.validEstimateOrNull(min: Double, max: Double): Double? =
        this?.takeIf { it.isFinite() && it in min..max }

    /**
     * An imported program may reference a catalog exercise that the user already
     * owns. Fill only missing metadata: local non-blank values always win.
     */
    internal fun enrichExistingExercise(existing: Exercise, imported: Exercise): Exercise =
        existing.copy(
            workoutTypeId = existing.workoutTypeId.nonBlankOr(imported.workoutTypeId),
            description = existing.description.nonBlankOr(imported.description),
            tips = existing.tips.nonBlankOr(imported.tips),
            videoUrl = existing.videoUrl.nonBlankOr(imported.videoUrl),
            imagePath = existing.imagePath.nonBlankOr(imported.imagePath),
            met = existing.met ?: imported.met,
            secondsPerRep = existing.secondsPerRep ?: imported.secondsPerRep,
        )

    private fun String?.nonBlankOr(fallback: String?): String? =
        takeIf { !it.isNullOrBlank() } ?: fallback

    internal data class ExecutionPolicy(
        val measurementSource: MeasurementSource,
        val completionRule: CompletionRule,
    )

    /**
     * Format v2 did not contain an execution policy. Mirror the database migration:
     * recordable exercises use the recorder, untracked duration exercises use the
     * clock, and rep/weight exercises remain explicit user-confirmed sets.
     */
    internal fun deriveLegacyExecutionPolicy(
        metricKind: MetricKind,
        workoutTypeId: String?,
    ): ExecutionPolicy = when {
        !workoutTypeId.isNullOrBlank() -> ExecutionPolicy(
            measurementSource = MeasurementSource.RECORDER,
            completionRule = if (
                metricKind == MetricKind.DURATION || metricKind == MetricKind.WEIGHT_DURATION
            ) {
                CompletionRule.DURATION_REACHED
            } else {
                CompletionRule.METRIC_TARGET_REACHED
            },
        )
        metricKind.usesDuration -> ExecutionPolicy(
            measurementSource = MeasurementSource.CLOCK,
            completionRule = CompletionRule.DURATION_REACHED,
        )
        else -> ExecutionPolicy(
            measurementSource = MeasurementSource.USER_INPUT,
            completionRule = CompletionRule.USER_CONFIRM,
        )
    }

    internal fun executionPolicyFor(
        entry: ExerciseEntryExport,
        metricKind: MetricKind,
        workoutTypeId: String?,
    ): ExecutionPolicy {
        val fallback = deriveLegacyExecutionPolicy(metricKind, workoutTypeId)
        val source = entry.measurementSource
            ?.trim()
            ?.uppercase()
            ?.let { value -> MeasurementSource.entries.firstOrNull { it.name == value } }
            ?: fallback.measurementSource
        val rule = entry.completionRule
            ?.trim()
            ?.uppercase()
            ?.let { value -> CompletionRule.entries.firstOrNull { it.name == value } }
            ?: fallback.completionRule
        return ExecutionPolicy(source, rule)
    }

    /** Keeps only workoutTypeId values that resolve to a real (built-in or custom) type. */
    private fun resolveWorkoutTypeId(context: Context, workoutTypeId: String?): String? {
        val id = workoutTypeId?.takeIf { it.isNotBlank() } ?: return null
        val db = Instance.getInstance(context).db
        if (db.workoutTypeDao().findById(id) != null) return id
        val builtIn = de.tadris.fitness.data.WorkoutTypeManager.getInstance()
            .getAllTypes(context).any { it.id == id }
        return if (builtIn) id else null
    }
}
