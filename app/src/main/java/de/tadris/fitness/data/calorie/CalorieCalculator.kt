/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack
 *
 * FitoTrack is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     FitoTrack is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.tadris.fitness.data.calorie

import android.content.Context
import de.tadris.fitness.data.CalculatedValues
import de.tadris.fitness.data.WorkoutData
import de.tadris.fitness.data.effectiveMeasurements
import de.tadris.fitness.data.preferences.UserMeasurements
import de.tadris.fitness.util.unit.Kcal
import kotlin.math.roundToInt

class CalorieCalculator(val provider: List<METProvider>) {

    constructor(context: Context) : this(
        listOf(
            METFunctionsProvider,
            FallbackMETProvider,
            WorkoutTypeMETProvider(context)
        )
    )

    fun calculateEnergy(context: Context, data: WorkoutData): Double {
        val current = UserMeasurements.from(context)
        val effective = data.workout.effectiveMeasurements(current)
        return calculateEnergy(
            effective.asUserMeasurements(),
            effective.weightKg,
            data.workout.activeDuration,
            CalculatedValues(data).getAscentAndDescent().first.roundToInt(),
            data.workout.workoutTypeId,
            data.workout.averageSpeedActive
        )
    }

    fun calculateEnergy(
        measurements: UserMeasurements,
        duration: Long,
        ascent: Int,
        workoutTypeId: String,
        speed: Double // m/s
    ) =
        Kcal().toJoules(calculateCalories(measurements, duration, ascent, workoutTypeId, speed))

    /** Keeps the captured profile weight's decimal precision while retaining MET compatibility. */
    fun calculateEnergy(
        measurements: UserMeasurements,
        userWeightKg: Double,
        duration: Long,
        ascent: Int,
        workoutTypeId: String,
        speed: Double,
    ) = Kcal().toJoules(
        calculateCalories(
            measurements = measurements,
            userWeightKg = userWeightKg,
            duration = duration,
            ascent = ascent,
            workoutTypeId = workoutTypeId,
            speed = speed,
        )
    )

    fun calculateCalories(
        measurements: UserMeasurements,
        duration: Long,
        ascent: Int,
        workoutTypeId: String,
        speed: Double // m/s
    ): Double = calculateCalories(
        measurements = measurements,
        userWeightKg = measurements.weight.toDouble(),
        duration = duration,
        ascent = ascent,
        workoutTypeId = workoutTypeId,
        speed = speed,
    )

    fun calculateCalories(
        measurements: UserMeasurements,
        userWeightKg: Double,
        duration: Long,
        ascent: Int,
        workoutTypeId: String,
        speed: Double,
    ): Double {
        val weight = userWeightKg.takeIf { it.isFinite() && it > 0.0 }
            ?: measurements.weight.toDouble()
        val mins = (duration / 1000).toDouble() / 60
        return (mins * (getMET(measurements, workoutTypeId, speed) * 3.5 * weight) / 200) + ascent
    }

    private fun getMET(
        measurements: UserMeasurements,
        workoutTypeId: String,
        speed: Double // m/s
    ): Double {
        provider.forEach { provider ->
            val result = provider.calculateMET(measurements, workoutTypeId, speed * 3.6)
            result?.let { return it } // return if not null
        }

        return 0.0 // nothing found
    }

}
