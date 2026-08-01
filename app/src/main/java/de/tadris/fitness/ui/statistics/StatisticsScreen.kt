/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.Workout
import de.tadris.fitness.util.Icon as WorkoutIcon
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private data class StatisticsDisplayGroup(
    val key: StatisticsGroupKey,
    val title: String,
    val iconName: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onWorkoutClick: (Workout) -> Unit = {},
) {
    val context = LocalContext.current
    val instance = remember(context) { Instance.getInstance(context) }
    val workouts by instance.db.workoutDao()
        .getVisibleWorkoutsFlow()
        .collectAsState(initial = emptyList())
    val zoneId = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zoneId) }
    val weekStartsOn = remember(instance) {
        DayOfWeek.of(((instance.userPreferences.firstDayOfWeek + 5) % 7) + 1)
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedRange by rememberSaveable { mutableStateOf(StatisticsRange.LAST_30_DAYS) }
    var selectedGroupIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var showGroupFilter by rememberSaveable { mutableStateOf(false) }

    val keysById = remember(workouts) {
        workouts.map(Workout::statisticsGroupKey).associateBy(StatisticsGroupKey::stableId)
    }
    val selectedGroups = remember(selectedGroupIds, keysById) {
        selectedGroupIds.mapNotNullTo(linkedSetOf()) { keysById[it] }
    }
    val uiModel = remember(
        workouts,
        selectedRange,
        selectedGroups,
        today,
        zoneId,
        weekStartsOn,
    ) {
        buildStatisticsUiModel(
            workouts = workouts,
            filter = StatisticsFilter(selectedRange, selectedGroups),
            today = today,
            zoneId = zoneId,
            weekStartsOn = weekStartsOn,
        )
    }
    val displayGroups = remember(workouts, uiModel.availableGroups, context) {
        resolveDisplayGroups(workouts, uiModel.availableGroups, context)
    }
    val availableIds = remember(displayGroups) {
        displayGroups.mapTo(linkedSetOf()) { it.key.stableId }
    }
    LaunchedEffect(availableIds) {
        if (selectedGroupIds.isNotEmpty()) {
            selectedGroupIds = selectedGroupIds.intersect(availableIds)
        }
    }

    if (showGroupFilter) {
        StatisticsGroupFilterDialog(
            groups = displayGroups,
            selectedIds = selectedGroupIds,
            onDismiss = { showGroupFilter = false },
            onApply = {
                selectedGroupIds = it
                showGroupFilter = false
            },
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.statistics),
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                ) {
                    listOf(R.string.stats_overview_title, R.string.stats_history_title)
                        .forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(stringResource(title)) },
                            )
                        }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        when (selectedTab) {
            0 -> StatisticsOverview(
                model = uiModel,
                displayGroups = displayGroups,
                selectedRange = selectedRange,
                onRangeSelected = { selectedRange = it },
                modifier = Modifier.padding(contentPadding),
            )

            else -> StatisticsHistory(
                model = uiModel,
                displayGroups = displayGroups,
                workouts = workouts,
                selectedRange = selectedRange,
                selectedGroupIds = selectedGroupIds,
                zoneId = zoneId,
                onRangeSelected = { selectedRange = it },
                onShowGroupFilter = { showGroupFilter = true },
                onWorkoutClick = onWorkoutClick,
                modifier = Modifier.padding(contentPadding),
            )
        }
    }
}

@Composable
private fun StatisticsOverview(
    model: StatisticsUiModel,
    displayGroups: List<StatisticsDisplayGroup>,
    selectedRange: StatisticsRange,
    onRangeSelected: (StatisticsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            StatisticsRangeChips(
                selectedRange = selectedRange,
                onRangeSelected = onRangeSelected,
            )
        }
        if (model.total.sessions == 0) {
            item { StatisticsEmptyState() }
        } else {
            item { StatisticsSummaryCard(model.total) }
            item {
                Text(
                    text = stringResource(R.string.statistics_activity_breakdown),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                StatisticsBreakdownCard(
                    model = model,
                    displayGroups = displayGroups,
                )
            }
        }
    }
}

@Composable
private fun StatisticsHistory(
    model: StatisticsUiModel,
    displayGroups: List<StatisticsDisplayGroup>,
    workouts: List<Workout>,
    selectedRange: StatisticsRange,
    selectedGroupIds: Set<String>,
    zoneId: ZoneId,
    onRangeSelected: (StatisticsRange) -> Unit,
    onShowGroupFilter: () -> Unit,
    onWorkoutClick: (Workout) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedMetric by rememberSaveable { mutableStateOf(StatisticsMetric.DURATION) }
    val recentWorkouts = remember(workouts, model.rangeStart, model.rangeEnd, selectedGroupIds, zoneId) {
        workouts.asSequence()
            .filter { workout ->
                val date = Instant.ofEpochMilli(workout.start).atZone(zoneId).toLocalDate()
                date in model.rangeStart..model.rangeEnd &&
                    (selectedGroupIds.isEmpty() || workout.statisticsGroupKey().stableId in selectedGroupIds)
            }
            .sortedByDescending(Workout::start)
            .take(20)
            .toList()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            FilterChip(
                selected = selectedGroupIds.isNotEmpty(),
                onClick = onShowGroupFilter,
                label = {
                    Text(
                        if (selectedGroupIds.isEmpty()) {
                            stringResource(R.string.statistics_all_workouts)
                        } else {
                            stringResource(
                                R.string.statistics_selected_workout_types,
                                selectedGroupIds.size,
                            )
                        }
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_list),
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
        }
        item {
            StatisticsRangeChips(
                selectedRange = selectedRange,
                onRangeSelected = onRangeSelected,
            )
        }
        item {
            StatisticsTrendCard(
                model = model,
                selectedMetric = selectedMetric,
                onMetricSelected = { selectedMetric = it },
            )
        }
        if (recentWorkouts.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.statistics_recent_workouts),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(recentWorkouts, key = Workout::id) { workout ->
                StatisticsWorkoutCard(
                    workout = workout,
                    metric = selectedMetric,
                    displayGroup = displayGroups.firstOrNull {
                        it.key == workout.statisticsGroupKey()
                    },
                    onClick = { onWorkoutClick(workout) },
                )
            }
        }
    }
}

@Composable
private fun StatisticsRangeChips(
    selectedRange: StatisticsRange,
    onRangeSelected: (StatisticsRange) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(StatisticsRange.entries, key = StatisticsRange::name) { range ->
            FilterChip(
                selected = selectedRange == range,
                onClick = { onRangeSelected(range) },
                label = { Text(statisticsRangeLabel(range)) },
                leadingIcon = if (selectedRange == range) {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun StatisticsSummaryCard(values: StatisticsValues) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.statistics_period_summary),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.statistics_workout_count,
                        values.sessions,
                        values.sessions,
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatisticsSummaryValue(
                    label = stringResource(R.string.statistics_metric_duration),
                    value = formatDuration(values.durationMillis),
                    modifier = Modifier.weight(1f),
                )
                StatisticsSummaryValue(
                    label = stringResource(R.string.statistics_calories),
                    value = formatCalories(values.energyJoules),
                    modifier = Modifier.weight(1f),
                )
                StatisticsSummaryValue(
                    label = stringResource(R.string.statistics_distance),
                    value = formatDistance(values.distanceMeters),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatisticsSummaryValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.74f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatisticsBreakdownCard(
    model: StatisticsUiModel,
    displayGroups: List<StatisticsDisplayGroup>,
) {
    val summaries = model.groupSummaries
        .filter { it.values.sessions > 0 }
        .sortedByDescending { it.values.sessions }
    val maxSessions = summaries.maxOfOrNull { it.values.sessions }?.coerceAtLeast(1) ?: 1

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            summaries.forEachIndexed { index, summary ->
                val display = displayGroups.firstOrNull { it.key == summary.group.key }
                if (display != null) {
                    StatisticsBreakdownRow(
                        group = display,
                        values = summary.values,
                        progress = summary.values.sessions.toFloat() / maxSessions,
                        totalSessions = model.total.sessions,
                    )
                    if (index != summaries.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsBreakdownRow(
    group: StatisticsDisplayGroup,
    values: StatisticsValues,
    progress: Float,
    totalSessions: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatisticsGroupIcon(group, size = 44)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.statistics_workout_count,
                        values.sessions,
                        values.sessions,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatDuration(values.durationMillis),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            val share = if (totalSessions == 0) 0 else {
                (values.sessions * 100f / totalSessions).roundToInt()
            }
            Text(
                text = stringResource(R.string.statistics_of_total, share),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatisticsTrendCard(
    model: StatisticsUiModel,
    selectedMetric: StatisticsMetric,
    onMetricSelected: (StatisticsMetric) -> Unit,
) {
    val metricValue = formatStatisticsValue(model.total, selectedMetric)
    val chartDescription = "${stringResource(R.string.statistics_trend)}: $metricValue"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.statistics_trend),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.statistics_total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = metricValue,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StatisticsMetric.entries, key = StatisticsMetric::name) { metric ->
                    FilterChip(
                        selected = selectedMetric == metric,
                        onClick = { onMetricSelected(metric) },
                        label = { Text(statisticsMetricLabel(metric)) },
                    )
                }
            }
            StatisticsLineChart(
                model = model,
                metric = selectedMetric,
                contentDescription = chartDescription,
            )
        }
    }
}

@Composable
private fun StatisticsLineChart(
    model: StatisticsUiModel,
    metric: StatisticsMetric,
    contentDescription: String,
) {
    val values = model.points.map { it.values.value(metric) }
    val maximum = values.maxOrNull()?.takeIf { it > 0.0 }
    if (maximum == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.statistics_chart_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val horizontalPadding = 6.dp.toPx()
        val verticalPadding = 10.dp.toPx()
        val chartWidth = (size.width - horizontalPadding * 2).coerceAtLeast(1f)
        val chartHeight = (size.height - verticalPadding * 2).coerceAtLeast(1f)

        repeat(4) { index ->
            val y = verticalPadding + chartHeight * index / 3f
            drawLine(
                color = grid,
                start = Offset(horizontalPadding, y),
                end = Offset(size.width - horizontalPadding, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        val offsets = values.mapIndexed { index, value ->
            val x = if (values.size == 1) {
                size.width / 2f
            } else {
                horizontalPadding + chartWidth * index / (values.lastIndex.toFloat())
            }
            val normalized = (value / maximum).toFloat().coerceIn(0f, 1f)
            val y = verticalPadding + chartHeight * (1f - normalized)
            Offset(x, y)
        }

        if (offsets.size == 1) {
            drawCircle(color = primary, radius = 5.dp.toPx(), center = offsets.first())
        } else {
            val line = Path().apply {
                moveTo(offsets.first().x, offsets.first().y)
                offsets.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val fill = Path().apply {
                moveTo(offsets.first().x, size.height - verticalPadding)
                lineTo(offsets.first().x, offsets.first().y)
                offsets.drop(1).forEach { lineTo(it.x, it.y) }
                lineTo(offsets.last().x, size.height - verticalPadding)
                close()
            }
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    colors = listOf(primary.copy(alpha = 0.28f), Color.Transparent),
                ),
            )
            drawPath(
                path = line,
                color = primary,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            offsets.zip(values).forEach { (point, value) ->
                if (value <= 0.0) return@forEach
                drawCircle(
                    color = primary,
                    radius = 3.dp.toPx(),
                    center = point,
                )
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatChartDate(model.points.first().startDate, model.bucketUnit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (model.points.size > 1) {
            Text(
                text = formatChartDate(model.points.last().endDate, model.bucketUnit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatisticsWorkoutCard(
    workout: Workout,
    metric: StatisticsMetric,
    displayGroup: StatisticsDisplayGroup?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val instance = remember(context) { Instance.getInstance(context) }
    val fallbackType = remember(workout, context) { workout.getDisplayWorkoutType(context) }
    val group = displayGroup ?: StatisticsDisplayGroup(
        key = workout.statisticsGroupKey(),
        title = fallbackType.title,
        iconName = workout.getDisplayIconName(context),
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatisticsGroupIcon(group, size = 46)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = instance.userDateTimeUtils.formatDateTime(Date(workout.start)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatWorkoutMetric(workout, metric),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun StatisticsGroupIcon(group: StatisticsDisplayGroup, size: Int) {
    Surface(
        modifier = Modifier.size(size.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(WorkoutIcon.getIcon(group.iconName)),
                contentDescription = null,
                modifier = Modifier.size((size * 0.55f).dp),
            )
        }
    }
}

@Composable
private fun StatisticsEmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_activity),
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.statistics_no_data_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.statistics_no_data_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatisticsGroupFilterDialog(
    groups: List<StatisticsDisplayGroup>,
    selectedIds: Set<String>,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit,
) {
    val allIds = remember(groups) { groups.mapTo(linkedSetOf()) { it.key.stableId } }
    var pendingIds by remember(groups, selectedIds) { mutableStateOf(selectedIds) }

    fun toggle(id: String) {
        val explicitlySelected = if (pendingIds.isEmpty()) allIds else pendingIds
        val changed = if (id in explicitlySelected) explicitlySelected - id else explicitlySelected + id
        pendingIds = if (changed.size == allIds.size) emptySet() else changed
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.statistics_activity_filter)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                StatisticsFilterRow(
                    title = stringResource(R.string.statistics_all_workouts),
                    iconName = "list",
                    checked = pendingIds.isEmpty(),
                    onClick = { pendingIds = emptySet() },
                )
                groups.forEach { group ->
                    StatisticsFilterRow(
                        title = group.title,
                        iconName = group.iconName,
                        checked = pendingIds.isEmpty() || group.key.stableId in pendingIds,
                        onClick = { toggle(group.key.stableId) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(pendingIds) }) {
                Text(stringResource(R.string.statistics_apply_filter))
            }
        },
        dismissButton = {
            TextButton(onClick = { pendingIds = emptySet() }) {
                Text(stringResource(R.string.statistics_clear_filter))
            }
        },
    )
}

@Composable
private fun StatisticsFilterRow(
    title: String,
    iconName: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(WorkoutIcon.getIcon(iconName)),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Checkbox(checked = checked, onCheckedChange = { onClick() })
    }
}

private fun resolveDisplayGroups(
    workouts: List<Workout>,
    groups: List<StatisticsGroup>,
    context: android.content.Context,
): List<StatisticsDisplayGroup> {
    val latestByKey = workouts
        .sortedByDescending(Workout::start)
        .distinctBy(Workout::statisticsGroupKey)
        .associateBy(Workout::statisticsGroupKey)
    return groups.map { group ->
        val workout = latestByKey[group.key]
        val fallback = workout?.getDisplayWorkoutType(context)
        StatisticsDisplayGroup(
            key = group.key,
            title = group.programName
                ?.takeIf(String::isNotBlank)
                ?: fallback?.title
                ?: context.getString(R.string.workouts),
            iconName = group.icon
                ?.takeIf(String::isNotBlank)
                ?: workout?.getDisplayIconName(context)
                ?: WorkoutIcon.ACTIVITY.name,
        )
    }
}

@Composable
private fun statisticsRangeLabel(range: StatisticsRange): String = stringResource(
    when (range) {
        StatisticsRange.LAST_7_DAYS -> R.string.statistics_period_7_days
        StatisticsRange.LAST_30_DAYS -> R.string.statistics_period_30_days
        StatisticsRange.LAST_90_DAYS -> R.string.statistics_period_3_months
        StatisticsRange.LAST_365_DAYS -> R.string.statistics_period_year
        StatisticsRange.ALL -> R.string.statistics_period_all_time
    }
)

@Composable
private fun statisticsMetricLabel(metric: StatisticsMetric): String = stringResource(
    when (metric) {
        StatisticsMetric.DURATION -> R.string.statistics_metric_duration
        StatisticsMetric.PAUSE_DURATION -> R.string.statistics_metric_pause
        StatisticsMetric.ENERGY -> R.string.statistics_metric_energy
        StatisticsMetric.DISTANCE -> R.string.statistics_metric_distance
        StatisticsMetric.AVERAGE_SPEED -> R.string.statistics_metric_speed
        StatisticsMetric.AVERAGE_PACE -> R.string.statistics_metric_pace
        StatisticsMetric.REPETITIONS -> R.string.statistics_metric_repetitions
        StatisticsMetric.AVERAGE_FREQUENCY -> R.string.statistics_metric_frequency
        StatisticsMetric.SESSIONS -> R.string.statistics_metric_sessions
    }
)

@Composable
private fun formatStatisticsValue(values: StatisticsValues, metric: StatisticsMetric): String = when (metric) {
    StatisticsMetric.DURATION -> formatDuration(values.durationMillis)
    StatisticsMetric.PAUSE_DURATION -> formatDuration(values.pauseDurationMillis)
    StatisticsMetric.ENERGY -> formatCalories(values.energyJoules)
    StatisticsMetric.DISTANCE -> formatDistance(values.distanceMeters)
    StatisticsMetric.AVERAGE_SPEED -> formatSpeed(values.value(metric))
    StatisticsMetric.AVERAGE_PACE -> formatPace(values.value(metric))
    StatisticsMetric.REPETITIONS -> stringResource(
        R.string.statistics_repetitions_short,
        values.repetitions,
    )
    StatisticsMetric.AVERAGE_FREQUENCY -> formatFrequency(values.value(metric))
    StatisticsMetric.SESSIONS -> pluralStringResource(
        R.plurals.statistics_workout_count,
        values.sessions,
        values.sessions,
    )
}

@Composable
private fun formatWorkoutMetric(workout: Workout, metric: StatisticsMetric): String = when (metric) {
    StatisticsMetric.DURATION -> formatDuration(workout.activeDuration)
    StatisticsMetric.PAUSE_DURATION -> formatDuration(workout.pauseDuration)
    StatisticsMetric.ENERGY -> formatCalories(workout.energy)
    StatisticsMetric.DISTANCE -> formatDistance(workout.distance)
    StatisticsMetric.AVERAGE_SPEED -> formatSpeed(workout.averageSpeedActive)
    StatisticsMetric.AVERAGE_PACE -> formatPace(workout.averagePaceActive)
    StatisticsMetric.REPETITIONS -> stringResource(
        R.string.statistics_repetitions_short,
        workout.repetitions,
    )
    StatisticsMetric.AVERAGE_FREQUENCY -> formatFrequency(workout.averageFrequency)
    StatisticsMetric.SESSIONS -> "1"
}

@Composable
private fun formatDuration(durationMillis: Long): String {
    val context = LocalContext.current
    return remember(durationMillis, context) {
        Instance.getInstance(context).distanceUnitUtils
            .getHourMinuteTime(durationMillis.coerceAtLeast(0), true)
    }
}

@Composable
private fun formatCalories(energyJoules: Double): String {
    val calories = (energyJoules.coerceAtLeast(0.0) / 4186.8).roundToInt()
    return "$calories ${stringResource(R.string.kcal_unit)}"
}

@Composable
private fun formatDistance(distanceMeters: Double): String {
    val context = LocalContext.current
    return remember(distanceMeters, context) {
        Instance.getInstance(context).distanceUnitUtils
            .getDistance(distanceMeters.coerceAtLeast(0.0).roundToInt())
    }
}

@Composable
private fun formatSpeed(speedMetersPerSecond: Double): String {
    val context = LocalContext.current
    return remember(speedMetersPerSecond, context) {
        Instance.getInstance(context).distanceUnitUtils
            .getSpeed(speedMetersPerSecond.coerceAtLeast(0.0))
    }
}

@Composable
private fun formatPace(paceMinutesPerKilometer: Double): String {
    val context = LocalContext.current
    return remember(paceMinutesPerKilometer, context) {
        if (paceMinutesPerKilometer > 0.0) {
            Instance.getInstance(context).distanceUnitUtils.getPace(paceMinutesPerKilometer)
        } else {
            "—"
        }
    }
}

@Composable
private fun formatFrequency(repetitionsPerSecond: Double): String = stringResource(
    R.string.statistics_frequency_short,
    repetitionsPerSecond.coerceAtLeast(0.0),
)

private fun formatChartDate(date: LocalDate, unit: StatisticsBucketUnit): String {
    val formatter = when (unit) {
        StatisticsBucketUnit.DAY,
        StatisticsBucketUnit.WEEK -> DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        StatisticsBucketUnit.MONTH -> DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault())
    }
    return date.format(formatter)
}
