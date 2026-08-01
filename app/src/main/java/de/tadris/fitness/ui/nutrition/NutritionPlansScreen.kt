/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.ui.nutrition

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.NutritionItem
import de.tadris.fitness.data.NutritionPlan
import de.tadris.fitness.data.backup.NutritionPlanImporter
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionPlansScreen(
    fabExpanded: Boolean = false,
    onFabExpandedChange: (Boolean) -> Unit = {},
    onOpenPlan: (Long) -> Unit,
    onPlanCreated: (Long) -> Unit,
    onAiPrompt: () -> Unit,
) {
    val context = LocalContext.current
    val dao = remember { Instance.getInstance(context).db.nutritionPlanDao() }
    val plans by dao.getPlansFlow().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var todayEpochDay by remember { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            todayEpochDay = LocalDate.now().toEpochDay()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val planId = withContext(Dispatchers.IO) {
                    val container = context.contentResolver.openInputStream(uri)?.use {
                        NutritionPlanImporter.read(it)
                    } ?: error(context.getString(R.string.nutritionImportFailed))
                    NutritionPlanImporter.import(context, container)
                }
                onOpenPlan(planId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                importError = error.message ?: context.getString(R.string.nutritionImportFailed)
            }
        }
    }

    var entries by remember { mutableStateOf<List<NutritionPlanListEntry>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(plans, todayEpochDay) {
        entries = withContext(Dispatchers.IO) {
            plans.map { plan ->
                val days = dao.getDaysOfPlan(plan.id)
                val start = plan.startDateEpochDay
                val end = start?.takeIf { days.isNotEmpty() }?.plus(days.lastIndex.toLong())
                val status = when {
                    start != null && end != null && todayEpochDay in start..end -> {
                        NutritionPlanStatus.ACTIVE
                    }
                    end != null && todayEpochDay > end -> NutritionPlanStatus.COMPLETED
                    else -> NutritionPlanStatus.SAVED
                }
                val selectedDayIndex = when (status) {
                    NutritionPlanStatus.ACTIVE -> (todayEpochDay - start!!)
                        .coerceIn(0L, days.lastIndex.toLong())
                        .toInt()
                    NutritionPlanStatus.COMPLETED -> days.lastIndex
                    NutritionPlanStatus.SAVED -> 0
                }
                val selectedDay = days.getOrNull(selectedDayIndex)
                val selectedMeals = selectedDay?.let { dao.getMealsOfDay(it.id) }.orEmpty()
                val calculatedEnergyKcal = selectedMeals
                    .asSequence()
                    .flatMap { meal -> dao.getItemsOfMeal(meal.id).asSequence() }
                    .mapNotNull(NutritionItem::energyKcal)
                    .sum()
                    .takeIf { it > 0.0 }
                NutritionPlanListEntry(
                    plan = plan,
                    dayCount = days.size,
                    dayIndex = selectedDayIndex.takeIf { days.isNotEmpty() },
                    mealCount = selectedMeals.size,
                    energyKcal = selectedDay?.energyKcal ?: calculatedEnergyKcal,
                    status = status,
                )
            }
        }
    }

    BackHandler(enabled = fabExpanded) { onFabExpandedChange(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(stringResource(R.string.nutritionTitle)) })
            },
        ) { padding ->
            if (plans.isNotEmpty() && entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (entries.isEmpty()) {
                NutritionEmptyState(
                    onCreate = { showCreateDialog = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            } else {
                val active = entries.filter { it.status == NutritionPlanStatus.ACTIVE }
                val completed = entries.filter { it.status == NutritionPlanStatus.COMPLETED }
                val saved = entries.filter { it.status == NutritionPlanStatus.SAVED }
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = padding.calculateTopPadding() + 12.dp,
                        end = 16.dp,
                        bottom = padding.calculateBottomPadding() + 112.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (active.isNotEmpty()) {
                        item(key = "nutrition_active_header") {
                            NutritionSectionHeader(stringResource(R.string.nutritionActivePlans))
                        }
                        items(active, key = { "nutrition_active_${it.plan.id}" }) { entry ->
                            NutritionTimelineCard(
                                entry = entry,
                                onClick = { onOpenPlan(entry.plan.id) },
                            )
                        }
                    }
                    if (saved.isNotEmpty()) {
                        item(key = "nutrition_saved_header") {
                            NutritionSectionHeader(stringResource(R.string.nutritionMyPlans))
                        }
                        items(saved, key = { "nutrition_saved_${it.plan.id}" }) { entry ->
                            NutritionSavedPlanCard(
                                entry = entry,
                                onClick = { onOpenPlan(entry.plan.id) },
                            )
                        }
                    }
                    if (completed.isNotEmpty()) {
                        item(key = "nutrition_completed_header") {
                            NutritionSectionHeader(stringResource(R.string.nutritionCompletedPlans))
                        }
                        items(completed, key = { "nutrition_completed_${it.plan.id}" }) { entry ->
                            NutritionTimelineCard(
                                entry = entry,
                                onClick = { onOpenPlan(entry.plan.id) },
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = fabExpanded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .matchParentSize()
                .zIndex(1f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onFabExpandedChange(false) },
                    ),
            )
        }

        NutritionSpeedDial(
            expanded = fabExpanded,
            onToggle = { onFabExpandedChange(!fabExpanded) },
            onCreate = {
                onFabExpandedChange(false)
                showCreateDialog = true
            },
            onImport = {
                onFabExpandedChange(false)
                importLauncher.launch(
                    arrayOf(
                        "application/json",
                        "text/json",
                        "text/plain",
                        "application/octet-stream",
                    )
                )
            },
            onAiPrompt = {
                onFabExpandedChange(false)
                onAiPrompt()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .zIndex(2f),
        )
    }

    if (showCreateDialog) {
        CreateNutritionPlanDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, objective ->
                val now = System.currentTimeMillis()
                val id = dao.insertPlanWithFirstDay(
                    NutritionPlan(
                        uuid = UUID.randomUUID().toString(),
                        name = name,
                        objective = objective,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
                showCreateDialog = false
                onPlanCreated(id)
            },
        )
    }

    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text(stringResource(R.string.nutritionImportFailed)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { importError = null }) {
                    Text(stringResource(R.string.okay))
                }
            },
        )
    }
}

private data class NutritionPlanListEntry(
    val plan: NutritionPlan,
    val dayCount: Int,
    val dayIndex: Int?,
    val mealCount: Int,
    val energyKcal: Double?,
    val status: NutritionPlanStatus,
)

@Composable
private fun NutritionTimelineCard(
    entry: NutritionPlanListEntry,
    onClick: () -> Unit,
) {
    val completed = entry.status == NutritionPlanStatus.COMPLETED
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NutritionPlanIconBadge(size = 44.dp, iconSize = 23.dp)
                    NutritionStatusBadge(
                        text = stringResource(
                            if (completed) {
                                R.string.nutritionCompleted
                            } else {
                                R.string.nutritionInProgress
                            }
                        ),
                        emphasized = !completed,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            NutritionPlanText(entry.plan)
            NutritionDaySummary(entry)
            if (entry.dayIndex != null && entry.dayCount > 0) {
                val progress = (entry.dayIndex + 1f) / entry.dayCount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(
                            R.string.nutritionDayOfPlan,
                            entry.dayIndex + 1,
                            entry.dayCount,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        }
    }
}

@Composable
private fun NutritionSavedPlanCard(
    entry: NutritionPlanListEntry,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NutritionPlanIconBadge()
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NutritionPlanText(entry.plan, compact = true)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (entry.dayCount > 0) {
                        Text(
                            pluralStringResource(
                                R.plurals.nutritionDaysCount,
                                entry.dayCount,
                                entry.dayCount,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    NutritionStatusBadge(stringResource(R.string.nutritionNotStarted))
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NutritionPlanText(plan: NutritionPlan, compact: Boolean = false) {
    Text(
        text = plan.name,
        style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    plan.description?.takeIf { it.isNotBlank() }?.let { description ->
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NutritionDaySummary(entry: NutritionPlanListEntry) {
    val parts = buildList {
        if (entry.mealCount > 0) {
            add(stringResource(R.string.nutritionMealCount, entry.mealCount))
        }
        entry.energyKcal?.let {
            add("${nutritionDecimal(it)} ${stringResource(R.string.nutritionKcalShort)}")
        }
    }
    if (parts.isNotEmpty()) {
        Text(
            text = parts.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NutritionStatusBadge(text: String, emphasized: Boolean = false) {
    Surface(
        shape = CircleShape,
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (emphasized) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (emphasized) {
                Surface(
                    modifier = Modifier.size(6.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {}
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun NutritionEmptyState(
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(88.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_nutrition_24),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.nutritionEmptyTitle),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.nutritionEmptyDescription),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.nutritionCreatePlan))
            }
        }
    }
}

@Composable
private fun CreateNutritionPlanDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, objective: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var objective by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutritionCreatePlan)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        if (it.length <= NUTRITION_MAX_NAME_LENGTH) name = it
                        nameError = false
                    },
                    label = { Text(stringResource(R.string.nutritionPlanName)) },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.nutritionEnterPlanName)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = objective,
                    onValueChange = {
                        if (it.length <= NUTRITION_MAX_TEXT_LENGTH) objective = it
                    },
                    label = { Text(stringResource(R.string.nutritionObjectiveOptional)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.trim().length !in 2..NUTRITION_MAX_NAME_LENGTH) {
                    nameError = true
                } else {
                    onCreate(name.trim(), objective.trim().ifBlank { null })
                }
            }) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
