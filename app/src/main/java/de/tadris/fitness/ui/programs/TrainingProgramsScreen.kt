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
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.ui.programs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.ProgramScheduling
import de.tadris.fitness.data.TrainingProgram
import de.tadris.fitness.data.backup.ProgramImporter
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Programs tab: active timelines lead, completed and saved programs remain reviewable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingProgramsScreen(
    fabExpanded: Boolean = false,
    onFabExpandedChange: (Boolean) -> Unit = {},
    onOpenProgram: (Long) -> Unit,
    onProgramCreated: (Long) -> Unit,
    onAiPrompt: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val dao = remember { Instance.getInstance(context).db.trainingProgramDao() }
    val programs by dao.getProgramsFlow().collectAsState(initial = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }

    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val container = context.contentResolver.openInputStream(uri)?.use { ProgramImporter.read(it) }
                ?: throw java.io.IOException("Cannot open file")
            val id = ProgramImporter.import(context, container)
            onOpenProgram(id)
        } catch (e: Exception) {
            importError = e.message ?: resources.getString(R.string.errorImportFailed)
        }
    }

    BackHandler(enabled = fabExpanded) { onFabExpandedChange(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(stringResource(R.string.trainingPrograms)) })
            },
        ) { padding ->
            if (programs.isEmpty()) {
                ProgramsEmptyState(
                    onCreate = { showCreateDialog = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            } else {
                val entries = programs.map { program ->
                    ProgramListEntry(
                        program = program,
                        dayCount = dao.countDaysOfProgram(program.id),
                    )
                }
                val active = entries.filter { entry ->
                    ProgramScheduling.isActive(
                        entry.program,
                        entry.dayCount,
                        today,
                        zone,
                    )
                }
                val completed = entries.filter { entry ->
                    entry.program.startDate != null && entry.dayCount > 0 && ProgramScheduling.hasEnded(
                        entry.program,
                        entry.dayCount,
                        today,
                        zone,
                    )
                }
                val inactive = entries.filter {
                    it.program.startDate == null || it.dayCount <= 0
                }
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 12.dp,
                        bottom = padding.calculateBottomPadding() + 112.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (active.isNotEmpty()) {
                        item(key = "active_header") {
                            ProgramSectionHeader(stringResource(R.string.activeProgram))
                        }
                    }
                    items(active, key = { "a${it.program.id}" }) { entry ->
                        val dayIndex = ProgramScheduling.dayIndexOf(
                            entry.program,
                            entry.dayCount,
                            today,
                            zone,
                        )
                        ProgramTimelineCard(
                            program = entry.program,
                            dayIndex = dayIndex,
                            dayCount = entry.dayCount,
                            completed = false,
                            onClick = { onOpenProgram(entry.program.id) },
                        )
                    }
                    if (completed.isNotEmpty()) {
                        item(key = "completed_header") {
                            ProgramSectionHeader(stringResource(R.string.completedPrograms))
                        }
                    }
                    items(completed, key = { "c${it.program.id}" }) { entry ->
                        ProgramTimelineCard(
                            program = entry.program,
                            dayIndex = (entry.dayCount - 1).takeIf { it >= 0 },
                            dayCount = entry.dayCount,
                            completed = true,
                            onClick = { onOpenProgram(entry.program.id) },
                        )
                    }
                    if (inactive.isNotEmpty()) {
                        item(key = "programs_header") {
                            ProgramSectionHeader(stringResource(R.string.myTrainingPrograms))
                        }
                    }
                    items(inactive, key = { "i${it.program.id}" }) { entry ->
                        ProgramCard(
                            program = entry.program,
                            dayCount = entry.dayCount,
                            onClick = { onOpenProgram(entry.program.id) },
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

        ProgramsSpeedDial(
            expanded = fabExpanded,
            onToggle = { onFabExpandedChange(!fabExpanded) },
            onCreate = {
                onFabExpandedChange(false)
                showCreateDialog = true
            },
            onImport = {
                onFabExpandedChange(false)
                importLauncher.launch("*/*")
            },
            onAiPrompt = {
                onFabExpandedChange(false)
                onAiPrompt()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .zIndex(2f),
        )
    }

    importError?.let { msg ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text(stringResource(R.string.error)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { importError = null }) { Text(stringResource(R.string.okay)) }
            },
        )
    }

    if (showCreateDialog) {
        CreateTrainingProgramDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, iconName ->
                val id = dao.insertProgram(
                    TrainingProgram(
                        uuid = UUID.randomUUID().toString(),
                        name = name,
                        createdAt = System.currentTimeMillis(),
                        icon = iconName,
                    )
                )
                showCreateDialog = false
                onProgramCreated(id)
            },
        )
    }
}

private data class ProgramListEntry(
    val program: TrainingProgram,
    val dayCount: Int,
)

@Composable
private fun ProgramTimelineCard(
    program: TrainingProgram,
    dayIndex: Int?,
    dayCount: Int,
    completed: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProgramIconBadge(
                        iconName = program.icon,
                        size = 44.dp,
                        iconSize = 23.dp,
                    )
                    ProgramStatusBadge(
                        text = stringResource(
                            if (completed) R.string.statusCompleted else R.string.inProgress
                        ),
                        emphasized = !completed,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = program.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            program.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (dayIndex != null && dayCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.programDayOfCycle, dayIndex + 1, dayCount),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = "${(((dayIndex + 1f) / dayCount) * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                LinearProgressIndicator(
                    progress = { (dayIndex + 1f) / dayCount },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            } else if (dayCount > 0) {
                ProgramDayCount(dayCount = dayCount, emphasized = true)
            }
        }
    }
}

@Composable
private fun ProgramCard(
    program: TrainingProgram,
    dayCount: Int,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgramIconBadge(iconName = program.icon)
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = program.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                program.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (dayCount > 0) {
                        ProgramDayCount(dayCount = dayCount)
                    }
                    ProgramStatusBadge(text = stringResource(R.string.notStarted))
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProgramSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun ProgramDayCount(
    dayCount: Int,
    emphasized: Boolean = false,
) {
    Text(
        text = pluralStringResource(R.plurals.programDaysCount, dayCount, dayCount),
        style = MaterialTheme.typography.labelMedium,
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun ProgramStatusBadge(
    text: String,
    emphasized: Boolean = false,
) {
    Surface(
        shape = CircleShape,
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (emphasized) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (emphasized) {
                Surface(
                    modifier = Modifier.size(6.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {}
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ProgramsSpeedDial(
    expanded: Boolean,
    onToggle: () -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onAiPrompt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (expanded) 28.dp else 16.dp,
        label = "programs_fab_corner",
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
                SpeedDialAction(
                    label = stringResource(R.string.createTrainingProgram),
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = onCreate,
                )
                SpeedDialAction(
                    label = stringResource(R.string.importProgram),
                    icon = { Icon(painterResource(R.drawable.ic_storage), contentDescription = null) },
                    onClick = onImport,
                )
                SpeedDialAction(
                    label = stringResource(R.string.aiPrompt),
                    icon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
                    onClick = onAiPrompt,
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
                    if (expanded) R.string.closeProgramActions else R.string.moreProgramActions
                ),
            )
        }
    }
}

@Composable
private fun SpeedDialAction(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = icon,
        text = {
            Text(
                text = label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
        },
        modifier = Modifier.semantics { contentDescription = label },
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

@Composable
private fun ProgramsEmptyState(
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(88.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.programsEmptyTitle),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.programsEmptyDescription),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.createTrainingProgram))
            }
        }
    }
}

@Composable
private fun CreateTrainingProgramDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, iconName: String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var iconName by remember { mutableStateOf(de.tadris.fitness.util.Icon.ACTIVITY.name) }
    var showIconPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.createTrainingProgram)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text(stringResource(R.string.programName)) },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.enterName)) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Surface(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        showIconPicker = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProgramIconBadge(
                            iconName = iconName,
                            size = 40.dp,
                            iconSize = 22.dp,
                        )
                        Text(
                            text = stringResource(R.string.programIcon),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.trim().length < 2) {
                    nameError = true
                    return@TextButton
                }
                onCreate(name.trim(), iconName)
            }) { Text(stringResource(R.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (showIconPicker) {
        ProgramIconPickerDialog(
            selectedIconName = iconName,
            onDismiss = { showIconPicker = false },
            onSelect = { selected ->
                iconName = selected
                showIconPicker = false
            },
        )
    }
}
