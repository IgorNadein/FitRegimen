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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Local, privacy-sensitive association. Profile UUID intentionally has no FK:
 * a shared/full plan remains valid if its profile is missing or later deleted.
 */
@Entity(
    tableName = "nutrition_plan_profile",
    primaryKeys = ["planId", "profileUuid"],
    foreignKeys = [
        ForeignKey(
            entity = NutritionPlan::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileUuid")],
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class NutritionPlanProfile(
    val planId: Long,
    val profileUuid: String,
)
