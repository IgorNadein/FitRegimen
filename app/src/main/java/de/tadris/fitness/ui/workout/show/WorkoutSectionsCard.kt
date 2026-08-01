/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack
 */
package de.tadris.fitness.ui.workout.show

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.WorkoutData
import de.tadris.fitness.ui.sections.DistanceTranslator
import de.tadris.fitness.ui.sections.SectionListModel
import de.tadris.fitness.ui.sections.SectionListModel.SectionCriterion
import de.tadris.fitness.util.unit.DistanceUnitUtils
import de.tadris.fitness.util.unit.TimeFormatter
import java.text.DecimalFormat
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Material 3 replacement for the former XML [de.tadris.fitness.ui.sections.SectionListView].
 * The existing [SectionListModel] remains responsible for splitting route samples so chart and
 * section calculations continue to share the same source of truth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutSectionsCard(
    data: WorkoutData,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val distanceUnitUtils = remember(context) { Instance.getInstance(context).distanceUnitUtils }
    val model = remember(data) { SectionListModel(data) }
    val translator = remember(model, distanceUnitUtils) {
        DistanceTranslator(distanceUnitUtils, model)
    }
    val stateKey = data.workout.id

    var criterionName by rememberSaveable(stateKey) {
        mutableStateOf(SectionCriterion.DISTANCE.name)
    }
    var selectedUnitIndex by rememberSaveable(stateKey) { mutableIntStateOf(0) }
    var lengthText by rememberSaveable(stateKey) { mutableStateOf("1") }
    var committedDisplayLength by rememberSaveable(stateKey) { mutableDoubleStateOf(1.0) }
    var showPace by rememberSaveable(stateKey) { mutableStateOf(true) }
    var unitMenuExpanded by remember { mutableStateOf(false) }

    val criterion = SectionCriterion.valueOf(criterionName)
    val criterionOptions = remember { SectionCriterion.values().toList() }
    val units = remember(criterion, translator) {
        if (criterion == SectionCriterion.TIME) translator.timeUnits() else translator.distanceUnits()
    }
    val parsedLength = parsePositiveSectionLength(lengthText)

    fun setDefaultLength(newCriterion: SectionCriterion, unitIndex: Int) {
        val defaultValue = if (newCriterion == SectionCriterion.TIME) {
            translator.getDefaultValueForTime(unitIndex).toDouble()
        } else {
            translator.getDefaultValueForDistance(unitIndex).toDouble()
        }
        selectedUnitIndex = unitIndex
        committedDisplayLength = defaultValue
        lengthText = formatEditableSectionLength(defaultValue)
    }

    fun selectCriterion(newCriterion: SectionCriterion) {
        criterionName = newCriterion.name
        setDefaultLength(newCriterion, defaultUnitIndex(newCriterion))
    }

    fun selectUnit(index: Int) {
        setDefaultLength(criterion, index)
        unitMenuExpanded = false
    }

    val sections = remember(data, criterion, selectedUnitIndex, committedDisplayLength) {
        model.setCriterion(criterion)
        model.setSelectedUnitID(selectedUnitIndex)
        val normalizedLength = translator.getNormalizedLength(
            criterion,
            selectedUnitIndex,
            committedDisplayLength,
        )
        if (normalizedLength.isFinite() && normalizedLength > 0.0) {
            model.setSectionLength(normalizedLength)
            model.sectionList
        } else {
            emptyList()
        }
    }
    val rows = remember(sections, criterion, showPace) {
        buildWorkoutSectionRows(
            sections = sections.map { section ->
                WorkoutSectionValues(
                    distanceMeters = section.dist,
                    durationMillis = section.getTime(true),
                    ascentMeters = section.ascent,
                    descentMeters = section.descent,
                    paceSecondsPerMeter = section.pace,
                    isBest = section.isBest,
                    isWorst = section.isWorst,
                )
            },
            criterion = criterion,
            showPace = showPace,
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                criterionOptions.forEach { option ->
                    FilterChip(
                        selected = criterion == option,
                        onClick = { selectCriterion(option) },
                        label = { Text(criterionLabel(option)) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                OutlinedTextField(
                    value = lengthText,
                    onValueChange = { value ->
                        lengthText = value
                        parsePositiveSectionLength(value)?.let { committedDisplayLength = it }
                    },
                    label = { Text(stringResource(R.string.workout_sections_length)) },
                    singleLine = true,
                    isError = parsedLength == null,
                    supportingText = if (parsedLength == null) {
                        { Text(stringResource(R.string.invalidNumberFormat)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() },
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { state ->
                            if (!state.isFocused && parsePositiveSectionLength(lengthText) == null) {
                                lengthText = formatEditableSectionLength(committedDisplayLength)
                            }
                        },
                    shape = MaterialTheme.shapes.large,
                )

                ExposedDropdownMenuBox(
                    expanded = unitMenuExpanded,
                    onExpandedChange = { unitMenuExpanded = it },
                    modifier = Modifier.weight(0.72f),
                ) {
                    OutlinedTextField(
                        value = units.getOrElse(selectedUnitIndex) { units.firstOrNull().orEmpty() },
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.workout_sections_unit)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitMenuExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        shape = MaterialTheme.shapes.large,
                    )
                    ExposedDropdownMenu(
                        expanded = unitMenuExpanded,
                        onDismissRequest = { unitMenuExpanded = false },
                    ) {
                        units.forEachIndexed { index, unit ->
                            DropdownMenuItem(
                                text = { Text(unit) },
                                onClick = { selectUnit(index) },
                            )
                        }
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(true, false).forEachIndexed { index, paceMode ->
                    SegmentedButton(
                        selected = showPace == paceMode,
                        onClick = { showPace = paceMode },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        label = {
                            Text(
                                stringResource(
                                    if (paceMode) R.string.workoutPace else R.string.workoutSpeed
                                )
                            )
                        },
                    )
                }
            }

            if (rows.isEmpty()) {
                Text(
                    text = stringResource(R.string.workout_sections_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { row ->
                        WorkoutSectionRow(
                            row = row,
                            criterion = criterion,
                            criterionName = criterionLabel(criterion),
                            showPace = showPace,
                            distanceUnitUtils = distanceUnitUtils,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutSectionRow(
    row: WorkoutSectionRow,
    criterion: SectionCriterion,
    criterionName: String,
    showPace: Boolean,
    distanceUnitUtils: DistanceUnitUtils,
) {
    val values = row.values
    val isOnlySection = values.isBest && values.isWorst
    val containerColor = when {
        isOnlySection -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        values.isBest -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.48f)
        values.isWorst -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val indicatorColor = when {
        isOnlySection -> MaterialTheme.colorScheme.primary
        values.isBest -> MaterialTheme.colorScheme.tertiary
        values.isWorst -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }
    val paceValue = if (showPace) {
        val pace = distanceUnitUtils.getPace(
            values.paceSecondsPerMeter / 60.0 * 1000.0,
            false,
            false,
        )
        "$pace ${distanceUnitUtils.paceUnit}"
    } else {
        val speed = if (values.paceSecondsPerMeter > 0.0) {
            distanceUnitUtils.getSpeedString(1.0 / values.paceSecondsPerMeter)
        } else {
            "-"
        }
        "$speed ${distanceUnitUtils.distanceUnitSystem.speedUnit}"
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = containerColor,
    ) {
        Box {
            Column(
                modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "#${row.index + 1} · $criterionName",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatCumulativeCriterion(
                                value = row.cumulativeCriterion,
                                criterion = criterion,
                                distanceUnitUtils = distanceUnitUtils,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (values.isBest) {
                                PerformanceBadge(
                                    label = stringResource(R.string.workout_sections_best),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                )
                            }
                            if (values.isWorst && !isOnlySection) {
                                PerformanceBadge(
                                    label = stringResource(R.string.workout_sections_slowest),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                )
                            }
                        }
                        Text(
                            text = paceValue,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                val metrics = sectionMetrics(values, criterion, distanceUnitUtils)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    metrics.forEach { metric ->
                        SectionMetricCell(metric = metric, modifier = Modifier.weight(1f))
                    }
                }
            }

            LinearProgressIndicator(
                progress = { row.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomCenter),
                color = indicatorColor,
                trackColor = Color.Transparent,
            )
        }
    }
}

@Composable
private fun PerformanceBadge(label: String, color: Color) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

private data class SectionMetric(
    val label: String,
    val value: String,
    val iconRes: Int? = null,
    val marker: String? = null,
)

@Composable
private fun SectionMetricCell(metric: SectionMetric, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            metric.iconRes?.let { iconRes ->
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = metric.label,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            metric.marker?.let { marker ->
                Text(
                    text = marker,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = metric.value,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = metric.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun sectionMetrics(
    values: WorkoutSectionValues,
    criterion: SectionCriterion,
    distanceUnitUtils: DistanceUnitUtils,
): List<SectionMetric> = buildList {
    if (criterion != SectionCriterion.DISTANCE) {
        add(
            SectionMetric(
                label = stringResource(R.string.workoutDistance),
                value = formatLongDistance(values.distanceMeters, distanceUnitUtils),
                iconRes = R.drawable.ic_distance,
            )
        )
    }
    if (criterion != SectionCriterion.TIME) {
        add(
            SectionMetric(
                label = stringResource(R.string.workoutTime),
                value = TimeFormatter.formatDuration((values.durationMillis / 1000.0).roundToLong()),
                iconRes = R.drawable.ic_clock,
            )
        )
    }
    if (criterion != SectionCriterion.ASCENT) {
        add(
            SectionMetric(
                label = stringResource(R.string.workoutAscent),
                value = distanceUnitUtils.getElevation(values.ascentMeters.roundToInt()),
                marker = "↑",
            )
        )
    }
    if (criterion != SectionCriterion.DESCENT) {
        add(
            SectionMetric(
                label = stringResource(R.string.workoutDescent),
                value = distanceUnitUtils.getElevation(values.descentMeters.roundToInt()),
                marker = "↓",
            )
        )
    }
}

@Composable
private fun criterionLabel(criterion: SectionCriterion): String = stringResource(
    when (criterion) {
        SectionCriterion.DISTANCE -> R.string.workoutDistance
        SectionCriterion.TIME -> R.string.workoutTime
        SectionCriterion.ASCENT -> R.string.workoutAscent
        SectionCriterion.DESCENT -> R.string.workoutDescent
    }
)

private fun formatCumulativeCriterion(
    value: Double,
    criterion: SectionCriterion,
    distanceUnitUtils: DistanceUnitUtils,
): String = when (criterion) {
    SectionCriterion.DISTANCE -> formatLongDistance(value, distanceUnitUtils)
    SectionCriterion.TIME -> TimeFormatter.formatDuration((value / 1000.0).roundToLong())
    SectionCriterion.ASCENT,
    SectionCriterion.DESCENT -> distanceUnitUtils.getElevation(value.roundToInt())
}

private fun formatLongDistance(
    distanceMeters: Double,
    distanceUnitUtils: DistanceUnitUtils,
): String {
    val units = distanceUnitUtils.distanceUnitSystem
    val displayValue = units.getDistanceFromKilometers(distanceMeters / 1000.0)
    return "${DecimalFormat("0.##").format(displayValue)} ${units.longDistanceUnit}"
}

private fun defaultUnitIndex(criterion: SectionCriterion): Int = when (criterion) {
    SectionCriterion.DISTANCE -> 0
    SectionCriterion.TIME,
    SectionCriterion.ASCENT,
    SectionCriterion.DESCENT -> 1
}

internal fun parsePositiveSectionLength(raw: String): Double? = raw
    .trim()
    .replace(',', '.')
    .toDoubleOrNull()
    ?.takeIf { it.isFinite() && it > 0.0 }

internal fun formatEditableSectionLength(value: Double): String =
    DecimalFormat("0.##").format(value)

internal data class WorkoutSectionValues(
    val distanceMeters: Double,
    val durationMillis: Double,
    val ascentMeters: Double,
    val descentMeters: Double,
    val paceSecondsPerMeter: Double,
    val isBest: Boolean,
    val isWorst: Boolean,
)

internal data class WorkoutSectionRow(
    val index: Int,
    val values: WorkoutSectionValues,
    val cumulativeCriterion: Double,
    val progress: Float,
)

internal fun buildWorkoutSectionRows(
    sections: List<WorkoutSectionValues>,
    criterion: SectionCriterion,
    showPace: Boolean,
): List<WorkoutSectionRow> {
    val finitePaces = sections.map { it.paceSecondsPerMeter }.filter { it.isFinite() && it > 0.0 }
    val bestPace = sections.firstOrNull { it.isBest }
        ?.paceSecondsPerMeter
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?: finitePaces.minOrNull()
        ?: 0.0
    val worstPace = sections.firstOrNull { it.isWorst }
        ?.paceSecondsPerMeter
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?: finitePaces.maxOrNull()
        ?: 0.0
    var cumulative = 0.0

    return sections.mapIndexed { index, values ->
        cumulative += when (criterion) {
            SectionCriterion.DISTANCE -> values.distanceMeters
            SectionCriterion.TIME -> values.durationMillis
            SectionCriterion.ASCENT -> values.ascentMeters
            SectionCriterion.DESCENT -> values.descentMeters
        }
        val rawProgress = if (showPace) {
            if (worstPace > 0.0) values.paceSecondsPerMeter / worstPace else 0.0
        } else {
            if (values.paceSecondsPerMeter > 0.0) bestPace / values.paceSecondsPerMeter else 0.0
        }
        WorkoutSectionRow(
            index = index,
            values = values,
            cumulativeCriterion = cumulative,
            progress = rawProgress.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)?.toFloat() ?: 0f,
        )
    }
}
