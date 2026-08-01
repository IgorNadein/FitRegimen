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

package de.tadris.fitness.ui.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.tadris.fitness.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    showNavigationIcon: Boolean = true,
    onNavigateToFitnessProfiles: () -> Unit = {},
    onNavigateToInterfaceSettings: () -> Unit = {},
    onNavigateToRecordingSettings: () -> Unit = {},
    onNavigateToBackupSettings: () -> Unit = {},
    onNavigateToWorkoutTypes: () -> Unit = {},
    onNavigateToExercises: () -> Unit = {},
    onNavigateToDebugSettings: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    if (showNavigationIcon) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.actionBack),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                ClickableSettingsItem(
                    title = stringResource(R.string.fitness_profiles_title),
                    supporting = stringResource(R.string.fitness_profiles_summary),
                    onClick = onNavigateToFitnessProfiles,
                )
            }
            item {
                ClickableSettingsItem(
                    title = stringResource(R.string.preferencesUserInterfaceTitle),
                    supporting = stringResource(R.string.preferencesUserInterfaceSummary),
                    onClick = onNavigateToInterfaceSettings,
                )
            }
            item {
                ClickableSettingsItem(
                    title = stringResource(R.string.preferencesRecordingTitle),
                    supporting = stringResource(R.string.preferencesRecordingSummary),
                    onClick = onNavigateToRecordingSettings,
                )
            }
            item {
                ClickableSettingsItem(
                    title = stringResource(R.string.customWorkoutTypesTitle),
                    onClick = onNavigateToWorkoutTypes,
                )
            }
            item {
                ClickableSettingsItem(
                    title = stringResource(R.string.customExercisesTitle),
                    supporting = stringResource(R.string.customExercisesSummary),
                    onClick = onNavigateToExercises,
                )
            }
            item {
                ClickableSettingsItem(
                    title = stringResource(R.string.preferencesBackupTitle),
                    supporting = stringResource(R.string.preferencesBackupSummary),
                    onClick = onNavigateToBackupSettings,
                )
            }
            item {
                ClickableSettingsItem(
                    title = stringResource(R.string.preferencesDebugTitle),
                    onClick = onNavigateToDebugSettings,
                )
            }
            item {
                ClickableSettingsItem(
                    title = stringResource(R.string.aboutTitle),
                    onClick = onNavigateToAbout,
                )
            }
        }
    }
}
