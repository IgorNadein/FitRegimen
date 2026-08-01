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

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * A user-defined, finite training program. Its ordered [ProgramDay] rows are the
 * complete timeline, so the day count is also the program duration in days.
 *
 * @property startDate epoch ms of midnight of program day 1. Null means
 * the program is a plan/template the user hasn't started following yet.
 */
@Entity(tableName = "training_program")
@JsonIgnoreProperties(ignoreUnknown = true)
data class TrainingProgram(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String,
    val name: String,
    val description: String? = null,
    val startDate: Long? = null,
    val createdAt: Long,
    /** Logical name of an app-provided icon; null uses the generic program icon. */
    val icon: String? = null,
)
