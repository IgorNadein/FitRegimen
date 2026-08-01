/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.ui.nutrition

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.NutritionDay
import de.tadris.fitness.data.NutritionItem
import de.tadris.fitness.data.NutritionMeal
import de.tadris.fitness.data.NutritionPlan
import de.tadris.fitness.data.ProgramScheduling
import de.tadris.fitness.data.TrainingProgram
import de.tadris.fitness.data.withUpdatedTimestamp
import de.tadris.fitness.data.backup.NutritionPlanExporter
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class NutritionPlanDetailSnapshot(
    val plan: NutritionPlan,
    val days: List<NutritionDay>,
    val linkedTrainingProgram: TrainingProgram?,
    val linkedTrainingProgramDayCount: Int,
)

private data class NutritionDayDetailContent(
    val dayId: Long,
    val meals: List<NutritionMeal>,
    val itemsByMeal: Map<Long, List<NutritionItem>>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionPlanDetailScreen(
    id: Long,
    onBack: () -> Unit,
    onEditPlan: () -> Unit,
) {
    val context = LocalContext.current
    val db = remember { Instance.getInstance(context).db }
    val dao = remember(db) { db.nutritionPlanDao() }
    val trainingProgramDao = remember(db) { db.trainingProgramDao() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var refreshKey by remember { mutableIntStateOf(0) }
    var snapshot by remember(id) { mutableStateOf<NutritionPlanDetailSnapshot?>(null) }
    var dayContent by remember(id) { mutableStateOf<NutritionDayDetailContent?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var showStopDialog by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var today by remember { mutableStateOf(LocalDate.now()) }

    LaunchedEffect(id, refreshKey) {
        snapshot = withContext(Dispatchers.IO) {
            dao.findPlanById(id)?.let { loadedPlan ->
                val linkedProgram = loadedPlan.linkedTrainingProgramUuid?.let { uuid ->
                    trainingProgramDao.getPrograms().firstOrNull { it.uuid == uuid }
                }
                NutritionPlanDetailSnapshot(
                    plan = loadedPlan,
                    days = dao.getDaysOfPlan(id),
                    linkedTrainingProgram = linkedProgram,
                    linkedTrainingProgramDayCount = linkedProgram?.let {
                        trainingProgramDao.countDaysOfProgram(it.id)
                    } ?: 0,
                )
            }
        }
    }

    LaunchedEffect(lifecycleOwner, id) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            today = LocalDate.now()
            refreshKey++
        }
    }

    val loaded = snapshot ?: return
    val current = loaded.plan
    val days = loaded.days
    val initialIndex = current.startDateEpochDay?.let { start ->
        (today.toEpochDay() - start)
            .coerceIn(0L, days.lastIndex.coerceAtLeast(0).toLong())
            .toInt()
    } ?: 0
    var selectedIndex by rememberSaveable(
        id,
        current.startDateEpochDay,
        days.size,
        today.toEpochDay(),
    ) {
        mutableIntStateOf(initialIndex)
    }
    val boundedSelectedIndex = selectedIndex.coerceIn(0, days.lastIndex.coerceAtLeast(0))
    val selectedDay = days.getOrNull(boundedSelectedIndex)
    LaunchedEffect(refreshKey, selectedDay?.id) {
        val targetDayId = selectedDay?.id
        if (targetDayId == null) {
            dayContent = null
            return@LaunchedEffect
        }
        dayContent = withContext(Dispatchers.IO) {
            val loadedMeals = dao.getMealsOfDay(targetDayId)
            NutritionDayDetailContent(
                dayId = targetDayId,
                meals = loadedMeals,
                itemsByMeal = loadedMeals.associate { meal ->
                    meal.id to dao.getItemsOfMeal(meal.id)
                },
            )
        }
    }
    val selectedContent = dayContent?.takeIf { it.dayId == selectedDay?.id }
    val meals = selectedContent?.meals.orEmpty()
    val itemsByMeal = selectedContent?.itemsByMeal.orEmpty()
    val allItems = itemsByMeal.values.flatten()
    val dayMetrics = selectedDay?.let {
        NutritionMetricValues.forDay(it, NutritionTotals.from(allItems))
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val container = NutritionPlanExporter.buildContainer(
                        context = context,
                        plan = current,
                        includePrivateLinks = false,
                    )
                    context.contentResolver.openOutputStream(uri)?.use {
                        NutritionPlanExporter.write(container, it)
                    } ?: error(context.getString(R.string.nutritionExportFailed))
                }
                snackbarHostState.showSnackbar(context.getString(R.string.nutritionPlanSaved))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                actionError = error.message ?: context.getString(R.string.nutritionExportFailed)
            }
        }
    }

    fun refresh() {
        refreshKey++
    }

    fun startPlan() {
        val linkedStartDate = loaded.linkedTrainingProgram?.let { linkedProgram ->
            val zoneId = ZoneId.systemDefault()
            if (ProgramScheduling.dayIndexOf(
                    program = linkedProgram,
                    dayCount = loaded.linkedTrainingProgramDayCount,
                    date = today,
                    zone = zoneId,
                ) != null
            ) {
                ProgramScheduling.firstScheduleBounds(
                    program = linkedProgram,
                    dayCount = loaded.linkedTrainingProgramDayCount,
                    zone = zoneId,
                )?.first
            } else {
                null
            }
        }
        dao.updatePlan(
            current.copy(
                startDateEpochDay = (linkedStartDate ?: today).toEpochDay(),
            ).withUpdatedTimestamp()
        )
        selectedIndex = 0
        refresh()
    }

    fun stopPlan() {
        dao.updatePlan(
            current.copy(
                startDateEpochDay = null,
            ).withUpdatedTimestamp()
        )
        selectedIndex = 0
        showStopDialog = false
        refresh()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = current.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.actionBack),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.nutritionMoreActions),
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.nutritionEditPlan)) },
                                onClick = {
                                    menuOpen = false
                                    onEditPlan()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.nutritionSaveFile)) },
                                onClick = {
                                    menuOpen = false
                                    saveLauncher.launch("${nutritionSafeFileName(current.name)}.fitregimen.json")
                                },
                            )
                            if (current.startDateEpochDay != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.nutritionStopPlan)) },
                                    onClick = {
                                        menuOpen = false
                                        showStopDialog = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (days.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    NutritionPlanIconBadge(size = 72.dp, iconSize = 34.dp)
                    Text(
                        stringResource(R.string.nutritionNoDays),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onEditPlan) {
                        Text(stringResource(R.string.nutritionEditPlan))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "nutrition_day_strip") {
                    NutritionDayStrip(
                        days = days,
                        startDateEpochDay = current.startDateEpochDay,
                        selectedIndex = boundedSelectedIndex,
                        onSelect = { selectedIndex = it },
                    )
                }

                selectedDay?.let { day ->
                    item(key = "nutrition_day_overview_${day.id}") {
                        NutritionDayOverviewCard(
                            day = day,
                            dayIndex = boundedSelectedIndex,
                            startDateEpochDay = current.startDateEpochDay,
                            today = today,
                            metrics = dayMetrics ?: NutritionMetricValues.forDay(
                                day,
                                NutritionTotals(),
                            ),
                            canStart = current.startDateEpochDay == null,
                            onStart = ::startPlan,
                        )
                    }
                }

                if (current.description != null || current.objective != null ||
                    current.preferences != null || current.restrictions != null ||
                    current.startDateEpochDay != null ||
                    loaded.linkedTrainingProgram != null
                ) {
                    item(key = "nutrition_plan_info") {
                        NutritionPlanInfoCard(
                            plan = current,
                            linkedTrainingProgramName = loaded.linkedTrainingProgram?.name,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                item(key = "nutrition_meals_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.nutritionMeals),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.nutritionMealCount, meals.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (meals.isEmpty()) {
                    item(key = "nutrition_meals_empty") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                            Text(
                                stringResource(R.string.nutritionNoMeals),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                } else {
                    meals.forEach { meal ->
                        item(key = "nutrition_meal_${meal.id}") {
                            NutritionMealCard(
                                meal = meal,
                                items = itemsByMeal[meal.id].orEmpty(),
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text(stringResource(R.string.nutritionStopPlan)) },
            text = { Text(stringResource(R.string.nutritionStopPlanMessage)) },
            confirmButton = {
                TextButton(onClick = ::stopPlan) {
                    Text(stringResource(R.string.nutritionStop))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    actionError?.let { message ->
        AlertDialog(
            onDismissRequest = { actionError = null },
            title = { Text(stringResource(R.string.nutritionExportFailed)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { actionError = null }) {
                    Text(stringResource(R.string.okay))
                }
            },
        )
    }
}

@Composable
private fun NutritionDayOverviewCard(
    day: NutritionDay,
    dayIndex: Int,
    startDateEpochDay: Long?,
    today: LocalDate,
    metrics: NutritionMetricValues,
    canStart: Boolean,
    onStart: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val date = startDateEpochDay?.let {
        LocalDate.ofEpochDay(it).plusDays(dayIndex.toLong())
    }
    val dateText = date?.format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                NutritionPlanIconBadge(size = 42.dp, iconSize = 22.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        day.name?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.nutritionDayNumber, dayIndex + 1),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    dateText?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (date == today) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            stringResource(R.string.nutritionToday),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            NutritionMetricGrid(values = metrics)

            day.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                SelectionContainer {
                    Text(
                        notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (canStart) {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.nutritionStartPlan))
                }
            }
        }
    }
}

@Composable
private fun NutritionPlanInfoCard(
    plan: NutritionPlan,
    linkedTrainingProgramName: String?,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(plan.id) { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    val fields = buildList {
        linkedTrainingProgramName?.takeIf { it.isNotBlank() }?.let {
            add(stringResource(R.string.nutritionLinkedTrainingProgram) to it)
        }
        plan.startDateEpochDay?.let {
            add(
                stringResource(R.string.nutritionStartDate) to
                    LocalDate.ofEpochDay(it).format(dateFormatter)
            )
        }
        plan.description?.takeIf { it.isNotBlank() }?.let {
            add(stringResource(R.string.nutritionDescription) to it)
        }
        plan.objective?.takeIf { it.isNotBlank() }?.let {
            add(stringResource(R.string.nutritionObjective) to it)
        }
        plan.preferences?.takeIf { it.isNotBlank() }?.let {
            add(stringResource(R.string.nutritionPreferences) to it)
        }
        plan.restrictions?.takeIf { it.isNotBlank() }?.let {
            add(stringResource(R.string.nutritionRestrictions) to it)
        }
    }
    val collapsible = fields.size > 2
    val visibleFields = if (collapsible && !expanded) fields.take(2) else fields

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            visibleFields.forEach { (label, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    if (!collapsible || expanded) {
                        SelectionContainer {
                            Text(
                                value,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (collapsible) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(
                        stringResource(
                            if (expanded) {
                                R.string.nutritionCollapseDetails
                            } else {
                                R.string.nutritionExpandDetails
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun NutritionMealCard(
    meal: NutritionMeal,
    items: List<NutritionItem>,
    modifier: Modifier = Modifier,
) {
    val totals = remember(items) { NutritionTotals.from(items) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        meal.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    nutritionTimeText(meal.plannedTimeMinutes)?.let { time ->
                        Text(
                            time,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    nutritionMetricText(
                        totals.energyKcal.takeIf { it > 0.0 },
                        stringResource(R.string.nutritionKcalShort),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            meal.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                SelectionContainer {
                    Text(
                        notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (items.isEmpty()) {
                Text(
                    stringResource(R.string.nutritionNoItems),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    NutritionItemRow(item)
                }
            }

            if (items.isNotEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Text(
                        stringResource(
                            R.string.nutritionMacroSummary,
                            nutritionDecimal(totals.proteinGrams),
                            nutritionDecimal(totals.fatGrams),
                            nutritionDecimal(totals.carbsGrams),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NutritionItemRow(item: NutritionItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(12.dp)
                .padding(top = 7.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                modifier = Modifier.size(8.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {}
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            val portion = nutritionPortionText(item)
            if (portion.isNotEmpty()) {
                Text(
                    portion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text(
                    notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item.energyKcal?.let {
            Text(
                "${nutritionDecimal(it)} ${stringResource(R.string.nutritionKcalShort)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun nutritionPortionText(item: NutritionItem): String = buildList {
    if (item.quantity != null) {
        add(
            buildString {
                append(nutritionDecimal(item.quantity))
                item.unit?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
            }
        )
    }
    item.weightGrams?.let {
        add("${nutritionDecimal(it)} ${stringResource(R.string.nutritionGramShort)}")
    }
}.joinToString(" · ")

private fun nutritionSafeFileName(name: String): String = name
    .trim()
    .replace(Regex("[\\\\/:*?\"<>|]+"), "_")
    .take(80)
    .ifBlank { "nutrition-plan" }
