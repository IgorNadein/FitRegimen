/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.ui.nutrition

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.tadris.fitness.R
import de.tadris.fitness.data.NutritionDay
import de.tadris.fitness.data.NutritionItem
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

internal enum class NutritionPlanStatus {
    ACTIVE,
    SAVED,
    COMPLETED,
}

internal const val NUTRITION_MAX_DAYS = 366
internal const val NUTRITION_MAX_MEALS_PER_DAY = 12
internal const val NUTRITION_MAX_ITEMS_PER_MEAL = 60
internal const val NUTRITION_MAX_NAME_LENGTH = 200
internal const val NUTRITION_MAX_TEXT_LENGTH = 5_000
internal const val NUTRITION_MAX_UNIT_LENGTH = 40
internal const val NUTRITION_MAX_NUMERIC_INPUT_LENGTH = 32
internal const val NUTRITION_MAX_ENERGY_KCAL = 50_000.0
internal const val NUTRITION_MAX_MACRO_GRAMS = 10_000.0
internal const val NUTRITION_MAX_WATER_OR_WEIGHT = 100_000.0
internal const val NUTRITION_MAX_QUANTITY = 1_000_000.0

/** Serializes nutrition-tree writes and exposes a configuration-safe disabled state to editors. */
internal object NutritionTreeMutationGuard {
    private val mutex = Mutex()
    private val mutableInProgress = MutableStateFlow(false)
    private val mutableRevision = MutableStateFlow(0L)
    val inProgress = mutableInProgress.asStateFlow()
    val revision = mutableRevision.asStateFlow()

    fun tryAcquire(): Boolean {
        if (!mutex.tryLock()) return false
        mutableInProgress.value = true
        return true
    }

    fun release() {
        mutableRevision.value += 1L
        mutableInProgress.value = false
        mutex.unlock()
    }
}

internal class NutritionUserVisibleException(message: String) : IllegalStateException(message)

internal data class NutritionTotals(
    val energyKcal: Double = 0.0,
    val proteinGrams: Double = 0.0,
    val fatGrams: Double = 0.0,
    val carbsGrams: Double = 0.0,
    val fiberGrams: Double = 0.0,
) {
    operator fun plus(other: NutritionTotals) = NutritionTotals(
        energyKcal = energyKcal + other.energyKcal,
        proteinGrams = proteinGrams + other.proteinGrams,
        fatGrams = fatGrams + other.fatGrams,
        carbsGrams = carbsGrams + other.carbsGrams,
        fiberGrams = fiberGrams + other.fiberGrams,
    )

    companion object {
        fun from(item: NutritionItem) = NutritionTotals(
            energyKcal = item.energyKcal ?: 0.0,
            proteinGrams = item.proteinGrams ?: 0.0,
            fatGrams = item.fatGrams ?: 0.0,
            carbsGrams = item.carbsGrams ?: 0.0,
            fiberGrams = item.fiberGrams ?: 0.0,
        )

        fun from(items: Iterable<NutritionItem>): NutritionTotals =
            items.fold(NutritionTotals()) { total, item -> total + from(item) }
    }
}

internal data class NutritionMetricValues(
    val energyKcal: Double?,
    val proteinGrams: Double?,
    val fatGrams: Double?,
    val carbsGrams: Double?,
    val fiberGrams: Double?,
    val waterMl: Double?,
) {
    companion object {
        fun forDay(day: NutritionDay, itemTotals: NutritionTotals) = NutritionMetricValues(
            energyKcal = day.energyKcal ?: itemTotals.energyKcal.takeIf { it > 0.0 },
            proteinGrams = day.proteinGrams ?: itemTotals.proteinGrams.takeIf { it > 0.0 },
            fatGrams = day.fatGrams ?: itemTotals.fatGrams.takeIf { it > 0.0 },
            carbsGrams = day.carbsGrams ?: itemTotals.carbsGrams.takeIf { it > 0.0 },
            fiberGrams = day.fiberGrams ?: itemTotals.fiberGrams.takeIf { it > 0.0 },
            waterMl = day.waterMl,
        )
    }
}

@Composable
internal fun NutritionPlanIconBadge(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 25.dp,
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_nutrition_24),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
internal fun NutritionSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
internal fun NutritionMetricGrid(
    values: NutritionMetricValues,
    modifier: Modifier = Modifier,
) {
    val cells = listOf(
        stringResource(R.string.nutritionCalories) to nutritionMetricText(
            values.energyKcal,
            stringResource(R.string.nutritionKcalShort),
        ),
        stringResource(R.string.nutritionProtein) to nutritionMetricText(
            values.proteinGrams,
            stringResource(R.string.nutritionGramShort),
        ),
        stringResource(R.string.nutritionFat) to nutritionMetricText(
            values.fatGrams,
            stringResource(R.string.nutritionGramShort),
        ),
        stringResource(R.string.nutritionCarbs) to nutritionMetricText(
            values.carbsGrams,
            stringResource(R.string.nutritionGramShort),
        ),
        stringResource(R.string.nutritionFiber) to nutritionMetricText(
            values.fiberGrams,
            stringResource(R.string.nutritionGramShort),
        ),
        stringResource(R.string.nutritionWater) to nutritionMetricText(
            values.waterMl,
            stringResource(R.string.nutritionMilliliterShort),
        ),
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cells.chunked(3).forEach { rowCells ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowCells.forEach { (label, value) ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 72.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = value,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NutritionDayStrip(
    days: List<NutritionDay>,
    startDateEpochDay: Long?,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return

    val boundedIndex = selectedIndex.coerceIn(0, days.lastIndex)
    val visibleDayCount = 7
    val windowStart = (boundedIndex - visibleDayCount / 2)
        .coerceIn(0, (days.size - visibleDayCount).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = windowStart)
    val locale = LocalConfiguration.current.locales[0]
    val lifecycleOwner = LocalLifecycleOwner.current
    var today by remember { mutableStateOf(LocalDate.now()) }
    val fullDateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    val monthFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    }

    LaunchedEffect(days.size, windowStart) {
        if (listState.firstVisibleItemIndex != windowStart) {
            listState.animateScrollToItem(windowStart)
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            today = LocalDate.now()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val selectedDate = startDateEpochDay?.let {
                LocalDate.ofEpochDay(it).plusDays(boundedIndex.toLong())
            }
            Text(
                text = selectedDate?.format(monthFormatter)
                    ?: stringResource(R.string.nutritionDays),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.nutritionDayOfPlan,
                    boundedIndex + 1,
                    days.size,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val horizontalPadding = 12.dp
            val itemWidth = (maxWidth - horizontalPadding * 2) / visibleDayCount.toFloat()

            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(days, key = { _, day -> day.id }) { index, _ ->
                    val date = startDateEpochDay?.let {
                        LocalDate.ofEpochDay(it).plusDays(index.toLong())
                    }
                    val selected = index == boundedIndex
                    val isToday = date == today
                    val description = if (date != null) {
                        date.format(fullDateFormatter)
                    } else {
                        stringResource(R.string.nutritionDayNumber, index + 1)
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .width(itemWidth)
                            .heightIn(min = 56.dp)
                            .minimumInteractiveComponentSize()
                            .semantics {
                                contentDescription = description
                                this.selected = selected
                            },
                    ) {
                        Surface(
                            onClick = { onSelect(index) },
                            shape = MaterialTheme.shapes.large,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            contentColor = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else if (isToday) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            border = if (isToday && !selected) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                null
                            },
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .fillMaxSize(),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = date?.dayOfWeek?.getDisplayName(TextStyle.SHORT, locale)
                                        ?: stringResource(R.string.nutritionDayShort),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                )
                                Text(
                                    text = date?.dayOfMonth?.toString() ?: (index + 1).toString(),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NutritionSpeedDial(
    expanded: Boolean,
    onToggle: () -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onAiPrompt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (expanded) 28.dp else 16.dp,
        label = "nutrition_fab_corner",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                NutritionSpeedDialAction(
                    label = stringResource(R.string.nutritionCreatePlan),
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = onCreate,
                )
                NutritionSpeedDialAction(
                    label = stringResource(R.string.nutritionImportPlan),
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_storage),
                            contentDescription = null,
                        )
                    },
                    onClick = onImport,
                )
                NutritionSpeedDialAction(
                    label = stringResource(R.string.nutritionAiPrompt),
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_auto_awesome_24),
                            contentDescription = null,
                        )
                    },
                    onClick = onAiPrompt,
                )
            }
        }

        FloatingActionButton(
            onClick = onToggle,
            shape = RoundedCornerShape(cornerRadius),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = stringResource(
                    if (expanded) {
                        R.string.nutritionCloseActions
                    } else {
                        R.string.nutritionOpenActions
                    }
                ),
            )
        }
    }
}

@Composable
private fun NutritionSpeedDialAction(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = icon,
        text = {
            Text(
                text = label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
        },
        modifier = Modifier.semantics { contentDescription = label },
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

internal fun nutritionDecimal(value: Double): String = BigDecimal.valueOf(value)
    .setScale(1, java.math.RoundingMode.HALF_UP)
    .stripTrailingZeros()
    .toPlainString()

internal fun nutritionMetricText(value: Double?, unit: String): String =
    value?.takeIf { it.isFinite() && it >= 0.0 }?.let { "${nutritionDecimal(it)} $unit" } ?: "—"

internal fun nutritionTimeText(minutes: Int?): String? = minutes
    ?.takeIf { it in 0..1439 }
    ?.let { String.format(Locale.getDefault(), "%02d:%02d", it / 60, it % 60) }

internal fun nutritionTimeMinutes(value: String): Int? {
    val match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(value.trim()) ?: return null
    val hours = match.groupValues[1].toIntOrNull() ?: return null
    val minutes = match.groupValues[2].toIntOrNull() ?: return null
    return if (hours in 0..23 && minutes in 0..59) hours * 60 + minutes else null
}

internal fun nutritionNumber(value: String): Double? = value
    .trim()
    .replace(',', '.')
    .toDoubleOrNull()
    ?.takeIf { it.isFinite() && it >= 0.0 }

internal fun nutritionNumber(value: String, maximum: Double): Double? =
    nutritionNumber(value)?.takeIf { it <= maximum }
