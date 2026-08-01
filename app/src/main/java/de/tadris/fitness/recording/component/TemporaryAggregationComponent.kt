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

package de.tadris.fitness.recording.component

import android.hardware.SensorManager
import de.tadris.fitness.data.RouteSample
import de.tadris.fitness.data.WorkoutData
import de.tadris.fitness.data.calorie.CalorieCalculator
import de.tadris.fitness.data.effectiveMeasurements
import de.tadris.fitness.data.preferences.UserMeasurements
import de.tadris.fitness.data.preferences.UserPreferences
import de.tadris.fitness.recording.RecorderService
import de.tadris.fitness.recording.event.AggregatedRecordingValuesEvent
import de.tadris.fitness.recording.event.HeartRateChangeEvent
import de.tadris.fitness.recording.event.HeartRateConnectionChangeEvent
import de.tadris.fitness.recording.event.RepetitionSampleEvent
import de.tadris.fitness.recording.event.RouteSampleEvent
import de.tadris.fitness.util.totalDistance
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

class TemporaryAggregationComponent : RecorderServiceComponent {

    companion object {
        private const val PRESSURE_ELEVATION_AVERAGE = 0.1
    }

    private lateinit var service: RecorderService

    private var lastSample: RouteSample? = null

    private var distance = 0.0
    private var elevation = -1.0
    private var ascent = 0.0
    private var descent = 0.0

    private var useAverageForCurrentSpeed = false
    private var currentSpeedAverageTime = 0 // ms
    private var samplesForSpeedAverage = mutableListOf<RouteSample>()

    private var lastHeartRate = -1
    private var currentFrequency = 0.0
    private var currentIntensity = 0.0
    private var repetitions = 0

    private var sampleCount = 0

    private var energy = 0.0

    override fun register(service: RecorderService) {
        this.service = service
        EventBus.getDefault().register(this)

        val prefs = UserPreferences(service)
        useAverageForCurrentSpeed = prefs.useAverageForCurrentSpeed
        currentSpeedAverageTime = prefs.timeForCurrentSpeed * 1000

        replayExistingWorkout()
        publishAggregatedValues()
    }

    fun replayExistingWorkout() {
        val workout = service.instance.db.workoutDao().findById(service.stateHolder.workoutId)
        if (workout != null) {
            val data = WorkoutData.fromWorkout(service.instance.db.workoutDao(), workout)
            for (sample in data.samples) {
                onNewRouteSample(RouteSampleEvent(sample))
            }
            for (sample in data.repetitionSamples) {
                onNewRepetitionSample(RepetitionSampleEvent(sample))
            }
        }
    }

    override fun unregister() {
        EventBus.getDefault().unregister(this)
    }

    override fun checkSlow() {
        recalculateEnergy()
    }

    override fun checkFast() {
        publishAggregatedValues()
    }

    private fun publishAggregatedValues() {
        EventBus.getDefault().postSticky(
            AggregatedRecordingValuesEvent(
                recordingType = service.stateHolder.recordingType,
                workoutType = service.stateHolder.workoutType,
                currentState = service.stateHolder.state,
                activeDuration = service.stateHolder.currentActiveDuration,
                pauseDuration = service.stateHolder.currentPauseDuration,
                distance = distance,
                ascent = ascent,
                descent = descent,
                energy = energy,
                currentSpeed = getCurrentSpeed(),
                currentHeartRate = lastHeartRate,
                currentFrequency = currentFrequency,
                currentIntensity = currentIntensity,
                repetitions = repetitions,
                sampleCount = sampleCount
            )
        )
    }

    @Subscribe
    fun onNewRouteSample(e: RouteSampleEvent) {
        lastSample?.let { addDifference(it, e.sample) }
        lastSample = e.sample
        addSampleToAverageList(e.sample)
        addElevation(e.sample)
        sampleCount++
    }

    @Subscribe
    fun onNewRepetitionSample(e: RepetitionSampleEvent){
        currentIntensity = e.sample.intensity
        currentFrequency = e.sample.frequency
        repetitions += e.sample.repetitions
        sampleCount++
    }

    @Subscribe
    fun onHeartRateChange(event: HeartRateChangeEvent) {
        lastHeartRate = event.heartRate
    }

    @Subscribe
    fun onHeartRateConnectionChange(event: HeartRateConnectionChangeEvent) {
        if (event.state != HeartRateComponent.HeartRateConnectionState.CONNECTED) {
            // If heart rate sensor currently not available
            lastHeartRate = -1
        }
    }

    private fun addDifference(first: RouteSample, second: RouteSample) {
        distance += first.toLatLong().vincentyDistance(second.toLatLong())
    }

    private fun addElevation(sample: RouteSample) {
        val sampleElevation = getElevationForPressure(sample.pressure)
        if (elevation != -1.0) {
            val newElevation =
                sampleElevation * PRESSURE_ELEVATION_AVERAGE + elevation * (1 - PRESSURE_ELEVATION_AVERAGE) // floating avg
            if (newElevation > elevation) {
                ascent += newElevation - elevation
            } else {
                descent += elevation - newElevation
            }
            elevation = newElevation
        } else {
            elevation = sampleElevation
        }
    }

    private fun getElevationForPressure(pressure: Float): Double {
        return SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)
            .toDouble()
    }

    private fun addSampleToAverageList(sample: RouteSample) {
        if (useAverageForCurrentSpeed) {
            synchronized(samplesForSpeedAverage) {
                samplesForSpeedAverage += sample
                // remove stale samples from list
                samplesForSpeedAverage.removeAll { sample.timestamp - it.timestamp > currentSpeedAverageTime }
            }
        }
    }

    /**
     * Returns speed with the method from user preferences.
     * Takes a snapshot under lock — this is read from the watchdog thread while
     * [addSampleToAverageList] mutates the same list from the GPS sample thread.
     * @return speed in m/s
     */
    private fun getCurrentSpeed(): Double {
        if (useAverageForCurrentSpeed) {
            val snapshot = synchronized(samplesForSpeedAverage) { samplesForSpeedAverage.toList() }
            if (snapshot.size >= 2) {
                val distance = snapshot.totalDistance
                val timeStart = snapshot.first().timestamp
                val timeEnd = snapshot.last().timestamp
                val duration = timeEnd - timeStart
                return distance / (duration / 1000.0)
            }
        }
        return lastSample?.speed ?: 0.0
    }

    private fun recalculateEnergy() {
        val duration = service.stateHolder.currentActiveDuration
        if (duration <= 0) return
        val avgSpeed = distance / (duration / 1000.0)
        val current = UserMeasurements.from(service)
        val workout = service.instance.db.workoutDao().findById(service.stateHolder.workoutId)
        val effective = workout?.effectiveMeasurements(current)
        val calculated = CalorieCalculator(service).calculateEnergy(
            measurements = effective?.asUserMeasurements() ?: current,
            userWeightKg = effective?.weightKg ?: current.weight.toDouble(),
            duration = duration,
            ascent = ascent.toInt(),
            workoutTypeId = service.stateHolder.workoutType.id,
            speed = avgSpeed,
        )
        energy = maxOf(calculated, energy)
    }

}
