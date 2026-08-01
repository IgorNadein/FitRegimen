/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data.backup.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import de.tadris.fitness.data.Exercise
import de.tadris.fitness.data.MetricKind

/**
 * Full-backup-only representation of a catalog exercise.
 *
 * [imageArchivePath] addresses binary data inside the backup ZIP. The local private
 * path is deliberately not serialized, so standalone program JSON and full backups
 * never expose an app-private filesystem layout.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ExerciseCatalogExport(
    val uuid: String = "",
    val name: String = "",
    val metricKind: String = MetricKind.REPS.name,
    val workoutTypeId: String? = null,
    val description: String? = null,
    val tips: String? = null,
    val videoUrl: String? = null,
    val hidden: Boolean = false,
    val met: Double? = null,
    val secondsPerRep: Double? = null,
    val imageArchivePath: String? = null,
)

fun Exercise.toCatalogBackupExport(imageArchivePath: String? = null) = ExerciseCatalogExport(
    uuid = uuid,
    name = name,
    metricKind = metricKind.name,
    workoutTypeId = workoutTypeId,
    description = description,
    tips = tips,
    videoUrl = videoUrl,
    hidden = hidden,
    met = met,
    secondsPerRep = secondsPerRep,
    imageArchivePath = imageArchivePath,
)

fun ExerciseCatalogExport.toEntity(imagePath: String? = null) = Exercise(
    uuid = uuid.trim(),
    name = name.trim(),
    metricKind = MetricKind.valueOf(metricKind.trim().uppercase()),
    workoutTypeId = workoutTypeId?.trim()?.takeIf(String::isNotEmpty),
    description = description?.trim()?.takeIf(String::isNotEmpty),
    tips = tips?.trim()?.takeIf(String::isNotEmpty),
    videoUrl = videoUrl?.trim()?.takeIf(String::isNotEmpty),
    imagePath = imagePath,
    hidden = hidden,
    met = met,
    secondsPerRep = secondsPerRep,
)
