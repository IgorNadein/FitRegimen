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
import androidx.room.TypeConverter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** Stored as String so later additions (DROPSET, FAILURE...) are no-op migrations. */
enum class SetType {
    NORMAL,
    WARMUP;

    companion object {
        fun findByName(name: String?): SetType =
            entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

class SetTypeConverter {
    @TypeConverter
    fun toStringValue(type: SetType): String = type.name

    @TypeConverter
    fun fromStringValue(value: String): SetType = SetType.findByName(value)
}

/**
 * One planned set of a [ProgramExercise]. Columns are uniform and all-nullable;
 * the exercise's [MetricKind] masks which ones the UI shows and fills, so pyramids
 * (12/10/8) and rep ranges ([reps]..[repsMax]) are plain rows.
 */
@Entity(
    tableName = "program_set",
    foreignKeys = [
        ForeignKey(
            entity = ProgramExercise::class,
            parentColumns = ["id"],
            childColumns = ["programExerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("programExerciseId")
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class ProgramSet(
    val programExerciseId: Long,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderIndex: Int,
    val type: SetType = SetType.NORMAL,
    val reps: Int? = null,
    val repsMax: Int? = null,
    val weightKg: Double? = null,
    val durationMillis: Long? = null,
    val distanceMeters: Double? = null,
)
