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
package de.tadris.fitness.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

const val PROGRAM_COMPLETION_STATUS_COMPLETED = 1
const val PROGRAM_COMPLETION_STATUS_SKIPPED = 2

/**
 * The outcome of one occurrence of a [ProgramExercise] (identified by
 * [occurrenceDate], midnight of that scheduled program date).
 * Rows are created lazily when the user acts - untouched occurrences are computed
 * on the fly from the program's schedule.
 *
 * Performed strength sets optionally hang off this row as [SetLog] children.
 * For recordable (cardio) exercises, [workoutId] links the actual recorded [Workout].
 */
@Entity(
    tableName = "program_completion",
    foreignKeys = [
        ForeignKey(
            entity = ProgramExercise::class,
            parentColumns = ["id"],
            childColumns = ["programExerciseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["programExerciseId", "occurrenceDate"], unique = true),
        Index("workoutId")
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class ProgramCompletion(
    val programExerciseId: Long,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val occurrenceDate: Long,
    val status: Int,
    val workoutId: Long? = null,
    val completedAt: Long = 0,
    val profileUuidSnapshot: String? = null,
    val userWeightKgSnapshot: Double? = null,
)
