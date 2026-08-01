/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack.
 */
package de.tadris.fitness.ui.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.tadris.fitness.R
import de.tadris.fitness.util.Icon as WorkoutIcon

/** Resolves persisted names without ever letting a removed or malformed icon break the UI. */
fun resolveProgramIcon(iconName: String?): WorkoutIcon =
    WorkoutIcon.values().firstOrNull { it.name == iconName } ?: WorkoutIcon.ACTIVITY

@Composable
fun ProgramIcon(
    iconName: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(resolveProgramIcon(iconName).iconRes),
        contentDescription = null,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun ProgramIconBadge(
    iconName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            ProgramIcon(
                iconName = iconName,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
fun ProgramIconPickerDialog(
    selectedIconName: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val icons = remember {
        // Programs describe training, so lead with the neutral choice and keep the
        // grid limited to sports instead of exposing car/moped utility icons.
        listOf(
            WorkoutIcon.ACTIVITY,
            WorkoutIcon.RUNNING,
            WorkoutIcon.WALKING,
            WorkoutIcon.CYCLING,
            WorkoutIcon.HIKING,
            WorkoutIcon.POOL,
            WorkoutIcon.ROWING,
            WorkoutIcon.BALL,
            WorkoutIcon.HANDBALL,
            WorkoutIcon.AMERICAN_FOOTBALL,
            WorkoutIcon.GOLF,
            WorkoutIcon.INLINE_SKATING,
            WorkoutIcon.SKATEBOARDING,
            WorkoutIcon.RIDING,
            WorkoutIcon.DOWNHILL_SKI,
            WorkoutIcon.CROSS_COUNTRY_SKI,
            WorkoutIcon.ROPE_SKIPPING,
            WorkoutIcon.PUSH_UPS,
            WorkoutIcon.PULL_UPS,
            WorkoutIcon.TRAMPOLINE_JUMPING,
            WorkoutIcon.WHEELCHAIR,
            WorkoutIcon.WHEELCHAIR_FAST,
            WorkoutIcon.OTHER,
        )
    }
    val selectedIcon = resolveProgramIcon(selectedIconName)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.programIcon)) },
        text = {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val gridHeight = (maxHeight * 0.72f).coerceIn(144.dp, 360.dp)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 56.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = gridHeight),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(icons, key = { it.name }) { icon ->
                        val isSelected = icon == selectedIcon
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.semantics { selected = isSelected },
                        ) {
                            IconButton(onClick = { onSelect(icon.name) }) {
                                Icon(
                                    painter = painterResource(icon.iconRes),
                                    contentDescription = stringResource(
                                        R.string.selectProgramIconDescription,
                                        icon.name.replace('_', ' ').replace('-', ' '),
                                    ),
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
