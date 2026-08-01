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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.tadris.fitness.R
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.data.WorkoutTypeManager
import de.tadris.fitness.util.Icon as WorkoutIcon

@Composable
internal fun DashboardFab(
    expanded: Boolean,
    lastWorkoutType: WorkoutType?,
    lastWorkoutLabel: String?,
    lastWorkoutIcon: String?,
    lastWorkoutProgramUuid: String?,
    onToggle: () -> Unit,
    onRecord: () -> Unit,
    onRecordLast: (WorkoutType) -> Unit,
    onOpenActiveProgram: (String?) -> Unit,
    onEnterWorkout: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (expanded) 28.dp else 16.dp,
        label = "dashboard_fab_corner",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                if (lastWorkoutType != null) {
                    FabAction(
                        label = lastWorkoutLabel ?: lastWorkoutType.title,
                        iconRes = WorkoutIcon.getIcon(lastWorkoutIcon ?: lastWorkoutType.icon),
                        onClick = {
                            if (lastWorkoutType.id == WorkoutTypeManager.WORKOUT_TYPE_ID_PROGRAM_TRAINING) {
                                onOpenActiveProgram(lastWorkoutProgramUuid)
                            } else {
                                onRecordLast(lastWorkoutType)
                            }
                        },
                    )
                }
                FabAction(
                    label = stringResource(R.string.recordWorkout),
                    iconRes = R.drawable.ic_record_24dp,
                    onClick = onRecord,
                )
                FabAction(
                    label = stringResource(R.string.enterWorkout),
                    iconRes = R.drawable.ic_edit,
                    onClick = onEnterWorkout,
                )
                FabAction(
                    label = stringResource(R.string.importWorkout),
                    iconRes = R.drawable.ic_storage,
                    onClick = onImport,
                )
            }
        }

        FloatingActionButton(
            onClick = onToggle,
            shape = RoundedCornerShape(cornerRadius),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = stringResource(
                    if (expanded) R.string.closeWorkoutActions else R.string.moreWorkoutActions
                ),
            )
        }
    }
}

@Composable
private fun FabAction(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    ExtendedFloatingActionButton(
        modifier = Modifier.semantics {
            contentDescription = label
        },
        onClick = onClick,
        icon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
            )
        },
        text = { Text(label) },
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}
