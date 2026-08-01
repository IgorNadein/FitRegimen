/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack
 *
 * FitoTrack is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package de.tadris.fitness.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

enum class TrainingSessionStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    ABORTED;

    companion object {
        fun findByName(name: String?): TrainingSessionStatus =
            entries.firstOrNull { it.name == name } ?: ACTIVE
    }
}

/** The persisted runner phase. READY means the next set is waiting for an explicit start. */
enum class TrainingSessionPhase {
    READY,
    PREPARE,
    ACTIVE_SET,
    REST,
    FINISHED;

    companion object {
        fun findByName(name: String?): TrainingSessionPhase =
            entries.firstOrNull { it.name == name } ?: READY
    }
}

class TrainingSessionStatusConverter {
    @TypeConverter
    fun toStringValue(status: TrainingSessionStatus): String = status.name

    @TypeConverter
    fun fromStringValue(value: String): TrainingSessionStatus =
        TrainingSessionStatus.findByName(value)
}

class TrainingSessionPhaseConverter {
    @TypeConverter
    fun toStringValue(phase: TrainingSessionPhase): String = phase.name

    @TypeConverter
    fun fromStringValue(value: String): TrainingSessionPhase =
        TrainingSessionPhase.findByName(value)
}

/**
 * One concrete execution of a program day. Source ids are nullable and SET_NULL:
 * the copied session must remain a complete historical record when its mutable plan
 * is edited or deleted.
 */
@Entity(
    tableName = "training_session",
    foreignKeys = [
        ForeignKey(
            entity = TrainingProgram::class,
            parentColumns = ["id"],
            childColumns = ["sourceProgramId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ProgramDay::class,
            parentColumns = ["id"],
            childColumns = ["sourceDayId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index("sourceProgramId"),
        Index("sourceDayId"),
        Index("status"),
    ],
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class TrainingSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String,
    val sourceProgramId: Long? = null,
    val sourceDayId: Long? = null,
    val occurrenceDate: Long? = null,
    val zoneId: String? = null,
    val nameSnapshot: String,
    val descriptionSnapshot: String? = null,
    val profileUuidSnapshot: String? = null,
    val userWeightKgSnapshot: Double? = null,
    val stepLengthMetersSnapshot: Double? = null,
    val status: TrainingSessionStatus = TrainingSessionStatus.ACTIVE,
    val phase: TrainingSessionPhase = TrainingSessionPhase.READY,
    val currentExerciseIndex: Int = 0,
    val currentSetIndex: Int = 0,
    val startedAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val phaseStartedAt: Long? = null,
    val phaseDeadlineAt: Long? = null,
    val pausedRemainingMillis: Long? = null,
    /** Time spent outside active execution; [startedAt] remains an immutable calendar anchor. */
    @ColumnInfo(defaultValue = "0")
    val accumulatedPausedMillis: Long = 0,
)
