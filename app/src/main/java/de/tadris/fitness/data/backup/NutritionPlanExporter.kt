/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.data.backup

import android.content.Context
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import de.tadris.fitness.Instance
import de.tadris.fitness.data.NutritionPlan
import de.tadris.fitness.data.backup.model.NutritionDayExport
import de.tadris.fitness.data.backup.model.NutritionItemExport
import de.tadris.fitness.data.backup.model.NutritionMealExport
import de.tadris.fitness.data.backup.model.NutritionPlanContainer
import de.tadris.fitness.data.backup.model.NutritionPlanExport
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

object NutritionPlanExporter {

    private val mapper = JsonMapper.builder()
        .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build()

    fun buildContainer(
        context: Context,
        plan: NutritionPlan,
        includePrivateLinks: Boolean = false,
    ): NutritionPlanContainer {
        val dao = Instance.getInstance(context).db.nutritionPlanDao()
        val days = dao.getDaysOfPlan(plan.id).map { day ->
            NutritionDayExport(
                name = day.name,
                notes = day.notes,
                energyKcal = day.energyKcal,
                proteinGrams = day.proteinGrams,
                fatGrams = day.fatGrams,
                carbsGrams = day.carbsGrams,
                fiberGrams = day.fiberGrams,
                waterMl = day.waterMl,
                meals = dao.getMealsOfDay(day.id).map { meal ->
                    NutritionMealExport(
                        name = meal.name,
                        plannedTimeMinutes = meal.plannedTimeMinutes,
                        notes = meal.notes,
                        items = dao.getItemsOfMeal(meal.id).map { item ->
                            NutritionItemExport(
                                name = item.name,
                                quantity = item.quantity,
                                unit = item.unit,
                                weightGrams = item.weightGrams,
                                energyKcal = item.energyKcal,
                                proteinGrams = item.proteinGrams,
                                fatGrams = item.fatGrams,
                                carbsGrams = item.carbsGrams,
                                fiberGrams = item.fiberGrams,
                                notes = item.notes,
                            )
                        },
                    )
                },
            )
        }
        return NutritionPlanContainer(
            plan = NutritionPlanExport(
                uuid = plan.uuid.takeIf { includePrivateLinks },
                name = plan.name,
                description = plan.description,
                objective = plan.objective,
                preferences = plan.preferences,
                restrictions = plan.restrictions,
                linkedTrainingProgramUuid = if (includePrivateLinks) {
                    plan.linkedTrainingProgramUuid
                } else {
                    null
                },
            ),
            days = days,
            profileUuids = if (includePrivateLinks) dao.getProfileUuids(plan.id) else emptyList(),
            startDateEpochDay = if (includePrivateLinks) plan.startDateEpochDay else null,
            createdAt = if (includePrivateLinks) plan.createdAt else null,
            updatedAt = if (includePrivateLinks) plan.updatedAt else null,
        )
    }

    fun write(container: NutritionPlanContainer, outputStream: OutputStream) {
        // This public writer is the standalone/share path. Full backup serializes
        // DataContainer directly and is the only path allowed to carry private links.
        validateNutritionPlanContainer(container, allowPrivateFields = false)
        val staged = LimitedByteArrayOutputStream(MAX_NUTRITION_PLAN_FILE_BYTES)
        mapper.writeValue(staged, container)
        staged.writeTo(outputStream)
    }

    /** Keeps a failed oversized export from partially overwriting its destination. */
    private class LimitedByteArrayOutputStream(
        private val maximumBytes: Int,
    ) : ByteArrayOutputStream() {
        override fun write(value: Int) {
            requireCapacity(1)
            super.write(value)
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            requireCapacity(length)
            super.write(bytes, offset, length)
        }

        private fun requireCapacity(additionalBytes: Int) {
            if (additionalBytes < 0 || count > maximumBytes - additionalBytes) {
                throw IOException("Nutrition-plan file exceeds 8 MiB")
            }
        }
    }
}
