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
package de.tadris.fitness.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.Workout
import de.tadris.fitness.data.WorkoutDao
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.ui.theme.FitoTrackTheme

@Composable
fun DashboardScreen(
    workoutDao: WorkoutDao,
    onWorkoutClick: (Workout) -> Unit = {},
    onDeleteWorkout: (Workout) -> Unit = {},
    onRecord: () -> Unit = {},
    onRecordLast: (WorkoutType) -> Unit = {},
    onEnterWorkout: () -> Unit = {},
    onImport: () -> Unit = {},
    onSettings: () -> Unit = {},
    onStatistics: () -> Unit = {},
    onPrograms: () -> Unit = {},
) {
    val workouts by workoutDao.getWorkoutsFlow().collectAsState(initial = emptyList())
    DashboardScreen(
        workouts = workouts,
        onWorkoutClick = onWorkoutClick,
        onDeleteWorkout = onDeleteWorkout,
        onRecord = onRecord,
        onRecordLast = onRecordLast,
        onEnterWorkout = onEnterWorkout,
        onImport = onImport,
        onSettings = onSettings,
        onStatistics = onStatistics,
        onPrograms = onPrograms,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    workouts: List<Workout>,
    onWorkoutClick: (Workout) -> Unit = {},
    onDeleteWorkout: (Workout) -> Unit = {},
    onRecord: () -> Unit = {},
    onRecordLast: (WorkoutType) -> Unit = {},
    onEnterWorkout: () -> Unit = {},
    onImport: () -> Unit = {},
    onSettings: () -> Unit = {},
    onStatistics: () -> Unit = {},
    onPrograms: () -> Unit = {},
) {
    val context = LocalContext.current
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    var workoutToDelete by remember { mutableStateOf<Workout?>(null) }
    val userPreferences = Instance.getInstance(context).userPreferences
    var showFirstStartDialog by rememberSaveable {
        val isFirstStart = userPreferences.firstStart
        if (isFirstStart) userPreferences.firstStart = false
        mutableStateOf(isFirstStart)
    }

    val lastWorkoutType = remember(workouts) {
        workouts.firstOrNull()?.getWorkoutType(context)
    }

    if (showFirstStartDialog) {
        FirstStartDialog(
            onSettings = { showFirstStartDialog = false; onSettings() },
            onDismiss = { showFirstStartDialog = false },
        )
    }

    if (workoutToDelete != null) {
        AlertDialog(
            onDismissRequest = { workoutToDelete = null },
            title = { Text(stringResource(R.string.deleteWorkout)) },
            text = { Text(stringResource(R.string.deleteWorkoutMessage)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteWorkout(workoutToDelete!!)
                    workoutToDelete = null
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { workoutToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.workouts)) },
                actions = {
                    IconButton(onClick = onPrograms) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = stringResource(R.string.trainingPrograms),
                        )
                    }
                    IconButton(onClick = onStatistics) {
                        Icon(
                            painter = painterResource(R.drawable.outline_bar_chart_24),
                            contentDescription = stringResource(R.string.workout_statistics),
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            DashboardFab(
                expanded = fabExpanded,
                lastWorkoutType = lastWorkoutType,
                onToggle = { fabExpanded = !fabExpanded },
                onRecord = { fabExpanded = false; onRecord() },
                onRecordLast = { type -> fabExpanded = false; onRecordLast(type) },
                onEnterWorkout = { fabExpanded = false; onEnterWorkout() },
                onImport = { fabExpanded = false; onImport() },
            )
        },
    ) { innerPadding ->
        if (workouts.isEmpty()) {
            DashboardEmptyPlaceholder(Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 88.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(workouts, key = { it.id }) { workout ->
                    WorkoutListItem(
                        workout = workout,
                        onClick = { onWorkoutClick(workout) },
                        onLongClick = { workoutToDelete = workout },
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardEmptyPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.hintAddWorkout),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp),
        )
    }
}

@Composable
private fun FirstStartDialog(
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setPreferencesTitle)) },
        text = { Text(stringResource(R.string.setPreferencesMessage)) },
        confirmButton = {
            TextButton(onClick = onSettings) {
                Text(stringResource(R.string.settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun DashboardPreview() {
    val workout = Workout(
        id = 0,
        uuid = "",
        start = System.currentTimeMillis() - 3_600_000,
        end = System.currentTimeMillis(),
        activeDuration = 3_600_000,
        comment = "Morning run",
        distance = 10_000.0,
        repetitions = 0,
        energy = 1_500_000.0,
        workoutTypeId = "running",
        intervalSetUsedId = -1,
    )
    FitoTrackTheme {
        DashboardScreen(workouts = listOf(workout, workout, workout))
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardEmptyPreview() {
    FitoTrackTheme {
        DashboardScreen(workouts = emptyList())
    }
}
