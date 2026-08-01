/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

enum class SessionSetStatus {
    PENDING,
    ACTIVE,
    COMPLETED,
    SKIPPED;

    companion object {
        fun findByName(name: String?): SessionSetStatus =
            entries.firstOrNull { it.name == name } ?: PENDING
    }
}

class SessionSetStatusConverter {
    @TypeConverter
    fun toStringValue(status: SessionSetStatus): String = status.name

    @TypeConverter
    fun fromStringValue(value: String): SessionSetStatus = SessionSetStatus.findByName(value)
}

/** Planned target and performed result of one set inside a session snapshot. */
@Entity(
    tableName = "session_set",
    foreignKeys = [
        ForeignKey(
            entity = SessionExercise::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProgramSet::class,
            parentColumns = ["id"],
            childColumns = ["sourceProgramSetId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["linkedWorkoutId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("sessionExerciseId"),
        Index("sourceProgramSetId"),
        Index("linkedWorkoutId"),
        Index(value = ["sessionExerciseId", "orderIndex"], unique = true),
    ],
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class SessionSet(
    val sessionExerciseId: Long,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderIndex: Int,
    val sourceProgramSetId: Long? = null,
    val type: SetType = SetType.NORMAL,
    val status: SessionSetStatus = SessionSetStatus.PENDING,
    val completionSource: MeasurementSource? = null,
    val targetReps: Int? = null,
    val targetRepsMax: Int? = null,
    val targetWeightKg: Double? = null,
    val targetDurationMillis: Long? = null,
    val targetDistanceMeters: Double? = null,
    val actualReps: Int? = null,
    val actualWeightKg: Double? = null,
    val actualDurationMillis: Long? = null,
    val actualDistanceMeters: Double? = null,
    val linkedWorkoutId: Long? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
)
