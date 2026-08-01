/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.ui.statistics

import de.tadris.fitness.data.Workout
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsUiModelTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 8, 1)

    @Test
    fun `program uuid takes precedence while ordinary workouts group by type`() {
        val program = workout(today, programUuid = " plan ", type = "running")
        val ordinary = workout(today, type = "running")

        assertEquals(StatisticsGroupKey.Program("plan"), program.statisticsGroupKey())
        assertEquals(
            StatisticsGroupKey.WorkoutType("running"),
            ordinary.statisticsGroupKey(),
        )
    }

    @Test
    fun `seven day range and group filter produce totals summaries and zero days`() {
        val selected = StatisticsGroupKey.Program("plan-a")
        val model = buildStatisticsUiModel(
            workouts = listOf(
                workout(
                    today.minusDays(6),
                    programUuid = "plan-a",
                    duration = 10_000,
                    energy = 500.0,
                    distance = 100.0,
                    repetitions = 8,
                ),
                workout(
                    today,
                    programUuid = "plan-a",
                    duration = 20_000,
                    energy = 700.0,
                    distance = 250.0,
                    repetitions = 12,
                ),
                workout(today.minusDays(1), type = "cycling", duration = 99_000),
                workout(today.minusDays(7), programUuid = "plan-a", duration = 99_000),
                workout(today.plusDays(1), programUuid = "plan-a", duration = 99_000),
            ),
            filter = StatisticsFilter(StatisticsRange.LAST_7_DAYS, setOf(selected)),
            today = today,
            zoneId = zone,
        )

        assertEquals(today.minusDays(6), model.rangeStart)
        assertEquals(7, model.points.size)
        assertEquals(30_000L, model.total.durationMillis)
        assertEquals(1_200.0, model.total.energyJoules, 0.0)
        assertEquals(350.0, model.total.distanceMeters, 0.0)
        assertEquals(20L, model.total.repetitions)
        assertEquals(2, model.total.sessions)
        assertEquals(model.total, model.groupSummaries.single().values)
        assertEquals(5, model.points.count { it.values.sessions == 0 })
        assertTrue(model.points.all { it.valuesByGroup.keys == setOf(selected) })
    }

    @Test
    fun `ninety days use contiguous Monday buckets`() {
        val model = buildStatisticsUiModel(
            workouts = listOf(
                workout(today.minusDays(89), type = "running"),
                workout(today, type = "running"),
            ),
            filter = StatisticsFilter(StatisticsRange.LAST_90_DAYS),
            today = today,
            zoneId = zone,
        )

        assertEquals(StatisticsBucketUnit.WEEK, model.bucketUnit)
        assertEquals(13, model.points.size)
        assertTrue(model.points.all { it.startDate.dayOfWeek.value == 1 })
        model.points.zipWithNext().forEach { (left, right) ->
            assertEquals(left.startDate.plusWeeks(1), right.startDate)
        }
        assertEquals(2, model.total.sessions)
        assertEquals(11, model.points.count { it.values.sessions == 0 })
    }

    @Test
    fun `weekly buckets respect the configured first day of week`() {
        val model = buildStatisticsUiModel(
            workouts = listOf(workout(today, type = "running")),
            filter = StatisticsFilter(StatisticsRange.LAST_90_DAYS),
            today = today,
            zoneId = zone,
            weekStartsOn = DayOfWeek.SUNDAY,
        )

        assertTrue(model.points.all { it.startDate.dayOfWeek == DayOfWeek.SUNDAY })
    }

    @Test
    fun `running workout is excluded until it has been completed`() {
        val running = workout(today, type = "running").copy(end = -1L)
        val completed = workout(today.minusDays(1), type = "walking")

        val model = buildStatisticsUiModel(
            workouts = listOf(running, completed),
            filter = StatisticsFilter(StatisticsRange.LAST_7_DAYS),
            today = today,
            zoneId = zone,
        )

        assertEquals(1, model.total.sessions)
        assertEquals(
            setOf(StatisticsGroupKey.WorkoutType("walking")),
            model.availableGroups.mapTo(linkedSetOf()) { it.key },
        )
    }

    @Test
    fun `year and all ranges use contiguous month buckets and enforce boundaries`() {
        val workouts = listOf(
            workout(today.minusDays(364), type = "running"),
            workout(today.minusDays(365), type = "running"),
            workout(LocalDate.of(2024, 1, 15), type = "walking"),
        )
        val year = buildStatisticsUiModel(
            workouts = workouts,
            filter = StatisticsFilter(StatisticsRange.LAST_365_DAYS),
            today = today,
            zoneId = zone,
        )
        val all = buildStatisticsUiModel(
            workouts = workouts,
            filter = StatisticsFilter(StatisticsRange.ALL),
            today = today,
            zoneId = zone,
        )

        assertEquals(1, year.total.sessions)
        assertEquals(13, year.points.size)
        assertEquals(LocalDate.of(2025, 8, 1), year.points.first().startDate)
        assertEquals(LocalDate.of(2024, 1, 15), all.rangeStart)
        assertEquals(LocalDate.of(2024, 1, 1), all.points.first().startDate)
        assertEquals(LocalDate.of(2026, 8, 1), all.points.last().startDate)
        all.points.zipWithNext().forEach { (left, right) ->
            assertEquals(left.startDate.plusMonths(1), right.startDate)
        }
        assertEquals(3, all.total.sessions)
    }

    @Test
    fun `available groups use latest program snapshots and metric accessors`() {
        val model = buildStatisticsUiModel(
            workouts = listOf(
                workout(
                    today.minusDays(2),
                    programUuid = "plan",
                    programName = "Old name",
                    icon = "old_icon",
                    duration = 0,
                ),
                workout(
                    today.minusDays(1),
                    programUuid = "plan",
                    programName = "New name",
                    icon = "new_icon",
                    duration = 123,
                    energy = 456.0,
                    distance = 789.0,
                    repetitions = 10,
                ),
            ),
            filter = StatisticsFilter(StatisticsRange.LAST_30_DAYS),
            today = today,
            zoneId = zone,
        )

        val group = model.availableGroups.single()
        assertEquals("New name", group.programName)
        assertEquals("new_icon", group.icon)
        assertEquals(123.0, model.total.value(StatisticsMetric.DURATION), 0.0)
        assertEquals(456.0, model.total.value(StatisticsMetric.ENERGY), 0.0)
        assertEquals(789.0, model.total.value(StatisticsMetric.DISTANCE), 0.0)
        assertEquals(10.0, model.total.value(StatisticsMetric.REPETITIONS), 0.0)
        assertEquals(2.0, model.total.value(StatisticsMetric.SESSIONS), 0.0)
    }

    private fun workout(
        date: LocalDate,
        type: String = "running",
        programUuid: String? = null,
        programName: String? = null,
        icon: String? = null,
        duration: Long = 1_000,
        energy: Double = 0.0,
        distance: Double = 0.0,
        repetitions: Int = 0,
    ): Workout {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        return Workout(
            id = start,
            uuid = "workout-$start-$type-${programUuid.orEmpty()}",
            start = start,
            end = start + duration,
            activeDuration = duration,
            distance = distance,
            repetitions = repetitions,
            energy = energy,
            workoutTypeId = type,
            intervalSetUsedId = 0,
            programUuid = programUuid,
            programName = programName,
            iconOverride = icon,
        )
    }
}
