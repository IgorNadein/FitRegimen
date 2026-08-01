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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.Workout
import de.tadris.fitness.data.WorkoutDao
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.ui.theme.FitoTrackTheme

@Composable
fun DashboardScreen(
    workoutDao: WorkoutDao,
    fabExpanded: Boolean = false,
    onFabExpandedChange: (Boolean) -> Unit = {},
    onWorkoutClick: (Workout) -> Unit = {},
    onDeleteWorkout: (Workout) -> Unit = {},
    onRecord: () -> Unit = {},
    onRecordLast: (WorkoutType) -> Unit = {},
    onOpenActiveProgram: (String?) -> Unit = {},
    onEnterWorkout: () -> Unit = {},
    onImport: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val workouts by workoutDao.getVisibleWorkoutsFlow().collectAsState(initial = emptyList())
    DashboardScreen(
        workouts = workouts,
        fabExpanded = fabExpanded,
        onFabExpandedChange = onFabExpandedChange,
        onWorkoutClick = onWorkoutClick,
        onDeleteWorkout = onDeleteWorkout,
        onRecord = onRecord,
        onRecordLast = onRecordLast,
        onOpenActiveProgram = onOpenActiveProgram,
        onEnterWorkout = onEnterWorkout,
        onImport = onImport,
        onSettings = onSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    workouts: List<Workout>,
    fabExpanded: Boolean = false,
    onFabExpandedChange: (Boolean) -> Unit = {},
    onWorkoutClick: (Workout) -> Unit = {},
    onDeleteWorkout: (Workout) -> Unit = {},
    onRecord: () -> Unit = {},
    onRecordLast: (WorkoutType) -> Unit = {},
    onOpenActiveProgram: (String?) -> Unit = {},
    onEnterWorkout: () -> Unit = {},
    onImport: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    var workoutToDelete by remember { mutableStateOf<Workout?>(null) }
    val userPreferences = Instance.getInstance(context).userPreferences
    var showFirstStartDialog by rememberSaveable {
        val isFirstStart = userPreferences.firstStart
        if (isFirstStart) userPreferences.firstStart = false
        mutableStateOf(isFirstStart)
    }

    val lastWorkout = workouts.firstOrNull()
    val lastWorkoutType = remember(lastWorkout) { lastWorkout?.getWorkoutType(context) }
    val lastWorkoutLabel = lastWorkout?.programName ?: lastWorkoutType?.title
    val lastWorkoutIcon = lastWorkout?.getDisplayIconName(context) ?: lastWorkoutType?.icon

    BackHandler(enabled = fabExpanded) { onFabExpandedChange(false) }

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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.workouts)) },
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

        AnimatedVisibility(
            visible = fabExpanded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .matchParentSize()
                .zIndex(1f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onFabExpandedChange(false) },
                    ),
            )
        }

        DashboardFab(
            expanded = fabExpanded,
            lastWorkoutType = lastWorkoutType,
            lastWorkoutLabel = lastWorkoutLabel,
            lastWorkoutIcon = lastWorkoutIcon,
            lastWorkoutProgramUuid = lastWorkout?.programUuid,
            onToggle = { onFabExpandedChange(!fabExpanded) },
            onRecord = { onFabExpandedChange(false); onRecord() },
            onRecordLast = { type -> onFabExpandedChange(false); onRecordLast(type) },
            onOpenActiveProgram = { programUuid ->
                onFabExpandedChange(false)
                onOpenActiveProgram(programUuid)
            },
            onEnterWorkout = { onFabExpandedChange(false); onEnterWorkout() },
            onImport = { onFabExpandedChange(false); onImport() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .zIndex(2f),
        )
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
