/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.data.backup.model

import de.tadris.fitness.data.FitnessProfile
import de.tadris.fitness.data.FitnessProfileDraft

/**
 * Portable full-backup representation. It intentionally lives outside the
 * shareable program format so exporting a plan can never leak profile data.
 */
data class FitnessProfileExport(
    val uuid: String = "",
    val name: String = "",
    val isActive: Boolean = false,
    val birthDateEpochDay: Long? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val stepLengthCm: Double? = null,
    val gender: String? = null,
    val fitnessLevel: String? = null,
    val notes: String? = null,
    val customAttributesJson: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    fun toDraft() = FitnessProfileDraft(
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

fun FitnessProfile.toBackupExport() = FitnessProfileExport(
    uuid = uuid,
    name = name,
    isActive = isActive,
    birthDateEpochDay = birthDateEpochDay,
    heightCm = heightCm,
    weightKg = weightKg,
    stepLengthCm = stepLengthCm,
    gender = gender,
    fitnessLevel = fitnessLevel,
    notes = notes,
    customAttributesJson = customAttributesJson,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
