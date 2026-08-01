/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@Entity(
    tableName = "nutrition_meal",
    foreignKeys = [
        ForeignKey(
            entity = NutritionDay::class,
            parentColumns = ["id"],
            childColumns = ["dayId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("dayId"),
        Index(value = ["dayId", "orderIndex"], unique = true),
    ],
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class NutritionMeal(
    val dayId: Long,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderIndex: Int,
    val name: String,
    /** Minutes after local midnight, in 0..1439. */
    val plannedTimeMinutes: Int? = null,
    val notes: String? = null,
)
