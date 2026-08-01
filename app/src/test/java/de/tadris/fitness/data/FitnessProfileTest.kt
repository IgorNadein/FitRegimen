/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessProfileTest {
    private val today = LocalDate.of(2026, 8, 1)

    @Test
    fun `validator normalizes optional text and accepts realistic measurements`() {
        val draft = FitnessProfileDraft(
            name = "  Алексей  ",
            birthDateEpochDay = LocalDate.of(1994, 2, 17).toEpochDay(),
            heightCm = 181.5,
            weightKg = 78.2,
            stepLengthCm = 79.0,
            gender = "  мужчина ",
            fitnessLevel = " средний ",
            notes = "  Беречь колено  ",
            customAttributesJson = " {\"restingHeartRate\": 58} ",
        )

        val normalized = FitnessProfileValidator.requireValid(draft, today.toEpochDay())

        assertEquals("Алексей", normalized.name)
        assertEquals("мужчина", normalized.gender)
        assertEquals("средний", normalized.fitnessLevel)
        assertEquals("Беречь колено", normalized.notes)
        assertTrue(FitnessProfileValidator.validate(normalized, today.toEpochDay()).isEmpty())
    }

    @Test
    fun `validator reports future birthday and invalid physical ranges`() {
        val errors = FitnessProfileValidator.validate(
            FitnessProfileDraft(
                name = "",
                birthDateEpochDay = today.plusDays(1).toEpochDay(),
                heightCm = 500.0,
                weightKg = Double.NaN,
                stepLengthCm = 2.0,
            ),
            today.toEpochDay(),
        )

        assertTrue(errors.containsError(FitnessProfileField.NAME, FitnessProfileValidationReason.REQUIRED))
        assertTrue(errors.containsError(FitnessProfileField.BIRTH_DATE, FitnessProfileValidationReason.FUTURE_DATE))
        assertTrue(errors.containsError(FitnessProfileField.HEIGHT, FitnessProfileValidationReason.OUT_OF_RANGE))
        assertTrue(errors.containsError(FitnessProfileField.WEIGHT, FitnessProfileValidationReason.OUT_OF_RANGE))
        assertTrue(errors.containsError(FitnessProfileField.STEP_LENGTH, FitnessProfileValidationReason.OUT_OF_RANGE))
    }

    @Test
    fun `custom attributes must be a JSON object`() {
        val arrayErrors = FitnessProfileValidator.validate(
            FitnessProfileDraft(name = "User", customAttributesJson = "[1, 2]"),
            today.toEpochDay(),
        )
        val malformedErrors = FitnessProfileValidator.validate(
            FitnessProfileDraft(name = "User", customAttributesJson = "{broken"),
            today.toEpochDay(),
        )

        assertTrue(
            arrayErrors.containsError(
                FitnessProfileField.CUSTOM_ATTRIBUTES,
                FitnessProfileValidationReason.INVALID_JSON_OBJECT,
            )
        )
        assertTrue(
            malformedErrors.containsError(
                FitnessProfileField.CUSTOM_ATTRIBUTES,
                FitnessProfileValidationReason.INVALID_JSON_OBJECT,
            )
        )
    }

    @Test
    fun `legacy migration preserves exact old measurements`() {
        val draft = FitnessProfileLegacyBridge.fromLegacy(
            name = "Пользователь",
            weightKg = 93,
            stepLengthMeters = 0.83f,
        )

        assertEquals(93.0, draft.weightKg!!, 0.0)
        assertEquals(83.0, draft.stepLengthCm!!, 0.001)
    }

    @Test
    fun `effective measurements use active profile and safe defaults for missing values`() {
        val complete = profile(weightKg = 72.6, stepLengthCm = 74.0)
        val missing = profile(weightKg = null, stepLengthCm = null)

        assertEquals(73, FitnessProfileLegacyBridge.effectiveMeasurements(complete).weight)
        assertEquals(0.74f, FitnessProfileLegacyBridge.effectiveMeasurements(complete).stepLength, 0.0001f)
        assertEquals(80, FitnessProfileLegacyBridge.effectiveMeasurements(missing).weight)
        assertEquals(0.79f, FitnessProfileLegacyBridge.effectiveMeasurements(missing).stepLength, 0.0001f)
    }

    @Test
    fun `age is calendar-correct around birthday and future date is absent`() {
        val profile = profile(birthDateEpochDay = LocalDate.of(2000, 8, 2).toEpochDay())

        assertEquals(25, profile.ageOn(LocalDate.of(2026, 8, 1)))
        assertEquals(26, profile.ageOn(LocalDate.of(2026, 8, 2)))
        assertNull(profile.ageOn(LocalDate.of(1999, 1, 1)))
    }

    private fun profile(
        birthDateEpochDay: Long? = null,
        weightKg: Double? = null,
        stepLengthCm: Double? = null,
    ) = FitnessProfile(
        uuid = "profile-1",
        name = "User",
        birthDateEpochDay = birthDateEpochDay,
        weightKg = weightKg,
        stepLengthCm = stepLengthCm,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun List<FitnessProfileValidationError>.containsError(
        field: FitnessProfileField,
        reason: FitnessProfileValidationReason,
    ) = any { it.field == field && it.reason == reason }
}
