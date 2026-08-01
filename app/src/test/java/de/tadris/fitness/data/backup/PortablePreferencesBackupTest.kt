/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data.backup

import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import de.tadris.fitness.data.backup.model.DataContainer
import de.tadris.fitness.data.backup.model.PortablePreferenceValueExport
import de.tadris.fitness.data.backup.model.PortablePreferencesExport
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PortablePreferencesBackupTest {

    @Test
    fun `portable preferences survive typed json round trip`() {
        val source = linkedMapOf<String, Any>(
            "themeSetting" to "2",
            "useSystemColors" to true,
            "autoTimeoutPeriod" to 35,
            "spokenUpdateDistancePeriod2" to 1.5f,
            "statisticsSelectedTypes" to linkedSetOf("running", "cycling"),
            "information_display_gps_0" to "distance",
        )
        val mapper = jsonMapper { addModule(kotlinModule()) }

        val encoded = PortablePreferencesBackup.export(source)
        val container = DataContainer(
            intervalSets = emptyList(),
            workoutTypes = emptyList(),
            preferences = encoded,
        )
        val restored = mapper.readValue(
            mapper.writeValueAsBytes(container),
            DataContainer::class.java,
        ).preferences

        assertEquals(source, PortablePreferencesBackup.decode(restored!!))
    }

    @Test
    fun `credentials local paths profile mirrors and runtime flags are excluded`() {
        val source = mapOf(
            "themeSetting" to "1",
            "accessToken" to "secret",
            "authenticated" to true,
            "offlineMapDirectoryName" to "content://device/tree/maps",
            "weight" to 93,
            "stepLength" to 0.91f,
            "lastVersion" to 1620,
            "firstStart" to false,
            "programWorkoutProjectionVersion" to 2,
        )

        val encoded = PortablePreferencesBackup.export(source)

        assertEquals(setOf("themeSetting"), encoded.values.keys)
        assertFalse(PortablePreferencesBackup.isPortableKey("accessToken"))
        assertFalse(PortablePreferencesBackup.isPortableKey("offlineMapDirectoryName"))
        assertTrue(PortablePreferencesBackup.isPortableKey("announcement_distance"))
        assertTrue(PortablePreferencesBackup.isPortableKey("information_display_share_gps_0"))
    }

    @Test
    fun `old data container keeps preferences absent instead of clearing settings`() {
        val mapper = jsonMapper { addModule(kotlinModule()) }
        val restored = mapper.readValue(
            """{"intervalSets":[],"workoutTypes":[]}""",
            DataContainer::class.java,
        )

        assertNull(restored.preferences)
    }

    @Test
    fun `statistics filter follows a remapped custom workout type`() {
        val source = PortablePreferencesBackup.export(
            mapOf("statisticsSelectedTypes" to setOf("running", "my_strength")),
        )

        val remapped = PortablePreferencesBackup.remapWorkoutTypeIds(
            source,
            mapOf("my_strength" to "my_strength_imported"),
        )

        assertEquals(
            setOf("running", "my_strength_imported"),
            PortablePreferencesBackup.decode(remapped)["statisticsSelectedTypes"],
        )
    }

    @Test
    fun `malformed or non portable preference fails preflight decoding`() {
        expectThrows<IOException> {
            PortablePreferencesBackup.decode(
                PortablePreferencesExport(
                    values = mapOf(
                        "accessToken" to PortablePreferenceValueExport(
                            type = "string",
                            stringValue = "secret",
                        ),
                    ),
                ),
            )
        }
        expectThrows<IOException> {
            PortablePreferencesBackup.decode(
                PortablePreferencesExport(
                    values = mapOf(
                        "themeSetting" to PortablePreferenceValueExport(type = "string"),
                    ),
                ),
            )
        }
        expectThrows<IOException> {
            PortablePreferencesBackup.decode(
                PortablePreferencesExport(
                    values = mapOf(
                        "autoBackupInterval" to PortablePreferenceValueExport(
                            type = "int",
                            intValue = 168,
                        ),
                    ),
                ),
            )
        }
    }

    private inline fun <reified T : Throwable> expectThrows(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw AssertionError("Expected ${T::class.java.name}, got ${error::class.java.name}", error)
        }
        fail("Expected ${T::class.java.name}")
        error("unreachable")
    }
}
