/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ProgramSchedulingTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val firstDate = LocalDate.of(2026, 3, 27)
    private val program = TrainingProgram(
        uuid = "program",
        name = "Four weeks",
        startDate = ProgramScheduling.occurrenceMillis(firstDate, zone),
        createdAt = 1L,
    )

    @Test
    fun boundedScheduleContainsOnlyMaterializedProgramDays() {
        val bounds = ProgramScheduling.firstScheduleBounds(program, 28, zone)!!

        assertEquals(firstDate, bounds.first)
        assertEquals(firstDate.plusDays(27), bounds.last)
        assertEquals(0, ProgramScheduling.boundedDayIndexOf(program, 28, firstDate, zone))
        assertEquals(27, ProgramScheduling.boundedDayIndexOf(program, 28, bounds.last, zone))
        assertNull(ProgramScheduling.boundedDayIndexOf(program, 28, firstDate.minusDays(1), zone))
        assertNull(ProgramScheduling.boundedDayIndexOf(program, 28, bounds.last.plusDays(1), zone))
        assertNull(ProgramScheduling.dayIndexOf(program, 28, bounds.last.plusDays(1), zone))
        assertEquals(false, ProgramScheduling.hasEnded(program, 28, bounds.last, zone))
        assertEquals(true, ProgramScheduling.hasEnded(program, 28, bounds.last.plusDays(1), zone))
    }

    @Test
    fun dateClampUsesFirstAndLastProgramDays() {
        val bounds = ProgramScheduling.firstScheduleBounds(program, 28, zone)!!

        assertEquals(bounds.first, bounds.clamp(bounds.first.minusMonths(1)))
        assertEquals(firstDate.plusDays(12), bounds.clamp(firstDate.plusDays(12)))
        assertEquals(bounds.last, bounds.clamp(bounds.last.plusMonths(1)))
    }

    @Test
    fun sevenDayWindowCentersInsideAndHugsProgramEdges() {
        assertEquals(0, ProgramScheduling.dateStripWindowStart(0, 28))
        assertEquals(0, ProgramScheduling.dateStripWindowStart(3, 28))
        assertEquals(1, ProgramScheduling.dateStripWindowStart(4, 28))
        assertEquals(21, ProgramScheduling.dateStripWindowStart(24, 28))
        assertEquals(21, ProgramScheduling.dateStripWindowStart(27, 28))
        assertEquals(0, ProgramScheduling.dateStripWindowStart(4, 5))
    }

    @Test
    fun activeProgramRequiresAStartedNonEmptyTimelineThatHasNotEnded() {
        assertEquals(true, ProgramScheduling.isActive(program, 28, firstDate, zone))
        assertEquals(true, ProgramScheduling.isActive(program, 28, firstDate.plusDays(27), zone))
        assertEquals(false, ProgramScheduling.isActive(program, 28, firstDate.plusDays(28), zone))
        assertEquals(false, ProgramScheduling.isActive(program, 0, firstDate, zone))
        assertEquals(false, ProgramScheduling.isActive(program.copy(startDate = null), 28, firstDate, zone))
    }

    @Test
    fun activeProgramShortcutUsesTheSameVisibleOrderAsTheProgramsTab() {
        val newest = program.copy(id = 2, uuid = "newest", createdAt = 2L)
        val older = program.copy(id = 1, uuid = "older", createdAt = 1L)

        assertEquals(
            newest,
            ProgramScheduling.firstActiveProgram(
                programs = listOf(newest, older),
                dayCountsByProgramId = mapOf(newest.id to 28, older.id to 28),
                date = firstDate,
                zone = zone,
            ),
        )
    }
}
