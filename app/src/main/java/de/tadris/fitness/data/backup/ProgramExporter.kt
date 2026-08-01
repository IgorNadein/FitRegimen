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
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import de.tadris.fitness.Instance
import de.tadris.fitness.data.TrainingProgram
import de.tadris.fitness.data.backup.model.CompletionExport
import de.tadris.fitness.data.backup.model.DayExport
import de.tadris.fitness.data.backup.model.ExerciseEntryExport
import de.tadris.fitness.data.backup.model.ExerciseExport
import de.tadris.fitness.data.backup.model.ProgramContainer
import de.tadris.fitness.data.backup.model.ProgramExport
import de.tadris.fitness.data.backup.model.SetExport
import de.tadris.fitness.data.backup.model.SetLogExport
import java.io.OutputStream

/** Builds and writes the standalone, shareable JSON (format v3) for a [TrainingProgram]. */
object ProgramExporter {

    private val mapper = JsonMapper.builder()
        .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build()

    fun buildContainer(
        context: Context,
        program: TrainingProgram,
        includeCompletions: Boolean = false,
    ): ProgramContainer {
        val db = Instance.getInstance(context).db
        val dao = db.trainingProgramDao()
        val exerciseDao = db.exerciseDao()

        val days = dao.getDaysOfProgram(program.id)
        val referencedExerciseIds = linkedSetOf<Long>()
        val completions = mutableListOf<CompletionExport>()

        val dayExports = days.mapIndexed { dayIndex, day ->
            val entries = dao.getExercisesOfDay(day.id)
            DayExport(
                name = day.name,
                isRest = day.isRest,
                exercises = entries.mapIndexed { exerciseIndex, entry ->
                    referencedExerciseIds.add(entry.exerciseId)
                    if (includeCompletions) {
                        completions.addAll(
                            exportCompletionsOf(context, entry.id, dayIndex, exerciseIndex)
                        )
                    }
                    ExerciseEntryExport(
                        exerciseUuid = exerciseDao.findById(entry.exerciseId)?.uuid ?: "",
                        note = entry.note,
                        restSeconds = entry.restSeconds,
                        supersetGroup = entry.supersetGroup,
                        measurementSource = entry.measurementSource.name,
                        completionRule = entry.completionRule.name,
                        prepareSeconds = entry.prepareSeconds,
                        transitionSeconds = entry.transitionSeconds,
                        sets = dao.getSetsOfProgramExercise(entry.id).map { set ->
                            SetExport(
                                type = set.type.name,
                                reps = set.reps,
                                repsMax = set.repsMax,
                                weightKg = set.weightKg,
                                durationMillis = set.durationMillis,
                                distanceMeters = set.distanceMeters,
                            )
                        },
                    )
                },
            )
        }

        val exercises = referencedExerciseIds.mapNotNull { exerciseDao.findById(it) }
        val usedTypeIds = exercises.mapNotNull { it.workoutTypeId }.toSet()
        val customTypes = db.workoutTypeDao().findAll().filter { it.id in usedTypeIds }

        return ProgramContainer(
            program = ProgramExport(
                uuid = program.uuid,
                name = program.name,
                description = program.description,
                icon = program.icon,
                startDate = if (includeCompletions) program.startDate else null,
                createdAt = if (includeCompletions) program.createdAt else null,
            ),
            days = dayExports,
            exercises = exercises.map { exercise ->
                ExerciseExport(
                    uuid = exercise.uuid,
                    name = exercise.name,
                    metricKind = exercise.metricKind.name,
                    workoutTypeId = exercise.workoutTypeId,
                    description = exercise.description,
                    tips = exercise.tips,
                    videoUrl = exercise.videoUrl,
                    met = exercise.met,
                    secondsPerRep = exercise.secondsPerRep,
                )
            },
            customWorkoutTypes = customTypes,
            completions = completions,
        )
    }

    private fun exportCompletionsOf(
        context: Context,
        programExerciseId: Long,
        dayIndex: Int,
        exerciseIndex: Int,
    ): List<CompletionExport> {
        val db = Instance.getInstance(context).db
        val dao = db.trainingProgramDao()
        // findCompletion is keyed by occurrence; enumerate via the program-wide query instead
        return db.trainingProgramDao().getCompletionsOfProgramExercise(programExerciseId).map { completion ->
            CompletionExport(
                dayIndex = dayIndex,
                exerciseIndex = exerciseIndex,
                occurrenceDate = completion.occurrenceDate,
                status = completion.status,
                completedAt = completion.completedAt,
                workoutUuid = completion.workoutId?.let { db.workoutDao().findById(it)?.uuid },
                profileUuidSnapshot = completion.profileUuidSnapshot,
                userWeightKgSnapshot = completion.userWeightKgSnapshot,
                setLogs = dao.getLogsOfCompletion(completion.id).map { log ->
                    SetLogExport(
                        type = log.type.name,
                        reps = log.reps,
                        weightKg = log.weightKg,
                        durationMillis = log.durationMillis,
                        distanceMeters = log.distanceMeters,
                        targetReps = log.targetReps,
                        targetRepsMax = log.targetRepsMax,
                        targetWeightKg = log.targetWeightKg,
                        targetDurationMillis = log.targetDurationMillis,
                        targetDistanceMeters = log.targetDistanceMeters,
                    )
                },
            )
        }
    }

    fun write(container: ProgramContainer, outputStream: OutputStream) {
        mapper.writeValue(outputStream, container)
    }
}
