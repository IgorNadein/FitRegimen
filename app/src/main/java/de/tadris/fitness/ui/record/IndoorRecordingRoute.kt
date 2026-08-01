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
package de.tadris.fitness.ui.record

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.DefaultValueFormatter
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.RecordingType
import de.tadris.fitness.data.WorkoutTypeManager
import de.tadris.fitness.recording.RecordingState
import de.tadris.fitness.recording.StopReason
import de.tadris.fitness.recording.event.RecordingStateChangeEvent
import de.tadris.fitness.util.NotificationHelper
import de.tadris.fitness.util.unit.UnitUtils

@Composable
fun IndoorRecordingRoute(
    workoutTypeId: String,
    isLaunchAction: Boolean,
    onFinish: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToIntervalSets: () -> Unit,
    onWorkoutFinished: (RecordedWorkoutResult?) -> Unit = {},
    autoStopGoal: RecordingAutoStopGoal? = null,
) {
    val context = LocalContext.current
    val vm: IndoorRecordingViewModel = viewModel()
    val workoutDao = remember { Instance.getInstance(context).db.workoutDao() }
    val eventAtRouteEntry = remember { RecordingStateChangeEvent.getCurrent() }
    var finishResultDelivered by remember { mutableStateOf(false) }
    var autoStopTriggered by remember(autoStopGoal) { mutableStateOf(false) }
    val recordingState by vm.recordingState.collectAsState()
    val recordingValues by vm.values.collectAsState()

    LaunchedEffect(autoStopGoal, recordingState, recordingValues) {
        val goal = autoStopGoal ?: return@LaunchedEffect
        if (
            !autoStopTriggered &&
            recordingState == RecordingState.RUNNING &&
            recordingValues.currentState == RecordingState.RUNNING &&
            goal.isReached(
                activeDurationMillis = recordingValues.activeDuration,
                recordedDistanceMeters = recordingValues.distance,
                recordedRepetitions = recordingValues.repetitions,
            )
        ) {
            autoStopTriggered = true
            vm.stop(StopReason.AUTO_STOP)
        }
    }

    val finishWithResult: () -> Unit = {
        if (!finishResultDelivered) {
            finishResultDelivered = true
            val finishEvent = RecordingStateChangeEvent.getCurrent()
            val belongsToThisRoute = finishEvent != eventAtRouteEntry
            val result = if (belongsToThisRoute) {
                RecordedWorkoutResultResolver.resolve(finishEvent, workoutDao)
            } else {
                null
            }
            onWorkoutFinished(result)
            onFinish()
        }
    }

    val workoutType = remember(workoutTypeId) {
        WorkoutTypeManager.getInstance().getWorkoutTypeById(context, workoutTypeId)
    }

    LaunchedEffect(workoutTypeId) {
        vm.init(workoutType, isLaunchAction)
        NotificationHelper.requestNotificationPermissionIfNecessary(context as Activity)
    }

    val showOnLockScreen = remember { Instance.getInstance(context).userPreferences.showOnLockScreen }
    RecordingWindowEffects(showOnLockScreen)

    // Activity recognition permission (Android Q+)
    val activityPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.restartService()
    }

    var showActivityConsentDialog by remember {
        mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !vm.hasServicePermission())
    }
    if (showActivityConsentDialog) {
        AlertDialog(
            onDismissRequest = { showActivityConsentDialog = false; vm.finish() },
            title = { Text(stringResource(R.string.recordingPermissionNotGrantedTitle)) },
            text = { Text(stringResource(R.string.recordingActivityPermissionMessage)) },
            confirmButton = {
                TextButton(onClick = {
                    showActivityConsentDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        activityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    }
                }) { Text(stringResource(R.string.actionGrant)) }
            },
            dismissButton = {
                TextButton(onClick = { showActivityConsentDialog = false; vm.finish() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Bluetooth permission + Material device chooser
    var bluetoothPermissionsGranted by remember {
        mutableStateOf(hasBluetoothPickerPermissions(context))
    }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        bluetoothPermissionsGranted = hasBluetoothPickerPermissions(context)
        if (!bluetoothPermissionsGranted) vm.dismissChooseBluetoothDevice()
    }

    val showChooseBluetoothDevice by vm.showChooseBluetoothDevice.collectAsState()
    LaunchedEffect(showChooseBluetoothDevice, bluetoothPermissionsGranted) {
        if (showChooseBluetoothDevice && !bluetoothPermissionsGranted &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            bluetoothPermissionLauncher.launch(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            )
        }
    }
    if (showChooseBluetoothDevice && bluetoothPermissionsGranted) {
        BluetoothDevicePickerSheet(
            onDismiss = vm::dismissChooseBluetoothDevice,
            onBluetoothUnavailable = {
                vm.dismissChooseBluetoothDevice()
                runCatching {
                    context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
            },
            onDeviceSelected = vm::onBtDeviceSelected,
        )
    }

    // Info slot selection sheet
    val selectInfoSlot by vm.selectInfoDialogSlot.collectAsState()
    selectInfoSlot?.let { slot ->
        val recordingType = RecordingType.findById(workoutType.recordingType)
        RecordingInformationPickerSheet(
            recordingType = recordingType,
            slot = slot,
            onDismiss = vm::dismissSelectInfoDialog,
            onInformationSelected = { info -> vm.onInfoSlotSelected(slot, info) },
        )
    }

    IndoorRecordingScreen(
        vm = vm,
        onFinish = finishWithResult,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToIntervalSets = onNavigateToIntervalSets,
    )
}

@Composable
fun IndoorRecordingScreen(
    vm: IndoorRecordingViewModel,
    onFinish: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToIntervalSets: () -> Unit,
) {
    val chartEntries by vm.chartEntries.collectAsState()
    val repetitions by vm.repetitions.collectAsState()
    val workoutType by vm.currentWorkoutType.collectAsState()

    val onBackgroundColor = MaterialTheme.colorScheme.onBackground.toArgb()
    val frequencyColor = MaterialTheme.colorScheme.primary.toArgb()
    val intensityColor = MaterialTheme.colorScheme.tertiary.toArgb()
    val frequencyLabel = stringResource(R.string.workoutFrequency)
    val intensityLabel = stringResource(R.string.workoutIntensity)

    val repetitionsLabel = workoutType?.let {
        LocalResources.current.getQuantityString(it.repeatingExerciseName, repetitions)
    }.orEmpty()

    RecordingScaffold(
        vm = vm,
        onFinish = onFinish,
        onOpenSettings = onNavigateToSettings,
        onManageIntervalSets = onNavigateToIntervalSets,
        mainContent = {
            IndoorChartContent(
                repetitionsLabel = repetitionsLabel,
                repetitions = repetitions,
                chartEntries = chartEntries,
                onBackgroundColor = onBackgroundColor,
                frequencyColor = frequencyColor,
                intensityColor = intensityColor,
                frequencyLabel = frequencyLabel,
                intensityLabel = intensityLabel,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

@Composable
private fun IndoorChartContent(
    repetitionsLabel: String,
    repetitions: Int,
    chartEntries: List<ChartEntry>,
    onBackgroundColor: Int,
    frequencyColor: Int,
    intensityColor: Int,
    frequencyLabel: String,
    intensityLabel: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 80.dp),
    ) {
        AndroidView(
            factory = { ctx ->
                LineChart(ctx).apply {
                    isScaleXEnabled = false
                    isScaleYEnabled = false
                    axisLeft.textColor = onBackgroundColor
                    axisRight.textColor = onBackgroundColor
                    xAxis.textColor = onBackgroundColor
                    legend.textColor = onBackgroundColor
                    description.textColor = onBackgroundColor
                    isHighlightPerDragEnabled = false
                    isHighlightPerTapEnabled = false
                    axisLeft.valueFormatter = object : DefaultValueFormatter(1) {
                        override fun getFormattedValue(value: Float): String =
                            super.getFormattedValue(value) + " " + UnitUtils.unitHertzShort
                    }
                    xAxis.valueFormatter = object : DefaultValueFormatter(1) {
                        override fun getFormattedValue(value: Float): String =
                            super.getFormattedValue(value) + " min"
                    }
                    axisLeft.setDrawGridLines(false)
                    axisRight.setDrawGridLines(false)
                    description.text = ""
                    setNoDataText("")
                }
            },
            update = { chart ->
                if (chartEntries.isEmpty()) {
                    chart.clear()
                    return@AndroidView
                }
                val freqEntries = chartEntries.map { Entry(it.timeMin, it.frequency) }
                val intEntries = chartEntries.filter { it.intensity > 0 }
                    .map { Entry(it.timeMin, it.intensity) }

                fun makeDataset(entries: List<Entry>, name: String, color: Int) =
                    LineDataSet(entries, name).apply {
                        this.color = color
                        valueTextColor = color
                        setDrawCircles(false)
                        lineWidth = 4f
                        highlightLineWidth = 2.5f
                        mode = LineDataSet.Mode.HORIZONTAL_BEZIER
                    }

                val lineData = LineData().apply {
                    addDataSet(makeDataset(freqEntries, frequencyLabel, frequencyColor).apply {
                        axisDependency = YAxis.AxisDependency.LEFT
                    })
                    addDataSet(makeDataset(intEntries, intensityLabel, intensityColor).apply {
                        axisDependency = YAxis.AxisDependency.RIGHT
                    })
                    setDrawValues(false)
                }
                chart.data = lineData
                chart.invalidate()
            },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = repetitions.toString(),
                fontSize = 120.sp,
                fontWeight = FontWeight.Light,
                lineHeight = 120.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = repetitionsLabel,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
