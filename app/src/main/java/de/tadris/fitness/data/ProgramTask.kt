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

/**
 * A single planned exercise within a [TrainingProgram]'s cycle, on day [dayIndex]
 * (0-based, wraps every [TrainingProgram.cycleLengthDays]). [workoutTypeId] refers
 * loosely to [WorkoutType.id], same non-enforced reference style [Workout] itself uses.
 * The nullable target fields are filled in as relevant to the workout type - distance/
 * duration for GPS types, sets/reps for indoor rep-based types.
 */
@Entity(
    tableName = "program_task",
    foreignKeys = [
        ForeignKey(
            entity = TrainingProgram::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("programId")
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class ProgramTask(
    val programId: Long,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dayIndex: Int,
    val dayTitle: String? = null,
    val orderIndex: Int,
    val workoutTypeId: String,
    val targetSets: Int? = null,
    val targetReps: Int? = null,
    val targetDurationMillis: Long? = null,
    val targetDistanceMeters: Double? = null,
    val note: String? = null,
)
