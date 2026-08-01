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
    tableName = "nutrition_item",
    foreignKeys = [
        ForeignKey(
            entity = NutritionMeal::class,
            parentColumns = ["id"],
            childColumns = ["mealId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("mealId"),
        Index(value = ["mealId", "orderIndex"], unique = true),
    ],
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class NutritionItem(
    val mealId: Long,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderIndex: Int,
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val weightGrams: Double? = null,
    val energyKcal: Double? = null,
    val proteinGrams: Double? = null,
    val fatGrams: Double? = null,
    val carbsGrams: Double? = null,
    val fiberGrams: Double? = null,
    val notes: String? = null,
)
