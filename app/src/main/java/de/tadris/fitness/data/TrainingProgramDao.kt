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
 *
 * This file was added in FitRegimen, a fork of FitoTrack.
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
interface TrainingProgramDao {

    // --- Programs ---

    @Query("SELECT * FROM training_program ORDER BY createdAt DESC")
    fun getProgramsFlow(): Flow<List<TrainingProgram>>

    @Query("SELECT * FROM training_program ORDER BY createdAt DESC")
    fun getPrograms(): List<TrainingProgram>

    @Query("SELECT * FROM training_program WHERE id = :id")
    fun findProgramById(id: Long): TrainingProgram?

    @Insert
    fun insertProgram(program: TrainingProgram): Long

    @Update
    fun updateProgram(program: TrainingProgram)

    /** Keep every derived history/statistics row visually tied to its program. */
    @Query(
        "UPDATE workout SET programName = :programName, iconOverride = :icon " +
            "WHERE programUuid = :programUuid"
    )
    fun updateProgramWorkoutIdentity(
        programUuid: String,
        programName: String,
        icon: String?,
    )

    @Delete
    fun deleteProgram(program: TrainingProgram)

    // --- Days ---

    @Query("SELECT * FROM program_day WHERE programId = :programId ORDER BY orderIndex ASC")
    fun getDaysOfProgram(programId: Long): List<ProgramDay>

    @Query("SELECT * FROM program_day WHERE programId = :programId ORDER BY orderIndex ASC")
    fun getDaysOfProgramFlow(programId: Long): Flow<List<ProgramDay>>

    @Query("SELECT COUNT(*) FROM program_day WHERE programId = :programId")
    fun countDaysOfProgram(programId: Long): Int

    @Query("SELECT * FROM program_day WHERE id = :id")
    fun findDayById(id: Long): ProgramDay?

    @Insert
    fun insertDay(day: ProgramDay): Long

    @Update
    fun updateDay(day: ProgramDay)

    @Update
    fun updateDays(days: List<ProgramDay>)

    @Delete
    fun deleteDay(day: ProgramDay)

    // --- Exercises in days ---

    @Query("SELECT * FROM program_exercise WHERE dayId = :dayId ORDER BY orderIndex ASC")
    fun getExercisesOfDay(dayId: Long): List<ProgramExercise>

    @Query("SELECT * FROM program_exercise WHERE dayId = :dayId ORDER BY orderIndex ASC")
    fun getExercisesOfDayFlow(dayId: Long): Flow<List<ProgramExercise>>

    @Query("SELECT * FROM program_exercise WHERE id = :id")
    fun findProgramExerciseById(id: Long): ProgramExercise?

    @Insert
    fun insertProgramExercise(programExercise: ProgramExercise): Long

    @Update
    fun updateProgramExercise(programExercise: ProgramExercise)

    @Update
    fun updateProgramExercises(programExercises: List<ProgramExercise>)

    @Delete
    fun deleteProgramExercise(programExercise: ProgramExercise)

    // --- Planned sets ---

    @Query("SELECT * FROM program_set WHERE programExerciseId = :programExerciseId ORDER BY orderIndex ASC")
    fun getSetsOfProgramExercise(programExerciseId: Long): List<ProgramSet>

    @Query("SELECT * FROM program_set WHERE id = :id")
    fun findSetById(id: Long): ProgramSet?

    @Insert
    fun insertSet(set: ProgramSet): Long

    @Update
    fun updateSet(set: ProgramSet)

    @Delete
    fun deleteSet(set: ProgramSet)

    @Query("DELETE FROM program_set WHERE programExerciseId = :programExerciseId")
    fun deleteSetsOfProgramExercise(programExerciseId: Long)

    @Transaction
    fun replaceSetsOfProgramExercise(programExerciseId: Long, sets: List<ProgramSet>) {
        deleteSetsOfProgramExercise(programExerciseId)
        sets.forEachIndexed { index, set ->
            insertSet(set.copy(id = 0, programExerciseId = programExerciseId, orderIndex = index))
        }
    }

    // --- Completions ---

    @Query("SELECT * FROM program_completion WHERE programExerciseId = :programExerciseId AND occurrenceDate = :occurrenceDate")
    fun findCompletion(programExerciseId: Long, occurrenceDate: Long): ProgramCompletion?

    @Query("SELECT * FROM program_completion WHERE programExerciseId = :programExerciseId ORDER BY occurrenceDate ASC")
    fun getCompletionsOfProgramExercise(programExerciseId: Long): List<ProgramCompletion>

    @Query(
        "SELECT program_completion.* FROM program_completion " +
            "INNER JOIN program_exercise ON program_completion.programExerciseId = program_exercise.id " +
            "INNER JOIN program_day ON program_exercise.dayId = program_day.id " +
            "WHERE program_day.programId = :programId"
    )
    fun getCompletionsOfProgram(programId: Long): List<ProgramCompletion>

    @Query(
        "SELECT program_completion.* FROM program_completion " +
            "INNER JOIN program_exercise ON program_completion.programExerciseId = program_exercise.id " +
            "WHERE program_exercise.dayId = :dayId AND program_completion.occurrenceDate = :occurrenceDate"
    )
    fun getCompletionsOfDayOccurrence(dayId: Long, occurrenceDate: Long): List<ProgramCompletion>

    /**
     * Reactive counterpart used by the program checklist. Guided sessions write
     * completions while the detail destination is underneath them in the back
     * stack, so a one-shot query cached by Compose would otherwise remain stale
     * after the user returns.
     */
    @Query(
        "SELECT program_completion.* FROM program_completion " +
            "INNER JOIN program_exercise ON program_completion.programExerciseId = program_exercise.id " +
            "WHERE program_exercise.dayId = :dayId AND program_completion.occurrenceDate = :occurrenceDate"
    )
    fun getCompletionsOfDayOccurrenceFlow(
        dayId: Long,
        occurrenceDate: Long,
    ): Flow<List<ProgramCompletion>>

    @Insert
    fun insertCompletion(completion: ProgramCompletion): Long

    @Update
    fun updateCompletion(completion: ProgramCompletion)

    @Delete
    fun deleteCompletion(completion: ProgramCompletion)

    /**
     * Removes only this program occurrence and its performed-set snapshot.
     *
     * A linked [Workout] is intentionally not touched: `workoutId` is merely a
     * reference from the completion, so undoing a program check-off must not erase
     * a real recorded workout (or remove it from workout history/statistics).
     */
    @Transaction
    fun deleteCompletionWithLogs(completion: ProgramCompletion) {
        deleteLogsOfCompletion(completion.id)
        deleteCompletion(completion)
    }

    // --- Set logs ---

    @Query("SELECT * FROM set_log WHERE completionId = :completionId ORDER BY orderIndex ASC")
    fun getLogsOfCompletion(completionId: Long): List<SetLog>

    @Insert
    fun insertSetLog(log: SetLog): Long

    @Query("DELETE FROM set_log WHERE completionId = :completionId")
    fun deleteLogsOfCompletion(completionId: Long)

    // --- Derived workout for a concrete day occurrence ---

    @Query(
        "SELECT * FROM program_occurrence_summary " +
            "WHERE dayId = :dayId AND occurrenceDate = :occurrenceDate"
    )
    fun findOccurrenceSummary(dayId: Long, occurrenceDate: Long): ProgramOccurrenceSummary?

    @Query(
        "SELECT DISTINCT program_exercise.dayId AS dayId, " +
            "program_completion.occurrenceDate AS occurrenceDate " +
            "FROM program_completion " +
            "INNER JOIN program_exercise " +
            "ON program_exercise.id = program_completion.programExerciseId " +
            "WHERE program_completion.status = 1"
    )
    fun getCompletedOccurrenceKeys(): List<ProgramOccurrenceKey>

    @Insert
    fun insertOccurrenceSummary(summary: ProgramOccurrenceSummary)

    @Update
    fun updateOccurrenceSummary(summary: ProgramOccurrenceSummary)

    @Delete
    fun deleteOccurrenceSummary(summary: ProgramOccurrenceSummary)
}
