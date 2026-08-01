/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.ui.statistics

import de.tadris.fitness.data.Workout
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** A program keeps its own identity; ordinary sessions are grouped by workout type. */
sealed interface StatisticsGroupKey {
    val stableId: String

    data class Program(val programUuid: String) : StatisticsGroupKey {
        override val stableId: String = "program:$programUuid"
    }

    data class WorkoutType(val workoutTypeId: String) : StatisticsGroupKey {
        override val stableId: String = "workout-type:$workoutTypeId"
    }
}

enum class StatisticsRange(val dayCount: Long?, val bucketUnit: StatisticsBucketUnit) {
    LAST_7_DAYS(7, StatisticsBucketUnit.DAY),
    LAST_30_DAYS(30, StatisticsBucketUnit.DAY),
    LAST_90_DAYS(90, StatisticsBucketUnit.WEEK),
    LAST_365_DAYS(365, StatisticsBucketUnit.MONTH),
    ALL(null, StatisticsBucketUnit.MONTH),
}

enum class StatisticsBucketUnit {
    DAY,
    WEEK,
    MONTH,
}

enum class StatisticsMetric {
    DURATION,
    PAUSE_DURATION,
    ENERGY,
    DISTANCE,
    AVERAGE_SPEED,
    AVERAGE_PACE,
    REPETITIONS,
    AVERAGE_FREQUENCY,
    SESSIONS,
}

/** Canonical units: active/pause milliseconds, joules, metres, repetitions and session count. */
data class StatisticsValues(
    val durationMillis: Long = 0,
    val pauseDurationMillis: Long = 0,
    val energyJoules: Double = 0.0,
    val distanceMeters: Double = 0.0,
    val repetitions: Long = 0,
    val sessions: Int = 0,
) {
    fun value(metric: StatisticsMetric): Double = when (metric) {
        StatisticsMetric.DURATION -> durationMillis.toDouble()
        StatisticsMetric.PAUSE_DURATION -> pauseDurationMillis.toDouble()
        StatisticsMetric.ENERGY -> energyJoules
        StatisticsMetric.DISTANCE -> distanceMeters
        StatisticsMetric.AVERAGE_SPEED -> if (durationMillis > 0) {
            distanceMeters / (durationMillis / 1_000.0)
        } else {
            0.0
        }
        StatisticsMetric.AVERAGE_PACE -> if (distanceMeters > 0.0) {
            (durationMillis / 60_000.0) / (distanceMeters / 1_000.0)
        } else {
            0.0
        }
        StatisticsMetric.REPETITIONS -> repetitions.toDouble()
        StatisticsMetric.AVERAGE_FREQUENCY -> if (durationMillis > 0) {
            repetitions / (durationMillis / 1_000.0)
        } else {
            0.0
        }
        StatisticsMetric.SESSIONS -> sessions.toDouble()
    }

    operator fun plus(other: StatisticsValues): StatisticsValues = StatisticsValues(
        durationMillis = durationMillis + other.durationMillis,
        pauseDurationMillis = pauseDurationMillis + other.pauseDurationMillis,
        energyJoules = energyJoules + other.energyJoules,
        distanceMeters = distanceMeters + other.distanceMeters,
        repetitions = repetitions + other.repetitions,
        sessions = sessions + other.sessions,
    )
}

data class StatisticsGroup(
    val key: StatisticsGroupKey,
    /** The latest snapshot is used so program renames do not split one group. */
    val programName: String? = null,
    val icon: String? = null,
    val latestWorkoutStart: Long? = null,
)

data class StatisticsGroupSummary(
    val group: StatisticsGroup,
    val values: StatisticsValues,
)

data class StatisticsTimePoint(
    /** Calendar bucket start: date, configured week start, or first day of month. */
    val startDate: LocalDate,
    val endDate: LocalDate,
    val values: StatisticsValues,
    /** Contains every included group, including zero-valued entries. */
    val valuesByGroup: Map<StatisticsGroupKey, StatisticsValues>,
)

data class StatisticsFilter(
    val range: StatisticsRange = StatisticsRange.LAST_30_DAYS,
    /** Empty means all groups. */
    val groups: Set<StatisticsGroupKey> = emptySet(),
)

data class StatisticsUiModel(
    val filter: StatisticsFilter,
    val rangeStart: LocalDate,
    val rangeEnd: LocalDate,
    val bucketUnit: StatisticsBucketUnit,
    /** All groups in history up to [rangeEnd], regardless of the active filter range. */
    val availableGroups: List<StatisticsGroup>,
    val total: StatisticsValues,
    val groupSummaries: List<StatisticsGroupSummary>,
    /** Never skips a calendar bucket, even when its values are all zero. */
    val points: List<StatisticsTimePoint>,
)

/**
 * Aggregates completed workout rows into a presentation-ready, deterministic model.
 * [today] and [zoneId] are explicit so calendar boundaries remain testable.
 */
fun buildStatisticsUiModel(
    workouts: List<Workout>,
    today: LocalDate,
    zoneId: ZoneId,
    filter: StatisticsFilter = StatisticsFilter(),
    weekStartsOn: DayOfWeek = DayOfWeek.MONDAY,
): StatisticsUiModel {
    val dated = workouts.asSequence()
        .filter { it.end != -1L }
        .map { workout ->
            DatedWorkout(
                workout = workout,
                date = Instant.ofEpochMilli(workout.start).atZone(zoneId).toLocalDate(),
                groupKey = workout.statisticsGroupKey(),
            )
        }
        .toList()
    val history = dated.filter { it.date <= today }
    val availableGroupsByKey = history
        .groupBy(DatedWorkout::groupKey)
        .mapValues { (key, entries) -> descriptorFor(key, entries) }
    val availableGroups = availableGroupsByKey.values.sortedWith(groupOrdering)

    val rangeStart = filter.range.dayCount
        ?.let { today.minusDays(it - 1) }
        ?: history.minOfOrNull(DatedWorkout::date)
        ?: today
    val includedKeys = if (filter.groups.isEmpty()) {
        availableGroups.mapTo(linkedSetOf()) { it.key }
    } else {
        filter.groups.sortedBy(StatisticsGroupKey::stableId).toCollection(linkedSetOf())
    }
    val inRange = history.filter { entry ->
        entry.date >= rangeStart && entry.groupKey in includedKeys
    }

    val valuesByGroup = includedKeys.associateWith { key ->
        inRange.asSequence()
            .filter { it.groupKey == key }
            .fold(StatisticsValues()) { total, entry -> total + entry.workout.statisticsValues() }
    }
    val groupSummaries = includedKeys.map { key ->
        StatisticsGroupSummary(
            group = availableGroupsByKey[key] ?: StatisticsGroup(key),
            values = valuesByGroup.getValue(key),
        )
    }.sortedWith { left, right -> groupOrdering.compare(left.group, right.group) }
    val total = valuesByGroup.values.fold(StatisticsValues(), StatisticsValues::plus)

    val firstBucket = bucketStart(rangeStart, filter.range.bucketUnit, weekStartsOn)
    val bucketStarts = generateSequence(firstBucket) { current ->
        nextBucket(current, filter.range.bucketUnit)
    }.takeWhile { it <= today }.toList()
    val entriesByBucket = inRange.groupBy {
        bucketStart(it.date, filter.range.bucketUnit, weekStartsOn)
    }
    val points = bucketStarts.map { start ->
        val entries = entriesByBucket[start].orEmpty()
        val bucketGroups = includedKeys.associateWith { key ->
            entries.asSequence()
                .filter { it.groupKey == key }
                .fold(StatisticsValues()) { subtotal, entry ->
                    subtotal + entry.workout.statisticsValues()
                }
        }
        StatisticsTimePoint(
            startDate = start,
            endDate = bucketEnd(start, filter.range.bucketUnit).coerceAtMost(today),
            values = bucketGroups.values.fold(StatisticsValues(), StatisticsValues::plus),
            valuesByGroup = bucketGroups,
        )
    }

    return StatisticsUiModel(
        filter = filter,
        rangeStart = rangeStart,
        rangeEnd = today,
        bucketUnit = filter.range.bucketUnit,
        availableGroups = availableGroups,
        total = total,
        groupSummaries = groupSummaries,
        points = points,
    )
}

fun Workout.statisticsGroupKey(): StatisticsGroupKey = programUuid
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.let(StatisticsGroupKey::Program)
    ?: StatisticsGroupKey.WorkoutType(workoutTypeId)

private data class DatedWorkout(
    val workout: Workout,
    val date: LocalDate,
    val groupKey: StatisticsGroupKey,
)

private fun Workout.statisticsValues(): StatisticsValues = StatisticsValues(
    durationMillis = activeDuration.coerceAtLeast(0),
    pauseDurationMillis = pauseDuration.coerceAtLeast(0),
    energyJoules = energy.takeIf { it.isFinite() && it > 0 } ?: 0.0,
    distanceMeters = distance.takeIf { it.isFinite() && it > 0 } ?: 0.0,
    repetitions = repetitions.coerceAtLeast(0).toLong(),
    sessions = 1,
)

private fun descriptorFor(
    key: StatisticsGroupKey,
    entries: List<DatedWorkout>,
): StatisticsGroup {
    val latest = entries.maxWithOrNull(compareBy({ it.workout.start }, { it.workout.id }))
    return StatisticsGroup(
        key = key,
        programName = latest?.workout?.programName?.takeIf { it.isNotBlank() },
        icon = latest?.workout?.iconOverride?.takeIf { it.isNotBlank() },
        latestWorkoutStart = latest?.workout?.start,
    )
}

private val groupOrdering = compareByDescending<StatisticsGroup> {
    it.latestWorkoutStart ?: Long.MIN_VALUE
}
    .thenBy { it.key.stableId }

private fun bucketStart(
    date: LocalDate,
    unit: StatisticsBucketUnit,
    weekStartsOn: DayOfWeek,
): LocalDate = when (unit) {
    StatisticsBucketUnit.DAY -> date
    StatisticsBucketUnit.WEEK -> date.with(TemporalAdjusters.previousOrSame(weekStartsOn))
    StatisticsBucketUnit.MONTH -> date.withDayOfMonth(1)
}

private fun nextBucket(date: LocalDate, unit: StatisticsBucketUnit): LocalDate = when (unit) {
    StatisticsBucketUnit.DAY -> date.plusDays(1)
    StatisticsBucketUnit.WEEK -> date.plusWeeks(1)
    StatisticsBucketUnit.MONTH -> date.plusMonths(1)
}

private fun bucketEnd(date: LocalDate, unit: StatisticsBucketUnit): LocalDate = when (unit) {
    StatisticsBucketUnit.DAY -> date
    StatisticsBucketUnit.WEEK -> date.plusDays(6)
    StatisticsBucketUnit.MONTH -> YearMonth.from(date).atEndOfMonth()
}
