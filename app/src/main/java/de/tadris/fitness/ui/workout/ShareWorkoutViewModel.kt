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

package de.tadris.fitness.ui.workout

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.tadris.fitness.Instance
import de.tadris.fitness.data.RecordingType
import de.tadris.fitness.data.Workout
import de.tadris.fitness.data.WorkoutData
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.stats.aggregation.WorkoutInformation
import de.tadris.fitness.stats.aggregation.WorkoutInformationManager
import de.tadris.fitness.stats.aggregation.information.Hidden
import de.tadris.fitness.util.DataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class ShareMetricSlot(val label: String, val value: String)

data class ShareWorkoutFormState(
    val workout: Workout,
    val data: WorkoutData,
    val workoutType: WorkoutType,
    val slots: List<ShareMetricSlot>,
    val availableInfo: List<WorkoutInformation>,
)

sealed interface ShareWorkoutUiState {
    data object Loading : ShareWorkoutUiState
    data class Ready(val form: ShareWorkoutFormState) : ShareWorkoutUiState
    data class Error(val message: String) : ShareWorkoutUiState
}

class ShareWorkoutViewModel(
    application: Application,
    private val workoutId: Long,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<ShareWorkoutUiState>(ShareWorkoutUiState.Loading)
    val uiState: StateFlow<ShareWorkoutUiState> = _uiState

    private val informationManager by lazy {
        WorkoutInformationManager(getApplication()).also { it.addInformation(Hidden(getApplication())) }
    }

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = withContext(Dispatchers.IO) {
                try {
                    val instance = Instance.getInstance(getApplication())
                    val dao = instance.db.workoutDao()
                    val workout = dao.getWorkoutById(workoutId)
                        ?: return@withContext ShareWorkoutUiState.Error("Workout not found")
                    val data = WorkoutData.fromWorkout(dao, workout)
                    val workoutType = workout.getDisplayWorkoutType(getApplication())
                    ShareWorkoutUiState.Ready(
                        ShareWorkoutFormState(
                            workout = workout,
                            data = data,
                            workoutType = workoutType,
                            slots = buildSlots(workout),
                            availableInfo = informationManager.getAvailableInformationFor(workout),
                        )
                    )
                } catch (e: Exception) {
                    ShareWorkoutUiState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    private fun buildSlots(workout: Workout): List<ShareMetricSlot> {
        val prefs = Instance.getInstance(getApplication())
            .userPreferences.sharingScreenInformationPreferences
        return (0..2).map { slot ->
            val id = prefs.getIdOfDisplayedInformation(RecordingType.GPS.id, slot)
            val info = informationManager.findById(id) ?: Hidden(getApplication())
            if (info is Hidden) {
                ShareMetricSlot("", "")
            } else {
                ShareMetricSlot(
                    label = getApplication<Application>().getString(info.titleRes),
                    value = info.getFormattedValueFromWorkout(workout),
                )
            }
        }
    }

    fun selectMetric(slot: Int, informationTypeId: String) {
        val current = _uiState.value as? ShareWorkoutUiState.Ready ?: return
        Instance.getInstance(getApplication()).userPreferences.sharingScreenInformationPreferences
            .setIdOfDisplayedInformation(RecordingType.GPS.id, slot, informationTypeId)
        _uiState.value = current.copy(
            form = current.form.copy(slots = buildSlots(current.form.workout))
        )
    }

    fun shareBitmap(bitmap: Bitmap, context: Context) {
        viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    val ts = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toString()
                    val file = DataManager.createSharableFile(context, "fitotrack-workout_$ts.png")
                    FileOutputStream(file).use {
                        bitmap.compress(
                            Bitmap.CompressFormat.PNG,
                            100,
                            it
                        )
                    }
                    DataManager.provide(context, file)
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    setDataAndType(uri, "image/png")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_STREAM, uri)
                }
                context.startActivity(Intent.createChooser(intent, null))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
