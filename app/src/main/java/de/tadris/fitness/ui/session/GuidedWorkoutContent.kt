/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack.
 *
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.tadris.fitness.R
import de.tadris.fitness.data.isSupportedExerciseVideoUrl
import de.tadris.fitness.ui.programs.ExerciseMedia
import java.io.File
import kotlin.math.roundToInt

/**
 * Stateless Material 3 surface for a complete guided-workout flow. Persistence,
 * timers, recorder control and navigation stay with the caller through [onEvent].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidedWorkoutContent(
    state: GuidedWorkoutUiState,
    onEvent: (GuidedWorkoutEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val phaseDescription = guidedPhaseLabel(state.phase)
    val detailExercise = state.detailsExerciseId
        ?.let { id -> state.exercises.firstOrNull { it.id == id } }
        ?: state.currentExercise
    var pendingVideoUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingVideoName by rememberSaveable { mutableStateOf<String?>(null) }
    var resumeAfterVideo by rememberSaveable { mutableStateOf(false) }
    var resumeWhenVideoPauseArrives by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.phase, resumeWhenVideoPauseArrives) {
        if (!resumeWhenVideoPauseArrives) return@LaunchedEffect
        when (state.phase) {
            GuidedWorkoutPhase.PAUSED -> {
                resumeWhenVideoPauseArrives = false
                onEvent(GuidedWorkoutEvent.ResumeWorkout)
            }
            GuidedWorkoutPhase.PREPARING,
            GuidedWorkoutPhase.ACTIVE -> Unit // Pause mutation is still in flight.
            else -> resumeWhenVideoPauseArrives = false // The workout genuinely advanced.
        }
    }

    fun openExerciseVideo(exercise: GuidedExerciseUiModel) {
        if (pendingVideoUrl != null) return
        val url = exercise.videoUrl
            ?.trim()
            ?.takeIf(::isSupportedExerciseVideoUrl)
            ?: return
        val videoPausedWorkout = shouldPauseGuidedWorkoutForVideo(
            phase = state.phase,
            timerIsRunning = state.timerIsRunning,
        )
        pendingVideoUrl = url
        pendingVideoName = exercise.name
        resumeAfterVideo = videoPausedWorkout
        if (videoPausedWorkout) onEvent(GuidedWorkoutEvent.PauseWorkout)
    }

    BackHandler {
        onEvent(
            if (state.showExerciseDetails) GuidedWorkoutEvent.HideExerciseDetails
            else GuidedWorkoutEvent.Back
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .semantics { stateDescription = phaseDescription },
        topBar = {
            GuidedTopBar(
                state = state,
                onBack = { onEvent(GuidedWorkoutEvent.Back) },
                onPause = { onEvent(GuidedWorkoutEvent.PauseWorkout) },
                onShowDetails = state.currentExercise?.let { exercise ->
                    { onEvent(GuidedWorkoutEvent.ShowExerciseDetails(exercise.id)) }
                },
            )
        },
        bottomBar = {
            GuidedBottomAction(
                state = state,
                onEvent = onEvent,
            )
        },
    ) { innerPadding ->
        when (state.phase) {
            GuidedWorkoutPhase.OVERVIEW -> OverviewContent(
                state = state,
                onExerciseClick = { onEvent(GuidedWorkoutEvent.ShowExerciseDetails(it)) },
                modifier = Modifier.padding(innerPadding),
            )

            GuidedWorkoutPhase.PREPARING -> PreparingContent(
                state = state,
                onPlayVideo = ::openExerciseVideo,
                modifier = Modifier.padding(innerPadding),
            )

            GuidedWorkoutPhase.ACTIVE -> ActiveContent(
                state = state,
                onEvent = onEvent,
                onPlayVideo = ::openExerciseVideo,
                modifier = Modifier.padding(innerPadding),
            )

            GuidedWorkoutPhase.REST -> RestContent(
                state = state,
                onEvent = onEvent,
                modifier = Modifier.padding(innerPadding),
            )

            GuidedWorkoutPhase.PAUSED -> PausedContent(
                state = state,
                onExit = { onEvent(GuidedWorkoutEvent.RequestExit) },
                onFinish = { onEvent(GuidedWorkoutEvent.RequestFinishSession) },
                onCancelSession = { onEvent(GuidedWorkoutEvent.RequestCancelSession) },
                modifier = Modifier.padding(innerPadding),
            )

            GuidedWorkoutPhase.COMPLETE -> CompleteContent(
                state = state,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    if (state.showExerciseDetails && detailExercise != null) {
        ExerciseDetailsSheet(
            exercise = detailExercise,
            onPlayVideo = { openExerciseVideo(detailExercise) },
            onDismiss = { onEvent(GuidedWorkoutEvent.HideExerciseDetails) },
        )
    }

    val videoUrl = pendingVideoUrl
    if (videoUrl != null) {
        ExerciseVideoPlayerDialog(
            videoUrl = videoUrl,
            exerciseName = pendingVideoName.orEmpty(),
            onDismiss = {
                val shouldResume = shouldResumeGuidedWorkoutAfterVideo(
                    pausedByVideo = resumeAfterVideo,
                    currentPhase = state.phase,
                )
                val shouldAwaitPause = resumeAfterVideo && state.phase in setOf(
                    GuidedWorkoutPhase.PREPARING,
                    GuidedWorkoutPhase.ACTIVE,
                )
                pendingVideoUrl = null
                pendingVideoName = null
                resumeAfterVideo = false
                resumeWhenVideoPauseArrives = shouldAwaitPause
                if (shouldResume) onEvent(GuidedWorkoutEvent.ResumeWorkout)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuidedTopBar(
    state: GuidedWorkoutUiState,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onShowDetails: (() -> Unit)?,
) {
    val progressDescription = phaseProgressDescription(state)
    Column {
        TopAppBar(
            title = {
                Text(
                    text = if (state.phase == GuidedWorkoutPhase.COMPLETE) {
                        stringResource(R.string.guided_summary_title)
                    } else {
                        state.workoutTitle
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.guided_back),
                    )
                }
            },
            actions = {
                if (onShowDetails != null && state.phase in setOf(
                        GuidedWorkoutPhase.PREPARING,
                        GuidedWorkoutPhase.ACTIVE,
                    )
                ) {
                    IconButton(onClick = onShowDetails) {
                        Icon(
                            painter = painterResource(R.drawable.ic_help),
                            contentDescription = stringResource(R.string.guided_exercise_details),
                        )
                    }
                }
                if (state.phase in setOf(
                        GuidedWorkoutPhase.PREPARING,
                        GuidedWorkoutPhase.ACTIVE,
                        GuidedWorkoutPhase.REST,
                    )
                ) {
                    IconButton(onClick = onPause) {
                        Icon(
                            painter = painterResource(R.drawable.ic_pause),
                            contentDescription = stringResource(R.string.guided_pause_workout),
                        )
                    }
                }
            },
        )
        if (state.phase !in setOf(GuidedWorkoutPhase.OVERVIEW, GuidedWorkoutPhase.COMPLETE)) {
            LinearProgressIndicator(
                progress = { state.overallProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = progressDescription
                    },
            )
        }
    }
}

@Composable
private fun OverviewContent(
    state: GuidedWorkoutUiState,
    onExerciseClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalSets = state.totalSetCount.takeIf { it > 0 }
        ?: state.exercises.sumOf { it.setCount }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "overview_header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = state.workoutTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                state.workoutDescription?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "overview_summary") {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OverviewMetric(
                        value = state.exercises.size.toString(),
                        label = stringResource(R.string.guided_exercises_label),
                        modifier = Modifier.weight(1f),
                    )
                    OverviewMetric(
                        value = totalSets.toString(),
                        label = stringResource(R.string.guided_sets_label),
                        modifier = Modifier.weight(1f),
                    )
                    state.estimatedDurationMillis?.let { duration ->
                        OverviewMetric(
                            value = formatGuidedDuration(duration),
                            label = stringResource(R.string.guided_duration_label),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item(key = "overview_plan_header") {
            Text(
                text = stringResource(R.string.guided_plan_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .semantics { heading() },
            )
        }

        itemsIndexed(
            items = state.exercises,
            key = { _, exercise -> exercise.id },
        ) { index, exercise ->
            ExerciseTimelineCard(
                exercise = exercise,
                index = index,
                onClick = { onExerciseClick(exercise.id) },
            )
        }
    }
}

@Composable
private fun OverviewMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
        )
    }
}

@Composable
private fun ExerciseTimelineCard(
    exercise: GuidedExerciseUiModel,
    index: Int,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box {
                ExerciseMediaSurface(
                    exercise = exercise,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(24.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (index + 1).toString(),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = exercise.target,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MeasurementSourceBadge(exercise.measurementSource)
                exercise.restAfterSeconds?.takeIf { it > 0 }?.let { seconds ->
                    Text(
                        text = pluralStringResource(
                            R.plurals.guided_rest_after,
                            seconds,
                            seconds,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreparingContent(
    state: GuidedWorkoutUiState,
    onPlayVideo: (GuidedExerciseUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val exercise = state.currentExercise ?: return
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.guided_get_ready),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
        }
        item {
            CurrentExerciseHeader(
                state = state,
                exercise = exercise,
                onPlayVideo = { onPlayVideo(exercise) },
            )
        }
        item {
            TimerProgress(
                remainingMillis = state.remainingMillis,
                totalMillis = state.phaseDurationMillis,
                contentDescription = stringResource(R.string.guided_preparation_countdown),
            )
        }
    }
}

@Composable
private fun ActiveContent(
    state: GuidedWorkoutUiState,
    onEvent: (GuidedWorkoutEvent) -> Unit,
    onPlayVideo: (GuidedExerciseUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val exercise = state.currentExercise ?: return
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "active_header") {
            CurrentExerciseHeader(
                state = state,
                exercise = exercise,
                onPlayVideo = { onPlayVideo(exercise) },
            )
        }

        when (exercise.measurementSource) {
            GuidedMeasurementSource.USER_INPUT -> item(key = "manual") {
                ManualSetContent(state = state, onEvent = onEvent)
            }

            GuidedMeasurementSource.CLOCK -> item(key = "timer") {
                TimedSetContent(state = state, onEvent = onEvent)
            }

            GuidedMeasurementSource.RECORDER -> item(key = "recorder") {
                RecorderSetContent(state = state, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun CurrentExerciseHeader(
    state: GuidedWorkoutUiState,
    exercise: GuidedExerciseUiModel,
    onPlayVideo: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExerciseMediaSurface(
            exercise = exercise,
            onPlayVideo = onPlayVideo,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.extraLarge),
        )
        Text(
            text = stringResource(
                R.string.guided_progress_exercise,
                state.currentExerciseIndex + 1,
                state.exercises.size.coerceAtLeast(1),
                state.currentSetIndex + 1,
                exercise.setCount.coerceAtLeast(1),
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = exercise.name,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Text(
                text = exercise.target,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        MeasurementSourceBadge(exercise.measurementSource)
    }
}

@Composable
private fun ManualSetContent(
    state: GuidedWorkoutUiState,
    onEvent: (GuidedWorkoutEvent) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.guided_actual_result),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.guided_manual_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.manualReps?.let { reps ->
                MetricStepper(
                    label = stringResource(R.string.guided_repetitions),
                    value = reps,
                    decreaseDescription = stringResource(R.string.guided_decrease_repetitions),
                    increaseDescription = stringResource(R.string.guided_increase_repetitions),
                    onValueChange = { onEvent(GuidedWorkoutEvent.ChangeManualReps(it)) },
                    onDecrease = { onEvent(GuidedWorkoutEvent.AdjustManualReps(-1)) },
                    onIncrease = { onEvent(GuidedWorkoutEvent.AdjustManualReps(1)) },
                    keyboardType = KeyboardType.Number,
                )
            }
            state.manualWeightKg?.let { weight ->
                MetricStepper(
                    label = stringResource(R.string.guided_weight_kg),
                    value = weight,
                    decreaseDescription = stringResource(R.string.guided_decrease_weight),
                    increaseDescription = stringResource(R.string.guided_increase_weight),
                    onValueChange = { onEvent(GuidedWorkoutEvent.ChangeManualWeight(it)) },
                    onDecrease = { onEvent(GuidedWorkoutEvent.AdjustManualWeight(-1.0)) },
                    onIncrease = { onEvent(GuidedWorkoutEvent.AdjustManualWeight(1.0)) },
                    keyboardType = KeyboardType.Decimal,
                )
            }
        }
    }
}

@Composable
private fun MetricStepper(
    label: String,
    value: String,
    decreaseDescription: String,
    increaseDescription: String,
    onValueChange: (String) -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    keyboardType: KeyboardType,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onDecrease,
                modifier = Modifier.semantics { contentDescription = decreaseDescription },
            ) {
                Text(text = "−", style = MaterialTheme.typography.headlineSmall)
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label, maxLines = 1) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onIncrease,
                modifier = Modifier.semantics { contentDescription = increaseDescription },
            ) {
                Text(text = "+", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
private fun TimedSetContent(
    state: GuidedWorkoutUiState,
    onEvent: (GuidedWorkoutEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TimerProgress(
            remainingMillis = state.remainingMillis,
            totalMillis = state.phaseDurationMillis,
            contentDescription = stringResource(R.string.guided_set_timer),
        )
        Text(
            text = stringResource(
                if (state.timerIsRunning) R.string.guided_timer_running_hint
                else R.string.guided_timer_paused_hint
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = { onEvent(GuidedWorkoutEvent.CompleteSet) }) {
            Text(stringResource(R.string.guided_complete_early))
        }
    }
}

@Composable
private fun RecorderSetContent(
    state: GuidedWorkoutUiState,
    onEvent: (GuidedWorkoutEvent) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.guided_live_tracking),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                RecorderStatusPill(state.recorderStatus)
            }

            state.recorderHint?.takeIf { it.isNotBlank() }?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.recorderMetrics.forEachIndexed { index, metric ->
                MetricRow(metric)
                if (index != state.recorderMetrics.lastIndex) HorizontalDivider()
            }

            if (state.recorderAvailable) {
                Button(
                    onClick = {
                        onEvent(
                            if (state.recorderIsRunning) GuidedWorkoutEvent.StopRecorder
                            else GuidedWorkoutEvent.StartRecorder
                        )
                    },
                    enabled = state.recorderStatus != GuidedRecorderStatus.CONNECTING,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_record_24dp),
                        contentDescription = null,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(
                            if (state.recorderIsRunning) R.string.guided_finish_recording
                            else R.string.guided_start_recording
                        )
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = { onEvent(GuidedWorkoutEvent.RetryRecorder) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.guided_retry_tracker))
                }
            }

            OutlinedButton(
                onClick = { onEvent(GuidedWorkoutEvent.UseManualFallback) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.guided_manual_fallback))
            }
        }
    }
}

@Composable
private fun MetricRow(metric: GuidedMetricUiModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = metric.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = metric.value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun RestContent(
    state: GuidedWorkoutUiState,
    onEvent: (GuidedWorkoutEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.guided_rest_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TimerProgress(
                        remainingMillis = state.remainingMillis,
                        totalMillis = state.phaseDurationMillis,
                        contentDescription = stringResource(R.string.guided_rest_countdown),
                        indicatorColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.18f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onEvent(GuidedWorkoutEvent.AdjustRest(-15)) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                        ) {
                            Text(stringResource(R.string.guided_rest_minus_15))
                        }
                        OutlinedButton(
                            onClick = { onEvent(GuidedWorkoutEvent.AdjustRest(15)) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                        ) {
                            Text(stringResource(R.string.guided_rest_plus_15))
                        }
                    }
                }
            }
        }
        state.upNext?.let { next ->
            item {
                UpNextCard(next)
            }
        }
    }
}

@Composable
private fun UpNextCard(exercise: GuidedExerciseUiModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.guided_up_next),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExerciseMediaSurface(
                    exercise = exercise,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = exercise.target,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MeasurementSourceBadge(exercise.measurementSource)
                }
            }
        }
    }
}

@Composable
private fun PausedContent(
    state: GuidedWorkoutUiState,
    onExit: () -> Unit,
    onFinish: () -> Unit,
    onCancelSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Surface(
                modifier = Modifier.size(88.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pause),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.guided_paused_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
        }
        state.currentExercise?.let { exercise ->
            item {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
        item {
            Text(
                text = stringResource(
                    R.string.guided_elapsed_value,
                    formatGuidedDuration(state.elapsedMillis),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            FilledTonalButton(
                onClick = onExit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.guided_save_and_exit))
            }
        }
        item {
            OutlinedButton(
                onClick = onFinish,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.guided_finish_workout))
            }
        }
        item {
            TextButton(
                onClick = onCancelSession,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.guided_cancel_session))
            }
        }
    }
}

@Composable
private fun CompleteContent(
    state: GuidedWorkoutUiState,
    modifier: Modifier = Modifier,
) {
    val baseMetrics = buildList {
        add(
            GuidedMetricUiModel(
                label = stringResource(R.string.guided_duration_label),
                value = formatGuidedDuration(state.elapsedMillis),
            )
        )
        add(
            GuidedMetricUiModel(
                label = stringResource(R.string.guided_sets_completed),
                value = stringResource(
                    R.string.guided_sets_ratio,
                    state.completedSetCount,
                    state.totalSetCount,
                ),
            )
        )
        if (state.skippedSetCount > 0) {
            add(
                GuidedMetricUiModel(
                    label = stringResource(R.string.guided_skipped),
                    value = state.skippedSetCount.toString(),
                )
            )
        }
        addAll(state.summaryMetrics)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.guided_complete_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = state.workoutTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        item {
            SummaryMetricsCard(baseMetrics)
        }

        if (state.exerciseResults.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.guided_results_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { heading() },
                )
            }
            itemsIndexed(
                items = state.exerciseResults,
                key = { index, result -> "${result.exerciseName}_$index" },
            ) { _, result ->
                ExerciseResultCard(result)
            }
        }
    }
}

@Composable
private fun SummaryMetricsCard(metrics: List<GuidedMetricUiModel>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            metrics.forEachIndexed { index, metric ->
                MetricRow(metric)
                if (index != metrics.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ExerciseResultCard(result: GuidedExerciseResultUiModel) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (result.skipped) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = result.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (result.skipped) {
                        stringResource(R.string.guided_skipped)
                    } else {
                        stringResource(
                            R.string.guided_sets_ratio,
                            result.completedSets,
                            result.totalSets,
                        )
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = result.result,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GuidedBottomAction(
    state: GuidedWorkoutUiState,
    onEvent: (GuidedWorkoutEvent) -> Unit,
) {
    val exercise = state.currentExercise
    val action: Pair<String, () -> Unit>? = when (state.phase) {
        GuidedWorkoutPhase.OVERVIEW -> stringResource(R.string.guided_start_workout) to {
            onEvent(GuidedWorkoutEvent.StartWorkout)
        }

        GuidedWorkoutPhase.PREPARING -> stringResource(R.string.guided_start_now) to {
            onEvent(GuidedWorkoutEvent.SkipPreparation)
        }

        GuidedWorkoutPhase.ACTIVE -> when (exercise?.measurementSource) {
            GuidedMeasurementSource.USER_INPUT -> stringResource(R.string.guided_set_complete) to {
                onEvent(GuidedWorkoutEvent.CompleteSet)
            }

            GuidedMeasurementSource.CLOCK -> stringResource(
                when {
                    state.timerIsRunning -> R.string.guided_pause_timer
                    state.timerHasStarted -> R.string.guided_resume_timer
                    else -> R.string.guided_start_timer
                }
            ) to { onEvent(GuidedWorkoutEvent.ToggleTimer) }

            GuidedMeasurementSource.RECORDER, null -> null
        }

        GuidedWorkoutPhase.REST -> stringResource(R.string.guided_skip_rest) to {
            onEvent(GuidedWorkoutEvent.SkipRest)
        }

        GuidedWorkoutPhase.PAUSED -> stringResource(R.string.guided_resume_workout) to {
            onEvent(GuidedWorkoutEvent.ResumeWorkout)
        }

        GuidedWorkoutPhase.COMPLETE -> stringResource(R.string.guided_finish) to {
            onEvent(GuidedWorkoutEvent.FinishWorkout)
        }
    }

    if (action == null) return

    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Button(
            onClick = action.second,
            enabled = state.phase != GuidedWorkoutPhase.OVERVIEW || state.exercises.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .heightIn(min = 56.dp),
        ) {
            when {
                state.phase == GuidedWorkoutPhase.OVERVIEW ||
                    state.phase == GuidedWorkoutPhase.PREPARING -> {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                }

                state.phase == GuidedWorkoutPhase.ACTIVE &&
                    exercise?.measurementSource == GuidedMeasurementSource.USER_INPUT -> {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                }
            }
            Text(action.first)
        }
    }
}

@Composable
private fun TimerProgress(
    remainingMillis: Long,
    totalMillis: Long,
    contentDescription: String,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val progress = if (totalMillis > 0L) {
        (remainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.64f)
                .widthIn(min = 180.dp, max = 260.dp)
                .aspectRatio(1f)
                .semantics {
                    this.contentDescription = contentDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = indicatorColor,
                trackColor = trackColor,
                strokeWidth = 8.dp,
            )
            Text(
                text = formatGuidedDuration(remainingMillis),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MeasurementSourceBadge(source: GuidedMeasurementSource) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = guidedMeasurementSourceLabel(source),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun RecorderStatusPill(status: GuidedRecorderStatus) {
    val containerColor = when (status) {
        GuidedRecorderStatus.READY -> MaterialTheme.colorScheme.primaryContainer
        GuidedRecorderStatus.CONNECTING -> MaterialTheme.colorScheme.tertiaryContainer
        GuidedRecorderStatus.RECORDING -> MaterialTheme.colorScheme.primary
        GuidedRecorderStatus.PAUSED -> MaterialTheme.colorScheme.secondaryContainer
        GuidedRecorderStatus.UNAVAILABLE -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (status) {
        GuidedRecorderStatus.READY -> MaterialTheme.colorScheme.onPrimaryContainer
        GuidedRecorderStatus.CONNECTING -> MaterialTheme.colorScheme.onTertiaryContainer
        GuidedRecorderStatus.RECORDING -> MaterialTheme.colorScheme.onPrimary
        GuidedRecorderStatus.PAUSED -> MaterialTheme.colorScheme.onSecondaryContainer
        GuidedRecorderStatus.UNAVAILABLE -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = guidedRecorderStatusLabel(status),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseDetailsSheet(
    exercise: GuidedExerciseUiModel,
    onPlayVideo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }
            item {
                ExerciseMediaSurface(
                    exercise = exercise,
                    onPlayVideo = onPlayVideo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.extraLarge),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.guided_target_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(exercise.target, style = MaterialTheme.typography.bodyLarge)
                    MeasurementSourceBadge(exercise.measurementSource)
                }
            }
            exercise.description?.takeIf { it.isNotBlank() }?.let { description ->
                item {
                    DetailTextSection(
                        title = stringResource(R.string.guided_instructions_title),
                        text = description,
                    )
                }
            }
            exercise.tips?.takeIf { it.isNotBlank() }?.let { tips ->
                item {
                    DetailTextSection(
                        title = stringResource(R.string.guided_tips_title),
                        text = tips,
                    )
                }
            }
            item {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.guided_close_details))
                }
            }
        }
    }
}

@Composable
private fun DetailTextSection(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExerciseMediaSurface(
    exercise: GuidedExerciseUiModel,
    onPlayVideo: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val videoUrl = exercise.videoUrl?.trim()?.takeIf(::isSupportedExerciseVideoUrl)
    val safePath = exercise.imagePath?.takeIf { path ->
        runCatching {
            val root = context.filesDir.canonicalFile
            val media = File(root, path).canonicalFile
            !File(path).isAbsolute &&
                media.path.startsWith(root.path + File.separator) &&
                media.isFile
        }.getOrDefault(false)
    }
    val imageDescription = stringResource(R.string.guided_exercise_image, exercise.name)

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = exercise.name.firstOrNull()?.uppercase() ?: "•",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            safePath?.let { path ->
                ExerciseMedia(
                    imagePath = path,
                    contentDescription = imageDescription,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (onPlayVideo != null && videoUrl != null) {
                ExerciseVideoPlayOverlay(
                    exerciseName = exercise.name,
                    onClick = onPlayVideo,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

internal fun shouldPauseGuidedWorkoutForVideo(
    phase: GuidedWorkoutPhase,
    timerIsRunning: Boolean,
): Boolean = timerIsRunning && phase in setOf(
    GuidedWorkoutPhase.PREPARING,
    GuidedWorkoutPhase.ACTIVE,
)

internal fun shouldResumeGuidedWorkoutAfterVideo(
    pausedByVideo: Boolean,
    currentPhase: GuidedWorkoutPhase,
): Boolean = pausedByVideo && currentPhase == GuidedWorkoutPhase.PAUSED

@Composable
private fun guidedPhaseLabel(phase: GuidedWorkoutPhase): String = stringResource(
    when (phase) {
        GuidedWorkoutPhase.OVERVIEW -> R.string.guided_phase_overview
        GuidedWorkoutPhase.PREPARING -> R.string.guided_phase_preparing
        GuidedWorkoutPhase.ACTIVE -> R.string.guided_phase_active
        GuidedWorkoutPhase.REST -> R.string.guided_phase_rest
        GuidedWorkoutPhase.PAUSED -> R.string.guided_phase_paused
        GuidedWorkoutPhase.COMPLETE -> R.string.guided_phase_complete
    }
)

@Composable
private fun guidedMeasurementSourceLabel(source: GuidedMeasurementSource): String = stringResource(
    when (source) {
        GuidedMeasurementSource.USER_INPUT -> R.string.guided_source_manual
        GuidedMeasurementSource.CLOCK -> R.string.guided_source_timer
        GuidedMeasurementSource.RECORDER -> R.string.guided_source_tracker
    }
)

@Composable
private fun guidedRecorderStatusLabel(status: GuidedRecorderStatus): String = stringResource(
    when (status) {
        GuidedRecorderStatus.READY -> R.string.guided_tracker_ready
        GuidedRecorderStatus.CONNECTING -> R.string.guided_tracker_connecting
        GuidedRecorderStatus.RECORDING -> R.string.guided_tracker_recording
        GuidedRecorderStatus.PAUSED -> R.string.guided_tracker_paused
        GuidedRecorderStatus.UNAVAILABLE -> R.string.guided_tracker_unavailable
    }
)

@Composable
private fun phaseProgressDescription(state: GuidedWorkoutUiState): String {
    val percent = (state.overallProgress.coerceIn(0f, 1f) * 100).roundToInt()
    return stringResource(R.string.guided_overall_progress, percent)
}

private fun formatGuidedDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis.coerceAtLeast(0L) + 999L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
