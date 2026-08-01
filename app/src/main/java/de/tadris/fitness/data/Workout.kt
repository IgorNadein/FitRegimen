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
 */
package de.tadris.fitness.data

import android.content.Context
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fasterxml.jackson.annotation.JsonIgnore
import de.tadris.fitness.util.Icon as WorkoutIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * @property id Unique ID (internal, fast)
 * @property uuid Unique UUID (external, globally unique)
 * @property start timestamp of start
 * @property end timestamp of end
 * @property activeDuration active duration in ms
 * @property comment User defined comment
 * @property distance Distance in meters
 * @property energy Energy in joules
 * @property workoutTypeId Type of workout
 * @property programUuid Portable identity of the source program, captured at completion
 * @property programName Display name of the source program, captured at completion
 * @property iconOverride Logical icon name captured from the source program
 */
@Entity(
    tableName = "workout",
    indices = [Index("profileUuid")],
)
data class Workout(
    @PrimaryKey
    val id: Long,
    val uuid: String,
    val start: Long,
    val end: Long,
    val activeDuration: Long,
    val comment: String? = null,
    val distance: Double,
    val repetitions: Int,
    val energy: Double,
    val workoutTypeId: String,
    val intervalSetUsedId: Long,
    val programUuid: String? = null,
    val programName: String? = null,
    val iconOverride: String? = null,
    val profileUuid: String? = null,
    val userWeightKgSnapshot: Double? = null,
    val stepLengthMetersSnapshot: Double? = null,
) {

    val kcal get() = energy / 4186.8

    val totalDuration get() = end - start // ms

    val pauseDuration get() = totalDuration - activeDuration // ms

    val averageSpeedActive get() = if (activeDuration > 0) distance / (activeDuration / 1000.0) else 0.0 // m/s

    val averageSpeedTotal get() = if (activeDuration > 0) distance / (totalDuration / 1000.0) else 0.0 // m/s

    val averagePaceActive get() = if (distance > 0) (activeDuration / 1000.0 / 60.0) / (distance / 1000.0) else 0.0 // min/km

    val averageFrequency get() = if (activeDuration > 0) repetitions / (activeDuration / 1000.0) else 0.0

    // returns a file name string without suffix that can be used for exports
    fun getExportFileName(context: Context?): String {
        return if (safeComment.isNotEmpty()) {
            String.format(
                "workout-%s-%s-%s",
                safeDateString, getSafeWorkoutTypeTitle(context),
                safeComment
            )
        } else {
            String.format(
                "workout-%s-%s",
                safeDateString, getSafeWorkoutTypeTitle(context)
            )
        }
    }

    @get:JsonIgnore
    val dateString: String
        get() = SimpleDateFormat.getDateTimeInstance().format(Date(start))

    @get:JsonIgnore
    val safeDateString: String
        get() = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
            .format(Date(start))

    @get:JsonIgnore
    val safeComment: String
        get() {
            if (comment == null) return ""
            val safeComment = comment.replace(
                "[^\\p{L}\\p{Nd}]".toRegex(),
                "_"
            ) // leave Unicode letters & digits, replace other chars by "_"
            return safeComment.substring(
                0,
                min(safeComment.length, 50)
            ) // cut the comment after 50 Chars
        }

    @JsonIgnore
    fun getSafeWorkoutTypeTitle(context: Context?): String {
        return getWorkoutType(context).title.replace("[^\\p{L}\\p{Nd}]".toRegex(), "_")
    }

    @JsonIgnore
    fun getWorkoutType(context: Context?): WorkoutType {
        return WorkoutTypeManager.getInstance().getWorkoutTypeById(context, workoutTypeId)
    }

    /**
     * Program summaries keep the base recording type for compatibility, but their
     * visible identity belongs to the concrete program that produced them.
     */
    @JsonIgnore
    fun getDisplayWorkoutType(context: Context?): WorkoutType {
        val base = getWorkoutType(context)
        val sourceUuid = programUuid?.takeIf { it.isNotBlank() } ?: return base
        return WorkoutType(
            "program:$sourceUuid",
            programName?.takeIf { it.isNotBlank() } ?: base.title,
            base.minDistance,
            base.color,
            getDisplayIconName(context),
            base.MET,
            base.recordingType,
            base.repeatingExerciseName,
        )
    }

    @JsonIgnore
    fun getDisplayIconName(context: Context?): String {
        val validOverride = iconOverride?.let { persisted ->
            WorkoutIcon.values().firstOrNull { it.name == persisted }?.name
        }
        return validOverride
            ?: if (!programUuid.isNullOrBlank()) WorkoutIcon.ACTIVITY.name
            else getWorkoutType(context).icon
    }

}
