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

package de.tadris.fitness.recording

import android.content.Context
import android.hardware.SensorManager
import android.util.Log
import de.tadris.fitness.data.CalculatedValues
import de.tadris.fitness.data.WorkoutDao
import de.tadris.fitness.data.WorkoutData
import de.tadris.fitness.data.effectiveMeasurements
import de.tadris.fitness.data.autoexport.AutoExportPlanner
import de.tadris.fitness.data.calorie.CalorieCalculator
import de.tadris.fitness.data.preferences.UserMeasurements
import de.tadris.fitness.util.AltitudeCorrection
import de.tadris.fitness.util.WorkoutLogger
import de.tadris.fitness.util.totalDistance
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class WorkoutFinalizer(val context: Context, val workoutDao: WorkoutDao, val workoutId: Long) {

    private val workout =
        workoutDao.getWorkoutById(workoutId) ?: throw Exception("Workout not found")
    private var data = WorkoutData.fromWorkout(workoutDao, workout)
    private val calculatedValues = CalculatedValues(data)
    
    fun finalizeWorkout() {
        recalculateElevation()
        smoothElevation()
        updateMSLValues()
        updateRouteSamples()

        val finalVersion = workout.copy(
            activeDuration = getActiveDuration(),
            distance = data.samples.totalDistance,
            repetitions = data.repetitionSamples.sumOf { it.repetitions },
            energy = getEnergy(),
        )

        workoutDao.updateWorkout(finalVersion)
        AutoExportPlanner(context).onWorkoutRecorded(finalVersion)
    }

    fun recalculateElevation() {
        if (data.samples.isEmpty()) return

        val pressureDataAvailable = data.samples[0].pressure != -1f

        if (!pressureDataAvailable) {
            // Because pressure data isn't available we just use the use GPS elevation
            // in WorkoutSample.elevation which was already set
            WorkoutLogger.log("WorkoutFinalizer", "No pressure data available, not recalculating elevation")
            return
        }else{
            WorkoutLogger.log("WorkoutFinalizer", "Pressure data available, recalculating elevation")
        }

        val avgElevation: Double = data.samples.sumOf { it.elevation } / data.samples.size
        val avgPressure: Double = data.samples.sumOf { it.pressure.toDouble() } / data.samples.size

        val avgPressureAltitude = SensorManager.getAltitude(
            SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
            avgPressure.toFloat()
        )

        data = data.copy(
            samples = data.samples.mapIndexed { index, sample ->
                // Altitude Difference to Average Elevation in meters
                val altitudeDifference =
                    SensorManager.getAltitude(
                        SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                        sample.pressure
                    ) - avgPressureAltitude

                if (!altitudeDifference.isNaN()) {
                    sample.copy(elevation = avgElevation + altitudeDifference)
                } else {
                    Log.w(
                        "WorkoutFinalizer",
                        "Cannot determine elevation for sample " + index + ": pressure=" + sample.pressure
                    )
                    sample
                }
            }
        )
    }

    fun smoothElevation() {
        val window = 7
        data = data.copy(
            samples = data.samples.mapIndexed { index, sample ->
                val minIndex = max(index - window, 0)
                val maxIndex = min(index + window, data.samples.lastIndex)
                val samplesInWindow = data.samples.subList(minIndex, maxIndex)
                val averageElevation = samplesInWindow.sumOf { it.elevation } / samplesInWindow.size

                sample.copy(elevation = averageElevation)
            }
        )
    }

    fun updateMSLValues() {
        if (data.samples.isEmpty()) return

        try {
            val firstSample = data.samples[0]
            val lat = firstSample.latitude.toInt()
            val lon = firstSample.longitude.toInt()
            val correction = AltitudeCorrection(context, lat, lon)

            data = data.copy(
                samples = data.samples.map { sample ->
                    if (sample.elevationMSL == 0.0 || sample.elevationMSL == sample.elevation) {
                        sample.copy(elevationMSL = correction.getHeightOverSeaLevel(sample.elevation))
                    } else {
                        sample
                    }
                }
            )
            
            WorkoutLogger.log("WorkoutFinalizer", "Updated MSL values for ${data.samples.size} samples")
        } catch (e: Exception) {
            WorkoutLogger.log("WorkoutFinalizer", "Failed to update MSL values: ${e.message}")
        }
    }

    private fun updateRouteSamples(){
        workoutDao.updateSamples(data.samples)
    }

    private fun getActiveDuration(): Long {
        var activeDuration = workout.end - workout.start
        data.pauses.forEach {
            activeDuration -= it.endDate - it.startDate
        }
        return activeDuration
    }

    private fun getEnergy(): Double {
        val current = UserMeasurements.from(context)
        val effective = workout.effectiveMeasurements(current)
        return CalorieCalculator(context).calculateEnergy(
            measurements = effective.asUserMeasurements(),
            userWeightKg = effective.weightKg,
            duration = getActiveDuration(),
            ascent = calculatedValues.getAscentAndDescent().first.roundToInt(),
            workoutTypeId = workout.workoutTypeId,
            speed = data.samples.totalDistance / (getActiveDuration() / 1000.0),
        )
    }

}
