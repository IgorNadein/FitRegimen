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
package de.tadris.fitness.data.backup

import android.content.Context
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.json.JsonMapper
import de.tadris.fitness.Instance
import de.tadris.fitness.data.TrainingProgram
import de.tadris.fitness.data.backup.model.ProgramContainer
import java.io.OutputStream

/** Builds and writes a standalone, shareable JSON file for a single [TrainingProgram]. */
object ProgramExporter {

    private val mapper = JsonMapper.builder()
        .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
        .build()

    fun buildContainer(context: Context, program: TrainingProgram): ProgramContainer {
        val db = Instance.getInstance(context).db
        val tasks = db.trainingProgramDao().getTasksOfProgram(program.id)
        val usedTypeIds = tasks.map { it.workoutTypeId }.toSet()
        // Only bundle *custom* types (the DB table only ever holds custom ones - built-in
        // presets are constructed in-memory and already exist on every install).
        val customTypes = db.workoutTypeDao().findAll().filter { it.id in usedTypeIds }
        return ProgramContainer(program = program, tasks = tasks, customWorkoutTypes = customTypes)
    }

    fun write(container: ProgramContainer, outputStream: OutputStream) {
        mapper.writeValue(outputStream, container)
    }
}
