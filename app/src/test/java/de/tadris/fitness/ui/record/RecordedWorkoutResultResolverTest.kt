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
import de.tadris.fitness.recording.RecordingState
import de.tadris.fitness.recording.event.RecordingStateChangeEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordedWorkoutResultResolverTest {

    @Test
    fun `maps the exact completed workout from a stopped event`() {
        val workout = workout(id = 42, start = 1_000, end = 6_000)
        val event = stoppedEvent(workout.id)

        val result = RecordedWorkoutResultResolver.resolve(event) { requestedId ->
            workout.takeIf { it.id == requestedId }
        }

        requireNotNull(result)
        assertEquals(42, result.workoutId)
        assertEquals("uuid-42", result.workoutUuid)
        assertEquals("running", result.workoutTypeId)
        assertEquals(5_000, result.totalDurationMillis)
        assertEquals(4_000, result.activeDurationMillis)
        assertEquals(1_250.5, result.distanceMeters, 0.0)
        assertEquals(17, result.repetitions)
        assertEquals(12_345.0, result.energyJoules, 0.0)
    }

    @Test
    fun `does not use a different workout when the exact id is missing`() {
        val event = stoppedEvent(42)

        val result = RecordedWorkoutResultResolver.resolve(event) { requestedId ->
            workout(id = 99).takeIf { it.id == requestedId }
        }

        assertNull(result)
    }

    @Test
    fun `rejects a non-stopped recorder event`() {
        val event = RecordingStateChangeEvent(
            workoutId = 42,
            oldState = RecordingState.IDLE,
            newState = RecordingState.RUNNING,
        )

        assertNull(RecordedWorkoutResultResolver.resolve(event) { workout(id = it) })
    }

    @Test
    fun `rejects missing discarded unstarted and running workouts`() {
        val event = stoppedEvent(42)

        assertNull(RecordedWorkoutResultResolver.resolve(event) { null })
        assertNull(
            RecordedWorkoutResultResolver.resolve(event) {
                workout(id = it, start = -1, end = -1)
            }
        )
        assertNull(
            RecordedWorkoutResultResolver.resolve(event) {
                workout(id = it, start = 1_000, end = -1)
            }
        )
    }

    private fun stoppedEvent(workoutId: Long) = RecordingStateChangeEvent(
        workoutId = workoutId,
        oldState = RecordingState.RUNNING,
        newState = RecordingState.STOPPED,
    )

    private fun workout(
        id: Long,
        start: Long = 1_000,
        end: Long = 6_000,
    ) = Workout(
        id = id,
        uuid = "uuid-$id",
        start = start,
        end = end,
        activeDuration = 4_000,
        comment = null,
        distance = 1_250.5,
        repetitions = 17,
        energy = 12_345.0,
        workoutTypeId = "running",
        intervalSetUsedId = -1,
    )
}
