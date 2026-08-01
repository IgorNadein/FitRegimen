package de.tadris.fitness.ui.workout.show

import de.tadris.fitness.ui.sections.SectionListModel.SectionCriterion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutSectionsCardTest {

    @Test
    fun `rows retain cumulative values for every criterion`() {
        val sections = listOf(
            values(distance = 400.0, duration = 120_000.0, ascent = 15.0, descent = 2.0),
            values(distance = 600.0, duration = 180_000.0, ascent = 5.0, descent = 8.0),
        )

        assertEquals(
            listOf(400.0, 1000.0),
            cumulative(sections, SectionCriterion.DISTANCE),
        )
        assertEquals(
            listOf(120_000.0, 300_000.0),
            cumulative(sections, SectionCriterion.TIME),
        )
        assertEquals(
            listOf(15.0, 20.0),
            cumulative(sections, SectionCriterion.ASCENT),
        )
        assertEquals(
            listOf(2.0, 10.0),
            cumulative(sections, SectionCriterion.DESCENT),
        )
    }

    @Test
    fun `progress preserves legacy pace and speed scaling`() {
        val sections = listOf(
            values(pace = 1.0, best = true),
            values(pace = 2.0),
            values(pace = 4.0, worst = true),
        )

        val paceProgress = buildWorkoutSectionRows(
            sections,
            SectionCriterion.DISTANCE,
            showPace = true,
        ).map { it.progress }
        val speedProgress = buildWorkoutSectionRows(
            sections,
            SectionCriterion.DISTANCE,
            showPace = false,
        ).map { it.progress }

        assertEquals(listOf(0.25f, 0.5f, 1f), paceProgress)
        assertEquals(listOf(1f, 0.5f, 0.25f), speedProgress)
    }

    @Test
    fun `section length accepts both decimal separators and rejects invalid values`() {
        assertEquals(1.25, parsePositiveSectionLength("1.25")!!, 0.0)
        assertEquals(1.25, parsePositiveSectionLength("1,25")!!, 0.0)
        assertNull(parsePositiveSectionLength("0"))
        assertNull(parsePositiveSectionLength("-2"))
        assertNull(parsePositiveSectionLength("text"))
    }

    private fun cumulative(
        sections: List<WorkoutSectionValues>,
        criterion: SectionCriterion,
    ): List<Double> = buildWorkoutSectionRows(
        sections = sections,
        criterion = criterion,
        showPace = true,
    ).map { it.cumulativeCriterion }

    private fun values(
        distance: Double = 100.0,
        duration: Double = 60_000.0,
        ascent: Double = 0.0,
        descent: Double = 0.0,
        pace: Double = 1.0,
        best: Boolean = false,
        worst: Boolean = false,
    ) = WorkoutSectionValues(
        distanceMeters = distance,
        durationMillis = duration,
        ascentMeters = ascent,
        descentMeters = descent,
        paceSecondsPerMeter = pace,
        isBest = best,
        isWorst = worst,
    )
}
