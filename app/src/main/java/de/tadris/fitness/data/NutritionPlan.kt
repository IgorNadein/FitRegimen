/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** A finite, ordered nutrition plan. Null [startDateEpochDay] means an unstarted template. */
@Entity(
    tableName = "nutrition_plan",
    indices = [Index(value = ["uuid"], unique = true)],
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class NutritionPlan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String,
    val name: String,
    val description: String? = null,
    val objective: String? = null,
    val preferences: String? = null,
    val restrictions: String? = null,
    /** Portable link; no FK so deleting or importing a training program cannot delete this plan. */
    val linkedTrainingProgramUuid: String? = null,
    /** LocalDate encoded with LocalDate.toEpochDay(), deliberately independent of time zones. */
    val startDateEpochDay: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Safe counterpart for edit flows that already hold the complete entity. */
fun NutritionPlan.withUpdatedTimestamp(now: Long = System.currentTimeMillis()): NutritionPlan = copy(
    updatedAt = maxOf(createdAt, updatedAt, now),
)
