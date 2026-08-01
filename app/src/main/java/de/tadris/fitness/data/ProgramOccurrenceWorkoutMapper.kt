/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data

import kotlin.math.roundToLong

internal const val PROGRAM_OCCURRENCE_WORKOUT_UUID_PREFIX = "program-occurrence:"
internal const val LEGACY_GUIDED_WORKOUT_UUID_PREFIX = "guided-session:"

/** All persisted inputs belonging to one exercise occurrence. */
internal data class ProgramOccurrenceExerciseResult(
    val completion: ProgramCompletion,
    val exercise: Exercise,
    val plannedSets: List<ProgramSet>,
    val actualLogs: List<SetLog>,
)

internal const val DEFAULT_PROGRAM_USER_WEIGHT_KG = 80.0

/** Context-free defaults and the same MET calorie equation used by CalorieCalculator. */
internal object ProgramExerciseEnergyEstimator {
    private const val JOULES_PER_KILOCALORIE = 4_186.8
    private const val MIN_MET = 0.5
    private const val MAX_MET = 25.0
    private const val MIN_SECONDS_PER_REP = 0.1
    private const val MAX_SECONDS_PER_REP = 60.0

    fun met(exercise: Exercise): Double = exercise.met
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?.coerceIn(MIN_MET, MAX_MET)
        ?: fallbackMet(exercise.metricKind, exercise.workoutTypeId)

    fun secondsPerRep(exercise: Exercise): Double = exercise.secondsPerRep
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?.coerceIn(MIN_SECONDS_PER_REP, MAX_SECONDS_PER_REP)
        ?: fallbackSecondsPerRep(exercise.metricKind, exercise.workoutTypeId)

    fun energyJoules(durationMillis: Long, met: Double, userWeightKg: Double): Double {
        if (durationMillis <= 0L) return 0.0
        val weight = userWeightKg
            .takeIf { it.isFinite() && it > 0.0 }
            ?: DEFAULT_PROGRAM_USER_WEIGHT_KG
        val minutes = durationMillis.toDouble() / 60_000.0
        val kilocalories = minutes * (met * 3.5 * weight) / 200.0
        return (kilocalories * JOULES_PER_KILOCALORIE)
            .takeIf(Double::isFinite)
            ?.coerceAtLeast(0.0)
            ?: Double.MAX_VALUE
    }

    private fun fallbackMet(kind: MetricKind, workoutTypeId: String?): Double =
        when (workoutTypeId) {
            WorkoutTypeManager.WORKOUT_TYPE_ID_RUNNING -> 8.3
            WorkoutTypeManager.WORKOUT_TYPE_ID_WALKING -> 3.5
            WorkoutTypeManager.WORKOUT_TYPE_ID_HIKING -> 6.0
            WorkoutTypeManager.WORKOUT_TYPE_ID_CYCLING -> 6.8
            WorkoutTypeManager.WORKOUT_TYPE_ID_INLINE_SKATING -> 7.0
            WorkoutTypeManager.WORKOUT_TYPE_ID_SKATEBOARDING -> 5.0
            WorkoutTypeManager.WORKOUT_TYPE_ID_ROWING -> 7.0
            WorkoutTypeManager.WORKOUT_TYPE_ID_SWIMMING -> 8.0
            WorkoutTypeManager.WORKOUT_TYPE_ID_TREADMILL -> 6.0
            WorkoutTypeManager.WORKOUT_TYPE_ID_ROPE_SKIPPING -> 11.0
            WorkoutTypeManager.WORKOUT_TYPE_ID_TRAMPOLINE_JUMPING -> 4.0
            WorkoutTypeManager.WORKOUT_TYPE_ID_PUSH_UPS,
            WorkoutTypeManager.WORKOUT_TYPE_ID_PULL_UPS,
            WorkoutTypeManager.WORKOUT_TYPE_ID_PROGRAM_TRAINING -> 6.0
            else -> when (kind) {
                MetricKind.WEIGHT_REPS -> 6.0
                MetricKind.REPS -> 5.0
                MetricKind.DURATION -> 4.0
                MetricKind.WEIGHT_DURATION -> 5.5
                MetricKind.DISTANCE_DURATION -> 6.0
            }
        }

    private fun fallbackSecondsPerRep(kind: MetricKind, workoutTypeId: String?): Double =
        when (workoutTypeId) {
            WorkoutTypeManager.WORKOUT_TYPE_ID_ROPE_SKIPPING -> 0.5
            WorkoutTypeManager.WORKOUT_TYPE_ID_TRAMPOLINE_JUMPING -> 0.8
            WorkoutTypeManager.WORKOUT_TYPE_ID_PUSH_UPS -> 2.0
            WorkoutTypeManager.WORKOUT_TYPE_ID_PULL_UPS -> 3.0
            else -> if (kind == MetricKind.WEIGHT_REPS) 3.0 else 2.0
        }
}

/**
 * Pure projection from program completion history to the legacy [Workout] model
 * consumed by the dashboard and statistics screens.
 *
 * A completion without explicit logs means "performed as planned". Rep-only work
 * receives an estimated active duration, allowing MET and user weight to produce a
 * useful energy estimate. Independently linked source workouts remain authoritative
 * for energy and contribute measured values once per distinct id.
 */
internal object ProgramOccurrenceWorkoutMapper {

    fun summaryUuid(dayId: Long, occurrenceDate: Long): String =
        "$PROGRAM_OCCURRENCE_WORKOUT_UUID_PREFIX$dayId:$occurrenceDate"

    fun isGeneratedSummary(workout: Workout): Boolean =
        workout.uuid.startsWith(PROGRAM_OCCURRENCE_WORKOUT_UUID_PREFIX) ||
            workout.uuid.startsWith(LEGACY_GUIDED_WORKOUT_UUID_PREFIX)

    fun toWorkout(
        dayId: Long,
        occurrenceDate: Long,
        title: String,
        exerciseResults: List<ProgramOccurrenceExerciseResult>,
        linkedWorkouts: List<Workout>,
        workoutId: Long,
        userWeightKg: Double,
        programUuid: String? = null,
        programName: String? = null,
        iconOverride: String? = null,
    ): Workout? {
        val completed = exerciseResults.filter {
            it.completion.status == PROGRAM_COMPLETION_STATUS_COMPLETED
        }
        if (completed.isEmpty()) return null

        val linkedById = linkedWorkouts
            .asSequence()
            .filterNot(::isGeneratedSummary)
            .distinctBy(Workout::id)
            .associateBy(Workout::id)
        val totals = Totals()
        completed.forEach { result ->
            val realSource = result.completion.workoutId?.let(linkedById::get)
            val metrics = when {
                result.actualLogs.isNotEmpty() -> metricsFromLogs(result)
                realSource == null -> metricsFromPlan(result)
                else -> null // The real source contributes all metrics below.
            }
            if (metrics != null) {
                totals.add(metrics)
                if (realSource == null) {
                    totals.addEnergy(
                        ProgramExerciseEnergyEstimator.energyJoules(
                            durationMillis = metrics.duration,
                            met = ProgramExerciseEnergyEstimator.met(result.exercise),
                            userWeightKg = result.completion.userWeightKgSnapshot
                                ?.takeIf { it.isFinite() && it > 0.0 }
                                ?: userWeightKg,
                        )
                    )
                }
            }
        }

        val allRealLinkedIds = completed
            .asSequence()
            .mapNotNull { it.completion.workoutId }
            .filter(linkedById::containsKey)
            .toSet()
        val linkedIdsWithoutLogs = completed
            .asSequence()
            .filter { it.actualLogs.isEmpty() }
            .mapNotNull { it.completion.workoutId }
            .filter(linkedById::containsKey)
            .toSet()

        allRealLinkedIds.forEach { linkedId ->
            val workout = linkedById.getValue(linkedId)
            if (linkedId in linkedIdsWithoutLogs) {
                totals.add(workout)
            } else {
                // Explicit logs own reps/time/distance, but measured energy remains
                // more accurate than estimating those logs from MET.
                totals.addEnergy(workout.energy.nonNegativeFinite())
            }
        }

        val activeDuration = totals.duration
        val commonProfileUuid = completed
            .map { result ->
                result.completion.profileUuidSnapshot?.takeIf(String::isNotBlank)
                    ?: result.completion.workoutId
                        ?.let(linkedById::get)
                        ?.profileUuid
                        ?.takeIf(String::isNotBlank)
            }
            .distinct()
            .singleOrNull()
        val commonWeightSnapshot = completed
            .map { result ->
                result.completion.userWeightKgSnapshot
                    ?.takeIf { it.isFinite() && it > 0.0 }
                    ?: result.completion.workoutId
                        ?.let(linkedById::get)
                        ?.userWeightKgSnapshot
                        ?.takeIf { it.isFinite() && it > 0.0 }
            }
            .distinct()
            .singleOrNull()
        return Workout(
            id = workoutId,
            uuid = summaryUuid(dayId, occurrenceDate),
            start = occurrenceDate,
            end = saturatedAdd(occurrenceDate, activeDuration),
            activeDuration = activeDuration,
            comment = title,
            distance = totals.distance,
            repetitions = totals.repetitions.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            energy = totals.energy,
            workoutTypeId = GUIDED_WORKOUT_TYPE_ID,
            intervalSetUsedId = -1L,
            programUuid = programUuid,
            programName = programName,
            iconOverride = iconOverride,
            profileUuid = commonProfileUuid,
            userWeightKgSnapshot = commonWeightSnapshot,
        )
    }

    private fun metricsFromLogs(result: ProgramOccurrenceExerciseResult): Metrics {
        val secondsPerRep = ProgramExerciseEnergyEstimator.secondsPerRep(result.exercise)
        val metrics = Metrics()
        result.actualLogs.forEach { log ->
            metrics.add(
                repetitions = log.reps.nonNegative(),
                explicitDuration = log.durationMillis.nonNegative(),
                distance = log.distanceMeters.nonNegativeFinite(),
                secondsPerRep = secondsPerRep,
            )
        }
        return metrics
    }

    private fun metricsFromPlan(result: ProgramOccurrenceExerciseResult): Metrics {
        val secondsPerRep = ProgramExerciseEnergyEstimator.secondsPerRep(result.exercise)
        val metrics = Metrics()
        result.plannedSets.forEach { set ->
            metrics.add(
                repetitions = (set.repsMax ?: set.reps).nonNegative(),
                explicitDuration = set.durationMillis.nonNegative(),
                distance = set.distanceMeters.nonNegativeFinite(),
                secondsPerRep = secondsPerRep,
            )
        }
        return metrics
    }

    private class Metrics {
        var duration: Long = 0
            private set
        var repetitions: Long = 0
            private set
        var distance: Double = 0.0
            private set

        fun add(
            repetitions: Int,
            explicitDuration: Long,
            distance: Double,
            secondsPerRep: Double,
        ) {
            this.repetitions = saturatedAdd(this.repetitions, repetitions.toLong())
            val duration = explicitDuration.takeIf { it > 0L }
                ?: estimatedRepDuration(repetitions, secondsPerRep)
            this.duration = saturatedAdd(this.duration, duration)
            this.distance = finiteAdd(this.distance, distance)
        }
    }

    private class Totals {
        var duration: Long = 0
            private set
        var repetitions: Long = 0
            private set
        var distance: Double = 0.0
            private set
        var energy: Double = 0.0
            private set

        fun add(metrics: Metrics) {
            duration = saturatedAdd(duration, metrics.duration)
            repetitions = saturatedAdd(repetitions, metrics.repetitions)
            distance = finiteAdd(distance, metrics.distance)
        }

        fun add(workout: Workout) {
            duration = saturatedAdd(duration, workout.activeDuration.coerceAtLeast(0L))
            repetitions = saturatedAdd(repetitions, workout.repetitions.coerceAtLeast(0).toLong())
            distance = finiteAdd(distance, workout.distance.nonNegativeFinite())
            energy = finiteAdd(energy, workout.energy.nonNegativeFinite())
        }

        fun addEnergy(joules: Double) {
            energy = finiteAdd(energy, joules.nonNegativeFinite())
        }
    }

    private fun estimatedRepDuration(repetitions: Int, secondsPerRep: Double): Long {
        if (repetitions <= 0) return 0L
        val millis = repetitions.toDouble() * secondsPerRep * 1_000.0
        return when {
            !millis.isFinite() || millis >= Long.MAX_VALUE.toDouble() -> Long.MAX_VALUE
            millis <= 0.0 -> 0L
            else -> millis.roundToLong()
        }
    }

    private fun Int?.nonNegative(): Int = this?.coerceAtLeast(0) ?: 0

    private fun Long?.nonNegative(): Long = this?.coerceAtLeast(0L) ?: 0L

    private fun Double?.nonNegativeFinite(): Double =
        this?.takeIf(Double::isFinite)?.coerceAtLeast(0.0) ?: 0.0

    private fun finiteAdd(left: Double, right: Double): Double {
        val sum = left + right
        return if (sum.isFinite()) sum else Double.MAX_VALUE
    }

    private fun saturatedAdd(left: Long, right: Long): Long = when {
        right <= 0L -> left
        Long.MAX_VALUE - left < right -> Long.MAX_VALUE
        else -> left + right
    }
}
