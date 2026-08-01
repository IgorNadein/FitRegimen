/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.data

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

enum class FitnessProfileField {
    NAME,
    BIRTH_DATE,
    HEIGHT,
    WEIGHT,
    STEP_LENGTH,
    GENDER,
    FITNESS_LEVEL,
    NOTES,
    CUSTOM_ATTRIBUTES,
}
enum class FitnessProfileValidationReason {
    REQUIRED,
    TOO_LONG,
    OUT_OF_RANGE,
    FUTURE_DATE,
    INVALID_JSON_OBJECT,
}

data class FitnessProfileValidationError(
    val field: FitnessProfileField,
    val reason: FitnessProfileValidationReason,
)

class InvalidFitnessProfileException(
    val errors: List<FitnessProfileValidationError>,
) : IllegalArgumentException("Invalid fitness profile: $errors")

object FitnessProfileValidator {
    const val MAX_NAME_LENGTH = 80
    const val MAX_SHORT_TEXT_LENGTH = 80
    const val MAX_NOTES_LENGTH = 4_000
    const val MAX_CUSTOM_ATTRIBUTES_LENGTH = 16_000

    private const val MIN_HEIGHT_CM = 30.0
    private const val MAX_HEIGHT_CM = 280.0
    private const val MIN_WEIGHT_KG = 2.0
    private const val MAX_WEIGHT_KG = 500.0
    private const val MIN_STEP_LENGTH_CM = 10.0
    private const val MAX_STEP_LENGTH_CM = 200.0
    private const val MAX_AGE_YEARS = 130L

    fun normalize(draft: FitnessProfileDraft): FitnessProfileDraft = draft.copy(
        name = draft.name.trim(),
        gender = draft.gender.normalizedNullable(),
        fitnessLevel = draft.fitnessLevel.normalizedNullable(),
        notes = draft.notes.normalizedNullable(),
        customAttributesJson = draft.customAttributesJson.normalizedNullable(),
    )

    fun validate(
        draft: FitnessProfileDraft,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
    ): List<FitnessProfileValidationError> {
        val value = normalize(draft)
        return buildList {
            if (value.name.isEmpty()) {
                add(FitnessProfileValidationError(FitnessProfileField.NAME, FitnessProfileValidationReason.REQUIRED))
            } else if (value.name.length > MAX_NAME_LENGTH) {
                add(FitnessProfileValidationError(FitnessProfileField.NAME, FitnessProfileValidationReason.TOO_LONG))
            }

            value.birthDateEpochDay?.let { birthDate ->
                when {
                    birthDate > todayEpochDay -> add(
                        FitnessProfileValidationError(
                            FitnessProfileField.BIRTH_DATE,
                            FitnessProfileValidationReason.FUTURE_DATE,
                        )
                    )
                    birthDate < LocalDate.ofEpochDay(todayEpochDay).minusYears(MAX_AGE_YEARS).toEpochDay() -> add(
                        FitnessProfileValidationError(
                            FitnessProfileField.BIRTH_DATE,
                            FitnessProfileValidationReason.OUT_OF_RANGE,
                        )
                    )
                }
            }
            value.heightCm.validateRange(FitnessProfileField.HEIGHT, MIN_HEIGHT_CM, MAX_HEIGHT_CM, this)
            value.weightKg.validateRange(FitnessProfileField.WEIGHT, MIN_WEIGHT_KG, MAX_WEIGHT_KG, this)
            value.stepLengthCm.validateRange(
                FitnessProfileField.STEP_LENGTH,
                MIN_STEP_LENGTH_CM,
                MAX_STEP_LENGTH_CM,
                this,
            )
            value.gender.validateLength(FitnessProfileField.GENDER, MAX_SHORT_TEXT_LENGTH, this)
            value.fitnessLevel.validateLength(FitnessProfileField.FITNESS_LEVEL, MAX_SHORT_TEXT_LENGTH, this)
            value.notes.validateLength(FitnessProfileField.NOTES, MAX_NOTES_LENGTH, this)
            value.customAttributesJson?.let { json ->
                if (json.length > MAX_CUSTOM_ATTRIBUTES_LENGTH) {
                    add(
                        FitnessProfileValidationError(
                            FitnessProfileField.CUSTOM_ATTRIBUTES,
                            FitnessProfileValidationReason.TOO_LONG,
                        )
                    )
                } else if (!isJsonObject(json)) {
                    add(
                        FitnessProfileValidationError(
                            FitnessProfileField.CUSTOM_ATTRIBUTES,
                            FitnessProfileValidationReason.INVALID_JSON_OBJECT,
                        )
                    )
                }
            }
        }
    }

    fun requireValid(
        draft: FitnessProfileDraft,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
    ): FitnessProfileDraft {
        val normalized = normalize(draft)
        val errors = validate(normalized, todayEpochDay)
        if (errors.isNotEmpty()) throw InvalidFitnessProfileException(errors)
        return normalized
    }

    private fun isJsonObject(value: String): Boolean = try {
        Json.parseToJsonElement(value) is JsonObject
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun String?.normalizedNullable(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private fun Double?.validateRange(
        field: FitnessProfileField,
        min: Double,
        max: Double,
        errors: MutableList<FitnessProfileValidationError>,
    ) {
        if (this != null && (!isFinite() || this !in min..max)) {
            errors += FitnessProfileValidationError(field, FitnessProfileValidationReason.OUT_OF_RANGE)
        }
    }

    private fun String?.validateLength(
        field: FitnessProfileField,
        max: Int,
        errors: MutableList<FitnessProfileValidationError>,
    ) {
        if (this != null && length > max) {
            errors += FitnessProfileValidationError(field, FitnessProfileValidationReason.TOO_LONG)
        }
    }
}
