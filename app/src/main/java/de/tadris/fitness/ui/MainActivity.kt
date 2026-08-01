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

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.RecordingType
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.data.WorkoutTypeManager
import de.tadris.fitness.ui.components.WorkoutTypePickerDialog
import de.tadris.fitness.ui.map.MapManager
import de.tadris.fitness.ui.navigation.AppNavigation
import de.tadris.fitness.ui.navigation.Route
import de.tadris.fitness.ui.record.MapControllerHolder
import de.tadris.fitness.ui.record.RecordingConstants
import de.tadris.fitness.ui.theme.FitoTrackTheme
import de.tadris.fitness.util.FitoTrackThemes
import de.tadris.fitness.util.osm.OAuthAuthentication

class MainActivity : FitoTrackActivity() {

    override fun getActivityThemeRes(): Int = R.style.AppThemeNoActionbar

    companion object {
        const val EXTRA_ROUTE = "extra_route"
        const val ROUTE_SETTINGS = "settings"
        const val ROUTE_RECORDING_SETTINGS = "recording_settings"
        const val ROUTE_MANAGE_INTERVAL_SETS = "manage_interval_sets"
    }

    private val initializer: AppInitializer by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        FitoTrackThemes(this).updateDarkModeSetting()
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MapManager.initMapProvider(this)

        splashScreen.setKeepOnScreenCondition { initializer.state.value is InitState.Loading }
        initializer.start()

        val isNewLaunch = savedInstanceState == null

        setContent {
            FitoTrackTheme {
                val state by initializer.state.collectAsStateWithLifecycle()

                when (val s = state) {
                    is InitState.Ready -> {
                        MainContent(s, isNewLaunch)
                    }
                    else -> MigrationScreen(
                        state = s,
                        onErrorDismiss = { finish() },
                    )
                }
            }
        }
    }

    @Composable
    private fun MainContent(state: InitState.Ready, isNewLaunch: Boolean) {
        var showWorkoutTypeDialog by remember { mutableStateOf(false) }

        val navController = rememberNavController()

        val navigateToRecord = remember<(WorkoutType, Boolean) -> Unit>(navController) {
            { workoutType, isLaunchAction ->
                val route =
                    if (RecordingType.findById(workoutType.recordingType) == RecordingType.GPS) {
                        Route.RecordGpsWorkout(workoutType.id, isLaunchAction)
                    } else {
                        Route.RecordIndoorWorkout(workoutType.id, isLaunchAction)
                    }
                navController.navigate(route)
            }
        }

        // Handle running workout detected at startup (e.g. app killed while recording)
        val runningWorkout = state.runningWorkout
        if (runningWorkout != null && isNewLaunch) {
            LaunchedEffect(Unit) {
                val workoutType = runningWorkout.getWorkoutType(this@MainActivity)
                navigateToRecord(workoutType, false)
            }
        } else if (state.runningTrainingSessionId != null && isNewLaunch) {
            LaunchedEffect(state.runningTrainingSessionId) {
                navController.navigate(Route.GuidedWorkout(state.runningTrainingSessionId)) {
                    launchSingleTop = true
                }
            }
        }

        // Handle intents from notification tap or shortcuts
        // Only on fresh launch — intent persists across config changes (e.g. rotation)
        LaunchedEffect(Unit) {
            if (!isNewLaunch) return@LaunchedEffect
            val action = intent.action
            val workoutTypeId = intent.getStringExtra(RecordingConstants.WORKOUT_TYPE_EXTRA)
            if ((action == RecordingConstants.LAUNCH_ACTION || action == RecordingConstants.RESUME_ACTION) && workoutTypeId != null) {
                val workoutType = WorkoutTypeManager.getInstance()
                    .getWorkoutTypeById(this@MainActivity, workoutTypeId)
                navigateToRecord(workoutType, action == RecordingConstants.LAUNCH_ACTION)
            }

            when (intent.getStringExtra(EXTRA_ROUTE)) {
                ROUTE_SETTINGS -> navController.navigate(Route.Settings)
                ROUTE_RECORDING_SETTINGS -> {
                    navController.navigate(Route.Settings)
                    navController.navigate(Route.RecordingSettings)
                }

                ROUTE_MANAGE_INTERVAL_SETS -> {
                    navController.navigate(Route.Settings)
                    navController.navigate(Route.RecordingSettings)
                    navController.navigate(Route.ManageIntervalSets)
                }
            }
        }

        AppNavigation(
            navController = navController,
            onRecord = { showWorkoutTypeDialog = true },
            onRecordLast = { workoutType -> navigateToRecord(workoutType, true) },
        )

        if (showWorkoutTypeDialog) {
            val types = remember { WorkoutTypeManager.getInstance().getAllTypesSorted(this) }
            WorkoutTypePickerDialog(
                types = types,
                onDismiss = { showWorkoutTypeDialog = false },
                onSelect = { workoutType ->
                    showWorkoutTypeDialog = false
                    navigateToRecord(workoutType, true)
                },
                onAddType = {
                    showWorkoutTypeDialog = false
                    navController.navigate(Route.EditWorkoutType(""))
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val instance = Instance.getInstance(this)
        if (instance.userPreferences.themeSetting == "0") {
            instance.themes.updateDarkModeSetting()
        }
        OAuthAuthentication.resetCustomTabState(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val instance = Instance.getInstance(this)
        if (!instance.userPreferences.zoomWithVolumeButtons) {
            return super.onKeyDown(keyCode, event)
        }
        val controller = MapControllerHolder.controller
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && controller != null) {
            controller.zoomIn()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && controller != null) {
            controller.zoomOut()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
