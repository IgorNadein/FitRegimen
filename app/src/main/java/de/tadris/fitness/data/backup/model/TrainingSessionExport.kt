/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data.backup.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Portable full-backup representation of one guided workout execution.
 *
 * Local Room ids and mutable plan foreign keys are deliberately absent. The nested
 * list order is the session order, immutable plan data is retained as snapshots,
 * and a recorder result is linked through its globally portable workout uuid.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TrainingSessionExport(
    val uuid: String,
    /** Portable source identity. Numeric Room ids must never leave the device. */
    val sourceProgramUuid: String? = null,
    /** Position in the source program's ordered day list. */
    val sourceDayIndex: Int? = null,
    val occurrenceDate: Long? = null,
    val zoneId: String? = null,
    val nameSnapshot: String,
    val descriptionSnapshot: String? = null,
    val profileUuidSnapshot: String? = null,
    val userWeightKgSnapshot: Double? = null,
    val stepLengthMetersSnapshot: Double? = null,
    val status: String = "ACTIVE",
    val phase: String = "READY",
    val currentExerciseIndex: Int = 0,
    val currentSetIndex: Int = 0,
    val startedAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val phaseStartedAt: Long? = null,
    val phaseDeadlineAt: Long? = null,
    val pausedRemainingMillis: Long? = null,
    val accumulatedPausedMillis: Long = 0,
    val exercises: List<SessionExerciseExport> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SessionExerciseExport(
    /** Position in the source day's ordered exercise list, when still linked. */
    val sourceProgramExerciseIndex: Int? = null,
    val exerciseUuidSnapshot: String,
    val nameSnapshot: String,
    val metricKindSnapshot: String = "REPS",
    val workoutTypeIdSnapshot: String? = null,
    val descriptionSnapshot: String? = null,
    val tipsSnapshot: String? = null,
    val videoUrlSnapshot: String? = null,
    /**
     * Binary snapshot inside the full-backup ZIP. New writers never serialize an
     * app-private filesystem path into [imagePathSnapshot].
     */
    val imageArchivePathSnapshot: String? = null,
    /** Backup-v6/v7 compatibility only; ignored unless rebound through the catalog. */
    val imagePathSnapshot: String? = null,
    val noteSnapshot: String? = null,
    val measurementSource: String = "USER_INPUT",
    val completionRule: String = "USER_CONFIRM",
    val prepareSeconds: Int = 0,
    val restSeconds: Int? = null,
    val transitionSeconds: Int? = null,
    val status: String = "PENDING",
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val sets: List<SessionSetExport> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SessionSetExport(
    /** Position in the source program exercise's ordered set list, when still linked. */
    val sourceProgramSetIndex: Int? = null,
    val type: String = "NORMAL",
    val status: String = "PENDING",
    val completionSource: String? = null,
    val targetReps: Int? = null,
    val targetRepsMax: Int? = null,
    val targetWeightKg: Double? = null,
    val targetDurationMillis: Long? = null,
    val targetDistanceMeters: Double? = null,
    val actualReps: Int? = null,
    val actualWeightKg: Double? = null,
    val actualDurationMillis: Long? = null,
    val actualDistanceMeters: Double? = null,
    val linkedWorkoutUuid: String? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
)
