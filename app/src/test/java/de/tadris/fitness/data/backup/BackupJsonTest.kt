/* Added in FitRegimen. Licensed under GPLv3 or later. */
package de.tadris.fitness.data.backup

import com.fasterxml.jackson.databind.json.JsonMapper
import de.tadris.fitness.data.backup.model.DataContainer
import de.tadris.fitness.data.backup.model.DayExport
import de.tadris.fitness.data.backup.model.FitnessProfileExport
import de.tadris.fitness.data.backup.model.ProgramContainer
import de.tadris.fitness.data.backup.model.ProgramExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonTest {
    private val original = DataContainer(
        intervalSets = emptyList(),
        workoutTypes = emptyList(),
        profiles = listOf(FitnessProfileExport(uuid = "demo", name = "User", isActive = true)),
        programs = listOf(ProgramContainer(
            program = ProgramExport(name = "Demo"),
            days = listOf(DayExport(name = "Recovery", isRest = true)),
        )),
    )

    @Test
    fun `full backup round trip preserves active profile and rest days`() {
        val writer = backupJsonMapper()
        val json = writer.writeValueAsBytes(original)
        val tree = writer.readTree(json)
        assertTrue(tree["profiles"][0]["isActive"].asBoolean())
        assertFalse(tree["profiles"][0].has("active"))
        assertTrue(tree["programs"][0]["days"][0]["isRest"].asBoolean())
        assertFalse(tree["programs"][0]["days"][0].has("rest"))
        val restored = backupJsonMapper().readValue(json, DataContainer::class.java)
        assertEquals(original, restored)
        assertEquals(1, validateBackupProfiles(9, restored.profiles).size)
    }

    @Test
    fun `backups from the old Java mapper retain profile and rest flags`() {
        // Reproduce the original BackupWriter: JavaBean getters emit active/rest.
        val legacyWriter = JsonMapper.builder().build()
        val json = legacyWriter.writeValueAsBytes(original)
        assertTrue(legacyWriter.readTree(json)["profiles"][0]["active"].asBoolean())
        val restored = backupJsonMapper().readValue(json, DataContainer::class.java)
        assertEquals(original, restored)
        assertEquals(1, validateBackupProfiles(9, restored.profiles).size)
    }
}
