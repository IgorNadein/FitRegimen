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
 * Records the outcome of one occurrence of a [ProgramTask] (identified by
 * [occurrenceDate], the calendar date that cycle iteration fell on). Rows are only
 * created when the user actually acts on an occurrence - future/untouched occurrences
 * are computed on the fly from the program's schedule, not pre-populated here.
 *
 * [workoutId] links to the actual recorded [Workout] that fulfilled this task, if any;
 * null with [status] == SKIPPED means the user explicitly marked it as skipped.
 */
@Entity(
    tableName = "program_completion",
    foreignKeys = [
        ForeignKey(
            entity = ProgramTask::class,
            parentColumns = ["id"],
            childColumns = ["programTaskId"],
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
        Index("programTaskId"),
        Index("workoutId")
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class ProgramCompletion(
    val programTaskId: Long,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val occurrenceDate: Long,
    val workoutId: Long? = null,
    val status: Int,
)
