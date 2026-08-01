/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.ui.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.CompletionRule
import de.tadris.fitness.data.MeasurementSource
import de.tadris.fitness.data.MetricKind
import de.tadris.fitness.data.SessionExercise
import de.tadris.fitness.data.SessionExerciseStatus
import de.tadris.fitness.data.SessionSet
import de.tadris.fitness.data.SessionSetMetrics
import de.tadris.fitness.data.SessionSetStatus
import de.tadris.fitness.data.TrainingSessionPhase
import de.tadris.fitness.data.TrainingSessionRepository
import de.tadris.fitness.data.TrainingSessionState
import de.tadris.fitness.data.TrainingSessionStatus
import de.tadris.fitness.data.TrainingSessionTiming
import de.tadris.fitness.data.preferences.UserMeasurements
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import kotlin.math.max

data class GuidedTrackerRequest(
    val sessionId: Long,
    val sessionSetId: Long,
    val workoutTypeId: String,
    val completionRule: CompletionRule,
    val targetDurationMillis: Long?,
    val targetDistanceMeters: Double?,
    val targetRepetitions: Int?,
)

/**
 * Adapts the durable Room session to display-ready state. Timer transitions happen
 * in the repository and are therefore independent from recomposition, rotation and
 * missed animation frames; this view model only asks the repository to reconcile
 * deadlines with the current time.
 */
class GuidedWorkoutViewModel(application: Application) : AndroidViewModel(application) {

    private val instance = Instance.getInstance(application)
    private val db = instance.db
    private val dao = db.trainingSessionDao()
    private val repository = TrainingSessionRepository(
        db = db,
        userWeightKg = { UserMeasurements.from(application).weight.toDouble() },
        profileSnapshot = { instance.fitnessProfileRepository.captureActiveSnapshot() },
    )
    private val mutationMutex = Mutex()

    private val _uiState = MutableStateFlow<GuidedWorkoutUiState?>(null)
    val uiState: StateFlow<GuidedWorkoutUiState?> = _uiState.asStateFlow()

    private var sessionId: Long? = null
    private var tickerJob: Job? = null
    private var currentState: TrainingSessionState? = null
    private var detailsExerciseId: Long? = null
    private val manualInputs = mutableMapOf<Long, Pair<String, String>>()
    private val dirtyManualInputSetIds = mutableSetOf<Long>()
    private val manualFallbackSetIds = mutableSetOf<Long>()

    fun initialize(id: Long, pauseOnOpen: Boolean = false) {
        if (sessionId == id && tickerJob?.isActive == true) return
        sessionId = id
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch(Dispatchers.IO) {
            // Recovered/existing sessions must be frozen before the first time
            // reconciliation. Otherwise an expired wall-clock deadline could
            // advance a set as soon as the process is relaunched.
            mutationMutex.withLock {
                val persisted = repository.getCurrentState(id)
                val hasPersistedProgress = dao.getExercisesOfSession(id).any { exercise ->
                    dao.getSetsOfExercise(exercise.id).any { set ->
                        set.status != SessionSetStatus.PENDING
                    }
                }
                if (
                    persisted != null &&
                    GuidedSessionPersistencePolicy.shouldPauseOnOpen(
                        pauseOnOpen = pauseOnOpen,
                        status = persisted.session.status,
                        phase = persisted.session.phase,
                        hasPersistedProgress = hasPersistedProgress,
                    )
                ) {
                    repository.pauseSession(id)
                }
                refresh(allowAutomaticStart = false)
            }
            while (isActive) {
                delay(TICK_MILLIS)
                mutationMutex.withLock { refresh(allowAutomaticStart = true) }
            }
        }
    }

    fun dispatch(event: GuidedWorkoutEvent) {
        when (event) {
            is GuidedWorkoutEvent.ChangeManualReps -> updateManual(reps = event.value)
            is GuidedWorkoutEvent.ChangeManualWeight -> updateManual(weight = event.value)
            is GuidedWorkoutEvent.AdjustManualReps -> adjustManualReps(event.delta)
            is GuidedWorkoutEvent.AdjustManualWeight -> adjustManualWeight(event.deltaKg)
            is GuidedWorkoutEvent.ShowExerciseDetails -> {
                detailsExerciseId = event.exerciseId
                publishCurrent()
            }
            GuidedWorkoutEvent.HideExerciseDetails -> {
                detailsExerciseId = null
                publishCurrent()
            }
            GuidedWorkoutEvent.UseManualFallback -> {
                currentState?.set?.id?.let { manualFallbackSetIds += it }
                publishCurrent()
            }
            GuidedWorkoutEvent.StartWorkout -> mutate { id -> repository.beginSet(id) }
            GuidedWorkoutEvent.SkipPreparation -> mutate { id ->
                val deadline = repository.getCurrentState(id)?.session?.phaseDeadlineAt
                if (deadline != null) repository.advanceTime(id, deadline) else repository.getCurrentState(id)
            }
            GuidedWorkoutEvent.PauseWorkout,
            GuidedWorkoutEvent.ToggleTimer -> mutate { id ->
                val state = repository.getCurrentState(id)
                if (state?.session?.status == TrainingSessionStatus.PAUSED) {
                    repository.resumeSession(id)
                } else {
                    repository.pauseSession(id)
                }
            }
            GuidedWorkoutEvent.ResumeWorkout -> mutate { id -> repository.resumeSession(id) }
            GuidedWorkoutEvent.CompleteSet -> completeCurrentSet()
            is GuidedWorkoutEvent.AdjustRest -> mutate { id -> repository.adjustRest(id, event.deltaSeconds) }
            GuidedWorkoutEvent.SkipRest -> mutate { id -> repository.skipRest(id) }
            GuidedWorkoutEvent.FinishWorkout -> mutate { id -> repository.finishSession(id) }

            // Navigation/recorder ownership stays at the route boundary.
            GuidedWorkoutEvent.Back,
            GuidedWorkoutEvent.RequestExit,
            GuidedWorkoutEvent.RequestFinishSession,
            GuidedWorkoutEvent.RequestCancelSession,
            GuidedWorkoutEvent.StartRecorder,
            GuidedWorkoutEvent.StopRecorder,
            GuidedWorkoutEvent.RetryRecorder -> Unit
        }
    }

    fun trackerRequest(): GuidedTrackerRequest? {
        val state = currentState ?: return null
        val exercise = state.exercise ?: return null
        val set = state.set ?: return null
        val workoutTypeId = exercise.workoutTypeIdSnapshot ?: return null
        return GuidedTrackerRequest(
            sessionId = state.session.id,
            sessionSetId = set.id,
            workoutTypeId = workoutTypeId,
            completionRule = exercise.completionRule,
            targetDurationMillis = set.targetDurationMillis,
            targetDistanceMeters = set.targetDistanceMeters,
            targetRepetitions = set.targetRepsMax ?: set.targetReps,
        )
    }

    /** Save the exact runner phase and remaining timer, then leave the route. */
    fun pauseAndExit(onComplete: () -> Unit = {}) {
        persistAndLeave(onComplete) { id ->
            persistManualDraft(id)
            val state = repository.getCurrentState(id)
            if (
                state != null &&
                GuidedSessionPersistencePolicy.shouldPauseOnExit(state.session.status)
            ) {
                repository.pauseSession(id)
            }
        }
    }

    /** Freeze a session when the host goes to the background without navigating. */
    fun pauseForBackground() {
        val id = sessionId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            mutationMutex.withLock {
                persistManualDraft(id)
                val state = repository.getCurrentState(id)
                if (
                    state != null &&
                    GuidedSessionPersistencePolicy.shouldPauseOnExit(state.session.status)
                ) {
                    repository.pauseSession(id)
                }
                refresh(allowAutomaticStart = false)
            }
        }
    }

    /** Explicit early finish: unfinished sets are skipped and a history row is written. */
    fun finishAndExit(onComplete: () -> Unit = {}) {
        persistAndLeave(onComplete) { id -> repository.finishSession(id) }
    }

    /** Explicit cancellation, kept separate from the ordinary save-and-exit path. */
    fun cancelAndExit(onComplete: () -> Unit = {}) {
        persistAndLeave(onComplete) { id -> repository.abortSession(id) }
    }

    private fun persistAndLeave(
        onComplete: () -> Unit,
        mutation: (Long) -> Unit,
    ) {
        val id = sessionId ?: return onComplete()
        viewModelScope.launch(Dispatchers.IO) {
            mutationMutex.withLock { mutation(id) }
            // Navigation happens strictly after Room's transaction returned.
            withContext(Dispatchers.Main.immediate) { onComplete() }
        }
    }

    private fun persistManualDraft(id: Long) {
        val set = currentState?.set ?: repository.getCurrentState(id)?.set ?: return
        if (set.id !in dirtyManualInputSetIds) return
        val input = manualInputs[set.id] ?: return
        repository.saveCurrentSetDraft(
            sessionId = id,
            metrics = SessionSetMetrics(
                reps = input.first.toIntOrNull(),
                weightKg = input.second.replace(',', '.').toDoubleOrNull(),
            ),
        )
        dirtyManualInputSetIds -= set.id
    }

    private fun completeCurrentSet() {
        mutate { id ->
            val state = repository.getCurrentState(id) ?: return@mutate null
            val exercise = state.exercise ?: return@mutate state
            val set = state.set ?: return@mutate state
            val input = manualInputs[set.id]
            val metrics = SessionSetMetrics(
                reps = input?.first?.toIntOrNull(),
                weightKg = input?.second?.replace(',', '.')?.toDoubleOrNull(),
            )
            val manualFallback = set.id in manualFallbackSetIds
            when {
                exercise.measurementSource == MeasurementSource.CLOCK && !manualFallback ->
                    repository.completeTimer(id, set.id)
                else -> repository.completeManual(id, set.id, metrics)
            }
        }
    }

    private fun mutate(block: (Long) -> TrainingSessionState?) {
        val id = sessionId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            mutationMutex.withLock {
                block(id)
                refresh(allowAutomaticStart = true)
            }
        }
    }

    private suspend fun refresh(allowAutomaticStart: Boolean) {
        val id = sessionId ?: return
        var state = repository.advanceTime(id) ?: return
        val allExercises = dao.getExercisesOfSession(id)
        val allSets = allExercises.associate { it.id to dao.getSetsOfExercise(it.id) }
        val completedCount = allSets.values.flatten().count { it.status == SessionSetStatus.COMPLETED }

        // READY is the initial overview and the hand-off point after rest/no-rest.
        // Once at least one set is complete, the runner continues automatically.
        if (
            allowAutomaticStart &&
            state.session.status == TrainingSessionStatus.ACTIVE &&
            state.session.phase == TrainingSessionPhase.READY &&
            completedCount > 0
        ) {
            state = repository.beginSet(id)
        }
        currentState = state
        publish(state, allExercises, allSets, System.currentTimeMillis())
    }

    private fun publishCurrent() {
        val id = sessionId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            mutationMutex.withLock {
                val state = repository.getCurrentState(id) ?: return@withLock
                currentState = state
                val exercises = dao.getExercisesOfSession(id)
                val sets = exercises.associate { it.id to dao.getSetsOfExercise(it.id) }
                publish(state, exercises, sets, System.currentTimeMillis())
            }
        }
    }

    private fun publish(
        state: TrainingSessionState,
        exercises: List<SessionExercise>,
        setsByExercise: Map<Long, List<SessionSet>>,
        now: Long,
    ) {
        val allSets = exercises.flatMap { setsByExercise[it.id].orEmpty() }
        val completedSets = allSets.count { it.status == SessionSetStatus.COMPLETED }
        val skippedSets = allSets.count { it.status == SessionSetStatus.SKIPPED }
        val currentSet = state.set

        currentSet?.let { set ->
            manualInputs.getOrPut(set.id) {
                (set.actualReps ?: set.targetRepsMax ?: set.targetReps)?.toString().orEmpty() to
                    formatEditable(set.actualWeightKg ?: set.targetWeightKg)
            }
        }

        val models = exercises.map { exercise ->
            val sets = setsByExercise[exercise.id].orEmpty()
            val displaySet = if (exercise.id == state.exercise?.id) currentSet else sets.firstOrNull()
            GuidedExerciseUiModel(
                id = exercise.id,
                name = exercise.nameSnapshot,
                target = targetText(displaySet, exercise.metricKindSnapshot, sets.size),
                measurementSource = guidedSource(
                    if (displaySet?.id?.let { it in manualFallbackSetIds } == true) MeasurementSource.USER_INPUT
                    else exercise.measurementSource
                ),
                setCount = sets.size,
                restAfterSeconds = exercise.restSeconds,
                description = exercise.descriptionSnapshot,
                tips = exercise.tipsSnapshot ?: exercise.noteSnapshot,
                imagePath = exercise.imagePathSnapshot,
                videoUrl = exercise.videoUrlSnapshot,
            )
        }

        val phase = when {
            state.session.status == TrainingSessionStatus.PAUSED -> GuidedWorkoutPhase.PAUSED
            state.session.status == TrainingSessionStatus.COMPLETED ||
                state.session.status == TrainingSessionStatus.ABORTED ||
                state.session.phase == TrainingSessionPhase.FINISHED -> GuidedWorkoutPhase.COMPLETE
            state.session.phase == TrainingSessionPhase.PREPARE -> GuidedWorkoutPhase.PREPARING
            state.session.phase == TrainingSessionPhase.ACTIVE_SET -> GuidedWorkoutPhase.ACTIVE
            state.session.phase == TrainingSessionPhase.REST -> GuidedWorkoutPhase.REST
            else -> GuidedWorkoutPhase.OVERVIEW
        }

        val currentExercise = state.exercise
        val effectiveSource = if (currentSet?.id?.let { it in manualFallbackSetIds } == true) {
            MeasurementSource.USER_INPUT
        } else {
            currentExercise?.measurementSource
        }
        val input = currentSet?.id?.let(manualInputs::get)
        val totalDuration = estimateDuration(exercises, setsByExercise)
        val elapsedEnd = state.session.completedAt
            ?: state.session.updatedAt.takeIf { state.session.status == TrainingSessionStatus.PAUSED }
            ?: now
        val elapsed = TrainingSessionTiming.elapsedExcludingPauses(
            startedAt = state.session.startedAt,
            endedAt = elapsedEnd,
            accumulatedPausedMillis = state.session.accumulatedPausedMillis,
        )
        val phaseDuration = state.session.phaseStartedAt?.let { started ->
            state.session.phaseDeadlineAt?.let { deadline -> max(0L, deadline - started) }
        } ?: currentSet?.targetDurationMillis ?: 0L

        val exerciseResults = exercises.map { exercise ->
            val sets = setsByExercise[exercise.id].orEmpty()
            val completed = sets.count { it.status == SessionSetStatus.COMPLETED }
            GuidedExerciseResultUiModel(
                exerciseName = exercise.nameSnapshot,
                result = resultText(sets, exercise.metricKindSnapshot),
                completedSets = completed,
                totalSets = sets.size,
                skipped = exercise.status == SessionExerciseStatus.SKIPPED,
            )
        }

        _uiState.value = GuidedWorkoutUiState(
            phase = phase,
            workoutTitle = state.session.nameSnapshot,
            workoutDescription = state.session.descriptionSnapshot,
            exercises = models,
            estimatedDurationMillis = totalDuration,
            currentExerciseIndex = state.session.currentExerciseIndex.coerceIn(0, max(0, models.lastIndex)),
            currentSetIndex = state.session.currentSetIndex,
            overallProgress = if (allSets.isEmpty()) 0f else completedSets.toFloat() / allSets.size,
            remainingMillis = state.remainingMillis ?: 0L,
            phaseDurationMillis = phaseDuration,
            timerIsRunning = state.session.status == TrainingSessionStatus.ACTIVE &&
                state.session.phase in setOf(TrainingSessionPhase.PREPARE, TrainingSessionPhase.ACTIVE_SET, TrainingSessionPhase.REST),
            timerHasStarted = currentSet?.startedAt != null,
            manualReps = input?.first?.takeIf { currentExercise?.metricKindSnapshot?.usesReps == true },
            manualWeightKg = input?.second?.takeIf { currentExercise?.metricKindSnapshot?.usesWeight == true },
            recorderStatus = if (currentExercise?.workoutTypeIdSnapshot == null) {
                GuidedRecorderStatus.UNAVAILABLE
            } else {
                GuidedRecorderStatus.READY
            },
            recorderHint = currentExercise?.workoutTypeIdSnapshot?.let { null }
                ?: if (effectiveSource == MeasurementSource.RECORDER) {
                    getApplication<Application>().getString(R.string.guided_tracker_manual_hint)
                } else {
                    null
                },
            recorderAvailable = currentExercise?.workoutTypeIdSnapshot != null,
            recorderIsRunning = false,
            upNext = if (phase == GuidedWorkoutPhase.REST) models.getOrNull(state.session.currentExerciseIndex) else null,
            showExerciseDetails = detailsExerciseId != null,
            detailsExerciseId = detailsExerciseId,
            elapsedMillis = elapsed,
            completedSetCount = completedSets,
            totalSetCount = allSets.size,
            skippedSetCount = skippedSets,
            summaryMetrics = listOf(
                GuidedMetricUiModel(
                    getApplication<Application>().getString(R.string.guided_metric_time),
                    formatClock(elapsed),
                ),
                GuidedMetricUiModel(
                    getApplication<Application>().getString(R.string.guided_metric_sets),
                    "$completedSets/${allSets.size}",
                ),
            ),
            exerciseResults = exerciseResults,
        )
    }

    private fun updateManual(reps: String? = null, weight: String? = null) {
        val set = currentState?.set ?: return
        val old = manualInputs[set.id] ?: ("" to "")
        manualInputs[set.id] = (reps ?: old.first) to (weight ?: old.second)
        dirtyManualInputSetIds += set.id
        publishCurrent()
    }

    private fun adjustManualReps(delta: Int) {
        val set = currentState?.set ?: return
        val old = manualInputs[set.id] ?: ("" to "")
        manualInputs[set.id] = max(0, (old.first.toIntOrNull() ?: 0) + delta).toString() to old.second
        dirtyManualInputSetIds += set.id
        publishCurrent()
    }

    private fun adjustManualWeight(delta: Double) {
        val set = currentState?.set ?: return
        val old = manualInputs[set.id] ?: ("" to "")
        val value = max(0.0, (old.second.replace(',', '.').toDoubleOrNull() ?: 0.0) + delta)
        manualInputs[set.id] = old.first to formatEditable(value)
        dirtyManualInputSetIds += set.id
        publishCurrent()
    }

    private fun targetText(set: SessionSet?, kind: MetricKind, setCount: Int): String {
        val resources = getApplication<Application>().resources
        if (set == null) return resources.getString(R.string.guided_sets_short, setCount)
        val parts = mutableListOf<String>()
        if (kind.usesReps) {
            val reps = set.targetReps?.toString().orEmpty() +
                (set.targetRepsMax?.let { "–$it" } ?: "")
            if (reps.isNotBlank()) parts += resources.getString(R.string.guided_reps_short, reps)
        }
        if (kind.usesWeight) set.targetWeightKg?.let { parts += "${formatEditable(it)} kg" }
        if (kind.usesDistance) set.targetDistanceMeters?.let { parts += "${formatEditable(it / 1000.0)} km" }
        if (kind.usesDuration) set.targetDurationMillis?.let { parts += formatClock(it) }
        return parts.joinToString(" · ").ifBlank {
            resources.getString(R.string.guided_sets_short, setCount)
        }
    }

    private fun resultText(sets: List<SessionSet>, kind: MetricKind): String {
        val resources = getApplication<Application>().resources
        val completed = sets.filter { it.status == SessionSetStatus.COMPLETED }
        if (completed.isEmpty()) return "—"
        val reps = if (kind.usesReps) completed.mapNotNull { it.actualReps }.sum() else null
        val distance = if (kind.usesDistance) completed.mapNotNull { it.actualDistanceMeters }.sum() else null
        val duration = if (kind.usesDuration) completed.mapNotNull { it.actualDurationMillis }.sum() else null
        return buildList {
            reps?.let { add(resources.getString(R.string.guided_reps_short, it.toString())) }
            distance?.let { add("${formatEditable(it / 1000.0)} km") }
            duration?.let { add(formatClock(it)) }
        }.joinToString(" · ").ifBlank {
            resources.getString(R.string.guided_sets_short, completed.size)
        }
    }

    private fun estimateDuration(
        exercises: List<SessionExercise>,
        setsByExercise: Map<Long, List<SessionSet>>,
    ): Long = exercises.sumOf { exercise ->
        val sets = setsByExercise[exercise.id].orEmpty()
        val active = sets.sumOf { it.targetDurationMillis ?: DEFAULT_MANUAL_SET_MILLIS }
        val betweenSets = max(0, sets.size - 1) * (exercise.restSeconds ?: 0) * 1_000L
        active + betweenSets + exercise.prepareSeconds * 1_000L + (exercise.transitionSeconds ?: 0) * 1_000L
    }

    private fun guidedSource(source: MeasurementSource?): GuidedMeasurementSource = when (source) {
        MeasurementSource.CLOCK -> GuidedMeasurementSource.CLOCK
        MeasurementSource.RECORDER -> GuidedMeasurementSource.RECORDER
        else -> GuidedMeasurementSource.USER_INPUT
    }

    private fun formatEditable(value: Double?): String = value?.let {
        if (it == kotlin.math.floor(it)) it.toLong().toString()
        else String.format(Locale.US, "%.1f", it)
    }.orEmpty()

    private fun formatClock(millis: Long): String {
        val totalSeconds = max(0L, millis) / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%02d:%02d".format(minutes, seconds)
    }

    companion object {
        private const val TICK_MILLIS = 250L
        private const val DEFAULT_MANUAL_SET_MILLIS = 45_000L
    }
}
