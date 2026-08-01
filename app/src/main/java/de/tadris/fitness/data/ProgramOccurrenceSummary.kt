/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Owns the single derived [Workout] shown for one concrete program-day occurrence.
 *
 * [ProgramCompletion.workoutId] deliberately keeps its original meaning: it points
 * to a real source workout recorded or linked for one exercise. The aggregate
 * workout is addressed here instead, so rebuilding or removing the aggregate can
 * never destroy that source recording.
 */
@Entity(
    tableName = "program_occurrence_summary",
    primaryKeys = ["dayId", "occurrenceDate"],
    foreignKeys = [
        ForeignKey(
            entity = ProgramDay::class,
            parentColumns = ["id"],
            childColumns = ["dayId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["summaryWorkoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["summaryWorkoutId"], unique = true),
    ],
)
data class ProgramOccurrenceSummary(
    val dayId: Long,
    val occurrenceDate: Long,
    val summaryWorkoutId: Long,
)

/** Lightweight Room projection used by idempotent summary backfills. */
data class ProgramOccurrenceKey(
    val dayId: Long,
    val occurrenceDate: Long,
)
