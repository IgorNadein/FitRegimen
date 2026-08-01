/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.data.calorie

import de.tadris.fitness.data.preferences.UserMeasurements
import org.junit.Assert.assertEquals
import org.junit.Test

class CalorieCalculatorSnapshotTest {

    @Test
    fun `energy equation uses decimal snapshot weight`() {
        val calculator = CalorieCalculator(
            listOf(object : METProvider {
                override fun calculateMET(
                    measurements: UserMeasurements,
                    typeId: String,
                    speedInKmh: Double,
                ) = 5.0
            })
        )
        val measurements = UserMeasurements(weight = 83, stepLength = 0.79f)

        val calories = calculator.calculateCalories(
            measurements = measurements,
            userWeightKg = 82.75,
            duration = 60_000,
            ascent = 0,
            workoutTypeId = "test",
            speed = 0.0,
        )

        assertEquals(5.0 * 3.5 * 82.75 / 200.0, calories, 0.000_001)
    }
}
