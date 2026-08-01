package de.tadris.fitness.ui.nutrition

import de.tadris.fitness.data.NutritionDay
import de.tadris.fitness.data.NutritionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionUiComponentsTest {

    @Test
    fun `localized decimal input is parsed within its bound`() {
        assertEquals(12.5, nutritionNumber("12,5", maximum = 20.0)!!, 0.0)
        assertNull(nutritionNumber("-1", maximum = 20.0))
        assertNull(nutritionNumber("21", maximum = 20.0))
        assertNull(nutritionNumber("NaN", maximum = 20.0))
    }

    @Test
    fun `meal time accepts one local day only`() {
        assertEquals(8 * 60 + 30, nutritionTimeMinutes("08:30"))
        assertEquals(23 * 60 + 59, nutritionTimeMinutes("23:59"))
        assertNull(nutritionTimeMinutes("24:00"))
        assertNull(nutritionTimeMinutes("8:3"))
    }

    @Test
    fun `day targets override totals calculated from foods`() {
        val totals = NutritionTotals(
            energyKcal = 500.0,
            proteinGrams = 25.0,
            fatGrams = 15.0,
            carbsGrams = 60.0,
            fiberGrams = 8.0,
        )
        val day = NutritionDay(
            planId = 1L,
            orderIndex = 0,
            energyKcal = 600.0,
        )

        val values = NutritionMetricValues.forDay(day, totals)

        assertEquals(600.0, values.energyKcal!!, 0.0)
        assertEquals(25.0, values.proteinGrams!!, 0.0)
        assertEquals(15.0, values.fatGrams!!, 0.0)
    }

    @Test
    fun `food totals add only recorded nutrition values`() {
        val totals = NutritionTotals.from(
            listOf(
                NutritionItem(
                    mealId = 1L,
                    orderIndex = 0,
                    name = "One",
                    energyKcal = 120.0,
                    proteinGrams = 10.0,
                ),
                NutritionItem(
                    mealId = 1L,
                    orderIndex = 1,
                    name = "Two",
                    energyKcal = 80.0,
                    carbsGrams = 15.0,
                ),
            )
        )

        assertEquals(200.0, totals.energyKcal, 0.0)
        assertEquals(10.0, totals.proteinGrams, 0.0)
        assertEquals(15.0, totals.carbsGrams, 0.0)
        assertEquals(0.0, totals.fiberGrams, 0.0)
    }

    @Test
    fun `tree mutation guard rejects a second writer and can be reused`() {
        assertTrue(NutritionTreeMutationGuard.tryAcquire())
        try {
            assertFalse(NutritionTreeMutationGuard.tryAcquire())
        } finally {
            NutritionTreeMutationGuard.release()
        }

        assertTrue(NutritionTreeMutationGuard.tryAcquire())
        NutritionTreeMutationGuard.release()
    }
}
