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

package de.tadris.fitness.data.preferences

import android.content.Context
import de.tadris.fitness.Instance

/**
 * @property weight Weight in kg
 * @property stepLength Step length in m
 */
data class UserMeasurements(
    val weight: Int,
    val stepLength: Float,
) {

    companion object {

        val DEFAULT = UserMeasurements(
            UserPreferences.DEFAULT_USER_WEIGHT_KG,
            UserPreferences.DEFAULT_STEP_LENGTH_METERS,
        ) // e.g. for testing

        @JvmStatic
        fun from(context: Context) = Instance.getInstance(context).userPreferences.measurements

    }

}
