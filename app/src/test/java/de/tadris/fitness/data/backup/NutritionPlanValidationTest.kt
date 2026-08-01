/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data.backup

import de.tadris.fitness.data.backup.model.NutritionDayExport
import de.tadris.fitness.data.backup.model.NutritionItemExport
import de.tadris.fitness.data.backup.model.NutritionMealExport
import de.tadris.fitness.data.backup.model.NutritionPlanContainer
import de.tadris.fitness.data.backup.model.NutritionPlanExport
import java.io.IOException
import java.time.LocalDate
import org.junit.Test

class NutritionPlanValidationTest {

    @Test
    fun `full backup accepts complete v9 private metadata`() {
        validateBackupNutritionPlans(9, listOf(validFullBackupPlan()))
    }

    @Test
    fun `full backup rejects version spoofing and duplicate UUIDs`() {
        expectNutritionValidationFailure {
            validateBackupNutritionPlans(8, listOf(validFullBackupPlan()))
        }
        expectNutritionValidationFailure {
            validateBackupNutritionPlans(9, listOf(validFullBackupPlan(), validFullBackupPlan()))
        }
    }

    @Test
    fun `validation rejects non finite and negative nutrition values`() {
        expectNutritionValidationFailure {
            validateNutritionPlanContainer(
                validFullBackupPlan().copy(
                    days = listOf(NutritionDayExport(energyKcal = Double.NaN)),
                ),
                allowPrivateFields = true,
                requireUuid = true,
                requirePrivateTimestamps = true,
            )
        }
        expectNutritionValidationFailure {
            validateNutritionPlanContainer(
                validFullBackupPlan().copy(
                    days = listOf(
                        NutritionDayExport(
                            meals = listOf(
                                NutritionMealExport(
                                    name = "Dinner",
                                    items = listOf(
                                        NutritionItemExport(name = "Soup", proteinGrams = -1.0),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                allowPrivateFields = true,
                requireUuid = true,
                requirePrivateTimestamps = true,
            )
        }
    }

    @Test
    fun `meal time is limited to one local day`() {
        listOf(-1, 1440).forEach { invalidTime ->
            expectNutritionValidationFailure {
                validateNutritionPlanContainer(
                    validFullBackupPlan().copy(
                        days = listOf(
                            NutritionDayExport(
                                meals = listOf(
                                    NutritionMealExport(
                                        name = "Meal",
                                        plannedTimeMinutes = invalidTime,
                                    ),
                                ),
                            ),
                        ),
                    ),
                    allowPrivateFields = true,
                    requireUuid = true,
                    requirePrivateTimestamps = true,
                )
            }
        }
    }

    @Test
    fun `validation bounds tree sizes and text lengths`() {
        expectNutritionValidationFailure {
            validateFull(
                validFullBackupPlan().copy(
                    days = List(367) { NutritionDayExport() },
                )
            )
        }
        expectNutritionValidationFailure {
            validateFull(
                validFullBackupPlan().copy(
                    days = listOf(
                        NutritionDayExport(
                            meals = List(13) { NutritionMealExport(name = "Meal") },
                        ),
                    ),
                )
            )
        }
        expectNutritionValidationFailure {
            validateFull(
                validFullBackupPlan().copy(
                    days = listOf(
                        NutritionDayExport(
                            meals = listOf(
                                NutritionMealExport(
                                    name = "Meal",
                                    items = List(61) { NutritionItemExport(name = "Food") },
                                ),
                            ),
                        ),
                    ),
                )
            )
        }
        expectNutritionValidationFailure {
            validateFull(
                validFullBackupPlan().copy(
                    plan = validFullBackupPlan().plan.copy(name = "x".repeat(201)),
                )
            )
        }
        expectNutritionValidationFailure {
            validateFull(
                validFullBackupPlan().copy(
                    days = listOf(
                        NutritionDayExport(
                            meals = listOf(
                                NutritionMealExport(
                                    name = "Meal",
                                    items = listOf(
                                        NutritionItemExport(name = "Food", unit = "u".repeat(41)),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            )
        }
    }

    @Test
    fun `validation enforces technical upper bounds`() {
        expectNutritionValidationFailure {
            validateFull(
                validFullBackupPlan().copy(
                    days = listOf(NutritionDayExport(energyKcal = 50_000.01)),
                )
            )
        }
        expectNutritionValidationFailure {
            validateFull(
                validFullBackupPlan().copy(
                    days = listOf(
                        NutritionDayExport(
                            meals = listOf(
                                NutritionMealExport(
                                    name = "Meal",
                                    items = listOf(
                                        NutritionItemExport(name = "Food", quantity = 1_000_001.0),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            )
        }
        expectNutritionValidationFailure {
            validateFull(
                validFullBackupPlan().copy(
                    days = listOf(NutritionDayExport(waterMl = 100_001.0)),
                )
            )
        }
    }

    @Test
    fun `validation rejects a plan whose final day exceeds LocalDate range`() {
        expectNutritionValidationFailure {
            validateFull(
                validFullBackupPlan().copy(
                    startDateEpochDay = LocalDate.MAX.toEpochDay(),
                    days = listOf(NutritionDayExport(), NutritionDayExport()),
                )
            )
        }
    }

    private fun validateFull(container: NutritionPlanContainer) {
        validateNutritionPlanContainer(
            container = container,
            allowPrivateFields = true,
            requireUuid = true,
            requirePrivateTimestamps = true,
        )
    }

    private fun validFullBackupPlan() = NutritionPlanContainer(
        plan = NutritionPlanExport(uuid = "nutrition-1", name = "Plan"),
        days = listOf(NutritionDayExport()),
        profileUuids = listOf("profile-1"),
        startDateEpochDay = 20_000,
        createdAt = 100,
        updatedAt = 200,
    )
}

private fun expectNutritionValidationFailure(block: () -> Unit): IOException {
    try {
        block()
    } catch (error: IOException) {
        return error
    }
    throw AssertionError("Expected IOException")
}
