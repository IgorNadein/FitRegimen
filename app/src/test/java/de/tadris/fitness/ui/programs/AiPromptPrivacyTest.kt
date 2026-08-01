package de.tadris.fitness.ui.programs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AiPromptPrivacyTest {

    @Test
    fun `selected profiles are anonymous input and output contract forbids disclosure`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            val prompt = buildPrompt(
                goal = "general conditioning",
                params = "",
                term = "4 weeks",
                daysPerWeek = "3",
                equipment = "",
                extra = "",
                profileContexts = listOf(
                    AiProfileContext(ageYears = 31, heightCm = 176.0, weightKg = 72.0),
                    AiProfileContext(ageYears = 44, heightCm = 183.0, weightKg = 91.0),
                ),
            )

            assertTrue(prompt.contains("Participant 1: age: 31"))
            assertTrue(prompt.contains("Participant 2: age: 44"))
            assertTrue(prompt.contains("one shared training program for a group of 2 participants"))
            assertTrue(prompt.contains("PROFILE PRIVACY"))
            assertTrue(prompt.contains("Do not add participant, profile, client or personal-data fields"))
            assertFalse(prompt.contains("profileUuid"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `russian prompt has the same privacy boundary`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
            val prompt = buildPrompt(
                goal = "общая физическая форма",
                params = "",
                term = "4 недели",
                daysPerWeek = "3",
                equipment = "",
                extra = "",
                profileContexts = listOf(AiProfileContext(ageYears = 29, weightKg = 68.0)),
            )

            assertTrue(prompt.contains("КОНФИДЕНЦИАЛЬНОСТЬ ПРОФИЛЕЙ"))
            assertTrue(prompt.contains("недоверенными фактическими данными"))
            assertTrue(prompt.contains("Участник 1: возраст: 29"))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
