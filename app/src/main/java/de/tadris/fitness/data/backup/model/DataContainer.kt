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

package de.tadris.fitness.data.backup.model

import de.tadris.fitness.data.WorkoutType

data class DataContainer(
    val intervalSets: List<IntervalSetContainer>,
    val workoutTypes: List<WorkoutType>,
    // Added in FitRegimen (backup format v5). Defaults to empty so older backups still deserialize.
    val programs: List<ProgramContainer> = emptyList(),
    // Added in backup format v6. Guided history is separate from mutable program templates.
    val sessions: List<TrainingSessionExport> = emptyList(),
    // Added in backup format v7. Profiles are never included in standalone program shares.
    val profiles: List<FitnessProfileExport> = emptyList(),
    // Full backup only. Includes hidden and currently unreferenced catalog entries.
    val exerciseCatalog: List<ExerciseCatalogExport> = emptyList(),
    // Full backup only. Null means the source backup predates portable settings.
    val preferences: PortablePreferencesExport? = null,
    // Added in backup format v9. Private profile links exist only in this full-backup block.
    val nutritionPlans: List<NutritionPlanContainer> = emptyList(),
)
