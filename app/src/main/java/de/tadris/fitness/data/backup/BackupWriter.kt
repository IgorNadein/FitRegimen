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

package de.tadris.fitness.data.backup

import android.content.Context
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.json.JsonMapper
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.WorkoutData
import de.tadris.fitness.data.backup.model.DataContainer
import de.tadris.fitness.data.backup.model.IntervalSetContainer
import de.tadris.fitness.data.backup.model.ProgramContainer
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupWriter(
    private val context: Context,
    private val output: File,
    private val listener: ExportStatusListener
) {

    companion object {
        internal const val VERSION = 5
        internal const val FILE_VERSION = "version.txt"
        internal const val FILE_DATA = "data.json"
        internal const val FILE_WORKOUT = "workout_%d.json"
    }

    private val db = Instance.getInstance(context).db
    private val fileStream = FileOutputStream(output)
    private val zipStream = ZipOutputStream(fileStream)
    private val mapper = JsonMapper.builder()
        .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
        .build()

    fun write(){
        listener.onStatusChanged(0, context.getString(R.string.initialising))
        writeVersionFile()
        listener.onStatusChanged(0, context.getString(R.string.customWorkoutTypesTitle))
        writeExtraData()
        listener.onStatusChanged(0, context.getString(R.string.workouts))
        writeWorkouts()
        listener.onStatusChanged(100, context.getString(R.string.finished))
        zipStream.close()
        fileStream.close()
    }

    private fun writeVersionFile() {
        zipStream.putNextEntry(ZipEntry(FILE_VERSION))
        zipStream.write(VERSION.toString().toByteArray())
        zipStream.closeEntry()
    }

    private fun writeExtraData() {
        val intervalSets = db.intervalDao().allSets.map {
            IntervalSetContainer(it, db.intervalDao().getAllIntervalsOfSet(it.id).toList())
        }
        val programs = db.trainingProgramDao().getPrograms().map { program ->
            val tasks = db.trainingProgramDao().getTasksOfProgram(program.id)
            val usedTypeIds = tasks.map { it.workoutTypeId }.toSet()
            val customTypes = db.workoutTypeDao().findAll().filter { it.id in usedTypeIds }
            val completions = db.trainingProgramDao().getCompletionsOfProgram(program.id)
            ProgramContainer(
                program = program,
                tasks = tasks,
                customWorkoutTypes = customTypes,
                completions = completions,
            )
        }
        val container = DataContainer(
            intervalSets,
            db.workoutTypeDao().findAll().toList(),
            programs,
        )
        zipStream.putNextEntry(ZipEntry(FILE_DATA))
        mapper.writeValue(zipStream, container)
        zipStream.closeEntry()
    }

    private fun writeWorkouts() {
        val dao = db.workoutDao()
        val workouts = dao.getWorkouts()
        workouts.forEachIndexed { index, workout ->
            zipStream.putNextEntry(ZipEntry(FILE_WORKOUT.format(index)))
            val data = WorkoutData.fromWorkout(dao, workout)
            mapper.writeValue(zipStream, data)
            zipStream.closeEntry()
            listener.onStatusChanged(100 * index / workouts.size, context.getString(R.string.workouts))
        }
    }

    fun interface ExportStatusListener {

        fun onStatusChanged(progress: Int, action: String?)

    }

}