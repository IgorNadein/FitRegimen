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
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * What an exercise measures. Drives which input fields every planning/logging UI
 * shows - an exercise never displays a field its kind doesn't declare.
 * Stored as String so adding kinds later is a no-op migration.
 */
enum class MetricKind(
    val usesReps: Boolean,
    val usesWeight: Boolean,
    val usesDuration: Boolean,
    val usesDistance: Boolean,
) {
    WEIGHT_REPS(usesReps = true, usesWeight = true, usesDuration = false, usesDistance = false),
    REPS(usesReps = true, usesWeight = false, usesDuration = false, usesDistance = false),
    DURATION(usesReps = false, usesWeight = false, usesDuration = true, usesDistance = false),
    WEIGHT_DURATION(usesReps = false, usesWeight = true, usesDuration = true, usesDistance = false),
    DISTANCE_DURATION(usesReps = false, usesWeight = false, usesDuration = true, usesDistance = true);

    /** Kinds where linking to a recordable [WorkoutType] (GPS/indoor session) makes sense. */
    val recordable get() = usesDuration || usesDistance

    companion object {
        fun findByName(name: String?): MetricKind =
            entries.firstOrNull { it.name == name } ?: REPS
    }
}

class MetricKindConverter {
    @TypeConverter
    fun toStringValue(kind: MetricKind): String = kind.name

    @TypeConverter
    fun fromStringValue(value: String): MetricKind = MetricKind.findByName(value)
}

/**
 * The user-authored exercise catalog (FitRegimen's planning taxonomy). Kept separate
 * from [WorkoutType], which stays FitoTrack's recording/session taxonomy: an Exercise
 * may loosely reference a WorkoutType via [workoutTypeId] (same non-enforced string
 * style [Workout] uses), which marks it recordable - the schedule then offers to
 * launch the GPS/indoor recorder and link the resulting [Workout].
 *
 * [hidden] is a soft delete: exercises referenced by program history are never
 * hard-deleted, they just disappear from the picker.
 *
 * [met] and [secondsPerRep] are optional execution estimates. Null means that the
 * app derives a conservative value from [metricKind] and [workoutTypeId], keeping
 * old and imported exercise catalogs useful without mandatory manual setup.
 */
@Entity(tableName = "exercise")
@JsonIgnoreProperties(ignoreUnknown = true)
data class Exercise(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String,
    val name: String,
    val metricKind: MetricKind,
    val workoutTypeId: String? = null,
    val description: String? = null,
    val tips: String? = null,
    val videoUrl: String? = null,
    val imagePath: String? = null,
    val hidden: Boolean = false,
    val met: Double? = null,
    val secondsPerRep: Double? = null,
)
