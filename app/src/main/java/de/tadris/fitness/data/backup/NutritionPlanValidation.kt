/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.data.backup

import de.tadris.fitness.data.backup.model.NUTRITION_PLAN_CONTAINER_FORMAT_VERSION
import de.tadris.fitness.data.backup.model.NUTRITION_PLAN_CONTAINER_TYPE
import de.tadris.fitness.data.backup.model.NutritionPlanContainer
import java.io.IOException
import java.time.LocalDate

internal const val MAX_NUTRITION_PLAN_FILE_BYTES = 8 * 1024 * 1024
private const val MAX_NUTRITION_DAYS = 366
private const val MAX_MEALS_PER_DAY = 12
private const val MAX_ITEMS_PER_MEAL = 60
private const val MAX_NAME_LENGTH = 200
private const val MAX_TEXT_LENGTH = 5_000
private const val MAX_UNIT_LENGTH = 40
private const val MAX_UUID_LENGTH = 200
private const val MAX_ENERGY_KCAL = 50_000.0
private const val MAX_MACRO_GRAMS = 10_000.0
private const val MAX_WATER_OR_WEIGHT = 100_000.0
private const val MAX_QUANTITY = 1_000_000.0

/** Shared strict validation for standalone AI files and full-backup preflight. */
@Throws(IOException::class)
internal fun validateNutritionPlanContainer(
    container: NutritionPlanContainer,
    allowPrivateFields: Boolean,
    requireUuid: Boolean = false,
    requirePrivateTimestamps: Boolean = false,
) {
    if (container.type != NUTRITION_PLAN_CONTAINER_TYPE) {
        throw IOException("Not a FitRegimen nutrition-plan file")
    }
    if (container.formatVersion != NUTRITION_PLAN_CONTAINER_FORMAT_VERSION) {
        throw IOException("Unsupported nutrition-plan formatVersion ${container.formatVersion}")
    }
    if (!allowPrivateFields && (
            container.profileUuids.isNotEmpty() ||
                container.startDateEpochDay != null ||
                container.createdAt != null ||
                container.updatedAt != null ||
                container.plan.uuid != null ||
                container.plan.linkedTrainingProgramUuid != null
        )
    ) {
        throw IOException("A standalone nutrition plan contains private backup fields")
    }

    val plan = container.plan
    validateRequiredText(plan.name, "nutrition plan name", MAX_NAME_LENGTH)
    validateOptionalText(plan.description, "nutrition plan description", MAX_TEXT_LENGTH)
    validateOptionalText(plan.objective, "nutrition plan objective", MAX_TEXT_LENGTH)
    validateOptionalText(plan.preferences, "nutrition plan preferences", MAX_TEXT_LENGTH)
    validateOptionalText(plan.restrictions, "nutrition plan restrictions", MAX_TEXT_LENGTH)
    if (requireUuid && plan.uuid.isNullOrBlank()) throw IOException("Nutrition plan UUID is missing")
    if (plan.uuid != null && plan.uuid.isBlank()) throw IOException("Nutrition plan UUID is blank")
    validateOptionalText(plan.uuid, "nutrition plan UUID", MAX_UUID_LENGTH)
    if (plan.linkedTrainingProgramUuid != null && plan.linkedTrainingProgramUuid.isBlank()) {
        throw IOException("Linked training-program UUID is blank")
    }
    validateOptionalText(
        plan.linkedTrainingProgramUuid,
        "linked training-program UUID",
        MAX_UUID_LENGTH,
    )
    if (container.days.size !in 1..MAX_NUTRITION_DAYS) {
        throw IOException("Nutrition plan must contain 1..$MAX_NUTRITION_DAYS days")
    }
    container.startDateEpochDay?.let { epochDay ->
        val latestValidStart = LocalDate.MAX.toEpochDay() - container.days.lastIndex
        if (epochDay > latestValidStart) {
            throw IOException("Nutrition plan end date is outside the supported date range")
        }
    }

    val profileUuids = container.profileUuids.map(String::trim)
    if (profileUuids.any(String::isEmpty) || profileUuids.distinct().size != profileUuids.size) {
        throw IOException("Nutrition plan has missing or duplicate profile UUIDs")
    }
    profileUuids.forEach { validateRequiredText(it, "profile UUID", MAX_UUID_LENGTH) }
    container.startDateEpochDay?.let { epochDay ->
        if (epochDay !in LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay()) {
            throw IOException("Nutrition plan start date is invalid")
        }
    }
    if (requirePrivateTimestamps && (container.createdAt == null || container.updatedAt == null)) {
        throw IOException("Nutrition plan backup timestamps are missing")
    }
    container.createdAt?.takeIf { it <= 0L }?.let {
        throw IOException("Nutrition plan creation time is invalid")
    }
    container.updatedAt?.takeIf { it <= 0L }?.let {
        throw IOException("Nutrition plan update time is invalid")
    }
    if (container.createdAt != null && container.updatedAt != null &&
        container.updatedAt < container.createdAt
    ) {
        throw IOException("Nutrition plan update time precedes its creation time")
    }

    container.days.forEachIndexed { dayIndex, day ->
        validateOptionalText(day.name, "day ${dayIndex + 1} name", MAX_NAME_LENGTH)
        validateOptionalText(day.notes, "day ${dayIndex + 1} notes", MAX_TEXT_LENGTH)
        validateNumber(day.energyKcal, "day ${dayIndex + 1} energy", MAX_ENERGY_KCAL)
        validateNumber(day.proteinGrams, "day ${dayIndex + 1} protein", MAX_MACRO_GRAMS)
        validateNumber(day.fatGrams, "day ${dayIndex + 1} fat", MAX_MACRO_GRAMS)
        validateNumber(day.carbsGrams, "day ${dayIndex + 1} carbohydrates", MAX_MACRO_GRAMS)
        validateNumber(day.fiberGrams, "day ${dayIndex + 1} fiber", MAX_MACRO_GRAMS)
        validateNumber(day.waterMl, "day ${dayIndex + 1} water", MAX_WATER_OR_WEIGHT)
        if (day.meals.size > MAX_MEALS_PER_DAY) {
            throw IOException("A nutrition day has more than $MAX_MEALS_PER_DAY meals")
        }
        day.meals.forEachIndexed { mealIndex, meal ->
            validateRequiredText(
                meal.name,
                "day ${dayIndex + 1}, meal ${mealIndex + 1} name",
                MAX_NAME_LENGTH,
            )
            validateOptionalText(meal.notes, "meal notes", MAX_TEXT_LENGTH)
            meal.plannedTimeMinutes?.let { minutes ->
                if (minutes !in 0..1439) {
                    throw IOException("Meal time must be between 0 and 1439 minutes")
                }
            }
            if (meal.items.size > MAX_ITEMS_PER_MEAL) {
                throw IOException("A meal has more than $MAX_ITEMS_PER_MEAL items")
            }
            meal.items.forEachIndexed { itemIndex, item ->
                validateRequiredText(
                    item.name,
                    "day ${dayIndex + 1}, meal ${mealIndex + 1}, item ${itemIndex + 1} name",
                    MAX_NAME_LENGTH,
                )
                validateOptionalText(item.unit, "food unit", MAX_UNIT_LENGTH)
                validateOptionalText(item.notes, "food notes", MAX_TEXT_LENGTH)
                validateNumber(item.quantity, "food quantity", MAX_QUANTITY)
                validateNumber(item.weightGrams, "food weight", MAX_WATER_OR_WEIGHT)
                validateNumber(item.energyKcal, "food energy", MAX_ENERGY_KCAL)
                validateNumber(item.proteinGrams, "food protein", MAX_MACRO_GRAMS)
                validateNumber(item.fatGrams, "food fat", MAX_MACRO_GRAMS)
                validateNumber(item.carbsGrams, "food carbohydrates", MAX_MACRO_GRAMS)
                validateNumber(item.fiberGrams, "food fiber", MAX_MACRO_GRAMS)
            }
        }
    }
}

private fun validateRequiredText(value: String, label: String, maxLength: Int) {
    if (value.isBlank()) throw IOException("$label is missing")
    if (value.length > maxLength) throw IOException("$label is longer than $maxLength characters")
}

private fun validateOptionalText(value: String?, label: String, maxLength: Int) {
    if (value != null && value.length > maxLength) {
        throw IOException("$label is longer than $maxLength characters")
    }
}

private fun validateNumber(value: Double?, label: String, maximum: Double) {
    if (value != null && (!value.isFinite() || value < 0.0 || value > maximum)) {
        throw IOException("Invalid $label value")
    }
}
