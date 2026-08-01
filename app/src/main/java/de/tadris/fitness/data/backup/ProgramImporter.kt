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
import com.fasterxml.jackson.core.StreamReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import de.tadris.fitness.Instance
import de.tadris.fitness.data.backup.model.PROGRAM_CONTAINER_TYPE
import de.tadris.fitness.data.backup.model.ProgramContainer
import java.io.IOException
import java.io.InputStream

/** Reads a standalone program JSON file (as written by [ProgramExporter]) and imports it as a new program. */
object ProgramImporter {

    private val mapper = jsonMapper {
        addModule(kotlinModule())
        configure(StreamReadFeature.IGNORE_UNDEFINED, true)
        configure(StreamReadFeature.AUTO_CLOSE_SOURCE, false)
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    class InvalidProgramFileException(message: String) : IOException(message)

    fun read(inputStream: InputStream): ProgramContainer {
        val container = mapper.readValue(inputStream, ProgramContainer::class.java)
        if (container.type != PROGRAM_CONTAINER_TYPE) {
            throw InvalidProgramFileException("Not a FitRegimen program file")
        }
        return container
    }

    /** Inserts [container] as a brand-new program (fresh ids), returning the new program id. */
    fun import(context: Context, container: ProgramContainer): Long {
        val db = Instance.getInstance(context).db
        val dao = db.trainingProgramDao()

        for (type in container.customWorkoutTypes) {
            if (db.workoutTypeDao().findById(type.id) == null) {
                db.workoutTypeDao().insert(type)
            }
        }

        val newProgramId = dao.insertProgram(container.program.copy(id = 0))
        for (task in container.tasks) {
            dao.insertTask(task.copy(id = 0, programId = newProgramId))
        }
        return newProgramId
    }
}
