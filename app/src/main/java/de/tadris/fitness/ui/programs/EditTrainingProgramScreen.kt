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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.ProgramDay

/**
 * Program editor: name/description plus the day list (add, rename, duplicate,
 * reorder, rest days). Day contents are edited on the day-editor screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTrainingProgramScreen(
    id: Long,
    onBack: () -> Unit,
    onProgramDeleted: () -> Unit,
    onOpenDay: (Long) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val dao = remember { Instance.getInstance(context).db.trainingProgramDao() }
    var program by remember { mutableStateOf(dao.findProgramById(id)) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val days = remember(refreshKey) { dao.getDaysOfProgram(id) }
    val exerciseCounts = remember(refreshKey) {
        days.associate { it.id to dao.getExercisesOfDay(it.id).size }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var renamingDay by remember { mutableStateOf<ProgramDay?>(null) }
    var editingMeta by remember { mutableStateOf(false) }

    fun refresh() {
        refreshKey++
        program = dao.findProgramById(id)
    }

    fun reindex(list: List<ProgramDay>) {
        dao.updateDays(list.mapIndexed { index, day -> day.copy(orderIndex = index) })
    }

    fun addDay(isRest: Boolean) {
        dao.insertDay(ProgramDay(programId = id, orderIndex = days.size, isRest = isRest))
        refresh()
    }

    fun duplicateDay(day: ProgramDay) {
        val newDayId = dao.insertDay(day.copy(id = 0, orderIndex = days.size))
        for (entry in dao.getExercisesOfDay(day.id)) {
            val newEntryId = dao.insertProgramExercise(entry.copy(id = 0, dayId = newDayId))
            dao.getSetsOfProgramExercise(entry.id).forEach { set ->
                dao.insertSet(set.copy(id = 0, programExerciseId = newEntryId))
            }
        }
        refresh()
    }

    fun moveDay(from: Int, to: Int) {
        if (to < 0 || to >= days.size) return
        val reordered = days.toMutableList().apply { add(to, removeAt(from)) }
        reindex(reordered)
        refresh()
    }

    val current = program ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = current.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.actionBack),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.deleteProgram))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "meta") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProgramIconBadge(iconName = current.icon)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = current.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                current.description?.takeIf { it.isNotBlank() }?.let { description ->
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        FilledTonalButton(
                            onClick = { editingMeta = true },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(stringResource(R.string.editTrainingProgram))
                        }
                    }
                }
            }
            item(key = "days_header") {
                Text(
                    text = stringResource(R.string.daysSection),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            itemsIndexed(days, key = { _, day -> day.id }) { index, day ->
                DayCard(
                    day = day,
                    index = index,
                    exerciseCount = exerciseCounts[day.id] ?: 0,
                    canMoveUp = index > 0,
                    canMoveDown = index < days.size - 1,
                    onClick = { onOpenDay(day.id) },
                    onRename = { renamingDay = day },
                    onDuplicate = { duplicateDay(day) },
                    onMoveUp = { moveDay(index, index - 1) },
                    onMoveDown = { moveDay(index, index + 1) },
                    onDelete = {
                        dao.deleteDay(day)
                        reindex(days.filter { it.id != day.id })
                        refresh()
                    },
                )
            }
            item(key = "add_buttons") {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth < 440.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { addDay(isRest = false) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = stringResource(R.string.addDay),
                                    textAlign = TextAlign.Center,
                                )
                            }
                            OutlinedButton(
                                onClick = { addDay(isRest = true) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = stringResource(R.string.addRestDay),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { addDay(isRest = false) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = stringResource(R.string.addDay),
                                    textAlign = TextAlign.Center,
                                )
                            }
                            OutlinedButton(
                                onClick = { addDay(isRest = true) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = stringResource(R.string.addRestDay),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.deleteProgram)) },
            text = { Text(stringResource(R.string.deleteProgramMessage)) },
            confirmButton = {
                TextButton(onClick = {
                    dao.deleteProgram(current)
                    showDeleteDialog = false
                    onProgramDeleted()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    renamingDay?.let { day ->
        var dayName by remember(day.id) { mutableStateOf(day.name ?: "") }
        AlertDialog(
            onDismissRequest = { renamingDay = null },
            title = { Text(stringResource(R.string.renameDay)) },
            text = {
                OutlinedTextField(
                    value = dayName,
                    onValueChange = { dayName = it },
                    label = { Text(stringResource(R.string.dayNameOptional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dao.updateDay(day.copy(name = dayName.trim().ifBlank { null }))
                    renamingDay = null
                    refresh()
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { renamingDay = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (editingMeta) {
        var name by remember { mutableStateOf(current.name) }
        var description by remember { mutableStateOf(current.description ?: "") }
        var iconName by remember { mutableStateOf(current.icon) }
        var nameError by remember { mutableStateOf(false) }
        var showIconPicker by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editingMeta = false },
            title = { Text(stringResource(R.string.editTrainingProgram)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.programDescriptionOptional)) },
                        minLines = 3,
                        maxLines = 5,
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
                    val updatedProgram = current.copy(
                        name = name.trim(),
                        description = description.trim().ifBlank { null },
                        icon = resolveProgramIcon(iconName).name,
                    )
                    dao.updateProgram(updatedProgram)
                    dao.updateProgramWorkoutIdentity(
                        programUuid = updatedProgram.uuid,
                        programName = updatedProgram.name,
                        icon = updatedProgram.icon,
                    )
                    editingMeta = false
                    refresh()
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingMeta = false }) { Text(stringResource(R.string.cancel)) }
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
}

@Composable
private fun DayCard(
    day: ProgramDay,
    index: Int,
    exerciseCount: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (day.isRest) Modifier else Modifier.clickable(onClick = onClick)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (day.isRest) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        contentColor = if (day.isRest) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                    ) {
                        Text(
                            text = if (day.isRest) {
                                stringResource(R.string.restDay)
                            } else {
                                stringResource(R.string.dayNumber, index + 1)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    day.name?.takeIf { it.isNotBlank() }?.let { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (!day.isRest) {
                    Text(
                        text = stringResource(R.string.exercisesColonCount, exerciseCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.renameDay)) },
                    onClick = { menuOpen = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.duplicateDay)) },
                    onClick = { menuOpen = false; onDuplicate() },
                )
                if (canMoveUp) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.moveUp)) },
                        onClick = { menuOpen = false; onMoveUp() },
                    )
                }
                if (canMoveDown) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.moveDown)) },
                        onClick = { menuOpen = false; onMoveDown() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.deleteDay)) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}
