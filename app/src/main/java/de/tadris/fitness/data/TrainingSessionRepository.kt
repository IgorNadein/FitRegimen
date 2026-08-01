/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data

import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.max

/** Keep every session/completion lookup on the same local-midnight key. */
internal fun canonicalProgramOccurrence(occurrenceDate: Long, zoneId: String): Long {
    val zone = ZoneId.of(zoneId)
    val localDate = Instant.ofEpochMilli(occurrenceDate).atZone(zone).toLocalDate()
    return ProgramScheduling.occurrenceMillis(localDate, zone)
}

data class TrainingSessionLaunch(
    val sessionId: Long,
    val resumedExistingSession: Boolean,
)

/**
 * Transactional boundary for the guided-workout runner. A session is snapshotted
 * once and thereafter never reads mutable plan targets while it is running.
 *
 * Methods are synchronous deliberately, matching the existing Room API. Callers
 * should invoke them from a worker dispatcher until the database is converted to
 * suspend DAOs.
 */
class TrainingSessionRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val userWeightKg: () -> Double = { DEFAULT_PROGRAM_USER_WEIGHT_KG },
    private val profileSnapshot: () -> FitnessProfileSnapshot = {
        FitnessProfileSnapshot(userWeightKg = userWeightKg())
    },
) {
    private val sessionDao get() = db.trainingSessionDao()
    private val programDao get() = db.trainingProgramDao()
    private val exerciseDao get() = db.exerciseDao()
    private val workoutDao get() = db.workoutDao()

    fun startSessionFromDay(dayId: Long, occurrenceDate: Long, zoneId: String): Long =
        db.runInTransaction<Long> {
            createSessionSnapshot(
                dayId = dayId,
                occurrenceDate = canonicalProgramOccurrence(occurrenceDate, zoneId),
                zoneId = zoneId,
            )
        }

    /**
     * Resolve an unfinished occurrence and create its snapshot atomically.
     * This prevents two quick taps from creating parallel ACTIVE sessions.
     */
    fun findOrStartSessionFromDay(
        dayId: Long,
        occurrenceDate: Long,
        zoneId: String,
    ): TrainingSessionLaunch = db.runInTransaction<TrainingSessionLaunch> {
        val canonicalOccurrence = canonicalProgramOccurrence(occurrenceDate, zoneId)
        val existing = sessionDao.findRunningSessionForDay(dayId, canonicalOccurrence)
        if (existing != null) {
            return@runInTransaction TrainingSessionLaunch(
                sessionId = existing.id,
                resumedExistingSession = true,
            )
        }
        TrainingSessionLaunch(
            sessionId = createSessionSnapshot(dayId, canonicalOccurrence, zoneId),
            resumedExistingSession = false,
        )
    }

    /** Must be called while [db] is already inside a transaction. */
    private fun createSessionSnapshot(
        dayId: Long,
        occurrenceDate: Long,
        zoneId: String,
    ): Long {
            val day = requireNotNull(programDao.findDayById(dayId)) { "Program day $dayId not found" }
            require(!day.isRest) { "Cannot start a workout from a rest day" }
            val program = requireNotNull(programDao.findProgramById(day.programId)) {
                "Program ${day.programId} not found"
            }
            val entries = programDao.getExercisesOfDay(day.id)
            require(entries.isNotEmpty()) { "Program day $dayId has no exercises" }

            val now = clock()
            val fitnessSnapshot = captureProfileSnapshot()
            val sessionId = sessionDao.insertSession(
                TrainingSession(
                    uuid = UUID.randomUUID().toString(),
                    sourceProgramId = program.id,
                    sourceDayId = day.id,
                    occurrenceDate = occurrenceDate,
                    zoneId = zoneId,
                    nameSnapshot = day.name?.takeIf { it.isNotBlank() } ?: program.name,
                    descriptionSnapshot = program.description,
                    profileUuidSnapshot = fitnessSnapshot.profileUuid,
                    userWeightKgSnapshot = fitnessSnapshot.userWeightKg,
                    stepLengthMetersSnapshot = fitnessSnapshot.stepLengthMeters,
                    startedAt = now,
                    updatedAt = now,
                )
            )

            entries.forEachIndexed { exerciseIndex, entry ->
                val exercise = requireNotNull(exerciseDao.findById(entry.exerciseId)) {
                    "Exercise ${entry.exerciseId} referenced by program entry ${entry.id} not found"
                }
                val sessionExerciseId = sessionDao.insertSessionExercise(
                    SessionExercise(
                        sessionId = sessionId,
                        orderIndex = exerciseIndex,
                        sourceProgramExerciseId = entry.id,
                        sourceExerciseId = exercise.id,
                        exerciseUuidSnapshot = exercise.uuid,
                        nameSnapshot = exercise.name,
                        metricKindSnapshot = exercise.metricKind,
                        workoutTypeIdSnapshot = exercise.workoutTypeId,
                        descriptionSnapshot = exercise.description,
                        tipsSnapshot = exercise.tips,
                        videoUrlSnapshot = exercise.videoUrl,
                        imagePathSnapshot = exercise.imagePath,
                        noteSnapshot = entry.note,
                        measurementSource = entry.measurementSource,
                        completionRule = entry.completionRule,
                        prepareSeconds = entry.prepareSeconds.coerceAtLeast(0),
                        restSeconds = entry.restSeconds?.coerceAtLeast(0),
                        transitionSeconds = entry.transitionSeconds?.coerceAtLeast(0),
                    )
                )

                val plannedSets = programDao.getSetsOfProgramExercise(entry.id)
                val snapshotSets = plannedSets.ifEmpty {
                    // A malformed/imported plan must still be runnable.
                    listOf(ProgramSet(programExerciseId = entry.id, orderIndex = 0))
                }
                snapshotSets.forEachIndexed { setIndex, set ->
                    sessionDao.insertSessionSet(
                        SessionSet(
                            sessionExerciseId = sessionExerciseId,
                            orderIndex = setIndex,
                            sourceProgramSetId = set.id.takeIf { it != 0L },
                            type = set.type,
                            targetReps = set.reps,
                            targetRepsMax = set.repsMax,
                            targetWeightKg = set.weightKg,
                            targetDurationMillis = set.durationMillis,
                            targetDistanceMeters = set.distanceMeters,
                        )
                    )
                }
            }
            return sessionId
    }

    fun getCurrentState(sessionId: Long, now: Long = clock()): TrainingSessionState? =
        sessionDao.findSessionById(sessionId)?.let { stateOf(it, now) }

    /** Begin the current set. The optional id makes repeated UI/service events safe. */
    fun beginSet(
        sessionId: Long,
        expectedSetId: Long? = null,
        now: Long = clock(),
    ): TrainingSessionState = db.runInTransaction<TrainingSessionState> {
        val session = requireRunnableSession(sessionId)
        val exercise = requireCurrentExercise(session)
        val set = requireCurrentSet(session, exercise)
        expectedSetId?.let { expected ->
            if (expected != set.id) {
                val requested = sessionDao.findSessionSetById(expected)
                if (requested?.status == SessionSetStatus.COMPLETED) return@runInTransaction stateOf(session, now)
                error("Set $expected is not the current set of session $sessionId")
            }
        }
        if (set.status == SessionSetStatus.COMPLETED || session.phase == TrainingSessionPhase.ACTIVE_SET ||
            session.phase == TrainingSessionPhase.PREPARE
        ) {
            return@runInTransaction stateOf(session, now)
        }
        require(session.phase == TrainingSessionPhase.READY) {
            "Session $sessionId cannot begin a set while phase is ${session.phase}"
        }

        val firstStartOfExercise = exercise.startedAt == null
        val activeExercise = if (firstStartOfExercise || exercise.status == SessionExerciseStatus.PENDING) {
            exercise.copy(status = SessionExerciseStatus.ACTIVE, startedAt = exercise.startedAt ?: now)
                .also(sessionDao::updateSessionExercise)
        } else {
            exercise
        }

        val updatedSession = if (firstStartOfExercise && activeExercise.prepareSeconds > 0) {
            session.copy(
                phase = TrainingSessionPhase.PREPARE,
                phaseStartedAt = now,
                phaseDeadlineAt = TrainingSessionTiming.deadline(
                    now,
                    activeExercise.prepareSeconds * 1_000L,
                ),
                pausedRemainingMillis = null,
                updatedAt = now,
            ).also(sessionDao::updateSession)
        } else {
            activateCurrentSet(session, activeExercise, set, now)
        }
        stateOf(updatedSession, now)
    }

    /** Advance expired prepare, work or rest timers. Safe to call repeatedly. */
    fun advanceTime(sessionId: Long, now: Long = clock()): TrainingSessionState? =
        db.runInTransaction<TrainingSessionState?> {
            var session = sessionDao.findSessionById(sessionId) ?: return@runInTransaction null
            var iterations = 0
            while (session.status == TrainingSessionStatus.ACTIVE && iterations++ < 8) {
                val timedTransition = TrainingSessionTiming.dueTransition(
                    session.phase,
                    now,
                    session.phaseDeadlineAt,
                )
                if (timedTransition == TimedPhaseTransition.NONE) break
                val transitionAt = session.phaseDeadlineAt ?: now
                session = when (timedTransition) {
                    TimedPhaseTransition.START_SET -> {
                        val exercise = requireCurrentExercise(session)
                        val set = requireCurrentSet(session, exercise)
                        activateCurrentSet(session, exercise, set, transitionAt)
                    }

                    TimedPhaseTransition.COMPLETE_SET -> {
                        val exercise = requireCurrentExercise(session)
                        val set = requireCurrentSet(session, exercise)
                        val duration = set.targetDurationMillis
                            ?: set.startedAt?.let { max(0L, transitionAt - it) }
                        completeSet(
                            session = session,
                            exercise = exercise,
                            set = set,
                            metrics = SessionSetMetrics(durationMillis = duration),
                            completionSource = MeasurementSource.CLOCK,
                            linkedWorkoutId = null,
                            completedAt = transitionAt,
                        )
                    }

                    TimedPhaseTransition.FINISH_REST -> session.copy(
                        phase = TrainingSessionPhase.READY,
                        phaseStartedAt = null,
                        phaseDeadlineAt = null,
                        pausedRemainingMillis = null,
                        updatedAt = transitionAt,
                    ).also(sessionDao::updateSession)

                    TimedPhaseTransition.NONE -> session
                }
            }
            stateOf(session, now)
        }

    fun completeManual(
        sessionId: Long,
        setId: Long,
        metrics: SessionSetMetrics = SessionSetMetrics(),
        now: Long = clock(),
    ): TrainingSessionState = complete(
        sessionId = sessionId,
        setId = setId,
        metrics = metrics,
        source = MeasurementSource.USER_INPUT,
        linkedWorkoutId = null,
        now = now,
    )

    fun completeTimer(
        sessionId: Long,
        setId: Long,
        elapsedMillis: Long? = null,
        now: Long = clock(),
    ): TrainingSessionState = db.runInTransaction<TrainingSessionState> {
        val set = requireNotNull(sessionDao.findSessionSetById(setId)) { "Session set $setId not found" }
        val measured = elapsedMillis
            ?: set.startedAt?.let { max(0L, now - it) }
            ?: set.targetDurationMillis
        completeInTransaction(
            sessionId,
            set,
            SessionSetMetrics(durationMillis = measured),
            MeasurementSource.CLOCK,
            null,
            now,
        )
    }

    fun completeTracker(
        sessionId: Long,
        setId: Long,
        linkedWorkoutId: Long,
        metrics: SessionSetMetrics,
        now: Long = clock(),
    ): TrainingSessionState = complete(
        sessionId = sessionId,
        setId = setId,
        metrics = metrics,
        source = MeasurementSource.RECORDER,
        linkedWorkoutId = linkedWorkoutId,
        now = now,
    )

    fun startRest(
        sessionId: Long,
        durationSeconds: Int,
        now: Long = clock(),
    ): TrainingSessionState = db.runInTransaction<TrainingSessionState> {
        val session = requireRunnableSession(sessionId)
        if (session.phase == TrainingSessionPhase.REST) return@runInTransaction stateOf(session, now)
        require(session.phase == TrainingSessionPhase.READY) { "Rest can only start from READY" }
        val durationMillis = durationSeconds.coerceAtLeast(0) * 1_000L
        val updated = if (durationMillis == 0L) session else session.copy(
            phase = TrainingSessionPhase.REST,
            phaseStartedAt = now,
            phaseDeadlineAt = TrainingSessionTiming.deadline(now, durationMillis),
            pausedRemainingMillis = null,
            updatedAt = now,
        ).also(sessionDao::updateSession)
        stateOf(updated, now)
    }

    fun skipRest(sessionId: Long, now: Long = clock()): TrainingSessionState =
        db.runInTransaction<TrainingSessionState> {
            val session = requireNotNull(sessionDao.findSessionById(sessionId)) { "Session $sessionId not found" }
            if (session.phase != TrainingSessionPhase.REST) return@runInTransaction stateOf(session, now)
            val updated = session.copy(
                phase = TrainingSessionPhase.READY,
                phaseStartedAt = null,
                phaseDeadlineAt = null,
                pausedRemainingMillis = null,
                updatedAt = now,
            ).also(sessionDao::updateSession)
            stateOf(updated, now)
        }

    fun adjustRest(sessionId: Long, deltaSeconds: Int, now: Long = clock()): TrainingSessionState =
        db.runInTransaction<TrainingSessionState> {
            val session = requireNotNull(sessionDao.findSessionById(sessionId)) { "Session $sessionId not found" }
            if (session.phase != TrainingSessionPhase.REST) return@runInTransaction stateOf(session, now)
            val deltaMillis = deltaSeconds * 1_000L
            val updated = if (session.status == TrainingSessionStatus.PAUSED) {
                session.copy(
                    pausedRemainingMillis = max(0L, (session.pausedRemainingMillis ?: 0L) + deltaMillis),
                    updatedAt = now,
                )
            } else {
                val deadline = session.phaseDeadlineAt ?: now
                session.copy(
                    phaseDeadlineAt = TrainingSessionTiming.adjustedDeadline(now, deadline, deltaMillis),
                    updatedAt = now,
                )
            }.also(sessionDao::updateSession)
            stateOf(updated, now)
        }

    fun pauseSession(sessionId: Long, now: Long = clock()): TrainingSessionState =
        db.runInTransaction<TrainingSessionState> {
            val session = requireNotNull(sessionDao.findSessionById(sessionId)) { "Session $sessionId not found" }
            if (session.status == TrainingSessionStatus.PAUSED) return@runInTransaction stateOf(session, now)
            require(session.status == TrainingSessionStatus.ACTIVE) { "Only an active session can be paused" }
            val updated = session.copy(
                status = TrainingSessionStatus.PAUSED,
                pausedRemainingMillis = TrainingSessionTiming.remainingMillis(now, session.phaseDeadlineAt),
                phaseDeadlineAt = null,
                updatedAt = now,
            ).also(sessionDao::updateSession)
            stateOf(updated, now)
        }

    fun resumeSession(sessionId: Long, now: Long = clock()): TrainingSessionState =
        db.runInTransaction<TrainingSessionState> {
            val session = requireNotNull(sessionDao.findSessionById(sessionId)) { "Session $sessionId not found" }
            if (session.status == TrainingSessionStatus.ACTIVE) return@runInTransaction stateOf(session, now)
            require(session.status == TrainingSessionStatus.PAUSED) { "Only a paused session can be resumed" }
            val remaining = session.pausedRemainingMillis
            val pausedAt = session.updatedAt
            if (session.phase == TrainingSessionPhase.ACTIVE_SET) {
                val exercise = sessionDao.findExerciseAt(session.id, session.currentExerciseIndex)
                val set = exercise?.let {
                    sessionDao.findSetAt(it.id, session.currentSetIndex)
                }
                if (set?.startedAt != null && set.status == SessionSetStatus.ACTIVE) {
                    sessionDao.updateSessionSet(
                        set.copy(
                            startedAt = TrainingSessionTiming.shiftPastPause(
                                timestamp = set.startedAt,
                                pausedAt = pausedAt,
                                resumedAt = now,
                            )
                        )
                    )
                }
            }
            val updated = session.copy(
                status = TrainingSessionStatus.ACTIVE,
                phaseDeadlineAt = remaining?.let { TrainingSessionTiming.deadline(now, it) },
                pausedRemainingMillis = null,
                accumulatedPausedMillis = TrainingSessionTiming.accumulatePausedMillis(
                    current = session.accumulatedPausedMillis,
                    pausedAt = pausedAt,
                    resumedAt = now,
                ),
                // Keep the phase clock frozen while retaining the immutable workout date.
                phaseStartedAt = session.phaseStartedAt?.let { phaseStartedAt ->
                    TrainingSessionTiming.shiftPastPause(
                        timestamp = phaseStartedAt,
                        pausedAt = pausedAt,
                        resumedAt = now,
                    )
                },
                updatedAt = now,
            ).also(sessionDao::updateSession)
            stateOf(updated, now)
        }

    /**
     * Persist unsubmitted manual values without completing or advancing the set.
     * This makes leaving midway through an input-backed set lossless.
     */
    fun saveCurrentSetDraft(
        sessionId: Long,
        metrics: SessionSetMetrics,
        now: Long = clock(),
    ): TrainingSessionState = db.runInTransaction<TrainingSessionState> {
        val session = requireNotNull(sessionDao.findSessionById(sessionId)) { "Session $sessionId not found" }
        if (session.status == TrainingSessionStatus.COMPLETED || session.status == TrainingSessionStatus.ABORTED) {
            return@runInTransaction stateOf(session, now)
        }
        val exercise = requireCurrentExercise(session)
        val set = requireCurrentSet(session, exercise)
        if (set.status != SessionSetStatus.COMPLETED && set.status != SessionSetStatus.SKIPPED) {
            sessionDao.updateSessionSet(
                set.copy(
                    actualReps = metrics.reps,
                    actualWeightKg = metrics.weightKg,
                )
            )
        }
        stateOf(session, now)
    }

    fun abortSession(sessionId: Long, now: Long = clock()): TrainingSessionState =
        endSession(sessionId, TrainingSessionStatus.ABORTED, now)

    /** Explicit finish skips unfinished snapshot rows; normal final-set completion finishes automatically. */
    fun finishSession(sessionId: Long, now: Long = clock()): TrainingSessionState =
        endSession(sessionId, TrainingSessionStatus.COMPLETED, now)

    private fun complete(
        sessionId: Long,
        setId: Long,
        metrics: SessionSetMetrics,
        source: MeasurementSource,
        linkedWorkoutId: Long?,
        now: Long,
    ): TrainingSessionState = db.runInTransaction<TrainingSessionState> {
        val set = requireNotNull(sessionDao.findSessionSetById(setId)) { "Session set $setId not found" }
        completeInTransaction(sessionId, set, metrics, source, linkedWorkoutId, now)
    }

    private fun completeInTransaction(
        sessionId: Long,
        set: SessionSet,
        metrics: SessionSetMetrics,
        source: MeasurementSource,
        linkedWorkoutId: Long?,
        now: Long,
    ): TrainingSessionState {
        val owner = requireNotNull(sessionDao.findSessionExerciseById(set.sessionExerciseId)) {
            "Owner of session set ${set.id} not found"
        }
        require(owner.sessionId == sessionId) { "Set ${set.id} does not belong to session $sessionId" }
        val session = requireNotNull(sessionDao.findSessionById(sessionId)) { "Session $sessionId not found" }
        if (set.status == SessionSetStatus.COMPLETED) return stateOf(session, now)
        require(session.status == TrainingSessionStatus.ACTIVE) { "Session $sessionId is not active" }
        val currentExercise = requireCurrentExercise(session)
        val currentSet = requireCurrentSet(session, currentExercise)
        require(currentExercise.id == owner.id && currentSet.id == set.id) {
            "Set ${set.id} is not current in session $sessionId"
        }
        val effectiveMetrics = SessionSetMetrics(
            reps = metrics.reps ?: set.targetReps,
            weightKg = metrics.weightKg ?: set.targetWeightKg,
            durationMillis = metrics.durationMillis
                ?: set.startedAt?.let { max(0L, now - it) }
                ?: set.targetDurationMillis,
            distanceMeters = metrics.distanceMeters ?: set.targetDistanceMeters,
        )
        val updated = completeSet(
            session,
            currentExercise,
            set,
            effectiveMetrics,
            source,
            linkedWorkoutId,
            now,
        )
        return stateOf(updated, now)
    }

    private fun activateCurrentSet(
        session: TrainingSession,
        exercise: SessionExercise,
        set: SessionSet,
        startedAt: Long,
    ): TrainingSession {
        if (exercise.status == SessionExerciseStatus.PENDING) {
            sessionDao.updateSessionExercise(
                exercise.copy(status = SessionExerciseStatus.ACTIVE, startedAt = exercise.startedAt ?: startedAt)
            )
        }
        if (set.status == SessionSetStatus.PENDING) {
            sessionDao.updateSessionSet(set.copy(status = SessionSetStatus.ACTIVE, startedAt = startedAt))
        }
        val deadline = if (
            exercise.measurementSource == MeasurementSource.CLOCK &&
            exercise.completionRule == CompletionRule.DURATION_REACHED &&
            set.targetDurationMillis != null
        ) {
            TrainingSessionTiming.deadline(startedAt, set.targetDurationMillis)
        } else {
            null
        }
        return session.copy(
            phase = TrainingSessionPhase.ACTIVE_SET,
            phaseStartedAt = startedAt,
            phaseDeadlineAt = deadline,
            pausedRemainingMillis = null,
            updatedAt = startedAt,
        ).also(sessionDao::updateSession)
    }

    private fun completeSet(
        session: TrainingSession,
        exercise: SessionExercise,
        set: SessionSet,
        metrics: SessionSetMetrics,
        completionSource: MeasurementSource,
        linkedWorkoutId: Long?,
        completedAt: Long,
    ): TrainingSession {
        if (set.status == SessionSetStatus.COMPLETED) return sessionDao.findSessionById(session.id) ?: session
        sessionDao.updateSessionSet(
            set.copy(
                status = SessionSetStatus.COMPLETED,
                completionSource = completionSource,
                actualReps = metrics.reps,
                actualWeightKg = metrics.weightKg,
                actualDurationMillis = metrics.durationMillis,
                actualDistanceMeters = metrics.distanceMeters,
                linkedWorkoutId = linkedWorkoutId,
                startedAt = set.startedAt ?: completedAt,
                completedAt = completedAt,
            )
        )

        val exerciseSets = sessionDao.getSetsOfExercise(exercise.id)
        val currentSetPosition = exerciseSets.indexOfFirst { it.id == set.id }
        require(currentSetPosition >= 0) { "Set ${set.id} disappeared from exercise ${exercise.id}" }
        val hasNextSet = currentSetPosition + 1 < exerciseSets.size
        if (hasNextSet) {
            return moveTo(
                session = session,
                exerciseIndex = session.currentExerciseIndex,
                setIndex = currentSetPosition + 1,
                restSeconds = exercise.restSeconds,
                at = completedAt,
            )
        }

        val completedExercise = exercise.copy(
            status = SessionExerciseStatus.COMPLETED,
            startedAt = exercise.startedAt ?: set.startedAt ?: completedAt,
            completedAt = completedAt,
        )
        sessionDao.updateSessionExercise(completedExercise)
        mirrorCompletedExerciseToLegacyHistory(
            session = session,
            exercise = completedExercise,
            sets = sessionDao.getSetsOfExercise(exercise.id),
            completedAt = completedAt,
        )
        val exercises = sessionDao.getExercisesOfSession(session.id)
        val currentExercisePosition = exercises.indexOfFirst { it.id == exercise.id }
        require(currentExercisePosition >= 0) { "Exercise ${exercise.id} disappeared from session ${session.id}" }
        if (currentExercisePosition + 1 < exercises.size) {
            return moveTo(
                session = session,
                exerciseIndex = currentExercisePosition + 1,
                setIndex = 0,
                restSeconds = exercise.transitionSeconds,
                at = completedAt,
            )
        }
        val finishedSession = session.copy(
            status = TrainingSessionStatus.COMPLETED,
            phase = TrainingSessionPhase.FINISHED,
            completedAt = completedAt,
            phaseStartedAt = null,
            phaseDeadlineAt = null,
            pausedRemainingMillis = null,
            updatedAt = completedAt,
        ).also(sessionDao::updateSession)
        upsertLegacyWorkoutSummary(finishedSession)
        return finishedSession
    }

    private fun moveTo(
        session: TrainingSession,
        exerciseIndex: Int,
        setIndex: Int,
        restSeconds: Int?,
        at: Long,
    ): TrainingSession {
        val restMillis = restSeconds?.coerceAtLeast(0)?.times(1_000L) ?: 0L
        return session.copy(
            phase = if (restMillis > 0) TrainingSessionPhase.REST else TrainingSessionPhase.READY,
            currentExerciseIndex = exerciseIndex,
            currentSetIndex = setIndex,
            phaseStartedAt = at.takeIf { restMillis > 0 },
            phaseDeadlineAt = if (restMillis > 0) TrainingSessionTiming.deadline(at, restMillis) else null,
            pausedRemainingMillis = null,
            updatedAt = at,
        ).also(sessionDao::updateSession)
    }

    /**
     * Transitional read-model bridge for the existing calendar/history screens.
     * The guided snapshot remains authoritative and can hold several recorder
     * segments; the legacy row mirrors the latest completed exercise occurrence.
     */
    private fun mirrorCompletedExerciseToLegacyHistory(
        session: TrainingSession,
        exercise: SessionExercise,
        sets: List<SessionSet>,
        completedAt: Long,
    ) {
        val sourceExerciseId = exercise.sourceProgramExerciseId ?: return
        val occurrenceDate = session.occurrenceDate ?: return
        val existing = programDao.findCompletion(sourceExerciseId, occurrenceDate)
        val linkedWorkoutId = sets.mapNotNull { it.linkedWorkoutId }.lastOrNull()
        val completion = if (existing == null) {
            ProgramCompletion(
                programExerciseId = sourceExerciseId,
                occurrenceDate = occurrenceDate,
                status = PROGRAM_COMPLETION_STATUS_COMPLETED,
                workoutId = linkedWorkoutId,
                completedAt = completedAt,
                profileUuidSnapshot = session.profileUuidSnapshot,
                userWeightKgSnapshot = session.userWeightKgSnapshot,
            ).let { inserted ->
                inserted.copy(id = programDao.insertCompletion(inserted))
            }
        } else {
            existing.copy(
                status = PROGRAM_COMPLETION_STATUS_COMPLETED,
                workoutId = linkedWorkoutId ?: existing.workoutId,
                completedAt = completedAt,
                profileUuidSnapshot = existing.profileUuidSnapshot ?: session.profileUuidSnapshot,
                userWeightKgSnapshot = existing.userWeightKgSnapshot ?: session.userWeightKgSnapshot,
            ).also(programDao::updateCompletion)
        }

        programDao.deleteLogsOfCompletion(completion.id)
        sets.filter { it.status == SessionSetStatus.COMPLETED }.forEachIndexed { index, set ->
            programDao.insertSetLog(
                SetLog(
                    completionId = completion.id,
                    orderIndex = index,
                    type = set.type,
                    reps = set.actualReps,
                    weightKg = set.actualWeightKg,
                    durationMillis = set.actualDurationMillis,
                    distanceMeters = set.actualDistanceMeters,
                    targetReps = set.targetReps,
                    targetRepsMax = set.targetRepsMax,
                    targetWeightKg = set.targetWeightKg,
                    targetDurationMillis = set.targetDurationMillis,
                    targetDistanceMeters = set.targetDistanceMeters,
                )
            )
        }
    }

    private fun endSession(
        sessionId: Long,
        finalStatus: TrainingSessionStatus,
        now: Long,
    ): TrainingSessionState = db.runInTransaction<TrainingSessionState> {
        val session = requireNotNull(sessionDao.findSessionById(sessionId)) { "Session $sessionId not found" }
        if (session.status == TrainingSessionStatus.COMPLETED || session.status == TrainingSessionStatus.ABORTED) {
            if (session.sourceDayId != null && session.occurrenceDate != null) {
                // A terminal session is an immutable archive. Rebuild from the
                // editable completion projection so a later manual uncheck is
                // respected instead of being resurrected from the old snapshot.
                ProgramOccurrenceHistoryRepository(
                    db = db,
                    userWeightKg = userWeightKg,
                    profileSnapshot = profileSnapshot,
                ).rebuild(
                    session.sourceDayId,
                    session.occurrenceDate,
                )
            } else {
                upsertLegacyWorkoutSummary(session)
            }
            return@runInTransaction stateOf(session, now)
        }
        sessionDao.getExercisesOfSession(sessionId).forEach { exercise ->
            sessionDao.getSetsOfExercise(exercise.id).forEach { set ->
                if (set.status == SessionSetStatus.PENDING || set.status == SessionSetStatus.ACTIVE) {
                    sessionDao.updateSessionSet(set.copy(status = SessionSetStatus.SKIPPED, completedAt = now))
                }
            }
            if (exercise.status == SessionExerciseStatus.PENDING || exercise.status == SessionExerciseStatus.ACTIVE) {
                sessionDao.updateSessionExercise(exercise.copy(status = SessionExerciseStatus.SKIPPED, completedAt = now))
            }
        }
        val updated = session.copy(
            status = finalStatus,
            phase = TrainingSessionPhase.FINISHED,
            completedAt = now,
            phaseStartedAt = null,
            phaseDeadlineAt = null,
            pausedRemainingMillis = null,
            accumulatedPausedMillis = if (session.status == TrainingSessionStatus.PAUSED) {
                TrainingSessionTiming.accumulatePausedMillis(
                    current = session.accumulatedPausedMillis,
                    pausedAt = session.updatedAt,
                    resumedAt = now,
                )
            } else {
                session.accumulatedPausedMillis
            },
            updatedAt = now,
        ).also(sessionDao::updateSession)
        upsertLegacyWorkoutSummary(updated)
        stateOf(updated, now)
    }

    /**
     * Creates or repairs the single dashboard/statistics row for a terminal
     * guided session. Its deterministic UUID is the idempotency key; recorder
     * child workouts and their samples are deliberately left untouched.
     */
    private fun upsertLegacyWorkoutSummary(session: TrainingSession): Workout? {
        val exercises = sessionDao.getExercisesOfSession(session.id)
        val setsByExerciseId = exercises.associate { exercise ->
            exercise.id to sessionDao.getSetsOfExercise(exercise.id)
        }
        val sourceDayId = session.sourceDayId
        val occurrenceDate = session.occurrenceDate
        if (sourceDayId != null && occurrenceDate != null) {
            // ProgramCompletion + SetLog are the editable source of truth for a
            // scheduled day. Refresh them from the terminal runner snapshot,
            // then let the shared occurrence projector create the one Workout
            // consumed by history and statistics.
            exercises
                .filter { it.status == SessionExerciseStatus.COMPLETED }
                .forEach { exercise ->
                    mirrorCompletedExerciseToLegacyHistory(
                        session = session,
                        exercise = exercise,
                        sets = setsByExerciseId[exercise.id].orEmpty(),
                        completedAt = exercise.completedAt ?: session.completedAt ?: session.updatedAt,
                    )
                }
            return ProgramOccurrenceHistoryRepository(
                db = db,
                userWeightKg = userWeightKg,
                profileSnapshot = profileSnapshot,
            ).rebuild(sourceDayId, occurrenceDate)
        }
        val linkedWorkoutIds = setsByExerciseId.values
            .flatten()
            .filter { it.status == SessionSetStatus.COMPLETED }
            .mapNotNull { it.linkedWorkoutId }
            .toSet()
        val linkedWorkouts = linkedWorkoutIds.mapNotNull { workoutId ->
            workoutDao.findById(workoutId)?.let { workoutId to it }
        }.toMap()

        val summaryUuid = GuidedWorkoutHistoryMapper.summaryUuid(session.uuid)
        val existing = workoutDao.findByUuid(summaryUuid)
        val summary = GuidedWorkoutHistoryMapper.toWorkout(
            session = session,
            exercises = exercises,
            setsByExerciseId = setsByExerciseId,
            linkedWorkoutsById = linkedWorkouts,
            workoutId = existing?.id ?: nextFreeWorkoutId(),
        ) ?: return null

        if (existing == null) workoutDao.insertWorkout(summary) else workoutDao.updateWorkout(summary)
        pointCompletedProgramExercisesToSummary(
            session = session,
            exercises = exercises,
            setsByExerciseId = setsByExerciseId,
            summaryWorkoutId = summary.id,
        )
        return summary
    }

    private fun pointCompletedProgramExercisesToSummary(
        session: TrainingSession,
        exercises: List<SessionExercise>,
        setsByExerciseId: Map<Long, List<SessionSet>>,
        summaryWorkoutId: Long,
    ) {
        val occurrenceDate = session.occurrenceDate ?: return
        exercises.filter { it.status == SessionExerciseStatus.COMPLETED }.forEach { exercise ->
            val sourceExerciseId = exercise.sourceProgramExerciseId ?: return@forEach
            val completedAt = exercise.completedAt ?: session.completedAt ?: session.updatedAt
            // Rebuild the legacy per-set projection as well. This repairs imported
            // or older terminal sessions whose completion/log rows are missing.
            mirrorCompletedExerciseToLegacyHistory(
                session = session,
                exercise = exercise,
                sets = setsByExerciseId[exercise.id].orEmpty(),
                completedAt = completedAt,
            )
            programDao.findCompletion(sourceExerciseId, occurrenceDate)
                ?.copy(
                    status = PROGRAM_COMPLETION_STATUS_COMPLETED,
                    workoutId = summaryWorkoutId,
                    completedAt = completedAt,
                )
                ?.also(programDao::updateCompletion)
        }
    }

    private fun nextFreeWorkoutId(): Long {
        var candidate = System.nanoTime().coerceAtLeast(1L)
        while (workoutDao.findById(candidate) != null) {
            candidate = if (candidate == Long.MAX_VALUE) 1L else candidate + 1L
        }
        return candidate
    }

    private fun captureProfileSnapshot(): FitnessProfileSnapshot {
        val captured = profileSnapshot()
        val fallbackWeight = userWeightKg()
            .takeIf { it.isFinite() && it > 0.0 }
            ?: DEFAULT_PROGRAM_USER_WEIGHT_KG
        return FitnessProfileSnapshot(
            profileUuid = captured.profileUuid?.trim()?.takeIf(String::isNotEmpty),
            userWeightKg = captured.userWeightKg
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?: fallbackWeight,
            stepLengthMeters = captured.stepLengthMeters
                ?.takeIf { it.isFinite() && it > 0.0 },
        )
    }

    private fun requireRunnableSession(sessionId: Long): TrainingSession {
        val session = requireNotNull(sessionDao.findSessionById(sessionId)) { "Session $sessionId not found" }
        require(session.status == TrainingSessionStatus.ACTIVE) { "Session $sessionId is not active" }
        return session
    }

    private fun requireCurrentExercise(session: TrainingSession): SessionExercise =
        requireNotNull(sessionDao.findExerciseAt(session.id, session.currentExerciseIndex)) {
            "Current exercise ${session.currentExerciseIndex} of session ${session.id} not found"
        }

    private fun requireCurrentSet(session: TrainingSession, exercise: SessionExercise): SessionSet =
        requireNotNull(sessionDao.findSetAt(exercise.id, session.currentSetIndex)) {
            "Current set ${session.currentSetIndex} of exercise ${exercise.id} not found"
        }

    private fun stateOf(session: TrainingSession, now: Long): TrainingSessionState {
        val exercises = sessionDao.getExercisesOfSession(session.id)
        val exercise = exercises.firstOrNull { it.orderIndex == session.currentExerciseIndex }
        val sets = exercise?.let { sessionDao.getSetsOfExercise(it.id) }.orEmpty()
        val set = sets.firstOrNull { it.orderIndex == session.currentSetIndex }
        val remaining = when {
            session.status == TrainingSessionStatus.PAUSED -> session.pausedRemainingMillis
            else -> TrainingSessionTiming.remainingMillis(now, session.phaseDeadlineAt)
        }
        return TrainingSessionState(session, exercise, set, exercises.size, sets.size, remaining)
    }
}
