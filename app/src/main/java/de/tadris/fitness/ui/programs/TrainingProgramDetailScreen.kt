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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.Intent
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.PROGRAM_COMPLETION_STATUS_COMPLETED
import de.tadris.fitness.data.PROGRAM_COMPLETION_STATUS_SKIPPED
import de.tadris.fitness.data.ProgramCompletion
import de.tadris.fitness.data.ProgramScheduling
import de.tadris.fitness.data.ProgramTask
import de.tadris.fitness.data.WorkoutType
import de.tadris.fitness.data.WorkoutTypeManager
import de.tadris.fitness.data.backup.ProgramExporter
import de.tadris.fitness.ui.ShareFileActivity
import de.tadris.fitness.util.DataManager
import de.tadris.fitness.util.Icon as WorkoutIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import java.time.LocalDate
import java.time.ZoneId

private enum class TaskStatus { COMPLETED, SKIPPED, MISSED, TODAY, UPCOMING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingProgramDetailScreen(
    id: Long,
    onBack: () -> Unit,
    onEditProgram: () -> Unit,
    onStartTask: (ProgramTask) -> Unit,
) {
    val context = LocalContext.current
    val dao = remember { Instance.getInstance(context).db.trainingProgramDao() }
    var program by remember { mutableStateOf(dao.findProgramById(id)) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val tasks = remember(refreshKey) { dao.getTasksOfProgram(id) }
    val workoutTypes = remember { WorkoutTypeManager.getInstance().getAllTypesSorted(context) }

    fun refresh() {
        refreshKey++
        program = dao.findProgramById(id)
    }

    val current = program ?: return
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(refreshKey) { LocalDate.now(zone) }
    var exportError by remember { mutableStateOf<String?>(null) }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    fun exportProgram() {
        Thread {
            try {
                val container = ProgramExporter.buildContainer(context, current)
                val file = DataManager.createSharableFile(context, "${current.name}.fitregimen.json")
                file.outputStream().use { ProgramExporter.write(container, it) }
                val uri = DataManager.provide(context, file)
                mainHandler.post {
                    context.startActivity(
                        Intent(context, ShareFileActivity::class.java)
                            .putExtra(ShareFileActivity.EXTRA_FILE_URI, uri.toString())
                    )
                }
            } catch (e: Exception) {
                mainHandler.post {
                    exportError = e.message ?: context.getString(R.string.errorExportFailed)
                }
            }
        }.start()
    }

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
                    IconButton(onClick = { exportProgram() }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.exportProgram))
                    }
                    IconButton(onClick = onEditProgram) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.editTrainingProgram))
                    }
                },
            )
        },
    ) { padding ->
        val startDate = current.startDate
        if (startDate == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text(
                        text = stringResource(R.string.startProgramMessage),
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = {
                        val todayMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()
                        val updated = current.copy(startDate = todayMillis)
                        dao.updateProgram(updated)
                        refresh()
                    }) { Text(stringResource(R.string.startProgram)) }
                }
            }
            return@Scaffold
        }

        val todayDayIndex = ProgramScheduling.dayIndexOf(current, today, zone) ?: 0
        val currentCycleStart = today.minusDays(todayDayIndex.toLong())

        val grouped = tasks.groupBy { it.dayIndex }

        LazyColumn(contentPadding = padding) {
            for (dayIndex in 0 until current.cycleLengthDays) {
                val dayDate = currentCycleStart.plusDays(dayIndex.toLong())
                val isToday = dayIndex == todayDayIndex
                val dayTasks = grouped[dayIndex].orEmpty()
                item(key = "day_$dayIndex") {
                    DayHeader(
                        title = dayTasks.firstOrNull()?.dayTitle?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.dayNumber, dayIndex + 1),
                        date = dayDate,
                        isToday = isToday,
                        isRestDay = dayTasks.isEmpty(),
                    )
                }
                items(dayTasks, key = { it.id }) { task ->
                    val occurrenceMillis = ProgramScheduling.occurrenceMillis(dayDate, zone)
                    val completion = dao.findCompletion(task.id, occurrenceMillis)
                    val status = when {
                        completion?.status == PROGRAM_COMPLETION_STATUS_COMPLETED -> TaskStatus.COMPLETED
                        completion?.status == PROGRAM_COMPLETION_STATUS_SKIPPED -> TaskStatus.SKIPPED
                        dayDate.isBefore(today) -> TaskStatus.MISSED
                        dayDate.isEqual(today) -> TaskStatus.TODAY
                        else -> TaskStatus.UPCOMING
                    }
                    val type = workoutTypes.firstOrNull { it.id == task.workoutTypeId }
                    TaskRow(
                        task = task,
                        type = type,
                        status = status,
                        onStart = { onStartTask(task) },
                        onSkip = {
                            dao.insertCompletion(
                                ProgramCompletion(
                                    programTaskId = task.id,
                                    occurrenceDate = occurrenceMillis,
                                    status = PROGRAM_COMPLETION_STATUS_SKIPPED,
                                )
                            )
                            refresh()
                        },
                    )
                }
            }
        }
    }

    exportError?.let { msg ->
        AlertDialog(
            onDismissRequest = { exportError = null },
            title = { Text(stringResource(R.string.error)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { exportError = null }) { Text(stringResource(R.string.okay)) }
            },
        )
    }
}

@Composable
private fun DayHeader(title: String, date: LocalDate, isToday: Boolean, isRestDay: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (isToday) {
            Text(
                text = stringResource(R.string.today),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    if (isRestDay) {
        Text(
            text = stringResource(R.string.restDay),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun TaskRow(
    task: ProgramTask,
    type: WorkoutType?,
    status: TaskStatus,
    onStart: () -> Unit,
    onSkip: () -> Unit,
) {
    var showSkipConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                type?.let {
                    Icon(
                        painter = painterResource(WorkoutIcon.getIcon(it.icon)),
                        contentDescription = null,
                        tint = Color(it.color),
                    )
                }
                Column {
                    Text(type?.title ?: task.workoutTypeId, style = MaterialTheme.typography.bodyLarge)
                    Text(statusLabel(status), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (status == TaskStatus.TODAY) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showSkipConfirm = true }) { Text(stringResource(R.string.skipTask)) }
                    TextButton(onClick = onStart) { Text(stringResource(R.string.startTask)) }
                }
            }
        }
    }

    if (showSkipConfirm) {
        AlertDialog(
            onDismissRequest = { showSkipConfirm = false },
            title = { Text(stringResource(R.string.skipTask)) },
            confirmButton = {
                TextButton(onClick = { showSkipConfirm = false; onSkip() }) { Text(stringResource(R.string.skipTask)) }
            },
            dismissButton = {
                TextButton(onClick = { showSkipConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun statusLabel(status: TaskStatus): String = when (status) {
    TaskStatus.COMPLETED -> stringResource(R.string.statusCompleted)
    TaskStatus.SKIPPED -> stringResource(R.string.statusSkipped)
    TaskStatus.MISSED -> stringResource(R.string.statusMissed)
    TaskStatus.TODAY -> stringResource(R.string.today)
    TaskStatus.UPCOMING -> stringResource(R.string.statusUpcoming)
}
