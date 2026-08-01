/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.data

import de.tadris.fitness.data.preferences.UserMeasurements
import kotlin.math.roundToInt

/**
 * Stable measurements used to interpret a persisted workout.
 *
 * New workouts carry their profile measurements with them. Legacy workouts do not, so each
 * missing or invalid field deliberately falls back to the current legacy preferences.
 */
data class EffectiveWorkoutMeasurements(
    val weightKg: Double,
    val stepLengthMeters: Double,
) {
    /** MET providers still accept the legacy value object; energy keeps [weightKg] as a Double. */
    fun asUserMeasurements(): UserMeasurements = UserMeasurements(
        weight = weightKg.roundToInt(),
        stepLength = stepLengthMeters.toFloat(),
    )
}

fun effectiveMeasurements(
    snapshot: FitnessProfileSnapshot,
    current: UserMeasurements,
): EffectiveWorkoutMeasurements = EffectiveWorkoutMeasurements(
    weightKg = snapshot.userWeightKg.validPositiveOrNull() ?: current.weight.toDouble(),
    stepLengthMeters = snapshot.stepLengthMeters.validPositiveOrNull()
        ?: current.stepLength.toDouble(),
)

fun Workout.effectiveMeasurements(current: UserMeasurements): EffectiveWorkoutMeasurements =
    effectiveMeasurements(
        snapshot = FitnessProfileSnapshot(
            profileUuid = profileUuid,
            userWeightKg = userWeightKgSnapshot,
            stepLengthMeters = stepLengthMetersSnapshot,
        ),
        current = current,
    )

private fun Double?.validPositiveOrNull(): Double? = this?.takeIf { it.isFinite() && it > 0.0 }
