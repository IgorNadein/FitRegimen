/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.ui.programs

import de.tadris.fitness.data.FitnessProfile

/**
 * Collision-free value snapshot of every input that can affect an AI prompt.
 *
 * Keeping a copy instead of reacting from a [androidx.compose.runtime.LaunchedEffect]
 * means an equal Room emission cannot accidentally invalidate a freshly generated prompt,
 * while a real input change hides Copy/Share synchronously on the next composition.
 */
internal data class AiPromptInputSnapshot(
    val goal: String,
    val params: String,
    val term: String,
    val daysPerWeek: String,
    val equipment: String,
    val extra: String,
    val includeProfiles: Boolean,
    val selectedProfileIds: List<Long>,
    val profiles: List<AiPromptProfileSnapshot>,
)

internal data class AiPromptProfileSnapshot(
    val id: Long,
    val uuid: String,
    val name: String,
    val isActive: Boolean,
    val birthDateEpochDay: Long?,
    val heightCm: Double?,
    val weightKg: Double?,
    val stepLengthCm: Double?,
    val gender: String?,
    val fitnessLevel: String?,
    val notes: String?,
    val customAttributesJson: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

internal data class GeneratedAiPrompt(
    val text: String,
    val generatedFrom: AiPromptInputSnapshot,
) {
    fun textFor(currentInputs: AiPromptInputSnapshot): String? =
        text.takeIf { generatedFrom == currentInputs }
}

internal fun GeneratedAiPrompt?.retainFor(
    currentInputs: AiPromptInputSnapshot,
): GeneratedAiPrompt? = this?.takeIf { it.generatedFrom == currentInputs }

internal fun aiPromptInputSnapshot(
    goal: String,
    params: String,
    term: String,
    daysPerWeek: String,
    equipment: String,
    extra: String,
    includeProfiles: Boolean,
    selectedProfileIds: List<Long>,
    profiles: List<FitnessProfile>,
): AiPromptInputSnapshot = AiPromptInputSnapshot(
    goal = goal,
    params = params,
    term = term,
    daysPerWeek = daysPerWeek,
    equipment = equipment,
    extra = extra,
    includeProfiles = includeProfiles,
    selectedProfileIds = selectedProfileIds.toList(),
    profiles = profiles.map(FitnessProfile::toAiPromptProfileSnapshot),
)

private fun FitnessProfile.toAiPromptProfileSnapshot() = AiPromptProfileSnapshot(
    id = id,
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
