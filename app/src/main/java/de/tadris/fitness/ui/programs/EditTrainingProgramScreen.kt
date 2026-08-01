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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.ProgramTask
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.data.WorkoutTypeManager
import de.tadris.fitness.util.Icon as WorkoutIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTrainingProgramScreen(
    id: Long,
    onBack: () -> Unit,
    onProgramDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val dao = remember { Instance.getInstance(context).db.trainingProgramDao() }
    var program by remember { mutableStateOf(dao.findProgramById(id)) }
    var tasks by remember { mutableStateOf(dao.getTasksOfProgram(id)) }
    val workoutTypes = remember { WorkoutTypeManager.getInstance().getAllTypesSorted(context) }

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<ProgramTask?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }

    fun refreshTasks() {
        tasks = dao.getTasksOfProgram(id)
    }

    val current = program ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showNameDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.editTrainingProgram))
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.deleteProgram))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddTaskDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.addTask))
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            if (tasks.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.hintNoTasks),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            val grouped = tasks.groupBy { it.dayIndex }.toSortedMap()
            grouped.forEach { (dayIndex, dayTasks) ->
                item(key = "day_$dayIndex") {
                    val title = dayTasks.firstOrNull()?.dayTitle?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.dayNumber, dayIndex + 1)
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(dayTasks, key = { it.id }) { task ->
                    val type = workoutTypes.firstOrNull { it.id == task.workoutTypeId }
                    ProgramTaskListItem(
                        task = task,
                        type = type,
                        onClick = { editingTask = task },
                        onDelete = { dao.deleteTask(task); refreshTasks() },
                    )
                }
            }
        }
    }

    if (showAddTaskDialog) {
        TaskDialog(
            title = stringResource(R.string.addTask),
            cycleLengthDays = current.cycleLengthDays,
            workoutTypes = workoutTypes,
            onDismiss = { showAddTaskDialog = false },
            onConfirm = { newTask ->
                dao.insertTask(newTask.copy(programId = id))
                showAddTaskDialog = false
                refreshTasks()
            },
        )
    }

    editingTask?.let { task ->
        TaskDialog(
            title = stringResource(R.string.editTask),
            cycleLengthDays = current.cycleLengthDays,
            workoutTypes = workoutTypes,
            existing = task,
            onDismiss = { editingTask = null },
            onConfirm = { updated ->
                dao.updateTask(updated.copy(id = task.id, programId = id))
                editingTask = null
                refreshTasks()
            },
        )
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

    if (showNameDialog) {
        RenameProgramDialog(
            initialName = current.name,
            initialDescription = current.description ?: "",
            onDismiss = { showNameDialog = false },
            onConfirm = { name, description ->
                val updated = current.copy(name = name, description = description.ifBlank { null })
                dao.updateProgram(updated)
                program = updated
                showNameDialog = false
            },
        )
    }
}

@Composable
private fun ProgramTaskListItem(
    task: ProgramTask,
    type: WorkoutType?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val details = buildList {
        type?.let { add(it.title) }
        task.targetSets?.let { sets -> task.targetReps?.let { reps -> add("$sets × $reps") } }
        task.targetDurationMillis?.let { add("${it / 60_000} min") }
        task.targetDistanceMeters?.let { add("${"%.1f".format(it / 1000.0)} km") }
    }.joinToString(" · ")

    ListItem(
        headlineContent = { Text(type?.title ?: task.workoutTypeId) },
        supportingContent = if (details.isNotEmpty()) { { Text(details) } } else null,
        leadingContent = type?.let {
            {
                Icon(
                    painter = painterResource(WorkoutIcon.getIcon(it.icon)),
                    contentDescription = null,
                    tint = Color(it.color),
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDialog(
    title: String,
    cycleLengthDays: Int,
    workoutTypes: List<WorkoutType>,
    existing: ProgramTask? = null,
    onDismiss: () -> Unit,
    onConfirm: (ProgramTask) -> Unit,
) {
    var dayIndexText by remember { mutableStateOf(((existing?.dayIndex ?: 0) + 1).toString()) }
    var dayTitle by remember { mutableStateOf(existing?.dayTitle ?: "") }
    var selectedType by remember { mutableStateOf(workoutTypes.firstOrNull { it.id == existing?.workoutTypeId } ?: workoutTypes.firstOrNull()) }
    var typeExpanded by remember { mutableStateOf(false) }
    var sets by remember { mutableStateOf(existing?.targetSets?.toString() ?: "") }
    var reps by remember { mutableStateOf(existing?.targetReps?.toString() ?: "") }
    var durationMinutes by remember { mutableStateOf(existing?.targetDurationMillis?.let { (it / 60_000).toString() } ?: "") }
    var distanceKm by remember { mutableStateOf(existing?.targetDistanceMeters?.let { (it / 1000.0).toString() } ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var dayIndexError by remember { mutableStateOf<String?>(null) }
    val invalidNumberMsg = stringResource(R.string.errorEnterValidNumber)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dayIndexText,
                    onValueChange = { dayIndexText = it; dayIndexError = null },
                    label = { Text(stringResource(R.string.dayIndex, cycleLengthDays)) },
                    isError = dayIndexError != null,
                    supportingText = dayIndexError?.let { e -> { Text(e) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = dayTitle,
                    onValueChange = { dayTitle = it },
                    label = { Text(stringResource(R.string.dayTitleOptional)) },
                    singleLine = true,
                )
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(
                        value = selectedType?.title ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.workoutType)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        workoutTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.title) },
                                onClick = { selectedType = type; typeExpanded = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = sets,
                    onValueChange = { sets = it },
                    label = { Text(stringResource(R.string.targetSetsOptional)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it },
                    label = { Text(stringResource(R.string.targetRepsOptional)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = durationMinutes,
                    onValueChange = { durationMinutes = it },
                    label = { Text(stringResource(R.string.targetDurationMinutesOptional)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = distanceKm,
                    onValueChange = { distanceKm = it },
                    label = { Text(stringResource(R.string.targetDistanceKmOptional)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.taskNoteOptional)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val dayNumber = dayIndexText.toIntOrNull()
                if (dayNumber == null || dayNumber < 1 || dayNumber > cycleLengthDays) {
                    dayIndexError = invalidNumberMsg
                    return@TextButton
                }
                val type = selectedType ?: return@TextButton
                onConfirm(
                    ProgramTask(
                        programId = existing?.programId ?: 0,
                        dayIndex = dayNumber - 1,
                        dayTitle = dayTitle.trim().ifBlank { null },
                        orderIndex = existing?.orderIndex ?: 0,
                        workoutTypeId = type.id,
                        targetSets = sets.toIntOrNull(),
                        targetReps = reps.toIntOrNull(),
                        targetDurationMillis = durationMinutes.toDoubleOrNull()?.let { (it * 60_000).toLong() },
                        targetDistanceMeters = distanceKm.toDoubleOrNull()?.let { it * 1000.0 },
                        note = note.trim().ifBlank { null },
                    )
                )
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun RenameProgramDialog(
    initialName: String,
    initialDescription: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var nameError by remember { mutableStateOf<String?>(null) }
    val enterNameMsg = stringResource(R.string.enterName)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editTrainingProgram)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text(stringResource(R.string.programName)) },
                    isError = nameError != null,
                    supportingText = nameError?.let { e -> { Text(e) } },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.programDescriptionOptional)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.trim().length <= 2) { nameError = enterNameMsg; return@TextButton }
                onConfirm(name.trim(), description.trim())
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
