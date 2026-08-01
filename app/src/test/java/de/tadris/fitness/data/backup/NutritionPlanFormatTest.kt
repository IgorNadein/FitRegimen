/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data.backup

import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.databind.json.JsonMapper
import de.tadris.fitness.data.backup.model.DataContainer
import de.tadris.fitness.data.backup.model.DayExport
import de.tadris.fitness.data.backup.model.NUTRITION_PLAN_CONTAINER_FORMAT_VERSION
import de.tadris.fitness.data.backup.model.NUTRITION_PLAN_CONTAINER_TYPE
import de.tadris.fitness.data.backup.model.NutritionDayExport
import de.tadris.fitness.data.backup.model.NutritionItemExport
import de.tadris.fitness.data.backup.model.NutritionMealExport
import de.tadris.fitness.data.backup.model.NutritionPlanContainer
import de.tadris.fitness.data.backup.model.NutritionPlanExport
import de.tadris.fitness.data.backup.model.ProgramContainer
import de.tadris.fitness.data.backup.model.ProgramExport
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionPlanFormatTest {

    @Test
    fun `standalone writer omits private backup fields and round trips the tree`() {
        val source = standalonePlan()
        val output = ByteArrayOutputStream()

        NutritionPlanExporter.write(source, output)

        val json = output.toString(Charsets.UTF_8.name())
        assertFalse(json.contains("profileUuids"))
        assertFalse(json.contains("startDateEpochDay"))
        assertFalse(json.contains("createdAt"))
        assertFalse(json.contains("updatedAt"))
        assertFalse(json.contains("\"uuid\""))
        assertFalse(json.contains("linkedTrainingProgramUuid"))
        val restored = NutritionPlanImporter.read(ByteArrayInputStream(output.toByteArray()))
        assertEquals(source, restored)
        assertEquals(NUTRITION_PLAN_CONTAINER_TYPE, restored.type)
        assertEquals(NUTRITION_PLAN_CONTAINER_FORMAT_VERSION, restored.formatVersion)
    }

    @Test
    fun `standalone reader rejects private full backup fields`() {
        val private = standalonePlan().copy(
            plan = standalonePlan().plan.copy(
                uuid = "private-plan-uuid",
                linkedTrainingProgramUuid = "private-program-uuid",
            ),
            profileUuids = listOf("profile-private"),
            startDateEpochDay = 20_000,
            createdAt = 10,
            updatedAt = 20,
        )
        val bytes = JsonMapper.builder().build().writeValueAsBytes(private)

        expectThrows<NutritionPlanImporter.InvalidNutritionPlanFileException> {
            NutritionPlanImporter.read(ByteArrayInputStream(bytes))
        }
        expectThrows<java.io.IOException> {
            NutritionPlanExporter.write(private, ByteArrayOutputStream())
        }
    }

    @Test
    fun `standalone reader rejects unknown fields instead of hiding private data`() {
        val json = """
            {
              "type":"fitregimen-nutrition-plan",
              "formatVersion":1,
              "plan":{"name":"Plan","participantName":"Private"},
              "days":[{}]
            }
        """.trimIndent()

        expectThrows<NutritionPlanImporter.InvalidNutritionPlanFileException> {
            NutritionPlanImporter.read(ByteArrayInputStream(json.toByteArray()))
        }
    }

    @Test
    fun `standalone reader requires an explicit type discriminator`() {
        val json = """
            {
              "formatVersion":1,
              "plan":{"name":"Plan"},
              "days":[{}]
            }
        """.trimIndent()

        val error = expectThrows<NutritionPlanImporter.InvalidNutritionPlanFileException> {
            NutritionPlanImporter.read(ByteArrayInputStream(json.toByteArray()))
        }

        assertTrue(error.message.orEmpty().contains("type is missing"))
    }

    @Test
    fun `standalone reader requires an explicit format version discriminator`() {
        val json = """
            {
              "type":"fitregimen-nutrition-plan",
              "plan":{"name":"Plan"},
              "days":[{}]
            }
        """.trimIndent()

        val error = expectThrows<NutritionPlanImporter.InvalidNutritionPlanFileException> {
            NutritionPlanImporter.read(ByteArrayInputStream(json.toByteArray()))
        }

        assertTrue(error.message.orEmpty().contains("formatVersion is missing"))
    }

    @Test
    fun `full backup retains private links and remaps a colliding training program`() {
        val private = standalonePlan().copy(
            plan = standalonePlan().plan.copy(
                uuid = "nutrition-private",
                linkedTrainingProgramUuid = "program-imported",
            ),
            profileUuids = listOf("profile-private"),
            startDateEpochDay = 20_000,
            createdAt = 10,
            updatedAt = 20,
        )
        val json = JsonMapper.builder().build().writeValueAsString(
            DataContainer(
                intervalSets = emptyList(),
                workoutTypes = emptyList(),
                nutritionPlans = listOf(private),
            )
        )
        val remapped = private.remapLinkedTrainingProgramUuid(
            mapOf("program-imported" to "program-local-copy")
        )

        assertTrue(json.contains("profile-private"))
        assertTrue(json.contains("startDateEpochDay"))
        assertTrue(json.contains("nutrition-private"))
        assertEquals("program-local-copy", remapped.plan.linkedTrainingProgramUuid)
        assertEquals(listOf("profile-private"), remapped.profileUuids)
    }

    @Test
    fun `backup merge equality is idempotent but preserves divergent copies`() {
        val incoming = standalonePlan().copy(
            plan = standalonePlan().plan.copy(uuid = "incoming-uuid"),
            profileUuids = listOf("profile-b", "profile-a"),
            createdAt = 10,
            updatedAt = 20,
        )
        val restoredCopy = incoming.copy(
            plan = incoming.plan.copy(
                uuid = "locally-remapped-uuid",
                description = "  ",
            ),
            profileUuids = listOf("profile-a", "profile-b"),
        )
        val divergent = restoredCopy.copy(
            days = restoredCopy.days.mapIndexed { index, day ->
                if (index == 0) day.copy(energyKcal = 2_300.0) else day
            },
        )

        assertTrue(nutritionPlanContainersEquivalent(restoredCopy, incoming))
        assertFalse(nutritionPlanContainersEquivalent(divergent, incoming))
    }

    @Test
    fun `backup semantic equality ignores touch timestamp but keeps creation provenance`() {
        val incoming = privateBackupPlan("source")
        val touched = incoming.copy(updatedAt = incoming.updatedAt!! + 10_000)

        assertTrue(
            nutritionPlanContainersEquivalent(
                touched,
                incoming,
            )
        )
        assertFalse(
            nutritionPlanContainersEquivalent(
                incoming.copy(createdAt = incoming.createdAt!! + 1),
                incoming,
            )
        )
        assertTrue(
            resolveNutritionBackupMerge(
                incoming = touched,
                replace = false,
                findByUuid = mapOf("source" to incoming)::get,
            ) is NutritionBackupMergeDecision.Skip
        )
    }

    @Test
    fun `replacement preserves identical plans with different source UUIDs`() {
        val first = privateBackupPlan("source-a")
        val second = first.copy(plan = first.plan.copy(uuid = "source-b"))

        val firstDecision = resolveNutritionBackupMerge(first, replace = true) { null }
        val secondDecision = resolveNutritionBackupMerge(second, replace = true) { null }

        assertEquals(
            "source-a",
            (firstDecision as NutritionBackupMergeDecision.Import).container.plan.uuid,
        )
        assertEquals(
            "source-b",
            (secondDecision as NutritionBackupMergeDecision.Import).container.plan.uuid,
        )
    }

    @Test
    fun `merge does not collapse an identical local plan with a different UUID`() {
        val incoming = privateBackupPlan("source")
        val local = incoming.copy(plan = incoming.plan.copy(uuid = "local-other"))
        val plansByUuid = mapOf("local-other" to local)

        val decision = resolveNutritionBackupMerge(
            incoming = incoming,
            replace = false,
            findByUuid = plansByUuid::get,
        )

        assertEquals(
            "source",
            (decision as NutritionBackupMergeDecision.Import).container.plan.uuid,
        )
    }

    @Test
    fun `repeated divergent UUID collision merge is idempotent`() {
        val incoming = privateBackupPlan("source-collision")
        val divergent = incoming.copy(
            plan = incoming.plan.copy(description = "Different local content"),
        )
        val plansByUuid = mutableMapOf("source-collision" to divergent)

        val firstDecision = resolveNutritionBackupMerge(
            incoming = incoming,
            replace = false,
            findByUuid = plansByUuid::get,
        ) as NutritionBackupMergeDecision.Import
        val derivedUuid = firstDecision.container.plan.uuid.orEmpty()
        assertTrue(derivedUuid.isNotEmpty())
        assertFalse(derivedUuid == "source-collision")
        plansByUuid[derivedUuid] = firstDecision.container

        val repeatedDecision = resolveNutritionBackupMerge(
            incoming = incoming,
            replace = false,
            findByUuid = plansByUuid::get,
        )

        assertTrue(repeatedDecision is NutritionBackupMergeDecision.Skip)
        val freshDecision = resolveNutritionBackupMerge(
            incoming = incoming,
            replace = false,
            findByUuid = mapOf("source-collision" to divergent)::get,
        ) as NutritionBackupMergeDecision.Import
        assertEquals(derivedUuid, freshDecision.container.plan.uuid)
    }

    @Test
    fun `first remapped merge uses derived identity and stable target repeat is idempotent`() {
        val source = privateBackupPlan("nutrition-source").copy(
            plan = privateBackupPlan("nutrition-source").plan.copy(
                linkedTrainingProgramUuid = "training-source",
            ),
        )
        val firstRemap = source.copy(
            plan = source.plan.copy(linkedTrainingProgramUuid = "training-copy-a"),
        )
        val plansByUuid = mutableMapOf<String, NutritionPlanContainer>()

        val firstDecision = resolveNutritionBackupMerge(
            source = source,
            incoming = firstRemap,
            replace = false,
            findByUuid = plansByUuid::get,
        ) as NutritionBackupMergeDecision.Import
        val derivedUuid = firstDecision.container.plan.uuid.orEmpty()
        assertFalse(derivedUuid == "nutrition-source")
        plansByUuid[derivedUuid] = firstDecision.container
        val secondRemap = source.copy(
            plan = source.plan.copy(linkedTrainingProgramUuid = "training-copy-a"),
        )

        val repeatedDecision = resolveNutritionBackupMerge(
            source = source,
            incoming = secondRemap,
            replace = false,
            findByUuid = plansByUuid::get,
        )

        assertTrue(repeatedDecision is NutritionBackupMergeDecision.Skip)
    }

    @Test
    fun `genuine source program link change is not deduplicated`() {
        val previous = privateBackupPlan("nutrition-source").copy(
            plan = privateBackupPlan("nutrition-source").plan.copy(
                linkedTrainingProgramUuid = "training-source-a",
            ),
        )
        val changed = previous.copy(
            plan = previous.plan.copy(linkedTrainingProgramUuid = "training-source-b"),
        )

        val decision = resolveNutritionBackupMerge(
            source = changed,
            incoming = changed,
            replace = false,
            findByUuid = mapOf("nutrition-source" to previous)::get,
        )

        assertTrue(decision is NutritionBackupMergeDecision.Import)
        assertFalse(
            (decision as NutritionBackupMergeDecision.Import).container.plan.uuid ==
                "nutrition-source"
        )
    }

    @Test
    fun `linked program content version selects correct nutrition copy and repeat skips`() {
        val source = privateBackupPlan("nutrition-source").copy(
            plan = privateBackupPlan("nutrition-source").plan.copy(
                linkedTrainingProgramUuid = "training-source",
            ),
        )
        val programV1Identity = trainingProgramDefinitionFingerprint(trainingProgram("Version 1"))
        val firstImport = resolveNutritionBackupMerge(
            source = source,
            incoming = source.copy(
                plan = source.plan.copy(linkedTrainingProgramUuid = "training-copy-a"),
            ),
            linkedProgramContentIdentity = programV1Identity,
            replace = false,
            findByUuid = { null },
        ) as NutritionBackupMergeDecision.Import
        val restored = mutableMapOf(
            firstImport.container.plan.uuid.orEmpty() to firstImport.container
        )

        val programV2Identity = trainingProgramDefinitionFingerprint(trainingProgram("Version 2"))
        assertFalse(programV1Identity == programV2Identity)
        val secondImport = resolveNutritionBackupMerge(
            source = source,
            incoming = source.copy(
                plan = source.plan.copy(linkedTrainingProgramUuid = "training-copy-b"),
            ),
            linkedProgramContentIdentity = programV2Identity,
            replace = false,
            findByUuid = restored::get,
        ) as NutritionBackupMergeDecision.Import

        assertEquals("training-copy-b", secondImport.container.plan.linkedTrainingProgramUuid)
        assertFalse(firstImport.container.plan.uuid == secondImport.container.plan.uuid)
        restored[secondImport.container.plan.uuid.orEmpty()] = secondImport.container

        val repeat = resolveNutritionBackupMerge(
            source = source,
            incoming = source.copy(
                plan = source.plan.copy(linkedTrainingProgramUuid = "training-copy-b"),
            ),
            linkedProgramContentIdentity = programV2Identity,
            replace = false,
            findByUuid = restored::get,
        )
        assertTrue(repeat is NutritionBackupMergeDecision.Skip)
    }

    @Test
    fun `merge skips equivalent plan with the same source UUID`() {
        val incoming = privateBackupPlan("same-source")
        val equivalent = incoming.copy(
            plan = incoming.plan.copy(description = "  "),
        )

        val decision = resolveNutritionBackupMerge(incoming, replace = false) { uuid ->
            equivalent.takeIf { uuid == "same-source" }
        }

        assertTrue(decision is NutritionBackupMergeDecision.Skip)
    }

    @Test
    fun `standalone reader rejects files larger than eight MiB before parsing`() {
        val oversized = ByteArray(MAX_NUTRITION_PLAN_FILE_BYTES + 1) { ' '.code.toByte() }

        val error = expectThrows<NutritionPlanImporter.InvalidNutritionPlanFileException> {
            NutritionPlanImporter.read(ByteArrayInputStream(oversized))
        }

        assertTrue(error.message.orEmpty().contains("8 MiB"))
    }

    @Test
    fun `standalone writer refuses valid trees whose JSON exceeds eight MiB`() {
        val longText = "x".repeat(5_000)
        val oversized = standalonePlan().copy(
            days = List(366) { dayIndex ->
                NutritionDayExport(
                    name = "Day ${dayIndex + 1}",
                    notes = longText,
                    meals = List(4) { mealIndex ->
                        NutritionMealExport(
                            name = "Meal ${mealIndex + 1}",
                            notes = longText,
                            items = listOf(
                                NutritionItemExport(name = "Food", notes = longText),
                            ),
                        )
                    },
                )
            },
        )

        expectThrows<java.io.IOException> {
            NutritionPlanExporter.write(oversized, ByteArrayOutputStream())
        }
    }

    @Test
    fun `older full backup defaults nutrition plans to empty`() {
        val mapper = jsonMapper { addModule(kotlinModule()) }
        val restored = mapper.readValue(
            """{"intervalSets":[],"workoutTypes":[],"programs":[]}""",
            DataContainer::class.java,
        )

        assertTrue(restored.nutritionPlans.isEmpty())
    }

    private fun standalonePlan() = NutritionPlanContainer(
        plan = NutritionPlanExport(
            name = "Balanced week",
            objective = "Maintenance",
            preferences = "Simple meals",
            restrictions = "No peanuts",
        ),
        days = listOf(
            NutritionDayExport(
                name = "Day 1",
                energyKcal = 2_200.0,
                proteinGrams = 130.0,
                meals = listOf(
                    NutritionMealExport(
                        name = "Breakfast",
                        plannedTimeMinutes = 8 * 60,
                        items = listOf(
                            NutritionItemExport(
                                name = "Oats",
                                quantity = 1.0,
                                unit = "bowl",
                                weightGrams = 80.0,
                                energyKcal = 300.0,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun privateBackupPlan(uuid: String): NutritionPlanContainer = standalonePlan().copy(
        plan = standalonePlan().plan.copy(uuid = uuid),
        createdAt = 10,
        updatedAt = 20,
    )

    private fun trainingProgram(description: String) = ProgramContainer(
        program = ProgramExport(
            uuid = "training-source",
            name = "Training",
            description = description,
            createdAt = 100,
        ),
        days = listOf(DayExport(name = "Day")),
    )
}

private inline fun <reified T : Throwable> expectThrows(block: () -> Unit): T {
    try {
        block()
    } catch (error: Throwable) {
        if (error is T) return error
        throw AssertionError("Expected ${T::class.java.name}, got ${error::class.java.name}", error)
    }
    throw AssertionError("Expected ${T::class.java.name}")
}
