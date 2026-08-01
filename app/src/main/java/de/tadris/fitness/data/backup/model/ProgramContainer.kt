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
package de.tadris.fitness.data.backup.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import de.tadris.fitness.data.ProgramCompletion
import de.tadris.fitness.data.ProgramTask
import de.tadris.fitness.data.TrainingProgram
import de.tadris.fitness.data.WorkoutType

const val PROGRAM_CONTAINER_TYPE = "fitregimen-program"
const val PROGRAM_CONTAINER_FORMAT_VERSION = 1

/**
 * A self-contained, shareable representation of one [TrainingProgram]: its tasks, plus
 * any *custom* [WorkoutType]s the tasks reference (built-in types don't need bundling,
 * every install already has them). Used both for the standalone single-program
 * export/import ([completions] empty) and, nested inside a full device backup, where
 * [completions] carries the program's execution history too.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ProgramContainer(
    val type: String = PROGRAM_CONTAINER_TYPE,
    val formatVersion: Int = PROGRAM_CONTAINER_FORMAT_VERSION,
    val program: TrainingProgram,
    val tasks: List<ProgramTask>,
    val customWorkoutTypes: List<WorkoutType> = emptyList(),
    val completions: List<ProgramCompletion> = emptyList(),
)
