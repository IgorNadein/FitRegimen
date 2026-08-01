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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.CompletionRule
import de.tadris.fitness.data.Exercise
import de.tadris.fitness.data.MeasurementSource
import de.tadris.fitness.data.MetricKind
import de.tadris.fitness.data.ProgramExercise
import de.tadris.fitness.data.ProgramSet
import de.tadris.fitness.data.SetType

/** Editor for one program day: the ordered exercise list with per-exercise planned sets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEditorScreen(
    dayId: Long,
    onBack: () -> Unit,
    onAddExercise: () -> Unit,
) {
    val context = LocalContext.current
    val db = remember { Instance.getInstance(context).db }
    val dao = remember { db.trainingProgramDao() }
    val exerciseDao = remember { db.exerciseDao() }

    var refreshKey by remember { mutableIntStateOf(0) }
    val day = remember(refreshKey) { dao.findDayById(dayId) }
    val entries = remember(refreshKey) { dao.getExercisesOfDay(dayId) }
    val exercisesById = remember(refreshKey) {
        entries.associate { it.exerciseId to exerciseDao.findById(it.exerciseId) }
    }
    val setsByEntry = remember(refreshKey) {
        entries.associate { it.id to dao.getSetsOfProgramExercise(it.id) }
    }

    var editingEntry by remember { mutableStateOf<ProgramExercise?>(null) }

    fun refresh() {
        refreshKey++
    }

    // The picker inserts rows itself; refresh when returning to this screen.
    androidx.compose.runtime.LaunchedEffect(Unit) { refresh() }

    fun moveEntry(from: Int, to: Int) {
        if (to < 0 || to >= entries.size) return
        val reordered = entries.toMutableList().apply { add(to, removeAt(from)) }
        dao.updateProgramExercises(reordered.mapIndexed { index, e -> e.copy(orderIndex = index) })
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        day?.name?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.dayNumber, (day?.orderIndex ?: 0) + 1)
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
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddExercise,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.addExercise)) },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(72.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.hintNoExercisesInDay),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = onAddExercise) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(stringResource(R.string.addExercise))
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    end = 16.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(entries, key = { _, e -> e.id }) { index, entry ->
                    val exercise = exercisesById[entry.exerciseId]
                    val summary = exercise?.let {
                        setsSummary(setsByEntry[entry.id].orEmpty(), it.metricKind)
                    }.orEmpty()
                    ExerciseCard(
                        index = index,
                        name = exercise?.name ?: "?",
                        summary = summary,
                        canMoveUp = index > 0,
                        canMoveDown = index < entries.size - 1,
                        onClick = { editingEntry = entry },
                        onMoveUp = { moveEntry(index, index - 1) },
                        onMoveDown = { moveEntry(index, index + 1) },
                        onDelete = {
                            dao.deleteProgramExercise(entry)
                            refresh()
                        },
                    )
                }
            }
        }
    }

    editingEntry?.let { entry ->
        val exercise = exercisesById[entry.exerciseId]
        if (exercise != null) {
            SetEditorSheet(
                entry = entry,
                exercise = exercise,
                initialSets = setsByEntry[entry.id].orEmpty(),
                onDismiss = { editingEntry = null },
                onSave = {
                    sets,
                    measurementSource,
                    completionRule,
                    prepareSeconds,
                    restSeconds,
                    transitionSeconds,
                    note,
                    ->
                    dao.replaceSetsOfProgramExercise(entry.id, sets)
                    dao.updateProgramExercise(
                        entry.copy(
                            note = note,
                            restSeconds = restSeconds,
                            measurementSource = measurementSource,
                            completionRule = completionRule,
                            prepareSeconds = prepareSeconds,
                            transitionSeconds = transitionSeconds,
                        )
                    )
                    editingEntry = null
                    refresh()
                },
            )
        } else {
            editingEntry = null
        }
    }
}

@Composable
private fun ExerciseCard(
    index: Int,
    name: String,
    summary: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (summary.isNotEmpty()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    if (canMoveUp) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.moveUp)) },
                            onClick = {
                                menuOpen = false
                                onMoveUp()
                            },
                        )
                    }
                    if (canMoveDown) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.moveDown)) },
                            onClick = {
                                menuOpen = false
                                onMoveDown()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Adaptive planned-set editor: shows only the columns the exercise's [MetricKind]
 * declares. Tapping the set-number badge toggles warm-up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetEditorSheet(
    entry: ProgramExercise,
    exercise: Exercise,
    initialSets: List<ProgramSet>,
    onDismiss: () -> Unit,
    onSave: (
        sets: List<ProgramSet>,
        measurementSource: MeasurementSource,
        completionRule: CompletionRule,
        prepareSeconds: Int,
        restSeconds: Int?,
        transitionSeconds: Int?,
        note: String?,
    ) -> Unit,
) {
    val kind = exercise.metricKind
    val availableSources = remember(kind, exercise.workoutTypeId) {
        buildList {
            add(MeasurementSource.USER_INPUT)
            if (kind.usesDuration) add(MeasurementSource.CLOCK)
            if (exercise.workoutTypeId != null) add(MeasurementSource.RECORDER)
        }
    }
    val initialSource = entry.measurementSource.takeIf { it in availableSources }
        ?: MeasurementSource.USER_INPUT
    val rows = remember {
        initialSets.map { SetRowState.from(it) }.toMutableStateList()
    }
    var measurementSource by remember { mutableStateOf(initialSource) }
    var completionRule by remember {
        mutableStateOf(
            entry.completionRule.takeIf { initialSource == entry.measurementSource }
                ?: defaultCompletionRule(initialSource, kind)
        )
    }
    var prepareSeconds by remember { mutableStateOf(entry.prepareSeconds.toString()) }
    var restSeconds by remember { mutableStateOf(entry.restSeconds?.toString() ?: "") }
    var transitionSeconds by remember {
        mutableStateOf(entry.transitionSeconds?.toString() ?: "")
    }
    var note by remember { mutableStateOf(entry.note ?: "") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Set rows are the sole vertical scroll owner. If the sheet also consumes
        // their boundary fling, both animations can keep handing velocity back.
        sheetGesturesEnabled = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(exercise.name, style = MaterialTheme.typography.titleLarge)

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.exerciseExecutionTitle),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.exerciseExecutionHint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        availableSources.forEachIndexed { index, source ->
                            SegmentedButton(
                                selected = measurementSource == source,
                                onClick = {
                                    measurementSource = source
                                    completionRule = defaultCompletionRule(source, kind)
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = availableSources.size,
                                ),
                                label = { Text(measurementSourceLabel(source)) },
                            )
                        }
                    }
                    Text(
                        text = stringResource(
                            R.string.exerciseCompletionSummary,
                            completionRuleLabel(completionRule),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = stringResource(R.string.warmupHint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            rows.forEachIndexed { index, row ->
                SetRow(
                    index = index,
                    row = row,
                    kind = kind,
                    onToggleWarmup = {
                        rows[index] = row.copy(
                            type = if (row.type == SetType.WARMUP) SetType.NORMAL else SetType.WARMUP
                        )
                    },
                    onChange = { rows[index] = it },
                    onDelete = { rows.removeAt(index) },
                )
            }

            TextButton(onClick = {
                rows.add(rows.lastOrNull()?.copy(type = SetType.NORMAL) ?: SetRowState.default(kind))
            }) { Text(stringResource(R.string.addSet)) }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.exerciseTimingTitle),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.exerciseTimingHint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TimingField(
                        value = prepareSeconds,
                        onValueChange = { prepareSeconds = sanitizeSeconds(it) },
                        label = stringResource(R.string.prepareSecondsLabel),
                        hint = stringResource(R.string.prepareSecondsHint),
                    )
                    TimingField(
                        value = restSeconds,
                        onValueChange = { restSeconds = sanitizeSeconds(it) },
                        label = stringResource(R.string.restBetweenSetsLabel),
                        hint = stringResource(R.string.restBetweenSetsHint),
                    )
                    TimingField(
                        value = transitionSeconds,
                        onValueChange = { transitionSeconds = sanitizeSeconds(it) },
                        label = stringResource(R.string.transitionSecondsLabel),
                        hint = stringResource(R.string.transitionSecondsHint),
                    )
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.taskNoteOptional)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = {
                    onSave(
                        rows.mapIndexed { index, row -> row.toProgramSet(entry.id, index, kind) },
                        measurementSource,
                        completionRule,
                        prepareSeconds.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                        restSeconds.toIntOrNull()?.takeIf { it > 0 },
                        transitionSeconds.toIntOrNull()?.takeIf { it > 0 },
                        note.trim().ifBlank { null },
                    )
                }) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

@Composable
private fun TimingField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            suffix = { Text(stringResource(R.string.secondsShort)) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            ),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.width(104.dp),
        )
    }
}

private fun sanitizeSeconds(value: String): String = value.filter(Char::isDigit)

private fun defaultCompletionRule(
    source: MeasurementSource,
    kind: MetricKind,
): CompletionRule = when (source) {
    MeasurementSource.USER_INPUT -> CompletionRule.USER_CONFIRM
    MeasurementSource.CLOCK -> CompletionRule.DURATION_REACHED
    MeasurementSource.RECORDER -> if (
        kind == MetricKind.DURATION || kind == MetricKind.WEIGHT_DURATION
    ) {
        CompletionRule.DURATION_REACHED
    } else {
        CompletionRule.METRIC_TARGET_REACHED
    }
}

@Composable
private fun measurementSourceLabel(source: MeasurementSource): String = when (source) {
    MeasurementSource.USER_INPUT -> stringResource(R.string.exerciseExecutionManual)
    MeasurementSource.CLOCK -> stringResource(R.string.exerciseExecutionTimer)
    MeasurementSource.RECORDER -> stringResource(R.string.exerciseExecutionTracker)
}

@Composable
private fun completionRuleLabel(rule: CompletionRule): String = when (rule) {
    CompletionRule.USER_CONFIRM -> stringResource(R.string.completionRuleUserConfirm)
    CompletionRule.DURATION_REACHED -> stringResource(R.string.completionRuleDurationReached)
    CompletionRule.METRIC_TARGET_REACHED -> stringResource(R.string.completionRuleMetricTargetReached)
}

data class SetRowState(
    val type: SetType,
    val reps: String,
    val repsMax: String,
    val weight: String,
    val minutes: String,
    val kilometers: String,
) {
    fun toProgramSet(programExerciseId: Long, orderIndex: Int, kind: MetricKind) = ProgramSet(
        programExerciseId = programExerciseId,
        orderIndex = orderIndex,
        type = type,
        reps = if (kind.usesReps) reps.toIntOrNull() else null,
        repsMax = if (kind.usesReps) repsMax.toIntOrNull() else null,
        weightKg = if (kind.usesWeight) weight.replace(',', '.').toDoubleOrNull() else null,
        durationMillis = if (kind.usesDuration) {
            minutes.replace(',', '.').toDoubleOrNull()?.let { (it * 60_000).toLong() }
        } else null,
        distanceMeters = if (kind.usesDistance) {
            kilometers.replace(',', '.').toDoubleOrNull()?.let { it * 1000.0 }
        } else null,
    )

    companion object {
        fun from(set: ProgramSet) = SetRowState(
            type = set.type,
            reps = set.reps?.toString() ?: "",
            repsMax = set.repsMax?.toString() ?: "",
            weight = set.weightKg?.let { formatDecimal(it) } ?: "",
            minutes = set.durationMillis?.let { formatMinutes(it) } ?: "",
            kilometers = set.distanceMeters?.let { formatDecimal(it / 1000.0) } ?: "",
        )

        fun default(kind: MetricKind) = SetRowState(
            type = SetType.NORMAL,
            reps = if (kind.usesReps) "10" else "",
            repsMax = "",
            weight = "",
            minutes = if (kind.usesDuration) "1" else "",
            kilometers = "",
        )
    }
}

@Composable
private fun SetRow(
    index: Int,
    row: SetRowState,
    kind: MetricKind,
    onToggleWarmup: () -> Unit,
    onChange: (SetRowState) -> Unit,
    onDelete: () -> Unit,
) {
    val isWarmup = row.type == SetType.WARMUP
    val warmupHint = stringResource(R.string.warmupHint)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isWarmup) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            contentColor = if (isWarmup) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isWarmup) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (isWarmup) {
                        MaterialTheme.colorScheme.onTertiary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .toggleable(
                            value = isWarmup,
                            role = Role.Checkbox,
                            onValueChange = { onToggleWarmup() },
                        ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text((index + 1).toString(), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "${stringResource(R.string.setsHeader)} ${index + 1}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (isWarmup) {
                        Text(
                            text = "✓ $warmupHint",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                }
            }

            if (kind.usesReps) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    NumberField(
                        value = row.reps,
                        onValueChange = { onChange(row.copy(reps = it)) },
                        label = stringResource(R.string.repsFrom),
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = row.repsMax,
                        onValueChange = { onChange(row.copy(repsMax = it)) },
                        label = stringResource(R.string.repsTo),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (kind.usesWeight || kind.usesDistance || kind.usesDuration) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (kind.usesWeight) {
                        NumberField(
                            value = row.weight,
                            onValueChange = { onChange(row.copy(weight = it)) },
                            label = stringResource(R.string.weightKgLabel),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (kind.usesDistance) {
                        NumberField(
                            value = row.kilometers,
                            onValueChange = { onChange(row.copy(kilometers = it)) },
                            label = stringResource(R.string.kilometersLabel),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (kind.usesDuration) {
                        NumberField(
                            value = row.minutes,
                            onValueChange = { onChange(row.copy(minutes = it)) },
                            label = stringResource(R.string.minutesLabel),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
        ),
        modifier = modifier,
    )
}
