/* Added in FitRegimen. Licensed under GPLv3 or later. */
package de.tadris.fitness.data.backup

import de.tadris.fitness.data.CompletionRule
import de.tadris.fitness.data.MeasurementSource
import de.tadris.fitness.data.MetricKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the shipped demo files through the same readers used by the app. */
class PortfolioDemoTest {
    @Test
    fun `demo program can be completed without a recorder or network`() {
        val plan = javaClass.getResourceAsStream("/program.json")!!.use(ProgramImporter::read)
        assertEquals("Portfolio demo", plan.program.name)
        assertEquals(2, plan.days.size)
        assertTrue(plan.days.last().isRest)
        val exercises = plan.exercises.associateBy { it.uuid }
        val entries = plan.days.first().exercises
        assertEquals(3, entries.sumOf { it.sets.size })
        entries.forEach { entry ->
            val exercise = exercises.getValue(entry.exerciseUuid)
            val policy = ProgramImporter.executionPolicyFor(
                entry, MetricKind.findByName(exercise.metricKind), exercise.workoutTypeId,
            )
            assertTrue(policy.measurementSource != MeasurementSource.RECORDER)
            if (exercise.metricKind == "DURATION") {
                assertEquals(MeasurementSource.CLOCK, policy.measurementSource)
                assertEquals(CompletionRule.DURATION_REACHED, policy.completionRule)
                assertEquals(20_000L, entry.sets.single().durationMillis)
            } else {
                assertEquals(CompletionRule.USER_CONFIRM, policy.completionRule)
                assertTrue(entry.sets.all { (it.reps ?: 0) > 0 })
            }
        }
    }

    @Test
    fun `demo nutrition plan passes standalone validation and has consistent totals`() {
        val plan = javaClass.getResourceAsStream("/nutrition.json")!!.use(NutritionPlanImporter::read)
        assertEquals("Portfolio menu", plan.plan.name)
        val meals = plan.days.single().meals
        assertEquals(3, meals.size)
        assertEquals(1290.0, meals.flatMap { it.items }.sumOf { it.energyKcal ?: 0.0 }, 0.01)
        assertTrue(plan.profileUuids.isEmpty())
        assertEquals(null, plan.startDateEpochDay)
    }
}
