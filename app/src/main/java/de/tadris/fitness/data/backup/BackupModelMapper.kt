/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data.backup

import de.tadris.fitness.data.CompletionRule
import de.tadris.fitness.data.MeasurementSource
import de.tadris.fitness.data.MetricKind
import de.tadris.fitness.data.SessionExercise
import de.tadris.fitness.data.SessionExerciseStatus
import de.tadris.fitness.data.SessionSet
import de.tadris.fitness.data.SessionSetStatus
import de.tadris.fitness.data.SetType
import de.tadris.fitness.data.TrainingSession
import de.tadris.fitness.data.TrainingSessionPhase
import de.tadris.fitness.data.TrainingSessionStatus
import de.tadris.fitness.data.WorkoutData
import de.tadris.fitness.data.backup.model.SessionExerciseExport
import de.tadris.fitness.data.backup.model.SessionSetExport
import de.tadris.fitness.data.backup.model.TrainingSessionExport

internal data class SessionExerciseWithSets(
    val exercise: SessionExercise,
    val sets: List<SessionSet>,
)

internal fun TrainingSession.toBackupExport(
    exercises: List<SessionExerciseWithSets>,
    workoutUuidForId: (Long) -> String?,
    sourceProgramUuidForId: (Long) -> String? = { null },
    sourceDayIndexForId: (Long) -> Int? = { null },
    sourceProgramExerciseIndexForId: (Long) -> Int? = { null },
    sourceProgramSetIndexForId: (Long) -> Int? = { null },
    imageArchivePathForExercise: (SessionExercise) -> String? = { null },
): TrainingSessionExport {
    val resolvedProgramUuid = sourceProgramId?.let(sourceProgramUuidForId)
    val resolvedDayIndex = sourceDayId?.let(sourceDayIndexForId)
    val hasPortableProgramSource = !resolvedProgramUuid.isNullOrBlank() &&
        resolvedDayIndex != null && resolvedDayIndex >= 0
    return TrainingSessionExport(
        uuid = uuid,
        sourceProgramUuid = resolvedProgramUuid.takeIf { hasPortableProgramSource },
        sourceDayIndex = resolvedDayIndex.takeIf { hasPortableProgramSource },
        occurrenceDate = occurrenceDate,
        zoneId = zoneId,
        nameSnapshot = nameSnapshot,
        descriptionSnapshot = descriptionSnapshot,
        profileUuidSnapshot = profileUuidSnapshot,
        userWeightKgSnapshot = userWeightKgSnapshot,
        stepLengthMetersSnapshot = stepLengthMetersSnapshot,
        status = status.name,
        phase = phase.name,
        currentExerciseIndex = currentExerciseIndex,
        currentSetIndex = currentSetIndex,
        startedAt = startedAt,
        updatedAt = updatedAt,
        completedAt = completedAt,
        phaseStartedAt = phaseStartedAt,
        phaseDeadlineAt = phaseDeadlineAt,
        pausedRemainingMillis = pausedRemainingMillis,
        accumulatedPausedMillis = accumulatedPausedMillis,
        exercises = exercises.map { tree ->
            val exercise = tree.exercise
            val portableSourceExerciseIndex = exercise.sourceProgramExerciseId
                ?.takeIf { hasPortableProgramSource }
                ?.let(sourceProgramExerciseIndexForId)
            SessionExerciseExport(
                sourceProgramExerciseIndex = portableSourceExerciseIndex,
                exerciseUuidSnapshot = exercise.exerciseUuidSnapshot,
                nameSnapshot = exercise.nameSnapshot,
                metricKindSnapshot = exercise.metricKindSnapshot.name,
                workoutTypeIdSnapshot = exercise.workoutTypeIdSnapshot,
                descriptionSnapshot = exercise.descriptionSnapshot,
                tipsSnapshot = exercise.tipsSnapshot,
                videoUrlSnapshot = exercise.videoUrlSnapshot,
                imageArchivePathSnapshot = imageArchivePathForExercise(exercise),
                // A private path is meaningful only inside the source installation and
                // could accidentally resolve to unrelated data on the target device.
                imagePathSnapshot = null,
                noteSnapshot = exercise.noteSnapshot,
                measurementSource = exercise.measurementSource.name,
                completionRule = exercise.completionRule.name,
                prepareSeconds = exercise.prepareSeconds,
                restSeconds = exercise.restSeconds,
                transitionSeconds = exercise.transitionSeconds,
                status = exercise.status.name,
                startedAt = exercise.startedAt,
                completedAt = exercise.completedAt,
                sets = tree.sets.map { set ->
                    SessionSetExport(
                        sourceProgramSetIndex = set.sourceProgramSetId
                            ?.takeIf { portableSourceExerciseIndex != null }
                            ?.let(sourceProgramSetIndexForId),
                        type = set.type.name,
                        status = set.status.name,
                        completionSource = set.completionSource?.name,
                        targetReps = set.targetReps,
                        targetRepsMax = set.targetRepsMax,
                        targetWeightKg = set.targetWeightKg,
                        targetDurationMillis = set.targetDurationMillis,
                        targetDistanceMeters = set.targetDistanceMeters,
                        actualReps = set.actualReps,
                        actualWeightKg = set.actualWeightKg,
                        actualDurationMillis = set.actualDurationMillis,
                        actualDistanceMeters = set.actualDistanceMeters,
                        linkedWorkoutUuid = set.linkedWorkoutId?.let(workoutUuidForId),
                        startedAt = set.startedAt,
                        completedAt = set.completedAt,
                    )
                },
            )
        },
    )
}

internal fun TrainingSessionExport.toEntity(
    sourceProgramId: Long? = null,
    sourceDayId: Long? = null,
): TrainingSession = TrainingSession(
    uuid = uuid,
    sourceProgramId = sourceProgramId,
    sourceDayId = sourceDayId,
    occurrenceDate = occurrenceDate,
    zoneId = zoneId,
    nameSnapshot = nameSnapshot,
    descriptionSnapshot = descriptionSnapshot,
    profileUuidSnapshot = profileUuidSnapshot,
    userWeightKgSnapshot = userWeightKgSnapshot,
    stepLengthMetersSnapshot = stepLengthMetersSnapshot,
    status = TrainingSessionStatus.findByName(status.normalizedEnumName()),
    phase = TrainingSessionPhase.findByName(phase.normalizedEnumName()),
    currentExerciseIndex = currentExerciseIndex.coerceAtLeast(0),
    currentSetIndex = currentSetIndex.coerceAtLeast(0),
    startedAt = startedAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    phaseStartedAt = phaseStartedAt,
    phaseDeadlineAt = phaseDeadlineAt,
    pausedRemainingMillis = pausedRemainingMillis,
    accumulatedPausedMillis = accumulatedPausedMillis.coerceAtLeast(0L),
)

internal fun SessionExerciseExport.toEntity(
    sessionId: Long,
    orderIndex: Int,
    sourceProgramExerciseId: Long? = null,
    sourceExerciseId: Long? = null,
    restoredImagePath: String? = null,
): SessionExercise = SessionExercise(
    sessionId = sessionId,
    orderIndex = orderIndex,
    sourceProgramExerciseId = sourceProgramExerciseId,
    sourceExerciseId = sourceExerciseId,
    exerciseUuidSnapshot = exerciseUuidSnapshot,
    nameSnapshot = nameSnapshot,
    metricKindSnapshot = MetricKind.findByName(metricKindSnapshot.normalizedEnumName()),
    workoutTypeIdSnapshot = workoutTypeIdSnapshot,
    descriptionSnapshot = descriptionSnapshot,
    tipsSnapshot = tipsSnapshot,
    videoUrlSnapshot = videoUrlSnapshot,
    imagePathSnapshot = restoredImagePath,
    noteSnapshot = noteSnapshot,
    measurementSource = MeasurementSource.findByName(measurementSource.normalizedEnumName()),
    completionRule = CompletionRule.findByName(completionRule.normalizedEnumName()),
    prepareSeconds = prepareSeconds.coerceAtLeast(0),
    restSeconds = restSeconds?.coerceAtLeast(0),
    transitionSeconds = transitionSeconds?.coerceAtLeast(0),
    status = SessionExerciseStatus.findByName(status.normalizedEnumName()),
    startedAt = startedAt,
    completedAt = completedAt,
)

internal fun SessionSetExport.toEntity(
    sessionExerciseId: Long,
    orderIndex: Int,
    workoutIdForUuid: (String) -> Long?,
    sourceProgramSetId: Long? = null,
): SessionSet = SessionSet(
    sessionExerciseId = sessionExerciseId,
    orderIndex = orderIndex,
    sourceProgramSetId = sourceProgramSetId,
    type = SetType.findByName(type.normalizedEnumName()),
    status = SessionSetStatus.findByName(status.normalizedEnumName()),
    completionSource = completionSource
        ?.normalizedEnumName()
        ?.let { name -> MeasurementSource.entries.firstOrNull { it.name == name } },
    targetReps = targetReps,
    targetRepsMax = targetRepsMax,
    targetWeightKg = targetWeightKg,
    targetDurationMillis = targetDurationMillis,
    targetDistanceMeters = targetDistanceMeters,
    actualReps = actualReps,
    actualWeightKg = actualWeightKg,
    actualDurationMillis = actualDurationMillis,
    actualDistanceMeters = actualDistanceMeters,
    linkedWorkoutId = linkedWorkoutUuid?.let(workoutIdForUuid),
    startedAt = startedAt,
    completedAt = completedAt,
)

/** Rebind every child FK when an incoming numeric workout id collides on merge. */
internal fun WorkoutData.remapForBackupImport(
    newWorkoutId: Long,
    intervalSetIdForImportedId: (Long) -> Long = { it },
    workoutTypeIdForImportedId: (String) -> String = { it },
): WorkoutData = copy(
    workout = workout.copy(
        id = newWorkoutId,
        intervalSetUsedId = intervalSetIdForImportedId(workout.intervalSetUsedId),
        workoutTypeId = workoutTypeIdForImportedId(workout.workoutTypeId),
    ),
    samples = samples.map { it.copy(workoutId = newWorkoutId, id = 0) },
    pauses = pauses.map { it.copy(workoutId = newWorkoutId, id = 0) },
    events = events.map { it.copy(workoutId = newWorkoutId, id = 0) },
    repetitionSamples = repetitionSamples.map { it.copy(workoutId = newWorkoutId, id = 0) },
    heartRateSamples = heartRateSamples.map { it.copy(workoutId = newWorkoutId, id = 0) },
)

/** Pure candidate search, split out so collision handling can be unit-tested. */
internal fun firstAvailableWorkoutId(
    seed: Long,
    isOccupied: (Long) -> Boolean,
): Long {
    var candidate = seed.coerceAtLeast(1L)
    while (isOccupied(candidate)) {
        check(candidate != Long.MAX_VALUE) { "No numeric workout id is available" }
        candidate++
    }
    return candidate
}

private fun String.normalizedEnumName(): String = trim().uppercase()
