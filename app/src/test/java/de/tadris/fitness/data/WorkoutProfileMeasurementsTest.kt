/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.data

import de.tadris.fitness.data.preferences.UserMeasurements
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutProfileMeasurementsTest {

    private val current = UserMeasurements(weight = 70, stepLength = 0.72f)

    @Test
    fun `valid snapshot wins without losing decimal precision`() {
        val result = effectiveMeasurements(
            FitnessProfileSnapshot(userWeightKg = 82.75, stepLengthMeters = 0.81),
            current,
        )

        assertEquals(82.75, result.weightKg, 0.0)
        assertEquals(0.81, result.stepLengthMeters, 0.0)
    }

    @Test
    fun `legacy null snapshot falls back to current measurements`() {
        val result = effectiveMeasurements(FitnessProfileSnapshot(), current)

        assertEquals(70.0, result.weightKg, 0.0)
        assertEquals(0.72, result.stepLengthMeters, 0.000_001)
    }

    @Test
    fun `invalid snapshot fields independently fall back to current measurements`() {
        val invalidWeight = effectiveMeasurements(
            FitnessProfileSnapshot(userWeightKg = Double.NaN, stepLengthMeters = 0.8),
            current,
        )
        val invalidStep = effectiveMeasurements(
            FitnessProfileSnapshot(userWeightKg = 80.5, stepLengthMeters = -1.0),
            current,
        )

        assertEquals(70.0, invalidWeight.weightKg, 0.0)
        assertEquals(0.8, invalidWeight.stepLengthMeters, 0.0)
        assertEquals(80.5, invalidStep.weightKg, 0.0)
        assertEquals(0.72, invalidStep.stepLengthMeters, 0.000_001)
    }
}
