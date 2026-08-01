/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack
 *
 * FitoTrack is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     FitoTrack is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Maps calendar dates to a finite [TrainingProgram] timeline. The ordered
 * [ProgramDay] rows are the complete schedule from its first through last day.
 */
object ProgramScheduling {

    data class DateBounds(
        val first: LocalDate,
        val last: LocalDate,
    ) {
        fun clamp(date: LocalDate): LocalDate = when {
            date.isBefore(first) -> first
            date.isAfter(last) -> last
            else -> date
        }
    }

    /** Returns the 0-based scheduled day, or null before day 1 / after the last day. */
    fun dayIndexOf(program: TrainingProgram, dayCount: Int, date: LocalDate, zone: ZoneId): Int? =
        boundedDayIndexOf(program, dayCount, date, zone)

    /**
     * Calendar bounds of the concrete days displayed by the program. The strip and
     * every completion/link lookup use these same finite boundaries.
     */
    fun firstScheduleBounds(
        program: TrainingProgram,
        dayCount: Int,
        zone: ZoneId,
    ): DateBounds? {
        val startDate = program.startDate ?: return null
        if (dayCount <= 0) return null
        val first = Instant.ofEpochMilli(startDate).atZone(zone).toLocalDate()
        return DateBounds(
            first = first,
            last = first.plusDays(dayCount.toLong() - 1L),
        )
    }

    /** A non-repeating index for the bounded date strip; dates outside it are absent. */
    fun boundedDayIndexOf(
        program: TrainingProgram,
        dayCount: Int,
        date: LocalDate,
        zone: ZoneId,
    ): Int? {
        val bounds = firstScheduleBounds(program, dayCount, zone) ?: return null
        if (date.isBefore(bounds.first) || date.isAfter(bounds.last)) return null
        return ChronoUnit.DAYS.between(bounds.first, date).toInt()
    }

    fun hasEnded(
        program: TrainingProgram,
        dayCount: Int,
        date: LocalDate,
        zone: ZoneId,
    ): Boolean = firstScheduleBounds(program, dayCount, zone)
        ?.let { date.isAfter(it.last) }
        ?: false

    /**
     * The single definition used by both the Programs tab and shortcuts that
     * need to open an unfinished program. An empty timeline cannot be followed.
     */
    fun isActive(
        program: TrainingProgram,
        dayCount: Int,
        date: LocalDate,
        zone: ZoneId,
    ): Boolean = program.startDate != null &&
        dayCount > 0 &&
        !hasEnded(program, dayCount, date, zone)

    /** Preserves the caller's order, which is the same order shown in the Programs tab. */
    fun firstActiveProgram(
        programs: List<TrainingProgram>,
        dayCountsByProgramId: Map<Long, Int>,
        date: LocalDate,
        zone: ZoneId,
    ): TrainingProgram? = programs.firstOrNull { program ->
        isActive(
            program = program,
            dayCount = dayCountsByProgramId[program.id] ?: 0,
            date = date,
            zone = zone,
        )
    }

    /**
     * First item of a fixed-size viewport. The selected day is centered whenever
     * both program edges leave enough room, otherwise the window hugs that edge.
     */
    fun dateStripWindowStart(
        selectedIndex: Int,
        dayCount: Int,
        visibleDayCount: Int = 7,
    ): Int {
        if (dayCount <= 0) return 0
        val safeVisibleCount = visibleDayCount.coerceAtLeast(1)
        val safeSelectedIndex = selectedIndex.coerceIn(0, dayCount - 1)
        val maxStart = (dayCount - safeVisibleCount).coerceAtLeast(0)
        return (safeSelectedIndex - safeVisibleCount / 2).coerceIn(0, maxStart)
    }

    /** Epoch ms of midnight of [date] in [zone] - the canonical [ProgramCompletion.occurrenceDate]. */
    fun occurrenceMillis(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()
}
