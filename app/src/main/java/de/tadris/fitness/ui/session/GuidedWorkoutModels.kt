/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack.
 *
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.ui.session

/** Presentation-only phases of a guided workout. */
enum class GuidedWorkoutPhase {
    OVERVIEW,
    PREPARING,
    ACTIVE,
    REST,
    PAUSED,
    COMPLETE,
}

/**
 * Where live values for the current set come from. This deliberately does not
 * describe the completion rule: a recorder-backed set may still finish on a timer.
 */
enum class GuidedMeasurementSource {
    USER_INPUT,
    CLOCK,
    RECORDER,
}

enum class GuidedRecorderStatus {
    READY,
    CONNECTING,
    RECORDING,
    PAUSED,
    UNAVAILABLE,
}

data class GuidedExerciseUiModel(
    val id: Long,
    val name: String,
    val target: String,
    val measurementSource: GuidedMeasurementSource,
    val setCount: Int = 1,
    val restAfterSeconds: Int? = null,
    val description: String? = null,
    val tips: String? = null,
    /** A path relative to the application's private files directory. */
    val imagePath: String? = null,
    /** A direct HTTP(S) media or HLS URL captured when the session was created. */
    val videoUrl: String? = null,
)

data class GuidedMetricUiModel(
    val label: String,
    val value: String,
)

data class GuidedExerciseResultUiModel(
    val exerciseName: String,
    val result: String,
    val completedSets: Int,
    val totalSets: Int,
    val skipped: Boolean = false,
)

/**
 * Immutable state consumed by [GuidedWorkoutContent]. It intentionally contains
 * display-ready strings and no Room/domain entities, keeping the UI reusable and
 * straightforward to preview and test.
 */
data class GuidedWorkoutUiState(
    val phase: GuidedWorkoutPhase,
    val workoutTitle: String,
    val workoutDescription: String? = null,
    val exercises: List<GuidedExerciseUiModel> = emptyList(),
    val estimatedDurationMillis: Long? = null,
    val currentExerciseIndex: Int = 0,
    val currentSetIndex: Int = 0,
    val overallProgress: Float = 0f,
    val remainingMillis: Long = 0L,
    val phaseDurationMillis: Long = 0L,
    val timerIsRunning: Boolean = false,
    val timerHasStarted: Boolean = false,
    /** Null hides the corresponding manual input. */
    val manualReps: String? = null,
    /** Null hides the corresponding manual input. */
    val manualWeightKg: String? = null,
    val recorderStatus: GuidedRecorderStatus = GuidedRecorderStatus.READY,
    val recorderMetrics: List<GuidedMetricUiModel> = emptyList(),
    val recorderHint: String? = null,
    val recorderAvailable: Boolean = true,
    val recorderIsRunning: Boolean = false,
    /** Explicit because after a set the next item may be another set of the same exercise. */
    val upNext: GuidedExerciseUiModel? = null,
    val showExerciseDetails: Boolean = false,
    val detailsExerciseId: Long? = null,
    val elapsedMillis: Long = 0L,
    val completedSetCount: Int = 0,
    val totalSetCount: Int = 0,
    val skippedSetCount: Int = 0,
    val summaryMetrics: List<GuidedMetricUiModel> = emptyList(),
    val exerciseResults: List<GuidedExerciseResultUiModel> = emptyList(),
) {
    val currentExercise: GuidedExerciseUiModel?
        get() = exercises.getOrNull(currentExerciseIndex)
}

/** Every user intent emitted by the stateless guided-workout UI. */
sealed interface GuidedWorkoutEvent {
    data object Back : GuidedWorkoutEvent
    data object StartWorkout : GuidedWorkoutEvent
    data object SkipPreparation : GuidedWorkoutEvent
    data object PauseWorkout : GuidedWorkoutEvent
    data object ResumeWorkout : GuidedWorkoutEvent
    data object CompleteSet : GuidedWorkoutEvent
    data object ToggleTimer : GuidedWorkoutEvent
    data object StartRecorder : GuidedWorkoutEvent
    data object StopRecorder : GuidedWorkoutEvent
    data object UseManualFallback : GuidedWorkoutEvent
    data object RetryRecorder : GuidedWorkoutEvent
    data object SkipRest : GuidedWorkoutEvent
    data class ShowExerciseDetails(val exerciseId: Long) : GuidedWorkoutEvent
    data object HideExerciseDetails : GuidedWorkoutEvent
    data object FinishWorkout : GuidedWorkoutEvent
    data object RequestExit : GuidedWorkoutEvent
    data object RequestFinishSession : GuidedWorkoutEvent
    data object RequestCancelSession : GuidedWorkoutEvent

    data class ChangeManualReps(val value: String) : GuidedWorkoutEvent
    data class AdjustManualReps(val delta: Int) : GuidedWorkoutEvent
    data class ChangeManualWeight(val value: String) : GuidedWorkoutEvent
    data class AdjustManualWeight(val deltaKg: Double) : GuidedWorkoutEvent
    data class AdjustRest(val deltaSeconds: Int) : GuidedWorkoutEvent
}
