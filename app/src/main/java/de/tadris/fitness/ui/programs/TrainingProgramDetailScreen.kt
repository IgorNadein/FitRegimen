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

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.Exercise
import de.tadris.fitness.data.PROGRAM_COMPLETION_STATUS_COMPLETED
import de.tadris.fitness.data.PROGRAM_COMPLETION_STATUS_SKIPPED
import de.tadris.fitness.data.ProgramCompletion
import de.tadris.fitness.data.ProgramExercise
import de.tadris.fitness.data.ProgramOccurrenceHistoryRepository
import de.tadris.fitness.data.ProgramScheduling
import de.tadris.fitness.data.ProgramSet
import de.tadris.fitness.data.SetLog
import de.tadris.fitness.data.SetType
import de.tadris.fitness.data.Workout
import de.tadris.fitness.data.backup.ProgramExporter
import de.tadris.fitness.data.preferences.UserMeasurements
import de.tadris.fitness.ui.ShareFileActivity
import de.tadris.fitness.util.DataManager
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Program schedule: a 7-day date strip over the selected day's exercise checklist.
 * Strength exercises quick-complete via checkbox or log actual sets in a sheet;
 * recordable (cardio) exercises launch the recorder or link an existing workout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingProgramDetailScreen(
    id: Long,
    onBack: () -> Unit,
    onEditProgram: () -> Unit,
    onStartRecording: (workoutTypeId: String) -> Unit,
    onStartGuidedWorkout: (dayId: Long, occurrenceDate: Long) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val db = remember { Instance.getInstance(context).db }
    val dao = remember { db.trainingProgramDao() }
    val exerciseDao = remember { db.exerciseDao() }
    val historyRepository = remember(db, context) {
        ProgramOccurrenceHistoryRepository(
            db = db,
            userWeightKg = { UserMeasurements.from(context).weight.toDouble() },
            profileSnapshot = {
                Instance.getInstance(context).fitnessProfileRepository.captureActiveSnapshot()
            },
        )
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var refreshKey by remember { mutableIntStateOf(0) }
    val program = remember(refreshKey) { dao.findProgramById(id) }
    val days = remember(refreshKey) { dao.getDaysOfProgram(id) }

    val zone = remember { ZoneId.systemDefault() }
    val today = remember(refreshKey) { LocalDate.now(zone) }

    var menuOpen by remember { mutableStateOf(false) }
    var showStopDialog by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var loggingTarget by remember { mutableStateOf<LoggingTarget?>(null) }
    var linkTarget by remember { mutableStateOf<LinkTarget?>(null) }
    var completionToRemove by remember { mutableStateOf<ProgramCompletion?>(null) }
    var showActiveSessionCompletionNotice by remember { mutableStateOf(false) }
    var descriptionExpanded by rememberSaveable(id) { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val saveProgramLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { targetUri ->
        val programToSave = program
        if (targetUri == null || programToSave == null) {
            return@rememberLauncherForActivityResult
        }
        Thread {
            try {
                val container = ProgramExporter.buildContainer(context, programToSave)
                val output = context.contentResolver.openOutputStream(targetUri)
                    ?: throw java.io.IOException(resources.getString(R.string.errorExportFailed))
                output.use { ProgramExporter.write(container, it) }
                mainHandler.post {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(resources.getString(R.string.savedSuccessfully))
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    exportError = e.message ?: resources.getString(R.string.errorExportFailed)
                }
            }
        }.start()
    }

    fun refresh() {
        refreshKey++
    }

    fun showHistoryMessage(messageRes: Int) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(resources.getString(messageRes))
        }
    }

    val current = program ?: return

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
                    exportError = e.message ?: resources.getString(R.string.errorExportFailed)
                }
            }
        }.start()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    IconButton(onClick = { exportProgram() }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.exportProgram))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.moreProgramActions),
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.saveProgramFile)) },
                                onClick = {
                                    menuOpen = false
                                    saveProgramLauncher.launch("${current.name}.fitregimen.json")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editTrainingProgram)) },
                                onClick = { menuOpen = false; onEditProgram() },
                            )
                            if (current.startDate != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.stopProgram)) },
                                    onClick = { menuOpen = false; showStopDialog = true },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val startDate = current.startDate
        val description = current.description?.takeIf { it.isNotBlank() }
        if (startDate == null || days.isEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 12.dp,
                    end = 16.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (description != null) {
                    item(key = "description") {
                        ProgramDescriptionCard(
                            description = description,
                            expanded = descriptionExpanded,
                            onExpandedChange = { descriptionExpanded = it },
                        )
                    }
                }
                item(key = "empty_state") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 32.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (days.isEmpty()) R.string.hintNoExercisesInDay else R.string.startProgramMessage
                            ),
                            textAlign = TextAlign.Center,
                        )
                        if (days.isEmpty()) {
                            TextButton(onClick = onEditProgram) {
                                Text(stringResource(R.string.editTrainingProgram))
                            }
                        } else {
                            FilledTonalButton(onClick = {
                                dao.updateProgram(
                                    current.copy(startDate = ProgramScheduling.occurrenceMillis(today, zone))
                                )
                                refresh()
                            }) { Text(stringResource(R.string.startProgram)) }
                        }
                    }
                }
            }
            return@Scaffold
        }

        val dateBounds = ProgramScheduling.firstScheduleBounds(current, days.size, zone)
            ?: return@Scaffold
        var selectedEpochDay by rememberSaveable(id, startDate, days.size) {
            mutableLongStateOf(dateBounds.clamp(today).toEpochDay())
        }
        val selectedDate = dateBounds.clamp(LocalDate.ofEpochDay(selectedEpochDay))

        val dayIndex = ProgramScheduling.boundedDayIndexOf(
            current,
            days.size,
            selectedDate,
            zone,
        )
        val day = dayIndex?.let { days.getOrNull(it) }
        val entries = remember(refreshKey, day?.id) {
            day?.let { dao.getExercisesOfDay(it.id) }.orEmpty()
        }
        val occurrence = ProgramScheduling.occurrenceMillis(selectedDate, zone)
        val completions by remember(day?.id, occurrence) {
            dao.getCompletionsOfDayOccurrenceFlow(day?.id ?: -1L, occurrence)
        }.collectAsState(initial = emptyList())
        val completionByExerciseId = remember(completions) {
            completions.associateBy(ProgramCompletion::programExerciseId)
        }
        val completedExerciseCount = remember(entries, completionByExerciseId) {
            entries.count { entry ->
                completionByExerciseId[entry.id]?.status == PROGRAM_COMPLETION_STATUS_COMPLETED
            }
        }
        val runningSession by remember(day?.id, occurrence) {
            db.trainingSessionDao().observeRunningSessionForDay(day?.id ?: -1L, occurrence)
        }.collectAsState(initial = null)
        val canStartGuidedWorkout = day != null &&
            !day.isRest &&
            entries.isNotEmpty() &&
            (selectedDate == today || runningSession != null)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "date_strip") {
                DateStrip(
                    programStart = dateBounds.first,
                    programEnd = dateBounds.last,
                    today = today,
                    selected = selectedDate,
                    onSelect = { selectedEpochDay = it.toEpochDay() },
                )
            }

            item(key = "day_overview") {
                ProgramDayOverviewCard(
                    programIconName = current.icon,
                    title = day?.name?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.dayNumber, (dayIndex ?: 0) + 1),
                    date = selectedDate,
                    isToday = selectedDate == today,
                    completedCount = completedExerciseCount,
                    exerciseCount = entries.size,
                    canStartWorkout = canStartGuidedWorkout,
                    hasRunningSession = runningSession != null,
                    onStartWorkout = {
                        day?.let { onStartGuidedWorkout(it.id, occurrence) }
                    },
                )
            }

            if (day != null && !day.isRest && entries.isNotEmpty()) {
                item(key = "exercises_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.guided_exercises_label),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(
                                R.string.dayExerciseProgress,
                                completedExerciseCount,
                                entries.size,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (day == null || day.isRest || entries.isEmpty()) {
                item(key = "rest_day") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.restDay),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(entries, key = { it.id }) { entry ->
                    Box(
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 12.dp,
                        ),
                    ) {
                        val exercise = remember(entry.exerciseId, refreshKey) {
                            exerciseDao.findById(entry.exerciseId)
                        } ?: return@items
                        val completion = completionByExerciseId[entry.id]
                        val sets = remember(refreshKey, entry.id) {
                            dao.getSetsOfProgramExercise(entry.id)
                        }
                        ScheduleRow(
                            entry = entry,
                            exercise = exercise,
                            sets = sets,
                            completion = completion,
                            isPast = selectedDate.isBefore(today),
                            isFuture = selectedDate.isAfter(today),
                            onQuickToggle = {
                                if (
                                    completion?.status == PROGRAM_COMPLETION_STATUS_COMPLETED &&
                                    runningSession != null
                                ) {
                                    // The guided snapshot is authoritative until it becomes terminal;
                                    // otherwise finalization would immediately recreate this row.
                                    showActiveSessionCompletionNotice = true
                                } else if (completion?.status == PROGRAM_COMPLETION_STATUS_COMPLETED) {
                                    val hasDetails = completion.workoutId != null ||
                                        dao.getLogsOfCompletion(completion.id).isNotEmpty()
                                    if (hasDetails) {
                                        completionToRemove = completion
                                    } else {
                                        historyRepository.setUncompleted(entry.id, occurrence)
                                        refresh()
                                        showHistoryMessage(R.string.dayWorkoutHistoryRemoved)
                                    }
                                } else {
                                    historyRepository.setCompleted(entry.id, occurrence)
                                    refresh()
                                    showHistoryMessage(R.string.dayWorkoutHistoryUpdated)
                                }
                            },
                            onOpenLog = {
                                loggingTarget = LoggingTarget(entry, exercise, sets, completion, occurrence)
                            },
                            onRecord = exercise.workoutTypeId?.let { typeId ->
                                { onStartRecording(typeId) }
                            },
                            onLinkWorkout = exercise.workoutTypeId?.let { typeId ->
                                { linkTarget = LinkTarget(entry, typeId, occurrence) }
                            },
                            onSkip = {
                                historyRepository.setSkipped(entry.id, occurrence)
                                refresh()
                            },
                        )
                    }
                }
            }

            if (description != null) {
                item(key = "description") {
                    ProgramDescriptionCard(
                        description = description,
                        expanded = descriptionExpanded,
                        onExpandedChange = { descriptionExpanded = it },
                        modifier = Modifier.padding(
                            start = 16.dp,
                            top = 16.dp,
                            end = 16.dp,
                            bottom = 4.dp,
                        ),
                    )
                }
            }
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text(stringResource(R.string.stopProgram)) },
            text = { Text(stringResource(R.string.stopProgramMessage)) },
            confirmButton = {
                TextButton(onClick = {
                    dao.updateProgram(current.copy(startDate = null))
                    showStopDialog = false
                    refresh()
                }) { Text(stringResource(R.string.stopProgram)) }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showActiveSessionCompletionNotice) {
        AlertDialog(
            onDismissRequest = { showActiveSessionCompletionNotice = false },
            title = { Text(stringResource(R.string.guided_completion_locked_title)) },
            text = { Text(stringResource(R.string.guided_completion_locked_message)) },
            confirmButton = {
                TextButton(onClick = { showActiveSessionCompletionNotice = false }) {
                    Text(stringResource(R.string.okay))
                }
            },
        )
    }

    completionToRemove?.let { completion ->
        AlertDialog(
            onDismissRequest = { completionToRemove = null },
            title = { Text(stringResource(R.string.removeCompletionTitle)) },
            text = {
                Text(
                    stringResource(
                        if (completion.workoutId == null) {
                            R.string.removeCompletionMessage
                        } else {
                            R.string.removeCompletionLinkedWorkoutMessage
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        historyRepository.setUncompleted(
                            completion.programExerciseId,
                            completion.occurrenceDate,
                        )
                        completionToRemove = null
                        refresh()
                        showHistoryMessage(R.string.dayWorkoutHistoryRemoved)
                    }
                ) {
                    Text(stringResource(R.string.removeCompletionMark))
                }
            },
            dismissButton = {
                TextButton(onClick = { completionToRemove = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
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

    loggingTarget?.let { target ->
        LogExerciseSheet(
            target = target,
            onDismiss = { loggingTarget = null },
            onFinished = {
                loggingTarget = null
                refresh()
                showHistoryMessage(R.string.dayWorkoutHistoryUpdated)
            },
        )
    }

    linkTarget?.let { target ->
        LinkWorkoutDialog(
            target = target,
            onDismiss = { linkTarget = null },
            onLinked = {
                linkTarget = null
                refresh()
                showHistoryMessage(R.string.dayWorkoutHistoryUpdated)
            },
        )
    }
}

@Composable
private fun ProgramDayOverviewCard(
    programIconName: String?,
    title: String,
    date: LocalDate,
    isToday: Boolean,
    completedCount: Int,
    exerciseCount: Int,
    canStartWorkout: Boolean,
    hasRunningSession: Boolean,
    onStartWorkout: () -> Unit,
) {
    val locale = Locale.forLanguageTag(LocalLocale.current.toLanguageTag())
    val dateLabel = remember(date, locale) {
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
    }
    val progress = if (exerciseCount == 0) 0f else completedCount.toFloat() / exerciseCount
    val isComplete = exerciseCount > 0 && completedCount == exerciseCount

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isComplete) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ProgramIconBadge(
                    iconName = programIconName,
                    size = 42.dp,
                    iconSize = 22.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isToday || isComplete) {
                    Surface(
                        shape = CircleShape,
                        color = if (isComplete) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        contentColor = if (isComplete) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    ) {
                        Text(
                            text = stringResource(
                                if (isComplete) R.string.statusCompleted else R.string.today
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            if (exerciseCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.dayProgressTitle),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = stringResource(
                            R.string.dayExerciseProgress,
                            completedCount,
                            exerciseCount,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            if (canStartWorkout) {
                Button(
                    onClick = onStartWorkout,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        stringResource(
                            if (hasRunningSession) {
                                R.string.guided_resume_workout
                            } else {
                                R.string.guided_start_workout
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramDescriptionCard(
    description: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (expanded) {
                SelectionContainer {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { onExpandedChange(!expanded) }) {
                    Text(
                        stringResource(
                            if (expanded) R.string.collapseDescription else R.string.expandDescription
                        )
                    )
                }
            }
        }
    }
}

private data class LoggingTarget(
    val entry: ProgramExercise,
    val exercise: Exercise,
    val sets: List<ProgramSet>,
    val completion: ProgramCompletion?,
    val occurrence: Long,
)

private data class LinkTarget(
    val entry: ProgramExercise,
    val workoutTypeId: String,
    val occurrence: Long,
)

@Composable
private fun DateStrip(
    programStart: LocalDate,
    programEnd: LocalDate,
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val visibleDayCount = 7
    val locale = Locale.forLanguageTag(LocalLocale.current.toLanguageTag())
    val dayCount = (ChronoUnit.DAYS.between(programStart, programEnd) + 1L)
        .coerceAtLeast(1L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
    val selectedIndex = ChronoUnit.DAYS.between(programStart, selected)
        .toInt()
        .coerceIn(0, dayCount - 1)
    val windowStart = ProgramScheduling.dateStripWindowStart(
        selectedIndex = selectedIndex,
        dayCount = dayCount,
        visibleDayCount = visibleDayCount,
    )
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = windowStart)
    val fullDateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    val monthFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    }
    val todayLabel = stringResource(R.string.today)
    val flingBehavior = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Start,
    )

    LaunchedEffect(programStart, programEnd, windowStart) {
        if (listState.firstVisibleItemIndex != windowStart) {
            listState.animateScrollToItem(windowStart)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selected.format(monthFormatter),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.programDayOfCycle, selectedIndex + 1, dayCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val horizontalPadding = 12.dp
            val itemWidth = (
                maxWidth - horizontalPadding * 2
                ) / visibleDayCount.toFloat()

            LazyRow(
                state = listState,
                flingBehavior = flingBehavior,
                contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(
                    count = dayCount,
                    key = { index -> programStart.plusDays(index.toLong()).toEpochDay() },
                ) { index ->
                    val date = programStart.plusDays(index.toLong())
                    val isSelected = date == selected
                    val isToday = date == today
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .width(itemWidth)
                            .heightIn(min = 56.dp)
                            .minimumInteractiveComponentSize()
                            .semantics {
                                contentDescription = buildString {
                                    append(date.format(fullDateFormatter))
                                    if (isToday) append(", $todayLabel")
                                }
                                this.selected = isSelected
                            }
                            .clickable { onSelect(date) },
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else if (isToday) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            border = if (isToday && !isSelected) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                null
                            },
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .fillMaxSize(),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                )
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    entry: ProgramExercise,
    exercise: Exercise,
    sets: List<ProgramSet>,
    completion: ProgramCompletion?,
    isPast: Boolean,
    isFuture: Boolean,
    onQuickToggle: () -> Unit,
    onOpenLog: () -> Unit,
    onRecord: (() -> Unit)?,
    onLinkWorkout: (() -> Unit)?,
    onSkip: () -> Unit,
) {
    var rowMenuOpen by remember { mutableStateOf(false) }
    val isCompleted = completion?.status == PROGRAM_COMPLETION_STATUS_COMPLETED
    val isSkipped = completion?.status == PROGRAM_COMPLETION_STATUS_SKIPPED

    val statusText = when {
        isCompleted -> stringResource(R.string.statusCompleted)
        isSkipped -> stringResource(R.string.statusSkipped)
        isPast -> stringResource(R.string.statusMissed)
        isFuture -> stringResource(R.string.statusUpcoming)
        else -> null
    }

    val summary = setsSummary(sets, exercise.metricKind)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onRecord == null, onClick = onOpenLog),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = isCompleted, onCheckedChange = { onQuickToggle() })

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (isSkipped) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (summary.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.planPrefix, summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (statusText != null) {
                    ScheduleStatusPill(
                        text = statusText,
                        isCompleted = isCompleted,
                        isSkipped = isSkipped,
                        isPast = isPast,
                        isFuture = isFuture,
                    )
                }
            }

            Box {
                IconButton(onClick = { rowMenuOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more),
                    )
                }
                DropdownMenu(expanded = rowMenuOpen, onDismissRequest = { rowMenuOpen = false }) {
                    if (onRecord == null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.logSets)) },
                            onClick = { rowMenuOpen = false; onOpenLog() },
                        )
                    }
                    if (onLinkWorkout != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.linkWorkout)) },
                            onClick = { rowMenuOpen = false; onLinkWorkout() },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (isCompleted) {
                                        R.string.removeCompletionMark
                                    } else {
                                        R.string.markDone
                                    }
                                )
                            )
                        },
                        onClick = { rowMenuOpen = false; onQuickToggle() },
                    )
                    if (completion == null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.skipTask)) },
                            onClick = { rowMenuOpen = false; onSkip() },
                        )
                    }
                }
            }
        }

        if (onRecord != null && !isCompleted && !isSkipped) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                FilledTonalButton(onClick = onRecord) {
                    Text(stringResource(R.string.recordNow))
                }
            }
        }
    }
}

@Composable
private fun ScheduleStatusPill(
    text: String,
    isCompleted: Boolean,
    isSkipped: Boolean,
    isPast: Boolean,
    isFuture: Boolean,
) {
    val containerColor = when {
        isCompleted -> MaterialTheme.colorScheme.primaryContainer
        isSkipped -> MaterialTheme.colorScheme.secondaryContainer
        isPast -> MaterialTheme.colorScheme.errorContainer
        isFuture -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isCompleted -> MaterialTheme.colorScheme.onPrimaryContainer
        isSkipped -> MaterialTheme.colorScheme.onSecondaryContainer
        isPast -> MaterialTheme.colorScheme.onErrorContainer
        isFuture -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** Log actual performed sets; pre-filled from existing logs or the plan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogExerciseSheet(
    target: LoggingTarget,
    onDismiss: () -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val db = remember { Instance.getInstance(context).db }
    val dao = remember(db) { db.trainingProgramDao() }
    val historyRepository = remember(db, context) {
        ProgramOccurrenceHistoryRepository(
            db = db,
            userWeightKg = { UserMeasurements.from(context).weight.toDouble() },
            profileSnapshot = {
                Instance.getInstance(context).fitnessProfileRepository.captureActiveSnapshot()
            },
        )
    }
    val kind = target.exercise.metricKind

    val existingLogs = remember(target) {
        target.completion?.let { dao.getLogsOfCompletion(it.id) }.orEmpty()
    }
    val rows = remember(target) {
        if (existingLogs.isNotEmpty()) {
            existingLogs.map { log ->
                SetRowState(
                    type = log.type,
                    reps = log.reps?.toString() ?: "",
                    repsMax = "",
                    weight = log.weightKg?.let { formatDecimal(it) } ?: "",
                    minutes = log.durationMillis?.let { formatMinutes(it) } ?: "",
                    kilometers = log.distanceMeters?.let { formatDecimal(it / 1000.0) } ?: "",
                )
            }.toMutableStateList()
        } else {
            target.sets.map { set ->
                SetRowState(
                    type = set.type,
                    reps = (set.repsMax ?: set.reps)?.toString() ?: "",
                    repsMax = "",
                    weight = set.weightKg?.let { formatDecimal(it) } ?: "",
                    minutes = set.durationMillis?.let { formatMinutes(it) } ?: "",
                    kilometers = set.distanceMeters?.let { formatDecimal(it / 1000.0) } ?: "",
                )
            }.toMutableStateList()
        }
    }

    val planSummary = setsSummary(target.sets, kind)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(target.exercise.name, style = MaterialTheme.typography.titleLarge)
            if (planSummary.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.planPrefix, planSummary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            rows.forEachIndexed { index, row ->
                LogRow(
                    index = index,
                    row = row,
                    kind = kind,
                    onChange = { rows[index] = it },
                    onDelete = { rows.removeAt(index) },
                )
            }
            TextButton(onClick = {
                rows.add(rows.lastOrNull() ?: SetRowState.default(kind))
            }) { Text(stringResource(R.string.addSet)) }

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = {
                    val logs = rows.mapIndexed { index, row ->
                        val planned = target.sets.getOrNull(index)
                        SetLog(
                            completionId = 0,
                            orderIndex = index,
                            type = row.type,
                            reps = if (kind.usesReps) row.reps.toIntOrNull() else null,
                            weightKg = if (kind.usesWeight) row.weight.replace(',', '.').toDoubleOrNull() else null,
                            durationMillis = if (kind.usesDuration) {
                                row.minutes.replace(',', '.').toDoubleOrNull()?.let { (it * 60_000).toLong() }
                            } else null,
                            distanceMeters = if (kind.usesDistance) {
                                row.kilometers.replace(',', '.').toDoubleOrNull()?.let { it * 1000.0 }
                            } else null,
                            targetReps = planned?.reps,
                            targetRepsMax = planned?.repsMax,
                            targetWeightKg = planned?.weightKg,
                            targetDurationMillis = planned?.durationMillis,
                            targetDistanceMeters = planned?.distanceMeters,
                        )
                    }
                    historyRepository.replaceLogs(target.entry.id, target.occurrence, logs)
                    onFinished()
                }) { Text(stringResource(R.string.finishLog)) }
            }
        }
    }
}

@Composable
private fun LogRow(
    index: Int,
    row: SetRowState,
    kind: de.tadris.fitness.data.MetricKind,
    onChange: (SetRowState) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = (index + 1).toString(),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (kind.usesReps) {
            LogNumberField(row.reps, { onChange(row.copy(reps = it)) }, stringResource(R.string.repsFrom))
        }
        if (kind.usesWeight) {
            LogNumberField(row.weight, { onChange(row.copy(weight = it)) }, stringResource(R.string.weightKgLabel))
        }
        if (kind.usesDistance) {
            LogNumberField(row.kilometers, { onChange(row.copy(kilometers = it)) }, stringResource(R.string.kilometersLabel))
        }
        if (kind.usesDuration) {
            LogNumberField(row.minutes, { onChange(row.copy(minutes = it)) }, stringResource(R.string.minutesLabel))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
        }
    }
}

@Composable
private fun LogNumberField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
        ),
        modifier = Modifier.width(86.dp),
    )
}

/** Pick a recent workout of the matching type to fulfill this occurrence. */
@Composable
private fun LinkWorkoutDialog(
    target: LinkTarget,
    onDismiss: () -> Unit,
    onLinked: () -> Unit,
) {
    val context = LocalContext.current
    val db = remember { Instance.getInstance(context).db }
    val historyRepository = remember(db, context) {
        ProgramOccurrenceHistoryRepository(
            db = db,
            userWeightKg = { UserMeasurements.from(context).weight.toDouble() },
            profileSnapshot = {
                Instance.getInstance(context).fitnessProfileRepository.captureActiveSnapshot()
            },
        )
    }
    val candidates = remember(target) {
        db.workoutDao().getWorkouts()
            .filter { it.workoutTypeId == target.workoutTypeId }
            .take(15)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.linkWorkoutPickerTitle)) },
        text = {
            if (candidates.isEmpty()) {
                Text(stringResource(R.string.noWorkoutsToLink))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(candidates, key = Workout::id) { workout ->
                        Surface(
                            onClick = {
                                historyRepository.linkWorkout(
                                    target.entry.id,
                                    target.occurrence,
                                    workout.id,
                                )
                                onLinked()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                text = workout.dateString,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
