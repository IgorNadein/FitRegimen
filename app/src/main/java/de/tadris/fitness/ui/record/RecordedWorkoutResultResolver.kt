/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack
 *
 * FitoTrack is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FitoTrack is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.tadris.fitness.ui.record

import de.tadris.fitness.data.Workout
import de.tadris.fitness.data.WorkoutDao
import de.tadris.fitness.recording.RecordingState
import de.tadris.fitness.recording.event.RecordingStateChangeEvent

/**
 * Immutable result returned by a legacy recorder route before it navigates back.
 *
 * Values are copied from the exact [Workout] identified by the recorder state event;
 * the resolver deliberately never falls back to the most recently created workout.
 */
data class RecordedWorkoutResult(
    val workoutId: Long,
    val workoutUuid: String,
    val workoutTypeId: String,
    val startedAt: Long,
    val endedAt: Long,
    val activeDurationMillis: Long,
    val totalDurationMillis: Long,
    val distanceMeters: Double,
    val repetitions: Int,
    val energyJoules: Double,
)

object RecordedWorkoutResultResolver {

    /**
     * Resolves only a recorder event that reached [RecordingState.STOPPED]. A discarded,
     * unstarted, still-running, or missing workout resolves to `null`.
     */
    fun resolve(
        event: RecordingStateChangeEvent,
        workoutDao: WorkoutDao,
    ): RecordedWorkoutResult? = resolve(event) { workoutId ->
        workoutDao.findById(workoutId)
    }

    /** Pure overload kept independently testable without Room or Android runtime state. */
    internal fun resolve(
        event: RecordingStateChangeEvent,
        findWorkout: (Long) -> Workout?,
    ): RecordedWorkoutResult? {
        if (event.newState != RecordingState.STOPPED || event.workoutId < 0) return null
        return validateAndMap(findWorkout(event.workoutId))
    }

    internal fun validateAndMap(workout: Workout?): RecordedWorkoutResult? {
        if (workout == null || workout.start < 0 || workout.end < workout.start) return null

        return RecordedWorkoutResult(
            workoutId = workout.id,
            workoutUuid = workout.uuid,
            workoutTypeId = workout.workoutTypeId,
            startedAt = workout.start,
            endedAt = workout.end,
            activeDurationMillis = workout.activeDuration,
            totalDurationMillis = workout.end - workout.start,
            distanceMeters = workout.distance,
            repetitions = workout.repetitions,
            energyJoules = workout.energy,
        )
    }
}
