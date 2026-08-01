/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TrainingSessionOccurrenceTest {

    @Test
    fun `session occurrence is normalized to local midnight`() {
        val zone = ZoneId.of("Europe/Moscow")
        val date = LocalDate.of(2026, 8, 1)
        val afternoon = date.atTime(16, 42).atZone(zone).toInstant().toEpochMilli()

        assertEquals(
            ProgramScheduling.occurrenceMillis(date, zone),
            canonicalProgramOccurrence(afternoon, zone.id),
        )
    }

    @Test
    fun `normalization respects daylight saving date boundary`() {
        val zone = ZoneId.of("Europe/Berlin")
        val date = LocalDate.of(2026, 3, 29)
        val evening = date.atTime(22, 15).atZone(zone).toInstant().toEpochMilli()

        assertEquals(
            ProgramScheduling.occurrenceMillis(date, zone),
            canonicalProgramOccurrence(evening, zone.id),
        )
    }
}
