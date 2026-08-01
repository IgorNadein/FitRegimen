package de.tadris.fitness.ui.programs

import de.tadris.fitness.data.FitnessProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AiProfileContextTest {

    @Test
    fun `prompt projection is anonymous and includes useful measurements`() {
        val line = AiProfileContext(
            ageYears = 34,
            heightCm = 181.0,
            weightKg = 82.5,
            stepLengthCm = 78.0,
            fitnessLevel = "intermediate",
        ).toPromptLine(russian = false)

        assertTrue(line.contains("age: 34"))
        assertTrue(line.contains("height: 181 cm"))
        assertTrue(line.contains("weight: 82.5 kg"))
        assertTrue(line.contains("fitness level: intermediate"))
        assertFalse(line.contains("name", ignoreCase = true))
    }

    @Test
    fun `free text is normalized to one line`() {
        val line = AiProfileContext(notes = "  knee\n  sensitivity\t after running ")
            .toPromptLine(russian = true)

        assertTrue(line.contains("важные особенности: knee sensitivity after running"))
        assertFalse(line.contains('\n'))
        assertFalse(line.contains('\t'))
    }

    @Test
    fun `empty projection has localized fallback`() {
        assertTrue(AiProfileContext().toPromptLine(russian = true).contains("не указаны"))
        assertTrue(AiProfileContext().toPromptLine(russian = false).contains("not specified"))
    }

    @Test
    fun `room profile projection derives age and omits identity`() {
        val profile = FitnessProfile(
            id = 7,
            uuid = "private-uuid",
            name = "Private name",
            birthDateEpochDay = LocalDate.of(1990, 8, 2).toEpochDay(),
            heightCm = 175.0,
            customAttributesJson = "{\"mobility\":\"limited\"}",
            createdAt = 1,
            updatedAt = 1,
        )

        val line = profile
            .toAiProfileContext(today = LocalDate.of(2026, 8, 1))
            .toPromptLine(russian = false)

        assertTrue(line.contains("age: 35"))
        assertTrue(line.contains("mobility: limited"))
        assertFalse(line.contains("Private name"))
        assertFalse(line.contains("private-uuid"))
    }

    @Test
    fun `known identity and reserved custom fields are redacted`() {
        val profile = FitnessProfile(
            uuid = "6f6fdf5d-55db-4e03-9ba5-875ad076c21d",
            name = "Alice Example",
            birthDateEpochDay = LocalDate.of(1990, 8, 2).toEpochDay(),
            notes = "Alice, alice@example.com, born 02.08.1990",
            customAttributesJson = """{
                "email":"alice@example.com",
                "goal":"Alice Example runs 5 km",
                "nested":{"private":"value"}
            }""".trimIndent(),
            createdAt = 1,
            updatedAt = 1,
        )

        val context = profile.toAiProfileContext(LocalDate.of(2026, 8, 1))
        val combined = listOfNotNull(context.notes, context.customParameters).joinToString(" ")

        assertFalse(combined.contains("Alice Example", ignoreCase = true))
        assertFalse(combined.contains("Alice", ignoreCase = true))
        assertFalse(combined.contains("alice@example.com", ignoreCase = true))
        assertFalse(combined.contains("02.08.1990"))
        assertFalse(combined.contains("email:"))
        assertFalse(combined.contains("nested:"))
        assertTrue(combined.contains("[private]"))
    }

    @Test
    fun `short profile name is redacted as a whole word without damaging other words`() {
        val profile = FitnessProfile(
            uuid = "profile-short-name",
            name = "Ли",
            notes = "Ли предпочитает брокколи, но лилия — просто цветок",
            createdAt = 1,
            updatedAt = 1,
        )

        val notes = profile.toAiProfileContext(LocalDate.of(2026, 8, 2)).notes.orEmpty()

        assertFalse(notes.contains("Ли предпочитает", ignoreCase = true))
        assertTrue(notes.contains("[private]"))
        assertTrue(notes.contains("лилия", ignoreCase = true))
    }
}
