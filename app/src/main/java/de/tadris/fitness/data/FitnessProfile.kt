/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate
import java.time.Period

/**
 * A local person whose measurements can be used for fitness calculations and
 * program generation. Calendar dates are stored as epoch days so a birthday
 * cannot move when the device time zone changes.
 *
 * Profiles are deliberately not linked to workout history yet: the currently
 * active profile replaces the former global weight/step-length source, while a
 * caller may explicitly select several profiles when generating a group plan.
 */
@Entity(
    tableName = "fitness_profile",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["isActive"]),
    ],
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class FitnessProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String,
    val name: String,
    val isActive: Boolean = false,
    val birthDateEpochDay: Long? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val stepLengthCm: Double? = null,
    val gender: String? = null,
    val fitnessLevel: String? = null,
    val notes: String? = null,
    /** Optional object-shaped JSON for future, user-defined fitness attributes. */
    val customAttributesJson: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun birthDate(): LocalDate? = birthDateEpochDay?.let(LocalDate::ofEpochDay)

    fun ageOn(date: LocalDate): Int? = birthDate()?.let { birthDate ->
        if (birthDate.isAfter(date)) null else Period.between(birthDate, date).years
    }

    fun toDraft(): FitnessProfileDraft = FitnessProfileDraft(
        name = name,
        birthDateEpochDay = birthDateEpochDay,
        heightCm = heightCm,
        weightKg = weightKg,
        stepLengthCm = stepLengthCm,
        gender = gender,
        fitnessLevel = fitnessLevel,
        notes = notes,
        customAttributesJson = customAttributesJson,
    )
}

data class FitnessProfileDraft(
    val name: String,
    val birthDateEpochDay: Long? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val stepLengthCm: Double? = null,
    val gender: String? = null,
    val fitnessLevel: String? = null,
    val notes: String? = null,
    val customAttributesJson: String? = null,
)

/**
 * Immutable measurements captured when a workout/session starts. The UUID is
 * deliberately optional so legacy preferences can remain a valid fallback when
 * no profile row is available yet.
 */
data class FitnessProfileSnapshot(
    val profileUuid: String? = null,
    val userWeightKg: Double? = null,
    val stepLengthMeters: Double? = null,
)
