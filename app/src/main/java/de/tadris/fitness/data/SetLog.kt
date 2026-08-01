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
 * One performed set, child of a [ProgramCompletion]. Optional: a completion with no
 * set logs simply means "done as planned". The target* columns snapshot the plan at
 * logging time (no FK to [ProgramSet] on purpose) so history survives later plan
 * edits and "planned vs done" stays queryable forever.
 *
 * Deliberately not attached directly to [Workout]. The occurrence history
 * projector combines these logs (or the planned-set fallback for a quick check)
 * into one summary workout for the dashboard and statistics.
 */
@Entity(
    tableName = "set_log",
    foreignKeys = [
        ForeignKey(
            entity = ProgramCompletion::class,
            parentColumns = ["id"],
            childColumns = ["completionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("completionId")
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class SetLog(
    val completionId: Long,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderIndex: Int,
    val type: SetType = SetType.NORMAL,
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
