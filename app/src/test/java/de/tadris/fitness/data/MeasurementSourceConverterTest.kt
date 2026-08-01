/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeasurementSourceConverterTest {

    private val converter = MeasurementSourceConverter()

    @Test
    fun nullableCompletionSourceStaysNull() {
        assertNull(converter.toStringValue(null))
        assertNull(converter.fromStringValue(null))
    }

    @Test
    fun concreteSourcesRoundTrip() {
        MeasurementSource.entries.forEach { source ->
            assertEquals(source, converter.fromStringValue(converter.toStringValue(source)))
        }
    }
}
