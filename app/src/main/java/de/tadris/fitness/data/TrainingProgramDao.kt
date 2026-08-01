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
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingProgramDao {

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

    @Delete
    fun deleteProgram(program: TrainingProgram)

    @Query("SELECT * FROM program_task WHERE programId = :programId ORDER BY dayIndex ASC, orderIndex ASC")
    fun getTasksOfProgram(programId: Long): List<ProgramTask>

    @Query("SELECT * FROM program_task WHERE programId = :programId ORDER BY dayIndex ASC, orderIndex ASC")
    fun getTasksOfProgramFlow(programId: Long): Flow<List<ProgramTask>>

    @Query("SELECT * FROM program_task WHERE id = :id")
    fun findTaskById(id: Long): ProgramTask?

    @Insert
    fun insertTask(task: ProgramTask): Long

    @Update
    fun updateTask(task: ProgramTask)

    @Delete
    fun deleteTask(task: ProgramTask)

    @Query(
        "SELECT program_completion.* FROM program_completion " +
            "INNER JOIN program_task ON program_completion.programTaskId = program_task.id " +
            "WHERE program_task.programId = :programId"
    )
    fun getCompletionsOfProgramFlow(programId: Long): Flow<List<ProgramCompletion>>

    @Query(
        "SELECT program_completion.* FROM program_completion " +
            "INNER JOIN program_task ON program_completion.programTaskId = program_task.id " +
            "WHERE program_task.programId = :programId"
    )
    fun getCompletionsOfProgram(programId: Long): List<ProgramCompletion>

    @Query("SELECT * FROM program_completion WHERE programTaskId = :programTaskId AND occurrenceDate = :occurrenceDate")
    fun findCompletion(programTaskId: Long, occurrenceDate: Long): ProgramCompletion?

    @Insert
    fun insertCompletion(completion: ProgramCompletion): Long

    @Update
    fun updateCompletion(completion: ProgramCompletion)

    @Delete
    fun deleteCompletion(completion: ProgramCompletion)
}
