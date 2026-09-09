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
package de.tadris.fitness.data.backup.model

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import de.tadris.fitness.data.WorkoutType

const val PROGRAM_CONTAINER_TYPE = "fitregimen-program"
const val PROGRAM_CONTAINER_FORMAT_VERSION = 3
const val PROGRAM_CONTAINER_MIN_SUPPORTED_VERSION = 1

/**
 * Format v3: a self-describing nested tree with NO database ids. Exercises are
 * referenced by uuid within the file and bundled in [exercises]; days carry their
 * exercise entries, execution policy and planned sets inline. This is also the exact
 * format the AI-prompt generator asks a model to produce, so import must stay tolerant
 * of minor sloppiness (missing uuids, unknown enum spellings). Format v2 remains
 * readable; its missing execution policy is derived by the importer.
 *
 * [completions] is only populated inside full-device backups; standalone shared
 * program files carry an empty list (plans travel, history doesn't).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ProgramContainer(
    val type: String = PROGRAM_CONTAINER_TYPE,
    val formatVersion: Int = PROGRAM_CONTAINER_FORMAT_VERSION,
    val program: ProgramExport,
    val days: List<DayExport> = emptyList(),
    val exercises: List<ExerciseExport> = emptyList(),
    val customWorkoutTypes: List<WorkoutType> = emptyList(),
    val completions: List<CompletionExport> = emptyList(),
    /** Format-v1 compatibility only. New writers always leave this empty. */
    val tasks: List<LegacyProgramTaskExport> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProgramExport(
    val uuid: String? = null,
    val name: String,
    val description: String? = null,
    /** Logical app icon name. Null lets older files use the generic program icon. */
    val icon: String? = null,
    val startDate: Long? = null,
    /** Format-v1 compatibility fields. */
    val cycleLengthDays: Int? = null,
    val createdAt: Long? = null,
)

/** Flat task used by standalone program v1 and full backup v5. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class LegacyProgramTaskExport(
    val id: Long = 0,
    val dayIndex: Int = 0,
    val dayTitle: String? = null,
    val orderIndex: Int = 0,
    val workoutTypeId: String,
    val targetSets: Int? = null,
    val targetReps: Int? = null,
    val targetDurationMillis: Long? = null,
    val targetDistanceMeters: Double? = null,
    val note: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DayExport(
    val name: String? = null,
    @param:JsonAlias("rest")
    val isRest: Boolean = false,
    val exercises: List<ExerciseEntryExport> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExerciseEntryExport(
    val exerciseUuid: String,
    val note: String? = null,
    val restSeconds: Int? = null,
    val supersetGroup: Int? = null,
    /** Nullable on read so format-v2 files can be distinguished from explicit v3 policy. */
    val measurementSource: String? = null,
    val completionRule: String? = null,
    val prepareSeconds: Int? = null,
    val transitionSeconds: Int? = null,
    val sets: List<SetExport> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SetExport(
    val type: String = "NORMAL",
    val reps: Int? = null,
    val repsMax: Int? = null,
    val weightKg: Double? = null,
    val durationMillis: Long? = null,
    val distanceMeters: Double? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExerciseExport(
    val uuid: String? = null,
    val name: String,
    val metricKind: String,
    val workoutTypeId: String? = null,
    val description: String? = null,
    val tips: String? = null,
    val videoUrl: String? = null,
    /** Null asks the importer/runtime to use its metric/type fallback. */
    val met: Double? = null,
    /** Estimated active seconds per repetition for rep-only sets. */
    val secondsPerRep: Double? = null,
)

/**
 * Full-backup only. The program exercise is addressed by its position in the tree
 * ([dayIndex]/[exerciseIndex]); the fulfilled workout by its portable uuid.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CompletionExport(
    val dayIndex: Int = -1,
    val exerciseIndex: Int = -1,
    val occurrenceDate: Long,
    val status: Int,
    val completedAt: Long = 0,
    val workoutUuid: String? = null,
    val profileUuidSnapshot: String? = null,
    val userWeightKgSnapshot: Double? = null,
    val setLogs: List<SetLogExport> = emptyList(),
    /** Format-v1/v5 compatibility fields; normalized before import. */
    val programTaskId: Long? = null,
    val workoutId: Long? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SetLogExport(
    val type: String = "NORMAL",
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationMillis: Long? = null,
    val distanceMeters: Double? = null,
    val targetReps: Int? = null,
    val targetRepsMax: Int? = null,
    val targetWeightKg: Double? = null,
    val targetDurationMillis: Long? = null,
    val targetDistanceMeters: Double? = null,
)
