/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data

/**
 * Atomic write boundary for a program checklist and its dashboard/statistics row.
 *
 * All public mutations rebuild the occurrence projection before their transaction
 * commits, so observers can never see a checked exercise without its corresponding
 * summary workout (or a stale summary after the final check is removed).
 */
class ProgramOccurrenceHistoryRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val userWeightKg: () -> Double = { DEFAULT_PROGRAM_USER_WEIGHT_KG },
    private val profileSnapshot: () -> FitnessProfileSnapshot = {
        FitnessProfileSnapshot(userWeightKg = userWeightKg())
    },
) {
    private val programDao get() = db.trainingProgramDao()
    private val workoutDao get() = db.workoutDao()

    fun setCompleted(entryId: Long, occurrenceDate: Long): Workout? =
        db.runInTransaction<Workout?> {
            val entry = requireEntry(entryId)
            adoptLegacySummary(entry.dayId, occurrenceDate)
            val now = clock()
            val existing = programDao.findCompletion(entryId, occurrenceDate)
            if (existing == null) {
                programDao.insertCompletion(
                    newCompletion(
                        entryId = entryId,
                        occurrenceDate = occurrenceDate,
                        status = PROGRAM_COMPLETION_STATUS_COMPLETED,
                        completedAt = now,
                    ),
                )
            } else {
                programDao.updateCompletion(
                    existing.copy(
                        status = PROGRAM_COMPLETION_STATUS_COMPLETED,
                        completedAt = now,
                    )
                )
            }
            rebuildInTransaction(entry.dayId, occurrenceDate)
        }

    fun setUncompleted(entryId: Long, occurrenceDate: Long): Workout? =
        db.runInTransaction<Workout?> {
            val entry = requireEntry(entryId)
            adoptLegacySummary(entry.dayId, occurrenceDate)
            programDao.findCompletion(entryId, occurrenceDate)?.let { completion ->
                programDao.deleteCompletionWithLogs(completion)
            }
            rebuildInTransaction(entry.dayId, occurrenceDate)
        }

    fun replaceLogs(
        entryId: Long,
        occurrenceDate: Long,
        logs: List<SetLog>,
    ): Workout? = db.runInTransaction<Workout?> {
        val entry = requireEntry(entryId)
        adoptLegacySummary(entry.dayId, occurrenceDate)
        val now = clock()
        val existing = programDao.findCompletion(entryId, occurrenceDate)
        val completionId = if (existing == null) {
            programDao.insertCompletion(
                newCompletion(
                    entryId = entryId,
                    occurrenceDate = occurrenceDate,
                    status = PROGRAM_COMPLETION_STATUS_COMPLETED,
                    completedAt = now,
                ),
            )
        } else {
            programDao.updateCompletion(
                existing.copy(
                    status = PROGRAM_COMPLETION_STATUS_COMPLETED,
                    completedAt = now,
                )
            )
            existing.id
        }
        programDao.deleteLogsOfCompletion(completionId)
        logs.forEachIndexed { index, log ->
            programDao.insertSetLog(
                log.copy(
                    completionId = completionId,
                    id = 0,
                    orderIndex = index,
                )
            )
        }
        rebuildInTransaction(entry.dayId, occurrenceDate)
    }

    fun setSkipped(entryId: Long, occurrenceDate: Long): Workout? =
        db.runInTransaction<Workout?> {
            val entry = requireEntry(entryId)
            adoptLegacySummary(entry.dayId, occurrenceDate)
            val now = clock()
            val existing = programDao.findCompletion(entryId, occurrenceDate)
            if (existing == null) {
                programDao.insertCompletion(
                    newCompletion(
                        entryId = entryId,
                        occurrenceDate = occurrenceDate,
                        status = PROGRAM_COMPLETION_STATUS_SKIPPED,
                        completedAt = now,
                    ),
                )
            } else {
                programDao.deleteLogsOfCompletion(existing.id)
                programDao.updateCompletion(
                    existing.copy(
                        status = PROGRAM_COMPLETION_STATUS_SKIPPED,
                        workoutId = null,
                        completedAt = now,
                    )
                )
            }
            rebuildInTransaction(entry.dayId, occurrenceDate)
        }

    fun linkWorkout(
        entryId: Long,
        occurrenceDate: Long,
        sourceWorkoutId: Long,
    ): Workout? = db.runInTransaction<Workout?> {
        val entry = requireEntry(entryId)
        adoptLegacySummary(entry.dayId, occurrenceDate)
        val source = requireNotNull(workoutDao.findById(sourceWorkoutId)) {
            "Workout $sourceWorkoutId not found"
        }
        require(!ProgramOccurrenceWorkoutMapper.isGeneratedSummary(source)) {
            "Generated workout $sourceWorkoutId cannot be linked as a source"
        }
        val now = clock()
        val existing = programDao.findCompletion(entryId, occurrenceDate)
        val linkedSnapshot = captureCompletionSnapshot(source)
        if (existing == null) {
            programDao.insertCompletion(
                newCompletion(
                    entryId = entryId,
                    occurrenceDate = occurrenceDate,
                    status = PROGRAM_COMPLETION_STATUS_COMPLETED,
                    workoutId = sourceWorkoutId,
                    completedAt = now,
                    snapshot = linkedSnapshot,
                ),
            )
        } else {
            programDao.updateCompletion(
                existing.copy(
                    status = PROGRAM_COMPLETION_STATUS_COMPLETED,
                    workoutId = sourceWorkoutId,
                    completedAt = now,
                    profileUuidSnapshot = existing.profileUuidSnapshot
                        ?: linkedSnapshot.profileUuid,
                    userWeightKgSnapshot = existing.userWeightKgSnapshot
                        ?: linkedSnapshot.userWeightKg,
                )
            )
        }
        rebuildInTransaction(entry.dayId, occurrenceDate)
    }

    fun rebuild(dayId: Long, occurrenceDate: Long): Workout? =
        db.runInTransaction<Workout?> {
            requireNotNull(programDao.findDayById(dayId)) { "Program day $dayId not found" }
            adoptLegacySummary(dayId, occurrenceDate)
            rebuildInTransaction(dayId, occurrenceDate)
        }

    /** Idempotently refreshes existing summaries after projection-model changes. */
    fun rebuildAll(): Int {
        val keys = programDao.getCompletedOccurrenceKeys()
        keys.forEach { key -> rebuild(key.dayId, key.occurrenceDate) }
        return keys.size
    }

    /** Must only be called from one of the repository's transactions. */
    private fun rebuildInTransaction(dayId: Long, occurrenceDate: Long): Workout? {
        val day = requireNotNull(programDao.findDayById(dayId)) { "Program day $dayId not found" }
        val program = requireNotNull(programDao.findProgramById(day.programId)) {
            "Training program ${day.programId} not found"
        }
        val expectedUuid = ProgramOccurrenceWorkoutMapper.summaryUuid(dayId, occurrenceDate)
        val mapping = programDao.findOccurrenceSummary(dayId, occurrenceDate)
        val mappedWorkout = mapping?.let { workoutDao.findById(it.summaryWorkoutId) }
        val existingSummary = mappedWorkout?.takeIf { it.uuid == expectedUuid }
            ?: workoutDao.findByUuid(expectedUuid)

        val completions = programDao.getCompletionsOfDayOccurrence(dayId, occurrenceDate)
        val results = completions.mapNotNull { completion ->
            val entry = programDao.findProgramExerciseById(completion.programExerciseId)
                ?.takeIf { it.dayId == dayId }
                ?: return@mapNotNull null
            val exercise = db.exerciseDao().findById(entry.exerciseId)
                ?: return@mapNotNull null
            val storedTypeMet = exercise.workoutTypeId
                ?.let(db.workoutTypeDao()::findById)
                ?.MET
                ?.takeIf { it > 0 }
                ?.toDouble()
            ProgramOccurrenceExerciseResult(
                completion = completion,
                exercise = if (exercise.met == null && storedTypeMet != null) {
                    exercise.copy(met = storedTypeMet)
                } else {
                    exercise
                },
                plannedSets = programDao.getSetsOfProgramExercise(entry.id),
                actualLogs = programDao.getLogsOfCompletion(completion.id),
            )
        }
        val linkedWorkouts = completions
            .asSequence()
            .filter { it.status == PROGRAM_COMPLETION_STATUS_COMPLETED }
            .mapNotNull(ProgramCompletion::workoutId)
            .distinct()
            .mapNotNull(workoutDao::findById)
            .filterNot(ProgramOccurrenceWorkoutMapper::isGeneratedSummary)
            .toList()

        val title = day.name
            ?.takeIf { it.isNotBlank() && it != program.name }
            ?.let { "${program.name} · $it" }
            ?: program.name
        val projected = ProgramOccurrenceWorkoutMapper.toWorkout(
            dayId = dayId,
            occurrenceDate = occurrenceDate,
            title = title,
            exerciseResults = results,
            linkedWorkouts = linkedWorkouts,
            workoutId = existingSummary?.id ?: nextFreeWorkoutId(),
            userWeightKg = userWeightKg(),
            programUuid = program.uuid,
            programName = program.name,
            iconOverride = program.icon,
        )

        if (projected == null) {
            val generated = existingSummary
                ?: mappedWorkout?.takeIf(ProgramOccurrenceWorkoutMapper::isGeneratedSummary)
            if (generated != null) {
                workoutDao.deleteWorkout(generated)
            } else if (mapping != null) {
                // Never delete an unrelated real workout referenced by a corrupt row.
                programDao.deleteOccurrenceSummary(mapping)
            }
            workoutDao.findByUuid(expectedUuid)?.let(workoutDao::deleteWorkout)
            return null
        }

        if (existingSummary == null) {
            workoutDao.insertWorkout(projected)
        } else {
            workoutDao.updateWorkout(projected)
        }

        val currentMapping = programDao.findOccurrenceSummary(dayId, occurrenceDate)
        if (currentMapping == null) {
            programDao.insertOccurrenceSummary(
                ProgramOccurrenceSummary(dayId, occurrenceDate, projected.id)
            )
        } else if (currentMapping.summaryWorkoutId != projected.id) {
            programDao.updateOccurrenceSummary(currentMapping.copy(summaryWorkoutId = projected.id))
        }
        return projected
    }

    /**
     * Adopt an old `guided-session:` row referenced by completions instead of
     * treating that generated aggregate as a real tracker source. The workout id
     * is preserved while its UUID is moved to the occurrence namespace.
     */
    private fun adoptLegacySummary(dayId: Long, occurrenceDate: Long) {
        val expectedUuid = ProgramOccurrenceWorkoutMapper.summaryUuid(dayId, occurrenceDate)
        val currentMapping = programDao.findOccurrenceSummary(dayId, occurrenceDate)
        val mapped = currentMapping?.let { workoutDao.findById(it.summaryWorkoutId) }

        if (mapped?.uuid == expectedUuid) return
        if (currentMapping != null && mapped == null) {
            programDao.deleteOccurrenceSummary(currentMapping)
        } else if (currentMapping != null && mapped != null &&
            !ProgramOccurrenceWorkoutMapper.isGeneratedSummary(mapped)
        ) {
            // A corrupt mapping must not gain ownership of a real source workout.
            programDao.deleteOccurrenceSummary(currentMapping)
        }

        workoutDao.findByUuid(expectedUuid)?.let { existing ->
            upsertMapping(dayId, occurrenceDate, existing.id)
            return
        }

        val completions = programDao.getCompletionsOfDayOccurrence(dayId, occurrenceDate)
        val legacy = mapped
            ?.takeIf { it.uuid.startsWith(LEGACY_GUIDED_WORKOUT_UUID_PREFIX) }
            ?: completions.asSequence()
                .mapNotNull(ProgramCompletion::workoutId)
                .distinct()
                .mapNotNull(workoutDao::findById)
                .firstOrNull { it.uuid.startsWith(LEGACY_GUIDED_WORKOUT_UUID_PREFIX) }
            ?: return

        completions.filter { it.workoutId == legacy.id }.forEach { completion ->
            programDao.updateCompletion(completion.copy(workoutId = null))
        }
        workoutDao.updateWorkout(legacy.copy(uuid = expectedUuid))
        upsertMapping(dayId, occurrenceDate, legacy.id)
    }

    private fun upsertMapping(dayId: Long, occurrenceDate: Long, workoutId: Long) {
        val existing = programDao.findOccurrenceSummary(dayId, occurrenceDate)
        if (existing == null) {
            programDao.insertOccurrenceSummary(
                ProgramOccurrenceSummary(dayId, occurrenceDate, workoutId)
            )
        } else if (existing.summaryWorkoutId != workoutId) {
            programDao.updateOccurrenceSummary(existing.copy(summaryWorkoutId = workoutId))
        }
    }

    private fun requireEntry(entryId: Long): ProgramExercise =
        requireNotNull(programDao.findProgramExerciseById(entryId)) {
            "Program exercise $entryId not found"
        }

    /** Called only for a new occurrence; updates intentionally keep its snapshot. */
    private fun newCompletion(
        entryId: Long,
        occurrenceDate: Long,
        status: Int,
        workoutId: Long? = null,
        completedAt: Long,
        snapshot: FitnessProfileSnapshot = captureCompletionSnapshot(),
    ): ProgramCompletion {
        return ProgramCompletion(
            programExerciseId = entryId,
            occurrenceDate = occurrenceDate,
            status = status,
            workoutId = workoutId,
            completedAt = completedAt,
            profileUuidSnapshot = snapshot.profileUuid,
            userWeightKgSnapshot = snapshot.userWeightKg,
        )
    }

    private fun captureCompletionSnapshot(sourceWorkout: Workout? = null): FitnessProfileSnapshot {
        val captured = profileSnapshot()
        val fallbackWeight = userWeightKg()
            .takeIf { it.isFinite() && it > 0.0 }
            ?: DEFAULT_PROGRAM_USER_WEIGHT_KG
        return FitnessProfileSnapshot(
            profileUuid = sourceWorkout?.profileUuid?.trim()?.takeIf(String::isNotEmpty)
                ?: captured.profileUuid?.trim()?.takeIf(String::isNotEmpty),
            userWeightKg = sourceWorkout?.userWeightKgSnapshot
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?: captured.userWeightKg
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?: fallbackWeight,
            stepLengthMeters = sourceWorkout?.stepLengthMetersSnapshot
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?: captured.stepLengthMeters?.takeIf { it.isFinite() && it > 0.0 },
        )
    }

    private fun nextFreeWorkoutId(): Long {
        var candidate = System.nanoTime().coerceAtLeast(1L)
        while (workoutDao.findById(candidate) != null) {
            candidate = if (candidate == Long.MAX_VALUE) 1L else candidate + 1L
        }
        return candidate
    }
}
