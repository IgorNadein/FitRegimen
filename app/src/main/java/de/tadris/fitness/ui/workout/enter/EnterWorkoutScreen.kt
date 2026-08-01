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

package de.tadris.fitness.ui.workout.enter

import android.app.Application
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.ui.components.WorkoutTypePickerDialog
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Calendar
import de.tadris.fitness.util.Icon as WorkoutIcon

@Composable
fun EnterWorkoutScreen(
    id: Long,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onAddWorkoutType: () -> Unit = {},
) {
    val context = LocalContext.current
    val vm: EnterWorkoutViewModel = viewModel(
        key = "enter_workout_$id",
        factory = viewModelFactory {
            initializer {
                EnterWorkoutViewModel(context.applicationContext as Application, id)
            }
        }
    )
    val state by vm.uiState.collectAsState()

    LaunchedEffect(state) {
        if (state is EnterWorkoutUiState.Saved) {
            onSaved((state as EnterWorkoutUiState.Saved).workoutId)
        }
    }

    when (val s = state) {
        is EnterWorkoutUiState.Loading -> {}
        is EnterWorkoutUiState.Ready -> EnterWorkoutContent(state = s, vm = vm, onBack = onBack, onAddWorkoutType = onAddWorkoutType)
        is EnterWorkoutUiState.Saved -> {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnterWorkoutContent(
    state: EnterWorkoutUiState.Ready,
    vm: EnterWorkoutViewModel,
    onBack: () -> Unit,
    onAddWorkoutType: () -> Unit,
) {
    val form = state.form
    val context = LocalContext.current
    val instance = remember { Instance.getInstance(context) }

    var showTypeDialog by remember { mutableStateOf(false) }
    var showDateDialog by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }
    var showDurationDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        if (state.snackbarMessage != null) {
            snackbarHostState.showSnackbar(state.snackbarMessage)
            vm.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (form.isEditMode) R.string.editWorkout else R.string.enterWorkout))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.save() }) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.actionSave)
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            FormListItem(
                label = stringResource(R.string.type),
                value = form.workoutType.title,
                onClick = { showTypeDialog = true },
                leadingContent = {
                    Icon(
                        painter = painterResource(WorkoutIcon.getIcon(form.workoutType.icon)),
                        contentDescription = null,
                        tint = Color(form.workoutType.color),
                    )
                },
            )
            OutlinedTextField(
                value = form.distanceText,
                onValueChange = { vm.setDistanceText(it) },
                label = { Text(stringResource(R.string.workoutDistance)) },
                suffix = { Text(form.unitSystem.getLongDistanceUnit()) },
                isError = state.distanceError,
                supportingText = if (state.distanceError) {
                    { Text(stringResource(R.string.errorEnterValidNumber)) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            FormListItem(
                label = stringResource(R.string.workoutDate),
                value = SimpleDateFormat.getDateInstance().format(form.start.time),
                onClick = { showDateDialog = true },
            )
            FormListItem(
                label = stringResource(R.string.workoutStartTime),
                value = SimpleDateFormat.getTimeInstance().format(form.start.time),
                onClick = { showTimeDialog = true },
            )
            FormListItem(
                label = stringResource(R.string.workoutDuration),
                value = instance.distanceUnitUtils.getHourMinuteSecondTime(form.durationMillis),
                onClick = { showDurationDialog = true },
            )
            OutlinedTextField(
                value = form.comment,
                onValueChange = { vm.setComment(it) },
                label = { Text(stringResource(R.string.comment)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            )
        }
    }

    if (showTypeDialog) {
        WorkoutTypePickerDialog(
            types = state.allWorkoutTypes,
            onDismiss = { showTypeDialog = false },
            onSelect = { type ->
                vm.setWorkoutType(type)
                showTypeDialog = false
            },
            onAddType = {
                showTypeDialog = false
                onAddWorkoutType()
            },
        )
    }

    if (showDateDialog) {
        val start = form.start
        WorkoutDatePickerDialog(
            initialYear = start.get(Calendar.YEAR),
            initialMonth = start.get(Calendar.MONTH),
            initialDay = start.get(Calendar.DAY_OF_MONTH),
            onDismiss = { showDateDialog = false },
            onConfirm = { year, month, day ->
                vm.setDate(year, month, day)
                showDateDialog = false
            },
        )
    }

    if (showTimeDialog) {
        val start = form.start
        WorkoutTimePickerDialog(
            initialHour = start.get(Calendar.HOUR_OF_DAY),
            initialMinute = start.get(Calendar.MINUTE),
            use24HourFormat = DateFormat.is24HourFormat(context),
            onDismiss = { showTimeDialog = false },
            onConfirm = { hour, minute ->
                vm.setTime(hour, minute)
                showTimeDialog = false
            },
        )
    }

    if (showDurationDialog) {
        DurationPickerDialog(
            initialDuration = form.durationMillis,
            onDismiss = { showDurationDialog = false },
            onConfirm = { duration ->
                vm.setDuration(duration)
                showDurationDialog = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutDatePickerDialog(
    initialYear: Int,
    /** Zero-based, matching [Calendar.MONTH] and [EnterWorkoutViewModel.setDate]. */
    initialMonth: Int,
    initialDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (year: Int, month: Int, day: Int) -> Unit,
) {
    val initialDateMillis = remember(initialYear, initialMonth, initialDay) {
        localDateToDatePickerMillis(initialYear, initialMonth, initialDay)
    }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { selectedMillis ->
                        val selectedDate = datePickerMillisToLocalDate(selectedMillis)
                        onConfirm(
                            selectedDate.year,
                            selectedDate.monthValue - 1,
                            selectedDate.dayOfMonth,
                        )
                    }
                },
                enabled = pickerState.selectedDateMillis != null,
            ) {
                Text(stringResource(R.string.okay))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    ) {
        DatePicker(
            state = pickerState,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    use24HourFormat: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = use24HourFormat,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workoutStartTime)) },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TimePicker(state = pickerState)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour, pickerState.minute) }) {
                Text(stringResource(R.string.okay))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** Material DatePicker represents calendar dates as UTC-midnight epoch milliseconds. */
internal fun localDateToDatePickerMillis(year: Int, zeroBasedMonth: Int, day: Int): Long =
    LocalDate.of(year, zeroBasedMonth + 1, day)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

internal fun datePickerMillisToLocalDate(selectedDateMillis: Long): LocalDate =
    Instant.ofEpochMilli(selectedDateMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()

@Composable
private fun DurationPickerDialog(
    initialDuration: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var hours by remember { mutableIntStateOf((initialDuration / 1000 / 60 / 60).toInt()) }
    var minutes by remember { mutableIntStateOf((initialDuration / 1000 / 60 % 60).toInt()) }
    var seconds by remember { mutableIntStateOf((initialDuration / 1000 % 60).toInt()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setDuration)) },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DurationNumberField(
                    value = hours,
                    onValueChange = { hours = it.coerceIn(0, 24) },
                    label = stringResource(R.string.timeHourShort),
                    modifier = Modifier.weight(1f),
                )
                DurationNumberField(
                    value = minutes,
                    onValueChange = { minutes = it.coerceIn(0, 59) },
                    label = stringResource(R.string.timeMinuteShort),
                    modifier = Modifier.weight(1f),
                )
                DurationNumberField(
                    value = seconds,
                    onValueChange = { seconds = it.coerceIn(0, 59) },
                    label = stringResource(R.string.timeSecondsShort),
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L)
            }) {
                Text(stringResource(R.string.okay))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun FormListItem(
    label: String,
    value: String,
    onClick: () -> Unit,
    leadingContent: @Composable (() -> Unit)? = null,
) {
    ListItem(
        overlineContent = { Text(label, style = MaterialTheme.typography.labelLarge) },
        headlineContent = { Text(value, style = MaterialTheme.typography.bodyLarge) },
        leadingContent = leadingContent,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun DurationNumberField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var tfv by remember {
        mutableStateOf(TextFieldValue(value.toString()))
    }
    OutlinedTextField(
        value = tfv,
        onValueChange = { new ->
            tfv = new
            onValueChange(new.text.toIntOrNull() ?: 0)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier.onFocusChanged { focusState ->
            if (focusState.isFocused) {
                tfv = tfv.copy(selection = TextRange(0, tfv.text.length))
            }
        },
    )
}
