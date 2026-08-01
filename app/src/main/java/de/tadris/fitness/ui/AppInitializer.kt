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

package de.tadris.fitness.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.tadris.fitness.BuildConfig
import de.tadris.fitness.Instance
import de.tadris.fitness.data.Workout
import de.tadris.fitness.data.ProgramOccurrenceHistoryRepository
import de.tadris.fitness.data.migration.Migration
import de.tadris.fitness.data.migration.Migration15DistancePeriod
import de.tadris.fitness.data.migration.Migration16Energy
import de.tadris.fitness.data.migration.Migration16PausesRepetitions
import de.tadris.fitness.data.migration.Migration16RunningWorkouts
import de.tadris.fitness.data.migration.MigrationCleanData
import de.tadris.fitness.recording.RecordingState
import de.tadris.fitness.recording.event.RecordingStateChangeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface InitState {
    data object Loading : InitState
    data class Migrating(val progress: Int) : InitState
    data class Ready(
        val runningWorkout: Workout?,
        val runningTrainingSessionId: Long?,
    ) : InitState
    data class Error(val exception: Exception) : InitState
}

class AppInitializer(application: Application) : AndroidViewModel(application), Migration.MigrationListener {

    private val _state = MutableStateFlow<InitState>(InitState.Loading)
    val state: StateFlow<InitState> = _state

    private var started = false

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch { runInit() }
    }

    private suspend fun runInit() {
        try {
            val context = getApplication<Application>()
            val instance = withContext(Dispatchers.IO) { Instance.getInstance(context) }
            instance.themes.updateDarkModeSetting()

            val preferences = instance.userPreferences
            if (preferences.lastVersionCode < BuildConfig.VERSION_CODE) {
                runMigrations(instance)
            }

            withContext(Dispatchers.IO) {
                if (preferences.programWorkoutProjectionVersion < PROGRAM_WORKOUT_PROJECTION_VERSION) {
                    ProgramOccurrenceHistoryRepository(
                        db = instance.db,
                        userWeightKg = { preferences.userWeight.toDouble() },
                        profileSnapshot = {
                            instance.fitnessProfileRepository.captureActiveSnapshot()
                        },
                    ).rebuildAll()
                    preferences.programWorkoutProjectionVersion = PROGRAM_WORKOUT_PROJECTION_VERSION
                }
                preferences.updateLastVersionCode()
                instance.shortcuts.init()
            }

            val (runningWorkout, runningTrainingSessionId) = withContext(Dispatchers.IO) {
                val workout = instance.db.workoutDao().findRunningWorkout()
                val recordingState = RecordingStateChangeEvent.getCurrent().newState
                val activeWorkout = if (workout != null && recordingState != RecordingState.STOPPED) {
                    workout
                } else {
                    null
                }
                activeWorkout to instance.db.trainingSessionDao().getRunningSessions().firstOrNull()?.id
            }

            _state.value = InitState.Ready(runningWorkout, runningTrainingSessionId)
        } catch (e: Exception) {
            _state.value = InitState.Error(e)
        }
    }

    private suspend fun runMigrations(instance: Instance) {
        val context = getApplication<Application>()
        val preferences = instance.userPreferences
        val migrations = buildList {
            if (preferences.lastVersionCode < 1300) add(MigrationCleanData(context, this@AppInitializer))
            if (preferences.lastVersionCode < 1500) add(Migration15DistancePeriod(context, this@AppInitializer))
            if (preferences.lastVersionCode < 1600) add(Migration16PausesRepetitions(context, this@AppInitializer))
            if (preferences.lastVersionCode < 1601) add(Migration16Energy(context, this@AppInitializer))
            if (preferences.lastVersionCode < 1602) add(Migration16RunningWorkouts(context, this@AppInitializer))
        }
        _state.value = InitState.Migrating(0)
        withContext(Dispatchers.IO) {
            for (migration in migrations) {
                Log.i("Migration", "Running migration ${migration.javaClass.simpleName}")
                migration.migrate()
            }
        }
    }

    override fun onProgressUpdate(progress: Int) {
        _state.value = InitState.Migrating(progress)
    }

    private companion object {
        const val PROGRAM_WORKOUT_PROJECTION_VERSION = 2
    }
}
