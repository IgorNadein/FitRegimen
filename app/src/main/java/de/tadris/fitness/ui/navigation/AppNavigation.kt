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
 * Modified in FitRegimen (fork of FitoTrack): training and nutrition routes with
 * the five-destination Material 3 bottom navigation.
 */

package de.tadris.fitness.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.RecordingType
import de.tadris.fitness.data.ProgramScheduling
import de.tadris.fitness.data.SessionSetMetrics
import de.tadris.fitness.data.TrainingSessionRepository
import de.tadris.fitness.data.WorkoutPause
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.data.WorkoutTypeManager
import de.tadris.fitness.data.preferences.UserMeasurements
import de.tadris.fitness.recording.RecordingStateHolder
import de.tadris.fitness.ui.ImportHandler
import de.tadris.fitness.ui.ImportViewModel
import de.tadris.fitness.ui.dashboard.DashboardScreen
import de.tadris.fitness.ui.nutrition.EditNutritionPlanScreen
import de.tadris.fitness.ui.nutrition.NutritionAiPromptScreen
import de.tadris.fitness.ui.nutrition.NutritionDayEditorScreen
import de.tadris.fitness.ui.nutrition.NutritionPlanDetailScreen
import de.tadris.fitness.ui.nutrition.NutritionPlansScreen
import de.tadris.fitness.ui.programs.AiPromptScreen
import de.tadris.fitness.ui.programs.DayEditorScreen
import de.tadris.fitness.ui.programs.EditTrainingProgramScreen
import de.tadris.fitness.ui.programs.ExercisePickerScreen
import de.tadris.fitness.ui.programs.TrainingProgramDetailScreen
import de.tadris.fitness.ui.programs.TrainingProgramsScreen
import de.tadris.fitness.ui.record.GpsRecordingRoute
import de.tadris.fitness.ui.record.IndoorRecordingRoute
import de.tadris.fitness.ui.record.RecordingAutoStopGoal
import de.tadris.fitness.ui.session.GuidedWorkoutRoute
import de.tadris.fitness.ui.settings.AboutScreen
import de.tadris.fitness.ui.settings.BackupSettingsScreen
import de.tadris.fitness.ui.settings.ConfigureExportTargetsScreen
import de.tadris.fitness.ui.settings.FitnessProfilesScreen
import de.tadris.fitness.ui.settings.InterfaceSettingsScreen
import de.tadris.fitness.ui.settings.ManageExercisesScreen
import de.tadris.fitness.ui.settings.PrivacyPolicyScreen
import de.tadris.fitness.ui.settings.RecordingSettingsScreen
import de.tadris.fitness.ui.settings.SettingsScreen
import de.tadris.fitness.ui.settings.VoiceAnnouncementsSettingsScreen
import de.tadris.fitness.ui.settings.debug.DebugSettingsScreen
import de.tadris.fitness.ui.settings.interval.EditIntervalSetScreen
import de.tadris.fitness.ui.settings.interval.ManageIntervalSetsScreen
import de.tadris.fitness.ui.settings.workout_type.EditWorkoutTypeScreen
import de.tadris.fitness.ui.settings.workout_type.ManageWorkoutTypesScreen
import de.tadris.fitness.ui.statistics.StatisticsScreen
import de.tadris.fitness.ui.workout.ShareWorkoutScreen
import de.tadris.fitness.ui.workout.enter.EnterWorkoutScreen
import de.tadris.fitness.ui.workout.show.DiagramType
import de.tadris.fitness.ui.workout.show.EditWorkoutStartEndScreen
import de.tadris.fitness.ui.workout.show.ShowWorkoutFullscreenMapScreen
import de.tadris.fitness.ui.workout.show.ShowWorkoutMapDiagramScreen
import de.tadris.fitness.ui.workout.show.ShowWorkoutScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

sealed interface Route {
    @Serializable
    data object Dashboard : Route

    @Serializable
    data object Statistics : Route

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
    data object FitnessProfiles : Route

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
    data object PrivacyPolicy : Route

    @Serializable
    data object ManageIntervalSets : Route

    @Serializable
    data class EditIntervalSet(val setId: Long) : Route

    @Serializable
    data object DebugSettings : Route

    @Serializable
    data object ManageWorkoutTypes : Route

    @Serializable
    data object ManageExercises : Route

    /** typeId == "" means create new workout type */
    @Serializable
    data class EditWorkoutType(val typeId: String = "") : Route

    @Serializable
    data class RecordGpsWorkout(
        val workoutTypeId: String,
        val isLaunchAction: Boolean,
        val guidedSessionId: Long? = null,
        val guidedSetId: Long? = null,
        val guidedCompletionRule: String? = null,
        val guidedTargetDurationMillis: Long? = null,
        val guidedTargetDistanceMeters: Double? = null,
        val guidedTargetRepetitions: Int? = null,
    ) : Route

    @Serializable
    data class RecordIndoorWorkout(
        val workoutTypeId: String,
        val isLaunchAction: Boolean,
        val guidedSessionId: Long? = null,
        val guidedSetId: Long? = null,
        val guidedCompletionRule: String? = null,
        val guidedTargetDurationMillis: Long? = null,
        val guidedTargetDistanceMeters: Double? = null,
        val guidedTargetRepetitions: Int? = null,
    ) : Route

    @Serializable
    data object TrainingPrograms : Route

    @Serializable
    data object NutritionPlans : Route

    @Serializable
    data class NutritionPlanDetail(val id: Long) : Route

    @Serializable
    data class EditNutritionPlan(val id: Long) : Route

    @Serializable
    data class NutritionDayEditor(val dayId: Long) : Route

    @Serializable
    data object NutritionAiPrompt : Route

    @Serializable
    data class EditTrainingProgram(val id: Long) : Route

    @Serializable
    data class TrainingProgramDetail(val id: Long) : Route

    @Serializable
    data class GuidedWorkout(
        val sessionId: Long,
        /** Existing/recovered sessions are frozen before their first render. */
        val pauseOnOpen: Boolean = true,
    ) : Route

    @Serializable
    data class DayEditor(val dayId: Long) : Route

    @Serializable
    data class ExercisePicker(val dayId: Long) : Route

    @Serializable
    data object AiPrompt : Route
}

@Composable
private fun BottomNavigationLabel(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    onRecord: () -> Unit,
    onRecordLast: (WorkoutType) -> Unit,
) {
    val context = LocalContext.current
    val importVm: ImportViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val database = remember { Instance.getInstance(context).db }
    val sessionRepository = remember(database, context) {
        val instance = Instance.getInstance(context)
        TrainingSessionRepository(
            db = database,
            userWeightKg = { UserMeasurements.from(context).weight.toDouble() },
            profileSnapshot = { instance.fitnessProfileRepository.captureActiveSnapshot() },
        )
    }

    ImportHandler(vm = importVm)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onDashboardTab = currentDestination?.hasRoute(Route.Dashboard::class) == true
    val onProgramsTab = currentDestination?.hasRoute(Route.TrainingPrograms::class) == true
    val onNutritionTab = currentDestination?.hasRoute(Route.NutritionPlans::class) == true
    val onStatisticsTab = currentDestination?.hasRoute(Route.Statistics::class) == true
    val onSettingsTab = currentDestination?.hasRoute(Route.Settings::class) == true
    val showBottomBar = onDashboardTab || onProgramsTab || onNutritionTab ||
        onStatisticsTab || onSettingsTab
    var actionMenuExpanded by rememberSaveable { mutableStateOf(false) }

    fun navigateToTopLevel(route: Route) {
        actionMenuExpanded = false
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateToRecordRoute(
        workoutTypeId: String,
        guidedSessionId: Long? = null,
        guidedSetId: Long? = null,
        guidedCompletionRule: String? = null,
        guidedTargetDurationMillis: Long? = null,
        guidedTargetDistanceMeters: Double? = null,
        guidedTargetRepetitions: Int? = null,
    ) {
        val workoutType = WorkoutTypeManager.getInstance().getWorkoutTypeById(context, workoutTypeId)
        val route = if (RecordingType.findById(workoutType.recordingType) == RecordingType.GPS) {
            Route.RecordGpsWorkout(
                workoutTypeId = workoutType.id,
                isLaunchAction = false,
                guidedSessionId = guidedSessionId,
                guidedSetId = guidedSetId,
                guidedCompletionRule = guidedCompletionRule,
                guidedTargetDurationMillis = guidedTargetDurationMillis,
                guidedTargetDistanceMeters = guidedTargetDistanceMeters,
                guidedTargetRepetitions = guidedTargetRepetitions,
            )
        } else {
            Route.RecordIndoorWorkout(
                workoutTypeId = workoutType.id,
                isLaunchAction = false,
                guidedSessionId = guidedSessionId,
                guidedSetId = guidedSetId,
                guidedCompletionRule = guidedCompletionRule,
                guidedTargetDurationMillis = guidedTargetDurationMillis,
                guidedTargetDistanceMeters = guidedTargetDistanceMeters,
                guidedTargetRepetitions = guidedTargetRepetitions,
            )
        }
        navController.navigate(route)
    }

    fun navigateToGuidedWorkout(dayId: Long, occurrenceDate: Long) {
        scope.launch {
            val launch = withContext(Dispatchers.IO) {
                sessionRepository.findOrStartSessionFromDay(
                    dayId = dayId,
                    occurrenceDate = occurrenceDate,
                    zoneId = ZoneId.systemDefault().id,
                )
            }
            navController.navigate(
                Route.GuidedWorkout(
                    sessionId = launch.sessionId,
                    pauseOnOpen = launch.resumedExistingSession,
                )
            ) {
                launchSingleTop = true
            }
        }
    }

    fun openActiveProgram(preferredProgramUuid: String?) {
        scope.launch {
            val activeProgramId = withContext(Dispatchers.IO) {
                val dao = database.trainingProgramDao()
                val programs = dao.getPrograms()
                val dayCounts = programs.associate { program ->
                    program.id to dao.countDaysOfProgram(program.id)
                }
                val zone = ZoneId.systemDefault()
                val date = LocalDate.now(zone)
                val preferred = preferredProgramUuid?.let { uuid ->
                    programs.firstOrNull { program ->
                        program.uuid == uuid && ProgramScheduling.isActive(
                            program = program,
                            dayCount = dayCounts[program.id] ?: 0,
                            date = date,
                            zone = zone,
                        )
                    }
                }
                (preferred ?: ProgramScheduling.firstActiveProgram(
                    programs = programs,
                    dayCountsByProgramId = dayCounts,
                    date = date,
                    zone = zone,
                ))?.id
            }
            if (activeProgramId != null) {
                navController.navigate(Route.TrainingProgramDetail(activeProgramId)) {
                    launchSingleTop = true
                }
            } else {
                navigateToTopLevel(Route.TrainingPrograms)
            }
        }
    }

    fun saveTrackerResult(
        sessionId: Long?,
        setId: Long?,
        result: de.tadris.fitness.ui.record.RecordedWorkoutResult?,
    ) {
        if (sessionId == null || setId == null || result == null) return
        scope.launch(Dispatchers.IO) {
            sessionRepository.completeTracker(
                sessionId = sessionId,
                setId = setId,
                linkedWorkoutId = result.workoutId,
                metrics = SessionSetMetrics(
                    reps = result.repetitions.takeIf { it > 0 },
                    durationMillis = result.activeDurationMillis.takeIf { it > 0 }
                        ?: result.totalDurationMillis.takeIf { it > 0 },
                    distanceMeters = result.distanceMeters.takeIf { it > 0.0 },
                ),
            )
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Box {
                    NavigationBar {
                        NavigationBarItem(
                            selected = onDashboardTab,
                            onClick = {
                                if (!onDashboardTab) {
                                    navigateToTopLevel(Route.Dashboard)
                                }
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_record_24dp),
                                    contentDescription = null,
                                )
                            },
                            label = { BottomNavigationLabel(R.string.workouts) },
                        )
                        NavigationBarItem(
                            selected = onProgramsTab,
                            onClick = {
                                if (!onProgramsTab) {
                                    navigateToTopLevel(Route.TrainingPrograms)
                                }
                            },
                            icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                            label = { BottomNavigationLabel(R.string.programs) },
                        )
                        NavigationBarItem(
                            selected = onNutritionTab,
                            onClick = {
                                if (!onNutritionTab) {
                                    navigateToTopLevel(Route.NutritionPlans)
                                }
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_nutrition_24),
                                    contentDescription = null,
                                )
                            },
                            label = { BottomNavigationLabel(R.string.nutritionTab) },
                        )
                        NavigationBarItem(
                            selected = onStatisticsTab,
                            onClick = {
                                if (!onStatisticsTab) {
                                    navigateToTopLevel(Route.Statistics)
                                }
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.outline_bar_chart_24),
                                    contentDescription = null,
                                )
                            },
                            label = { BottomNavigationLabel(R.string.statistics) },
                        )
                        NavigationBarItem(
                            selected = onSettingsTab,
                            onClick = {
                                if (!onSettingsTab) {
                                    navigateToTopLevel(Route.Settings)
                                }
                            },
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            label = { BottomNavigationLabel(R.string.settings) },
                        )
                    }
                    if (actionMenuExpanded) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { actionMenuExpanded = false },
                                ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Route.Dashboard,
            enterTransition = { slideInHorizontally { it } },
            exitTransition = { slideOutHorizontally { -it } },
            popEnterTransition = { slideInHorizontally { -it } },
            popExitTransition = { slideOutHorizontally { it } },
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<Route.Dashboard> {
                DashboardScreen(
                    workoutDao = Instance.getInstance(context).db.workoutDao(),
                    fabExpanded = actionMenuExpanded,
                    onFabExpandedChange = { actionMenuExpanded = it },
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
                    onOpenActiveProgram = ::openActiveProgram,
                    onEnterWorkout = { navController.navigate(Route.EnterWorkout()) },
                    onImport = { importVm.requestImport() },
                    onSettings = {
                        navigateToTopLevel(Route.Settings)
                    },
                )
            }
            composable<Route.TrainingPrograms> {
                TrainingProgramsScreen(
                    fabExpanded = actionMenuExpanded,
                    onFabExpandedChange = { actionMenuExpanded = it },
                    onOpenProgram = { id -> navController.navigate(Route.TrainingProgramDetail(id)) },
                    onProgramCreated = { id -> navController.navigate(Route.EditTrainingProgram(id)) },
                    onAiPrompt = { navController.navigate(Route.AiPrompt) },
                )
            }
            composable<Route.NutritionPlans> {
                NutritionPlansScreen(
                    fabExpanded = actionMenuExpanded,
                    onFabExpandedChange = { actionMenuExpanded = it },
                    onOpenPlan = { id -> navController.navigate(Route.NutritionPlanDetail(id)) },
                    onPlanCreated = { id -> navController.navigate(Route.EditNutritionPlan(id)) },
                    onAiPrompt = { navController.navigate(Route.NutritionAiPrompt) },
                )
            }
            composable<Route.Statistics> {
                StatisticsScreen(
                    onWorkoutClick = { workout ->
                        navController.navigate(Route.ShowWorkout(workout.id))
                    },
                )
            }
            composable<Route.EditTrainingProgram> { entry ->
                EditTrainingProgramScreen(
                    id = entry.toRoute<Route.EditTrainingProgram>().id,
                    onBack = { navController.popBackStack() },
                    onProgramDeleted = {
                        navController.popBackStack(Route.TrainingPrograms, inclusive = false)
                    },
                    onOpenDay = { dayId -> navController.navigate(Route.DayEditor(dayId)) },
                )
            }
            composable<Route.TrainingProgramDetail> { entry ->
                val programId = entry.toRoute<Route.TrainingProgramDetail>().id
                TrainingProgramDetailScreen(
                    id = programId,
                    onBack = { navController.popBackStack() },
                    onEditProgram = { navController.navigate(Route.EditTrainingProgram(programId)) },
                    onStartRecording = { workoutTypeId -> navigateToRecordRoute(workoutTypeId) },
                    onStartGuidedWorkout = ::navigateToGuidedWorkout,
                )
            }
            composable<Route.GuidedWorkout> { entry ->
                val route = entry.toRoute<Route.GuidedWorkout>()
                GuidedWorkoutRoute(
                    sessionId = route.sessionId,
                    pauseOnOpen = route.pauseOnOpen,
                    onBack = { navController.popBackStack() },
                    onStartRecorder = { request ->
                        navigateToRecordRoute(
                            workoutTypeId = request.workoutTypeId,
                            guidedSessionId = request.sessionId,
                            guidedSetId = request.sessionSetId,
                            guidedCompletionRule = request.completionRule.name,
                            guidedTargetDurationMillis = request.targetDurationMillis,
                            guidedTargetDistanceMeters = request.targetDistanceMeters,
                            guidedTargetRepetitions = request.targetRepetitions,
                        )
                    },
                )
            }
            composable<Route.DayEditor> { entry ->
                val dayId = entry.toRoute<Route.DayEditor>().dayId
                DayEditorScreen(
                    dayId = dayId,
                    onBack = { navController.popBackStack() },
                    onAddExercise = { navController.navigate(Route.ExercisePicker(dayId)) },
                )
            }
            composable<Route.ExercisePicker> { entry ->
                ExercisePickerScreen(
                    dayId = entry.toRoute<Route.ExercisePicker>().dayId,
                    onBack = { navController.popBackStack() },
                    onPicked = { navController.popBackStack() },
                )
            }
            composable<Route.AiPrompt> {
                AiPromptScreen(
                    onBack = { navController.popBackStack() },
                    onManageProfiles = { navController.navigate(Route.FitnessProfiles) },
                )
            }
            composable<Route.NutritionPlanDetail> { entry ->
                val planId = entry.toRoute<Route.NutritionPlanDetail>().id
                NutritionPlanDetailScreen(
                    id = planId,
                    onBack = { navController.popBackStack() },
                    onEditPlan = { navController.navigate(Route.EditNutritionPlan(planId)) },
                )
            }
            composable<Route.EditNutritionPlan> { entry ->
                EditNutritionPlanScreen(
                    id = entry.toRoute<Route.EditNutritionPlan>().id,
                    onBack = { navController.popBackStack() },
                    onPlanDeleted = {
                        navController.popBackStack(Route.NutritionPlans, inclusive = false)
                    },
                    onOpenDay = { dayId ->
                        navController.navigate(Route.NutritionDayEditor(dayId))
                    },
                )
            }
            composable<Route.NutritionDayEditor> { entry ->
                NutritionDayEditorScreen(
                    dayId = entry.toRoute<Route.NutritionDayEditor>().dayId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<Route.NutritionAiPrompt> {
                NutritionAiPromptScreen(
                    onBack = { navController.popBackStack() },
                    onManageProfiles = { navController.navigate(Route.FitnessProfiles) },
                    onPlanImported = { planId ->
                        navController.navigate(Route.NutritionPlanDetail(planId)) {
                            popUpTo(Route.NutritionPlans)
                        }
                    },
                )
            }
            composable<Route.ShowWorkoutFullscreenMap> { backStack ->
                ShowWorkoutFullscreenMapScreen(
                    id = backStack.toRoute<Route.ShowWorkoutFullscreenMap>().id,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<Route.ShowWorkoutMapDiagram> { backStack ->
                val route = backStack.toRoute<Route.ShowWorkoutMapDiagram>()
                ShowWorkoutMapDiagramScreen(
                    id = route.id,
                    diagramType = route.diagramType,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<Route.EditWorkoutStartEnd> { backStack ->
                EditWorkoutStartEndScreen(
                    id = backStack.toRoute<Route.EditWorkoutStartEnd>().id,
                    onBack = { navController.popBackStack() },
                    onWorkoutModified = { navController.popBackStack() },
                )
            }
            composable<Route.ShareWorkout> { backStack ->
                ShareWorkoutScreen(
                    id = backStack.toRoute<Route.ShareWorkout>().id,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<Route.EnterWorkout> { backStack ->
                EnterWorkoutScreen(
                    id = backStack.toRoute<Route.EnterWorkout>().id,
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
                    showNavigationIcon = false,
                    onNavigateToFitnessProfiles = {
                        navController.navigate(Route.FitnessProfiles)
                    },
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
                    onNavigateToExercises = {
                        navController.navigate(Route.ManageExercises)
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
                    onOpenPrivacyPolicy = { navController.navigate(Route.PrivacyPolicy) },
                )
            }
            composable<Route.PrivacyPolicy> {
                PrivacyPolicyScreen(
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
            composable<Route.ConfigureExportTargets> { backStack ->
                ConfigureExportTargetsScreen(
                    sourceId = backStack.toRoute<Route.ConfigureExportTargets>().sourceId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<Route.InterfaceSettings> {
                InterfaceSettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable<Route.FitnessProfiles> {
                FitnessProfilesScreen(
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
            composable<Route.EditIntervalSet> { backStack ->
                EditIntervalSetScreen(
                    setId = backStack.toRoute<Route.EditIntervalSet>().setId,
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
            composable<Route.ManageExercises> {
                ManageExercisesScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable<Route.EditWorkoutType> { backStack ->
                EditWorkoutTypeScreen(
                    typeId = backStack.toRoute<Route.EditWorkoutType>().typeId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<Route.RecordGpsWorkout> { backStack ->
                val route = backStack.toRoute<Route.RecordGpsWorkout>()
                GpsRecordingRoute(
                    workoutTypeId = route.workoutTypeId,
                    isLaunchAction = route.isLaunchAction,
                    onFinish = { navController.popBackStack() },
                    onNavigateToSettings = { navController.navigate(Route.RecordingSettings) },
                    onNavigateToIntervalSets = { navController.navigate(Route.ManageIntervalSets) },
                    onWorkoutFinished = { result ->
                        saveTrackerResult(route.guidedSessionId, route.guidedSetId, result)
                    },
                    autoStopGoal = route.guidedCompletionRule?.let { rule ->
                        RecordingAutoStopGoal(
                            completionRule = de.tadris.fitness.data.CompletionRule.findByName(rule),
                            durationMillis = route.guidedTargetDurationMillis,
                            distanceMeters = route.guidedTargetDistanceMeters,
                            repetitions = route.guidedTargetRepetitions,
                        )
                    },
                )
            }
            composable<Route.RecordIndoorWorkout> { backStack ->
                val route = backStack.toRoute<Route.RecordIndoorWorkout>()
                IndoorRecordingRoute(
                    workoutTypeId = route.workoutTypeId,
                    isLaunchAction = route.isLaunchAction,
                    onFinish = { navController.popBackStack() },
                    onNavigateToSettings = { navController.navigate(Route.RecordingSettings) },
                    onNavigateToIntervalSets = { navController.navigate(Route.ManageIntervalSets) },
                    onWorkoutFinished = { result ->
                        saveTrackerResult(route.guidedSessionId, route.guidedSetId, result)
                    },
                    autoStopGoal = route.guidedCompletionRule?.let { rule ->
                        RecordingAutoStopGoal(
                            completionRule = de.tadris.fitness.data.CompletionRule.findByName(rule),
                            durationMillis = route.guidedTargetDurationMillis,
                            distanceMeters = route.guidedTargetDistanceMeters,
                            repetitions = route.guidedTargetRepetitions,
                        )
                    },
                )
            }
            composable<Route.ShowWorkout> { backStack ->
                ShowWorkoutScreen(
                    id = backStack.toRoute<Route.ShowWorkout>().id,
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
}
