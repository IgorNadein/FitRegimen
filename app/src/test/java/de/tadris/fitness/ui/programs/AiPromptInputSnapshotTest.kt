package de.tadris.fitness.ui.programs

import de.tadris.fitness.data.FitnessProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiPromptInputSnapshotTest {

    private val profile = FitnessProfile(
        id = 1,
        uuid = "profile-1",
        name = "User",
        isActive = true,
        birthDateEpochDay = 7_500,
        heightCm = 180.0,
        weightKg = 80.0,
        stepLengthCm = 75.0,
        gender = "male",
        fitnessLevel = "intermediate",
        notes = "knee sensitivity",
        customAttributesJson = "{\"mobility\":\"limited\"}",
        createdAt = 100,
        updatedAt = 200,
    )

    private fun snapshot(
        goal: String = "strength",
        params: String = "beginner",
        term: String = "4 weeks",
        daysPerWeek: String = "3",
        equipment: String = "dumbbells",
        extra: String = "quiet exercises",
        includeProfiles: Boolean = true,
        selectedProfileIds: List<Long> = listOf(1),
        profiles: List<FitnessProfile> = listOf(profile),
    ) = aiPromptInputSnapshot(
        goal = goal,
        params = params,
        term = term,
        daysPerWeek = daysPerWeek,
        equipment = equipment,
        extra = extra,
        includeProfiles = includeProfiles,
        selectedProfileIds = selectedProfileIds,
        profiles = profiles,
    )

    @Test
    fun `every form input invalidates a generated prompt`() {
        val original = snapshot()
        val changes = listOf(
            snapshot(goal = "endurance"),
            snapshot(params = "advanced"),
            snapshot(term = "6 weeks"),
            snapshot(daysPerWeek = "4"),
            snapshot(equipment = "none"),
            snapshot(extra = "outdoors"),
            snapshot(includeProfiles = false),
            snapshot(selectedProfileIds = listOf(1, 2)),
        )

        changes.forEach { changed ->
            assertNotEquals(original, changed)
            val generated = GeneratedAiPrompt("ready", original)
            assertNull(generated.textFor(changed))
            assertNull(generated.retainFor(changed))
        }
    }

    @Test
    fun `every profile field participates in the snapshot`() {
        val original = snapshot()
        val changedProfiles = listOf(
            profile.copy(id = 2),
            profile.copy(uuid = "profile-2"),
            profile.copy(name = "Athlete"),
            profile.copy(isActive = false),
            profile.copy(birthDateEpochDay = 7_501),
            profile.copy(heightCm = 181.0),
            profile.copy(weightKg = 81.0),
            profile.copy(stepLengthCm = 76.0),
            profile.copy(gender = "other"),
            profile.copy(fitnessLevel = "advanced"),
            profile.copy(notes = "shoulder sensitivity"),
            profile.copy(customAttributesJson = "{\"mobility\":\"good\"}"),
            profile.copy(createdAt = 101),
            profile.copy(updatedAt = 201),
        )

        changedProfiles.forEach { changedProfile ->
            val changed = snapshot(profiles = listOf(changedProfile))
            assertNotEquals(original, changed)
            val generated = GeneratedAiPrompt("ready", original)
            assertNull(generated.textFor(changed))
            assertNull(generated.retainFor(changed))
        }
    }

    @Test
    fun `equal Room emission keeps freshly generated prompt visible`() {
        val generatedFrom = snapshot()
        val equalEmission = snapshot(
            selectedProfileIds = listOf(1L).toList(),
            profiles = listOf(profile.copy()),
        )

        assertEquals(generatedFrom, equalEmission)
        val generated = GeneratedAiPrompt("ready", generatedFrom)
        assertEquals("ready", generated.textFor(equalEmission))
        assertEquals(generated, generated.retainFor(equalEmission))
    }
}
