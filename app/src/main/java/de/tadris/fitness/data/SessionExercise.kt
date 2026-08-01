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

enum class SessionExerciseStatus {
    PENDING,
    ACTIVE,
    COMPLETED,
    SKIPPED;

    companion object {
        fun findByName(name: String?): SessionExerciseStatus =
            entries.firstOrNull { it.name == name } ?: PENDING
    }
}

class SessionExerciseStatusConverter {
    @TypeConverter
    fun toStringValue(status: SessionExerciseStatus): String = status.name

    @TypeConverter
    fun fromStringValue(value: String): SessionExerciseStatus =
        SessionExerciseStatus.findByName(value)
}

/** Immutable exercise snapshot belonging to a concrete [TrainingSession]. */
@Entity(
    tableName = "session_exercise",
    foreignKeys = [
        ForeignKey(
            entity = TrainingSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProgramExercise::class,
            parentColumns = ["id"],
            childColumns = ["sourceProgramExerciseId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["sourceExerciseId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index("sourceProgramExerciseId"),
        Index("sourceExerciseId"),
        Index(value = ["sessionId", "orderIndex"], unique = true),
    ],
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class SessionExercise(
    val sessionId: Long,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderIndex: Int,
    val sourceProgramExerciseId: Long? = null,
    val sourceExerciseId: Long? = null,
    val exerciseUuidSnapshot: String,
    val nameSnapshot: String,
    val metricKindSnapshot: MetricKind,
    val workoutTypeIdSnapshot: String? = null,
    val descriptionSnapshot: String? = null,
    val tipsSnapshot: String? = null,
    val videoUrlSnapshot: String? = null,
    val imagePathSnapshot: String? = null,
    val noteSnapshot: String? = null,
    val measurementSource: MeasurementSource,
    val completionRule: CompletionRule,
    val prepareSeconds: Int = 0,
    val restSeconds: Int? = null,
    val transitionSeconds: Int? = null,
    val status: SessionExerciseStatus = SessionExerciseStatus.PENDING,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
)
