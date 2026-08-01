/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.ui.settings

import de.tadris.fitness.data.Exercise
import de.tadris.fitness.data.MetricKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageExercisesScreenTest {

    @Test
    fun `management search trims ignores case and excludes hidden rows`() {
        val visible = exercise(id = 1, name = "Goblet Squat")
        val other = exercise(id = 2, name = "Push-up")
        val hidden = exercise(id = 3, name = "Squat jump", hidden = true)

        assertEquals(
            listOf(visible),
            filterExercisesForManagement(
                exercises = listOf(visible, other, hidden),
                query = "  SQUAT ",
            ),
        )
        assertEquals(
            listOf(visible, other),
            filterExercisesForManagement(listOf(visible, other, hidden), ""),
        )
    }

    @Test
    fun `soft delete preserves exercise identity and content`() {
        val source = exercise(id = 7, name = "Row").copy(
            description = "Technique",
            videoUrl = "https://example.org/row.mp4",
        )

        val hidden = source.hideFromExerciseCatalog()

        assertTrue(hidden.hidden)
        assertFalse(source.hidden)
        assertEquals(source.id, hidden.id)
        assertEquals(source.uuid, hidden.uuid)
        assertEquals(source.description, hidden.description)
        assertEquals(source.videoUrl, hidden.videoUrl)
    }

    private fun exercise(
        id: Long,
        name: String,
        hidden: Boolean = false,
    ) = Exercise(
        id = id,
        uuid = "exercise-$id",
        name = name,
        metricKind = MetricKind.REPS,
        hidden = hidden,
    )
}
