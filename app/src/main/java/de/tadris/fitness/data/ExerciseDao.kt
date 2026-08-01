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
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercise ORDER BY id ASC")
    fun getAllExercises(): List<Exercise>

    @Query("SELECT * FROM exercise WHERE hidden = 0 ORDER BY name COLLATE NOCASE ASC")
    fun getVisibleExercisesFlow(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercise WHERE hidden = 0 ORDER BY name COLLATE NOCASE ASC")
    fun getVisibleExercises(): List<Exercise>

    @Query("SELECT * FROM exercise WHERE id = :id")
    fun findById(id: Long): Exercise?

    @Query("SELECT * FROM exercise WHERE uuid = :uuid LIMIT 1")
    fun findByUuid(uuid: String): Exercise?

    @Query("SELECT * FROM exercise WHERE name = :name COLLATE NOCASE AND metricKind = :metricKind LIMIT 1")
    fun findByNameAndKind(name: String, metricKind: String): Exercise?

    @Query("SELECT COUNT(*) FROM program_exercise WHERE exerciseId = :exerciseId")
    fun countUsages(exerciseId: Long): Int

    @Insert
    fun insert(exercise: Exercise): Long

    @Update
    fun update(exercise: Exercise)
}
