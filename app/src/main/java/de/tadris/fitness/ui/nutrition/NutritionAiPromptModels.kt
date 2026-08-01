/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.ui.nutrition

import de.tadris.fitness.ui.programs.AiProfileContext

/**
 * Prompt-safe workload input. It deliberately has no program, day or exercise text,
 * database id, UUID, note, media URL or profile association.
 */
internal data class NutritionTrainingExerciseLoad(
    val metricKind: String,
    val setCount: Int,
    val targetRepetitions: Int = 0,
    val targetDurationSeconds: Double = 0.0,
    val targetDistanceMeters: Double = 0.0,
    val targetWeightVolumeKgReps: Double = 0.0,
    val estimatedActiveSeconds: Double = 0.0,
    val plannedRestSeconds: Int = 0,
    val met: Double? = null,
)

internal data class NutritionTrainingDayLoad(
    val isRest: Boolean,
    val exercises: List<NutritionTrainingExerciseLoad>,
)

internal data class NutritionTrainingExerciseSummary(
    val metricKind: String,
    val setCount: Int,
    val targetRepetitions: Int,
    val targetDurationMinutes: Double,
    val targetDistanceMeters: Double,
    val targetWeightVolumeKgReps: Double,
    val estimatedActiveMinutes: Double,
    val plannedRestMinutes: Double,
    val met: Double?,
)

internal data class NutritionTrainingDaySummary(
    val dayNumber: Int,
    val isRest: Boolean,
    val exerciseCount: Int,
    val setCount: Int,
    val targetRepetitions: Int,
    val targetDurationMinutes: Double,
    val targetDistanceMeters: Double,
    val targetWeightVolumeKgReps: Double,
    val estimatedActiveMinutes: Double,
    val plannedRestMinutes: Double,
    val metMinutes: Double?,
    val exercises: List<NutritionTrainingExerciseSummary>,
)

internal data class NutritionTrainingProgramSummary(
    val durationDays: Int,
    val days: List<NutritionTrainingDaySummary>,
)

/** Pure aggregation: its result cannot carry user-authored text by construction. */
internal fun summarizeNutritionTrainingProgram(
    days: List<NutritionTrainingDayLoad>,
): NutritionTrainingProgramSummary = NutritionTrainingProgramSummary(
    durationDays = days.size,
    days = days.mapIndexed { dayIndex, day ->
        val exercises = if (day.isRest) {
            emptyList()
        } else {
            day.exercises.map { load ->
                val activeMinutes = load.estimatedActiveSeconds.safeNonNegative() / 60.0
                NutritionTrainingExerciseSummary(
                    metricKind = load.metricKind.safeMetricKind(),
                    setCount = load.setCount.coerceAtLeast(0),
                    targetRepetitions = load.targetRepetitions.coerceAtLeast(0),
                    targetDurationMinutes = load.targetDurationSeconds.safeNonNegative() / 60.0,
                    targetDistanceMeters = load.targetDistanceMeters.safeNonNegative(),
                    targetWeightVolumeKgReps = load.targetWeightVolumeKgReps.safeNonNegative(),
                    estimatedActiveMinutes = activeMinutes,
                    plannedRestMinutes = load.plannedRestSeconds.coerceAtLeast(0) / 60.0,
                    met = load.met?.takeIf { it.isFinite() && it in 0.5..25.0 },
                )
            }
        }
        val metContributions = exercises.mapNotNull { exercise ->
            exercise.met?.let { it * exercise.estimatedActiveMinutes }
        }
        NutritionTrainingDaySummary(
            dayNumber = dayIndex + 1,
            isRest = day.isRest,
            exerciseCount = exercises.size,
            setCount = exercises.sumOf(NutritionTrainingExerciseSummary::setCount),
            targetRepetitions = exercises.sumOf(NutritionTrainingExerciseSummary::targetRepetitions),
            targetDurationMinutes = exercises.sumOf(NutritionTrainingExerciseSummary::targetDurationMinutes),
            targetDistanceMeters = exercises.sumOf(NutritionTrainingExerciseSummary::targetDistanceMeters),
            targetWeightVolumeKgReps = exercises.sumOf(
                NutritionTrainingExerciseSummary::targetWeightVolumeKgReps
            ),
            estimatedActiveMinutes = exercises.sumOf(NutritionTrainingExerciseSummary::estimatedActiveMinutes),
            plannedRestMinutes = exercises.sumOf(NutritionTrainingExerciseSummary::plannedRestMinutes),
            metMinutes = metContributions.takeIf { it.isNotEmpty() }?.sum(),
            exercises = exercises,
        )
    },
)

private fun Double.safeNonNegative(): Double = takeIf { it.isFinite() && it > 0.0 } ?: 0.0

private fun String.safeMetricKind(): String {
    val normalized = trim().uppercase()
    return normalized.takeIf { it in SAFE_METRIC_KINDS } ?: "UNKNOWN"
}

private val SAFE_METRIC_KINDS = setOf(
    "WEIGHT_REPS",
    "REPS",
    "DURATION",
    "WEIGHT_DURATION",
    "DISTANCE_DURATION",
)

internal data class NutritionAiProfileSnapshot(
    val profileId: Long,
    val context: AiProfileContext,
)

/** Exact value snapshot of everything that can affect a generated nutrition prompt. */
internal data class NutritionAiPromptInputSnapshot(
    val goal: String,
    val duration: String,
    val mealsPerDay: String,
    val allergiesAndFeatures: String,
    val preferences: String,
    val excludedFoods: String,
    val requiredFoods: String,
    val budget: String,
    val cookingTime: String,
    val extra: String,
    val includeProfiles: Boolean,
    val profiles: List<NutritionAiProfileSnapshot>,
    val selectedTrainingProgramId: Long?,
    val trainingProgram: NutritionTrainingProgramSummary?,
)

internal data class GeneratedNutritionAiPrompt(
    val text: String,
    val generatedFrom: NutritionAiPromptInputSnapshot,
) {
    fun textFor(currentInputs: NutritionAiPromptInputSnapshot): String? =
        text.takeIf { generatedFrom == currentInputs }
}

internal fun GeneratedNutritionAiPrompt?.retainFor(
    currentInputs: NutritionAiPromptInputSnapshot,
): GeneratedNutritionAiPrompt? = this?.takeIf { it.generatedFrom == currentInputs }
