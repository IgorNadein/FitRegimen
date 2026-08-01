/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack. It was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FitnessProfileFormattingTest {

    @Test
    fun `legacy float artifacts are hidden in editable measurements`() {
        assertEquals("79", formatProfileNumber(79.00000214576721))
    }

    @Test
    fun `useful fractional measurements keep two decimal places`() {
        assertEquals("82.75", formatProfileNumber(82.75))
        assertEquals("82.76", formatProfileNumber(82.755))
    }

    @Test
    fun `male walking step estimate uses male coefficient`() {
        assertEquals(74.7, estimateWalkingStepLengthCm(180.0, "male")!!, 0.0)
    }

    @Test
    fun `female walking step estimate uses female coefficient`() {
        assertEquals(74.3, estimateWalkingStepLengthCm(180.0, "female")!!, 0.0)
    }

    @Test
    fun `unspecified and other genders use neutral coefficient`() {
        assertEquals(72.5, estimateWalkingStepLengthCm(175.0, null)!!, 0.0)
        assertEquals(72.5, estimateWalkingStepLengthCm(175.0, "other")!!, 0.0)
    }

    @Test
    fun `step estimate rejects missing non finite and out of range height`() {
        assertNull(estimateWalkingStepLengthCm(null, "male"))
        assertNull(estimateWalkingStepLengthCm(Double.NaN, "male"))
        assertNull(estimateWalkingStepLengthCm(29.9, "female"))
        assertNull(estimateWalkingStepLengthCm(280.1, null))
    }
}
