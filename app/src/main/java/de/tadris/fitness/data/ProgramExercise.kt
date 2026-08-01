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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** Where the performed value of an exercise comes from. Stored as text for forward compatibility. */
enum class MeasurementSource {
    USER_INPUT,
    CLOCK,
    RECORDER;

    companion object {
        fun findByName(name: String?): MeasurementSource =
            entries.firstOrNull { it.name == name } ?: USER_INPUT
    }
}

/** What makes the active set complete. Stored separately from [MeasurementSource]. */
enum class CompletionRule {
    USER_CONFIRM,
    DURATION_REACHED,
    METRIC_TARGET_REACHED;

    companion object {
        fun findByName(name: String?): CompletionRule =
            entries.firstOrNull { it.name == name } ?: USER_CONFIRM
    }
}

class MeasurementSourceConverter {
    @TypeConverter
    fun toStringValue(source: MeasurementSource?): String? = source?.name

    @TypeConverter
    fun fromStringValue(value: String?): MeasurementSource? =
        value?.let(MeasurementSource::findByName)
}

class CompletionRuleConverter {
    @TypeConverter
    fun toStringValue(rule: CompletionRule): String = rule.name

    @TypeConverter
    fun fromStringValue(value: String): CompletionRule = CompletionRule.findByName(value)
}

/**
 * One exercise slot inside a [ProgramDay], referencing the [Exercise] catalog.
 * Planned sets live in [ProgramSet] child rows.
 *
 * [supersetGroup] is a schema hook only (no UI yet): slots sharing a non-null value
 * form a superset. [restSeconds] is the rest between sets; [transitionSeconds] is
 * the rest after the last set before the next exercise. Measurement and completion
 * are deliberately orthogonal: a clock can still require confirmation, while a
 * recorder can auto-complete when a metric target is reached.
 */
@Entity(
    tableName = "program_exercise",
    foreignKeys = [
        ForeignKey(
            entity = ProgramDay::class,
            parentColumns = ["id"],
            childColumns = ["dayId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"]
        )
    ],
    indices = [
        Index("dayId"),
        Index("exerciseId")
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class ProgramExercise(
    val dayId: Long,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderIndex: Int,
    val exerciseId: Long,
    val note: String? = null,
    val restSeconds: Int? = null,
    val supersetGroup: Int? = null,
    @ColumnInfo(defaultValue = "'USER_INPUT'")
    val measurementSource: MeasurementSource = MeasurementSource.USER_INPUT,
    @ColumnInfo(defaultValue = "'USER_CONFIRM'")
    val completionRule: CompletionRule = CompletionRule.USER_CONFIRM,
    @ColumnInfo(defaultValue = "0")
    val prepareSeconds: Int = 0,
    val transitionSeconds: Int? = null,
)
