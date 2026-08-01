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
package de.tadris.fitness.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    fun editWorkout(id: Long, editor: (Workout) -> Workout) {
        val workout = findById(id)!!
        updateWorkout(editor(workout))
    }

    @Query("select * from workout where `end` == -1")
    fun findRunningWorkout(): Workout?

    @Query("select * from workout where `end` == -1")
    fun findAllRunningWorkouts(): List<Workout>

    @Query("SELECT * FROM workout WHERE id = :id")
    fun findById(id: Long): Workout?

    @Query("SELECT * FROM workout WHERE uuid = :uuid LIMIT 1")
    fun findByUuid(uuid: String): Workout?

    @Query("SELECT * FROM route_sample WHERE id = :id")
    fun findSampleById(id: Long): RouteSample?

    @Query("SELECT * FROM route_sample WHERE workoutId = :workoutId order by relativeTime asc")
    fun getAllSamplesOfWorkout(workoutId: Long): Array<RouteSample>

    @Query("SELECT * FROM workout_pause WHERE workoutId = :workoutId order by startDate asc")
    fun getAllPausesOfWorkout(workoutId: Long): Array<WorkoutPause>

    @Query("SELECT * FROM workout_event WHERE workoutId = :workoutId order by relativeTime asc")
    fun getEventsOfWorkout(workoutId: Long): Array<WorkoutEvent>

    @Query("SELECT * FROM repetition_sample WHERE workoutId = :workoutId order by relativeTime asc")
    fun getAllRepetitionSamplesOfWorkout(workoutId: Long): Array<RepetitionSample>

    @Query("SELECT * FROM heart_rate_sample WHERE workoutId = :workoutId order by relativeTime asc")
    fun getAllHeartRateSamplesOfWorkout(workoutId: Long): Array<HeartRateSample>

    @Query("SELECT * FROM workout ORDER BY start DESC")
    fun getWorkouts(): List<Workout>

    @Query("select * from workout order by start desc")
    fun getWorkoutsFlow(): Flow<List<Workout>>

    /**
     * User-facing history. Recorder workouts used as individual measurements
     * inside a guided session or program occurrence remain in the database and
     * backups, but the corresponding summary is the single workout shown and
     * counted to avoid duplicate history/statistics entries.
     */
    @Query(
        "SELECT workout.* FROM workout WHERE workout.id NOT IN (" +
            "SELECT session_set.linkedWorkoutId FROM session_set " +
            "INNER JOIN session_exercise ON session_exercise.id = session_set.sessionExerciseId " +
            "INNER JOIN training_session ON training_session.id = session_exercise.sessionId " +
            "WHERE session_set.linkedWorkoutId IS NOT NULL " +
            "AND training_session.status IN ('COMPLETED', 'ABORTED') " +
            "AND EXISTS (SELECT 1 FROM workout AS summary " +
            "WHERE summary.uuid = 'guided-session:' || training_session.uuid)" +
            ") AND workout.id NOT IN (" +
            "SELECT program_completion.workoutId FROM program_completion " +
            "INNER JOIN program_exercise " +
            "ON program_exercise.id = program_completion.programExerciseId " +
            "INNER JOIN program_occurrence_summary " +
            "ON program_occurrence_summary.dayId = program_exercise.dayId " +
            "AND program_occurrence_summary.occurrenceDate = program_completion.occurrenceDate " +
            "WHERE program_completion.status = 1 " +
            "AND program_completion.workoutId IS NOT NULL " +
            "AND program_completion.workoutId != program_occurrence_summary.summaryWorkoutId" +
            ") ORDER BY workout.start DESC"
    )
    fun getVisibleWorkouts(): List<Workout>

    @Query(
        "SELECT workout.* FROM workout WHERE workout.id NOT IN (" +
            "SELECT session_set.linkedWorkoutId FROM session_set " +
            "INNER JOIN session_exercise ON session_exercise.id = session_set.sessionExerciseId " +
            "INNER JOIN training_session ON training_session.id = session_exercise.sessionId " +
            "WHERE session_set.linkedWorkoutId IS NOT NULL " +
            "AND training_session.status IN ('COMPLETED', 'ABORTED') " +
            "AND EXISTS (SELECT 1 FROM workout AS summary " +
            "WHERE summary.uuid = 'guided-session:' || training_session.uuid)" +
            ") AND workout.id NOT IN (" +
            "SELECT program_completion.workoutId FROM program_completion " +
            "INNER JOIN program_exercise " +
            "ON program_exercise.id = program_completion.programExerciseId " +
            "INNER JOIN program_occurrence_summary " +
            "ON program_occurrence_summary.dayId = program_exercise.dayId " +
            "AND program_occurrence_summary.occurrenceDate = program_completion.occurrenceDate " +
            "WHERE program_completion.status = 1 " +
            "AND program_completion.workoutId IS NOT NULL " +
            "AND program_completion.workoutId != program_occurrence_summary.summaryWorkoutId" +
            ") ORDER BY workout.start DESC"
    )
    fun getVisibleWorkoutsFlow(): Flow<List<Workout>>

    @Query("SELECT * FROM workout ORDER BY start DESC LIMIT 1")
    fun getLastWorkout(): Workout?

    @Query("SELECT * FROM workout WHERE workoutTypeId = :type ORDER BY start DESC LIMIT 1")
    fun getLastWorkoutByType(type: String): Workout?

    @Query("SELECT * FROM workout WHERE start = :start")
    fun getWorkoutByStart(start: Long): Workout?

    @Query("SELECT * FROM workout WHERE id = :id")
    fun getWorkoutById(id: Long): Workout?

    @Query("SELECT COUNT(*) FROM workout")
    fun countWorkouts(): Long

    @Query("SELECT COUNT(*) FROM route_sample")
    fun countRouteSamples(): Long

    @Query("SELECT COUNT(*) FROM repetition_sample")
    fun countRepetitionSamples(): Long

    @Query("SELECT COUNT(*) FROM heart_rate_sample")
    fun countHeartRateSamples(): Long

    @Query("SELECT COUNT(*) FROM workout_event")
    fun countWorkoutEvents(): Long

    @Query("SELECT COUNT(*) FROM workout_pause")
    fun countWorkoutPauses(): Long


    @Transaction
    fun insertWorkoutData(data: WorkoutData){
        insertWorkout(data.workout)
        for (sample in data.samples) {
            insertSample(sample)
        }

        for (pause in data.pauses) {
            insertPause(pause)
        }

        for (event in data.events) {
            insertWorkoutEvent(event)
        }

        for (repetitionSample in data.repetitionSamples) {
            insertSample(repetitionSample)
        }

        for (heartRateSample in data.heartRateSamples) {
            insertSample(heartRateSample)
        }
    }

    @Transaction
    fun updateWorkoutData(data: WorkoutData){
        updateWorkout(data.workout)
        for (sample in data.samples) {
            updateSample(sample)
        }

        for (pause in data.pauses) {
            updatePause(pause)
        }

        for (event in data.events) {
            updateWorkoutEvent(event)
        }

        for (repetitionSample in data.repetitionSamples) {
            updateSample(repetitionSample)
        }

        for (heartRateSample in data.heartRateSamples) {
            updateSample(heartRateSample)
        }
    }

    @Insert
    fun insertWorkoutAndSamples(workout: Workout, samples: Array<RouteSample>)

    @Delete
    fun deleteWorkoutAndSamples(workout: Workout, toArray: Array<RouteSample>)

    @Insert
    fun insertWorkout(workout: Workout)

    @Delete
    fun deleteWorkout(workout: Workout)

    @Update
    fun updateWorkout(workout: Workout)

    @Insert
    fun insertSample(sample: RouteSample)

    @Insert
    fun insertSample(sample: HeartRateSample)

    @Insert
    fun insertSample(sample: RepetitionSample)

    @Insert
    fun insertPause(pause: WorkoutPause)

    @Delete
    fun deleteSample(sample: RouteSample)

    @Update
    fun updateSamples(samples: List<RouteSample>)

    @Update
    fun updateSample(sample: RouteSample)

    @Update
    fun updateSample(sample: HeartRateSample)

    @Update
    fun updateSample(sample: RepetitionSample)

    @Update
    fun updatePause(pause: WorkoutPause)

    @Update
    fun updateWorkoutEvent(event: WorkoutEvent)

    @Update
    fun updateRepetitionSample(sample: RepetitionSample)

    @Insert
    fun insertWorkoutEvent(event: WorkoutEvent)
}
