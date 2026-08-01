/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.data.backup.model

import com.fasterxml.jackson.annotation.JsonInclude

const val NUTRITION_PLAN_CONTAINER_TYPE = "fitregimen-nutrition-plan"
const val NUTRITION_PLAN_CONTAINER_FORMAT_VERSION = 1

/**
 * Portable, id-free nutrition tree shared by manual export and AI generation.
 *
 * [profileUuids], [startDateEpochDay] and source timestamps are populated only
 * inside a full-device backup. [JsonInclude] guarantees that a standalone writer
 * cannot leak empty/null private-link fields merely because they exist in the model.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class NutritionPlanContainer(
    val type: String = NUTRITION_PLAN_CONTAINER_TYPE,
    val formatVersion: Int = NUTRITION_PLAN_CONTAINER_FORMAT_VERSION,
    val plan: NutritionPlanExport,
    val days: List<NutritionDayExport> = emptyList(),
    val profileUuids: List<String> = emptyList(),
    val startDateEpochDay: Long? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class NutritionPlanExport(
    val uuid: String? = null,
    val name: String,
    val description: String? = null,
    val objective: String? = null,
    val preferences: String? = null,
    val restrictions: String? = null,
    val linkedTrainingProgramUuid: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class NutritionDayExport(
    val name: String? = null,
    val notes: String? = null,
    val energyKcal: Double? = null,
    val proteinGrams: Double? = null,
    val fatGrams: Double? = null,
    val carbsGrams: Double? = null,
    val fiberGrams: Double? = null,
    val waterMl: Double? = null,
    val meals: List<NutritionMealExport> = emptyList(),
)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class NutritionMealExport(
    val name: String,
    val plannedTimeMinutes: Int? = null,
    val notes: String? = null,
    val items: List<NutritionItemExport> = emptyList(),
)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class NutritionItemExport(
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
