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

package de.tadris.fitness.ui.navigation

import android.content.Intent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.tadris.fitness.Instance
import de.tadris.fitness.data.RecordingType
import de.tadris.fitness.data.WorkoutPause
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.recording.RecordingStateHolder
import de.tadris.fitness.ui.ImportHandler
import de.tadris.fitness.ui.ImportViewModel
import de.tadris.fitness.data.WorkoutTypeManager
import de.tadris.fitness.ui.dashboard.DashboardScreen
import de.tadris.fitness.ui.programs.EditTrainingProgramScreen
import de.tadris.fitness.ui.programs.TrainingProgramDetailScreen
import de.tadris.fitness.ui.programs.TrainingProgramsScreen
import de.tadris.fitness.ui.record.GpsRecordingRoute
import de.tadris.fitness.ui.record.IndoorRecordingRoute
import de.tadris.fitness.ui.settings.AboutScreen
import de.tadris.fitness.ui.settings.BackupSettingsScreen
import de.tadris.fitness.ui.settings.ConfigureExportTargetsScreen
import de.tadris.fitness.ui.settings.InterfaceSettingsScreen
import de.tadris.fitness.ui.settings.RecordingSettingsScreen
import de.tadris.fitness.ui.settings.SettingsScreen
import de.tadris.fitness.ui.settings.VoiceAnnouncementsSettingsScreen
import de.tadris.fitness.ui.settings.debug.DebugSettingsScreen
import de.tadris.fitness.ui.settings.interval.EditIntervalSetScreen
import de.tadris.fitness.ui.settings.interval.ManageIntervalSetsScreen
import de.tadris.fitness.ui.settings.workout_type.EditWorkoutTypeScreen
import de.tadris.fitness.ui.settings.workout_type.ManageWorkoutTypesScreen
import de.tadris.fitness.ui.statistics.StatisticsActivity
import de.tadris.fitness.ui.workout.ShareWorkoutScreen
import de.tadris.fitness.ui.workout.enter.EnterWorkoutScreen
import de.tadris.fitness.ui.workout.show.DiagramType
import de.tadris.fitness.ui.workout.show.EditWorkoutStartEndScreen
import de.tadris.fitness.ui.workout.show.ShowWorkoutFullscreenMapScreen
import de.tadris.fitness.ui.workout.show.ShowWorkoutMapDiagramScreen
import de.tadris.fitness.ui.workout.show.ShowWorkoutScreen
import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable
    data object Dashboard : Route

    @Serializable
    data class ShowWorkout(val id: Long) : Route

    @Serializable
    data class ShowWorkoutFullscreenMap(val id: Long) : Route

    @Serializable
    data class ShowWorkoutMapDiagram(val id: Long, val diagramType: DiagramType) : Route

    @Serializable
    data class EditWorkoutStartEnd(val id: Long) : Route

    /** id == 0L means create new workout, non-zero means edit existing */
    @Serializable
    data class EnterWorkout(val id: Long = 0L) : Route

    @Serializable
    data class ShareWorkout(val id: Long) : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object InterfaceSettings : Route

    @Serializable
    data object RecordingSettings : Route

    @Serializable
    data object VoiceAnnouncementsSettings : Route

    @Serializable
    data object BackupSettings : Route

    @Serializable
    data class ConfigureExportTargets(val sourceId: String) : Route

    @Serializable
    data object About : Route

    @Serializable
    data object ManageIntervalSets : Route

    @Serializable
    data class EditIntervalSet(val setId: Long) : Route

    @Serializable
    data object DebugSettings : Route

    @Serializable
    data object ManageWorkoutTypes : Route

    /** typeId == "" means create new workout type */
    @Serializable
    data class EditWorkoutType(val typeId: String = "") : Route

    @Serializable
    data class RecordGpsWorkout(val workoutTypeId: String, val isLaunchAction: Boolean) : Route

    @Serializable
    data class RecordIndoorWorkout(val workoutTypeId: String, val isLaunchAction: Boolean) : Route

    @Serializable
    data object TrainingPrograms : Route

    @Serializable
    data class EditTrainingProgram(val id: Long) : Route

    @Serializable
    data class TrainingProgramDetail(val id: Long) : Route
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    onRecord: () -> Unit,
    onRecordLast: (WorkoutType) -> Unit,
) {
    val context = LocalContext.current
    val importVm: ImportViewModel = viewModel()

    ImportHandler(vm = importVm)

    NavHost(
        navController = navController,
        startDestination = Route.Dashboard,
        enterTransition = { slideInHorizontally { it } },
        exitTransition = { slideOutHorizontally { -it } },
        popEnterTransition = { slideInHorizontally { -it } },
        popExitTransition = { slideOutHorizontally { it } },
    ) {
        composable<Route.Dashboard> {
            DashboardScreen(
                workoutDao = Instance.getInstance(context).db.workoutDao(),
                onWorkoutClick = { workout ->
                    navController.navigate(Route.ShowWorkout(workout.id))
                },
                onDeleteWorkout = { workout ->
                    Thread {
                        Instance.getInstance(context).db.workoutDao().deleteWorkout(workout)
                    }.start()
                },
                onRecord = onRecord,
                onRecordLast = onRecordLast,
                onEnterWorkout = { navController.navigate(Route.EnterWorkout()) },
                onImport = { importVm.requestImport() },
                onSettings = {
                    navController.navigate(Route.Settings)
                },
                onStatistics = {
                    context.startActivity(Intent(context, StatisticsActivity::class.java))
                },
                onPrograms = { navController.navigate(Route.TrainingPrograms) },
            )
        }
        composable<Route.TrainingPrograms> {
            TrainingProgramsScreen(
                onBack = { navController.popBackStack() },
                onOpenProgram = { id -> navController.navigate(Route.TrainingProgramDetail(id)) },
                onProgramCreated = { id -> navController.navigate(Route.EditTrainingProgram(id)) },
            )
        }
        composable<Route.EditTrainingProgram> { backStackEntry ->
            EditTrainingProgramScreen(
                id = backStackEntry.toRoute<Route.EditTrainingProgram>().id,
                onBack = { navController.popBackStack() },
                onProgramDeleted = {
                    navController.popBackStack(Route.TrainingPrograms, inclusive = false)
                },
            )
        }
        composable<Route.TrainingProgramDetail> { backStackEntry ->
            val programId = backStackEntry.toRoute<Route.TrainingProgramDetail>().id
            TrainingProgramDetailScreen(
                id = programId,
                onBack = { navController.popBackStack() },
                onEditProgram = { navController.navigate(Route.EditTrainingProgram(programId)) },
                onStartTask = { task ->
                    val workoutType = WorkoutTypeManager.getInstance().getWorkoutTypeById(context, task.workoutTypeId)
                    val route = if (RecordingType.findById(workoutType.recordingType) == RecordingType.GPS) {
                        Route.RecordGpsWorkout(workoutType.id, false)
                    } else {
                        Route.RecordIndoorWorkout(workoutType.id, false)
                    }
                    navController.navigate(route)
                },
            )
        }
        composable<Route.ShowWorkoutFullscreenMap> { backStackEntry ->
            ShowWorkoutFullscreenMapScreen(
                id = backStackEntry.toRoute<Route.ShowWorkoutFullscreenMap>().id,
                onBack = { navController.popBackStack() },
            )
        }
        composable<Route.ShowWorkoutMapDiagram> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.ShowWorkoutMapDiagram>()
            ShowWorkoutMapDiagramScreen(
                id = route.id,
                diagramType = route.diagramType,
                onBack = { navController.popBackStack() },
            )
        }
        composable<Route.EditWorkoutStartEnd> { backStackEntry ->
            EditWorkoutStartEndScreen(
                id = backStackEntry.toRoute<Route.EditWorkoutStartEnd>().id,
                onBack = { navController.popBackStack() },
                onWorkoutModified = { navController.popBackStack() },
            )
        }
        composable<Route.ShareWorkout> { backStackEntry ->
            ShareWorkoutScreen(
                id = backStackEntry.toRoute<Route.ShareWorkout>().id,
                onBack = { navController.popBackStack() },
            )
        }
        composable<Route.EnterWorkout> { backStackEntry ->
            EnterWorkoutScreen(
                id = backStackEntry.toRoute<Route.EnterWorkout>().id,
                onBack = { navController.popBackStack() },
                onSaved = { workoutId ->
                    navController.navigate(Route.ShowWorkout(workoutId)) {
                        popUpTo(Route.Dashboard)
                    }
                },
                onAddWorkoutType = { navController.navigate(Route.EditWorkoutType("")) },
            )
        }
        composable<Route.Settings> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToInterfaceSettings = {
                    navController.navigate(Route.InterfaceSettings)
                },
                onNavigateToRecordingSettings = {
                    navController.navigate(Route.RecordingSettings)
                },
                onNavigateToBackupSettings = {
                    navController.navigate(Route.BackupSettings)
                },
                onNavigateToWorkoutTypes = {
                    navController.navigate(Route.ManageWorkoutTypes)
                },
                onNavigateToDebugSettings = {
                    navController.navigate(Route.DebugSettings)
                },
                onNavigateToAbout = {
                    navController.navigate(Route.About)
                },
            )
        }
        composable<Route.About> {
            AboutScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<Route.DebugSettings> {
            DebugSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<Route.BackupSettings> {
            BackupSettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToConfigureExportTargets = { sourceId ->
                    navController.navigate(Route.ConfigureExportTargets(sourceId))
                },
            )
        }
        composable<Route.ConfigureExportTargets> { backStackEntry ->
            ConfigureExportTargetsScreen(
                sourceId = backStackEntry.toRoute<Route.ConfigureExportTargets>().sourceId,
                onBack = { navController.popBackStack() },
            )
        }
        composable<Route.InterfaceSettings> {
            InterfaceSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<Route.RecordingSettings> {
            RecordingSettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToVoiceAnnouncements = {
                    navController.navigate(Route.VoiceAnnouncementsSettings)
                },
                onNavigateToIntervalSets = {
                    navController.navigate(Route.ManageIntervalSets)
                },
            )
        }
        composable<Route.ManageIntervalSets> {
            ManageIntervalSetsScreen(
                onBack = { navController.popBackStack() },
                onEditSet = { setId -> navController.navigate(Route.EditIntervalSet(setId)) },
            )
        }
        composable<Route.EditIntervalSet> { backStackEntry ->
            EditIntervalSetScreen(
                setId = backStackEntry.toRoute<Route.EditIntervalSet>().setId,
                onBack = { navController.popBackStack() },
            )
        }
        composable<Route.VoiceAnnouncementsSettings> {
            VoiceAnnouncementsSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<Route.ManageWorkoutTypes> {
            ManageWorkoutTypesScreen(
                onBack = { navController.popBackStack() },
                onEditType = { typeId ->
                    navController.navigate(Route.EditWorkoutType(typeId ?: ""))
                },
            )
        }
        composable<Route.EditWorkoutType> { backStackEntry ->
            EditWorkoutTypeScreen(
                typeId = backStackEntry.toRoute<Route.EditWorkoutType>().typeId,
                onBack = { navController.popBackStack() },
            )
        }
        composable<Route.RecordGpsWorkout> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.RecordGpsWorkout>()
            GpsRecordingRoute(
                workoutTypeId = route.workoutTypeId,
                isLaunchAction = route.isLaunchAction,
                onFinish = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Route.RecordingSettings) },
                onNavigateToIntervalSets = { navController.navigate(Route.ManageIntervalSets) },
            )
        }
        composable<Route.RecordIndoorWorkout> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.RecordIndoorWorkout>()
            IndoorRecordingRoute(
                workoutTypeId = route.workoutTypeId,
                isLaunchAction = route.isLaunchAction,
                onFinish = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Route.RecordingSettings) },
                onNavigateToIntervalSets = { navController.navigate(Route.ManageIntervalSets) },
            )
        }
        composable<Route.ShowWorkout> { backStackEntry ->
            ShowWorkoutScreen(
                id = backStackEntry.toRoute<Route.ShowWorkout>().id,
                onBack = { navController.popBackStack() },
                onShowFullscreenMap = { workoutId ->
                    navController.navigate(Route.ShowWorkoutFullscreenMap(workoutId))
                },
                onShowDiagram = { workoutId, diagramType ->
                    navController.navigate(Route.ShowWorkoutMapDiagram(workoutId, diagramType))
                },
                onEditStartEnd = { workoutId ->
                    navController.navigate(Route.EditWorkoutStartEnd(workoutId))
                },
                onEditWorkout = { workoutId ->
                    navController.navigate(Route.EnterWorkout(workoutId))
                },
                onShareWorkout = { workoutId ->
                    navController.navigate(Route.ShareWorkout(workoutId))
                },
                onResumeWorkout = { workout ->
                    val dao = Instance.getInstance(context).db.workoutDao()
                    dao.insertPause(
                        WorkoutPause(
                            workout.id,
                            0,
                            workout.end,
                            System.currentTimeMillis(),
                            true
                        )
                    )
                    dao.updateWorkout(workout.copy(end = RecordingStateHolder.RUNNING_WORKOUT_END_TIME))
                    val workoutType = workout.getWorkoutType(context)
                    val route = if (RecordingType.findById(workoutType.recordingType) == RecordingType.GPS) {
                        Route.RecordGpsWorkout(workoutType.id, false)
                    } else {
                        Route.RecordIndoorWorkout(workoutType.id, false)
                    }
                    navController.navigate(route)
                },
            )
        }
    }
}
