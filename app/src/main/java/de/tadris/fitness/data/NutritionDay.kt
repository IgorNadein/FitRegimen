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
    tableName = "nutrition_day",
    foreignKeys = [
        ForeignKey(
            entity = NutritionPlan::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("planId"),
        Index(value = ["planId", "orderIndex"], unique = true),
    ],
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class NutritionDay(
    val planId: Long,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderIndex: Int,
    val name: String? = null,
    val notes: String? = null,
    val energyKcal: Double? = null,
    val proteinGrams: Double? = null,
    val fatGrams: Double? = null,
    val carbsGrams: Double? = null,
    val fiberGrams: Double? = null,
    val waterMl: Double? = null,
)
