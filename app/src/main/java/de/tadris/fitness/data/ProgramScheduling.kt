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
 * Maps calendar dates to a [TrainingProgram]'s repeating cycle. Shared by the schedule
 * screen and the "link a recorded workout to a program task" flow so both agree on which
 * day of the cycle a given date falls on.
 */
object ProgramScheduling {

    /** Returns the 0-based day-of-cycle for [date], or null if the program hasn't been started. */
    fun dayIndexOf(program: TrainingProgram, date: LocalDate, zone: ZoneId): Int? {
        val startDate = program.startDate ?: return null
        if (program.cycleLengthDays <= 0) return null
        val startLocalDate = Instant.ofEpochMilli(startDate).atZone(zone).toLocalDate()
        val daysSinceStart = ChronoUnit.DAYS.between(startLocalDate, date)
        val cycleLength = program.cycleLengthDays.toLong()
        return (((daysSinceStart % cycleLength) + cycleLength) % cycleLength).toInt()
    }

    /** Epoch ms of midnight of [date] in [zone] - the canonical [ProgramCompletion.occurrenceDate]. */
    fun occurrenceMillis(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()
}
