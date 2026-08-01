/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.ui.workout.enter

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class EnterWorkoutDatePickerTest {

    @Test
    fun `local calendar fields round trip through Material date picker millis`() {
        val cases = listOf(
            LocalDate.of(2024, 2, 29),
            LocalDate.of(2026, 3, 29),
            LocalDate.of(2026, 10, 25),
            LocalDate.of(2030, 12, 31),
        )

        cases.forEach { date ->
            val millis = localDateToDatePickerMillis(
                year = date.year,
                zeroBasedMonth = date.monthValue - 1,
                day = date.dayOfMonth,
            )

            assertEquals(date, datePickerMillisToLocalDate(millis))
            assertEquals(
                date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                millis,
            )
        }
    }
}
