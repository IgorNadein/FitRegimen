/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.ui.nutrition

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.NutritionDay
import de.tadris.fitness.data.NutritionItem
import de.tadris.fitness.data.NutritionMeal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class NutritionDayEditorSnapshot(
    val day: NutritionDay,
    val meals: List<NutritionMeal>,
    val itemsByMeal: Map<Long, List<NutritionItem>>,
)

private const val NUTRITION_TIME_INPUT_MAX_LENGTH = 5
private val NUTRITION_PARTIAL_TIME_PATTERN = Regex("^(?:\\d{0,2}|\\d{2}:\\d{0,2})$")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionDayEditorScreen(
    dayId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val db = remember { Instance.getInstance(context).db }
    val dao = remember(db) { db.nutritionPlanDao() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val mutationRevision by NutritionTreeMutationGuard.revision.collectAsState()
    var refreshKey by remember { mutableIntStateOf(0) }
    var snapshot by remember(dayId) { mutableStateOf<NutritionDayEditorSnapshot?>(null) }

    LaunchedEffect(dayId, refreshKey, mutationRevision) {
        snapshot = withContext(Dispatchers.IO) {
            val loadedDay = dao.findDayById(dayId) ?: return@withContext null
            val loadedMeals = dao.getMealsOfDay(dayId)
            NutritionDayEditorSnapshot(
                day = loadedDay,
                meals = loadedMeals,
                itemsByMeal = loadedMeals.associate { meal ->
                    meal.id to dao.getItemsOfMeal(meal.id)
                },
            )
        }
    }

    val loaded = snapshot ?: return
    val current = loaded.day
    val meals = loaded.meals
    val itemsByMeal = loaded.itemsByMeal
    val actualTotals = remember(itemsByMeal) { NutritionTotals.from(itemsByMeal.values.flatten()) }

    var name by rememberSaveable(dayId) { mutableStateOf(current.name.orEmpty()) }
    var notes by rememberSaveable(dayId) { mutableStateOf(current.notes.orEmpty()) }
    var energyKcal by rememberSaveable(dayId) { mutableStateOf(current.energyKcal.editableNumber()) }
    var proteinGrams by rememberSaveable(dayId) { mutableStateOf(current.proteinGrams.editableNumber()) }
    var fatGrams by rememberSaveable(dayId) { mutableStateOf(current.fatGrams.editableNumber()) }
    var carbsGrams by rememberSaveable(dayId) { mutableStateOf(current.carbsGrams.editableNumber()) }
    var fiberGrams by rememberSaveable(dayId) { mutableStateOf(current.fiberGrams.editableNumber()) }
    var waterMl by rememberSaveable(dayId) { mutableStateOf(current.waterMl.editableNumber()) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var mealEditorTarget by remember { mutableStateOf<MealEditorTarget?>(null) }
    var itemEditorTarget by remember { mutableStateOf<ItemEditorTarget?>(null) }
    val mutationInProgress by NutritionTreeMutationGuard.inProgress.collectAsState()

    fun refresh() {
        refreshKey++
    }

    fun touchPlan() {
        dao.touchPlan(current.planId)
    }

    val isDirty = name.trim().ifBlank { null } != current.name ||
        notes.trim().ifBlank { null } != current.notes ||
        nutritionNumber(energyKcal) != current.energyKcal ||
        nutritionNumber(proteinGrams) != current.proteinGrams ||
        nutritionNumber(fatGrams) != current.fatGrams ||
        nutritionNumber(carbsGrams) != current.carbsGrams ||
        nutritionNumber(fiberGrams) != current.fiberGrams ||
        nutritionNumber(waterMl) != current.waterMl ||
        listOf(energyKcal, proteinGrams, fatGrams, carbsGrams, fiberGrams, waterMl).any {
            it.isNotBlank() && nutritionNumber(it) == null
        }

    fun targetsAreValid(): Boolean =
        name.length <= NUTRITION_MAX_NAME_LENGTH &&
            notes.length <= NUTRITION_MAX_TEXT_LENGTH &&
            (energyKcal.isBlank() || nutritionNumber(
                energyKcal,
                NUTRITION_MAX_ENERGY_KCAL,
            ) != null) &&
            listOf(proteinGrams, fatGrams, carbsGrams, fiberGrams).all {
                it.isBlank() || nutritionNumber(it, NUTRITION_MAX_MACRO_GRAMS) != null
            } &&
            (waterMl.isBlank() || nutritionNumber(
                waterMl,
                NUTRITION_MAX_WATER_OR_WEIGHT,
            ) != null)

    fun <T> runMutation(
        mutation: () -> T,
        onSuccess: suspend (T) -> Unit = {},
    ): Boolean {
        if (mutationInProgress || !NutritionTreeMutationGuard.tryAcquire()) return false
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val outcome = try {
                runCatching {
                    withContext(Dispatchers.IO) { mutation() }
                }
            } finally {
                NutritionTreeMutationGuard.release()
            }

            val failure = outcome.exceptionOrNull()
            when {
                failure is CancellationException -> throw failure
                failure is NutritionUserVisibleException -> snackbarHostState.showSnackbar(
                    failure.message ?: context.getString(R.string.error)
                )
                failure != null -> snackbarHostState.showSnackbar(context.getString(R.string.error))
                else -> {
                    try {
                        onSuccess(outcome.getOrThrow())
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        snackbarHostState.showSnackbar(context.getString(R.string.error))
                    }
                }
            }
        }
        return true
    }

    fun saveDay(
        showMessage: Boolean = true,
        onSaved: () -> Unit = {},
    ): Boolean {
        if (!targetsAreValid()) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.nutritionCheckNumericValues))
            }
            return false
        }
        val updatedValues: (NutritionDay) -> NutritionDay = { fresh ->
            fresh.copy(
                    name = name.trim().ifBlank { null },
                    notes = notes.trim().ifBlank { null },
                    energyKcal = nutritionNumber(energyKcal, NUTRITION_MAX_ENERGY_KCAL),
                    proteinGrams = nutritionNumber(proteinGrams, NUTRITION_MAX_MACRO_GRAMS),
                    fatGrams = nutritionNumber(fatGrams, NUTRITION_MAX_MACRO_GRAMS),
                    carbsGrams = nutritionNumber(carbsGrams, NUTRITION_MAX_MACRO_GRAMS),
                    fiberGrams = nutritionNumber(fiberGrams, NUTRITION_MAX_MACRO_GRAMS),
                    waterMl = nutritionNumber(waterMl, NUTRITION_MAX_WATER_OR_WEIGHT),
                )
        }
        return runMutation(
            mutation = {
                db.runInTransaction {
                    val freshDay = checkNotNull(dao.findDayById(dayId))
                    dao.updateDay(updatedValues(freshDay))
                    dao.touchPlan(freshDay.planId)
                }
            },
            onSuccess = {
                refresh()
                onSaved()
                if (showMessage) {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.nutritionChangesSaved)
                    )
                }
            },
        )
    }

    fun requestBack() {
        if (mutationInProgress) return
        if (isDirty) showDiscardDialog = true else onBack()
    }

    fun moveMeal(mealId: Long, offset: Int) {
        if (mutationInProgress) return
        runMutation(
            mutation = {
                db.runInTransaction {
                    val freshMeals = dao.getMealsOfDay(dayId)
                    val from = freshMeals.indexOfFirst { it.id == mealId }
                    val to = from + offset
                    check(from >= 0 && to in freshMeals.indices)
                    val reordered = freshMeals.toMutableList().apply { add(to, removeAt(from)) }
                    dao.reorderMeals(dayId, reordered)
                    touchPlan()
                }
            },
            onSuccess = { refresh() },
        )
    }

    fun duplicateMeal(meal: NutritionMeal) {
        if (mutationInProgress) return
        if (meals.size >= NUTRITION_MAX_MEALS_PER_DAY) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.nutritionMaximumMealsReached))
            }
            return
        }
        val maximumMealsMessage = context.getString(R.string.nutritionMaximumMealsReached)
        runMutation(
            mutation = {
                db.runInTransaction {
                    val freshMeals = dao.getMealsOfDay(dayId)
                    if (freshMeals.size >= NUTRITION_MAX_MEALS_PER_DAY) {
                        throw NutritionUserVisibleException(maximumMealsMessage)
                    }
                    val freshMeal = checkNotNull(freshMeals.firstOrNull { it.id == meal.id })
                    val newMealId = dao.insertMeal(
                        freshMeal.copy(
                            id = 0,
                            orderIndex = freshMeals.size,
                            name = context.getString(R.string.nutritionCopyName, freshMeal.name)
                                .take(NUTRITION_MAX_NAME_LENGTH),
                        )
                    )
                    dao.getItemsOfMeal(freshMeal.id).forEach { item ->
                        dao.insertItem(item.copy(id = 0, mealId = newMealId))
                    }
                    touchPlan()
                }
            },
            onSuccess = { refresh() },
        )
    }

    fun deleteMeal(meal: NutritionMeal) {
        if (mutationInProgress) return
        runMutation(
            mutation = {
                db.runInTransaction {
                    val freshMeals = dao.getMealsOfDay(dayId)
                    val freshMeal = checkNotNull(freshMeals.firstOrNull { it.id == meal.id })
                    dao.deleteMeal(freshMeal)
                    dao.reorderMeals(dayId, freshMeals.filterNot { it.id == freshMeal.id })
                    touchPlan()
                }
            },
            onSuccess = { refresh() },
        )
    }

    fun moveItem(mealId: Long, itemId: Long, offset: Int) {
        if (mutationInProgress) return
        runMutation(
            mutation = {
                db.runInTransaction {
                    val freshItems = dao.getItemsOfMeal(mealId)
                    val from = freshItems.indexOfFirst { it.id == itemId }
                    val to = from + offset
                    check(from >= 0 && to in freshItems.indices)
                    val reordered = freshItems.toMutableList().apply { add(to, removeAt(from)) }
                    dao.reorderItems(mealId, reordered)
                    touchPlan()
                }
            },
            onSuccess = { refresh() },
        )
    }

    fun deleteItem(meal: NutritionMeal, item: NutritionItem) {
        if (mutationInProgress) return
        runMutation(
            mutation = {
                db.runInTransaction {
                    val freshItems = dao.getItemsOfMeal(meal.id)
                    val freshItem = checkNotNull(freshItems.firstOrNull { it.id == item.id })
                    dao.deleteItem(freshItem)
                    dao.reorderItems(meal.id, freshItems.filterNot { it.id == freshItem.id })
                    touchPlan()
                }
            },
            onSuccess = { refresh() },
        )
    }

    fun requestAddMeal() {
        if (mutationInProgress) return
        if (meals.size >= NUTRITION_MAX_MEALS_PER_DAY) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.nutritionMaximumMealsReached))
            }
        } else {
            mealEditorTarget = MealEditorTarget(null)
        }
    }

    fun requestAddItem(meal: NutritionMeal) {
        if (mutationInProgress) return
        if (itemsByMeal[meal.id].orEmpty().size >= NUTRITION_MAX_ITEMS_PER_MEAL) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.nutritionMaximumItemsReached))
            }
        } else {
            itemEditorTarget = ItemEditorTarget(meal, null)
        }
    }

    BackHandler { requestBack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        current.name?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.nutritionDayNumber, current.orderIndex + 1),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = ::requestBack,
                        enabled = !mutationInProgress,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.actionBack),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { saveDay() },
                        enabled = !mutationInProgress,
                    ) {
                        Text(stringResource(R.string.nutritionSave))
                    }
                },
            )
        },
        floatingActionButton = {
            if (!mutationInProgress) {
                ExtendedFloatingActionButton(
                    onClick = ::requestAddMeal,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.nutritionAddMeal)) },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "nutrition_day_targets") {
                NutritionDayTargetsCard(
                    enabled = !mutationInProgress,
                    name = name,
                    onNameChange = {
                        if (it.length <= NUTRITION_MAX_NAME_LENGTH) name = it
                    },
                    notes = notes,
                    onNotesChange = {
                        if (it.length <= NUTRITION_MAX_TEXT_LENGTH) notes = it
                    },
                    energyKcal = energyKcal,
                    onEnergyKcalChange = { energyKcal = it },
                    proteinGrams = proteinGrams,
                    onProteinGramsChange = { proteinGrams = it },
                    fatGrams = fatGrams,
                    onFatGramsChange = { fatGrams = it },
                    carbsGrams = carbsGrams,
                    onCarbsGramsChange = { carbsGrams = it },
                    fiberGrams = fiberGrams,
                    onFiberGramsChange = { fiberGrams = it },
                    waterMl = waterMl,
                    onWaterMlChange = { waterMl = it },
                    actualTotals = actualTotals,
                )
            }

            item(key = "nutrition_meals_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
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
                item(key = "nutrition_no_meals") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                stringResource(R.string.nutritionNoMeals),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FilledTonalButton(
                                onClick = ::requestAddMeal,
                                enabled = !mutationInProgress,
                            ) {
                                Text(stringResource(R.string.nutritionAddMeal))
                            }
                        }
                    }
                }
            }

            meals.forEachIndexed { mealIndex, meal ->
                item(key = "nutrition_edit_meal_${meal.id}") {
                    NutritionEditableMealCard(
                        meal = meal,
                        items = itemsByMeal[meal.id].orEmpty(),
                        enabled = !mutationInProgress,
                        canMoveUp = mealIndex > 0,
                        canMoveDown = mealIndex < meals.lastIndex,
                        onEdit = { mealEditorTarget = MealEditorTarget(meal) },
                        onDuplicate = { duplicateMeal(meal) },
                        onMoveUp = { moveMeal(meal.id, -1) },
                        onMoveDown = { moveMeal(meal.id, 1) },
                        onDelete = { deleteMeal(meal) },
                        onAddItem = { requestAddItem(meal) },
                        onEditItem = { item -> itemEditorTarget = ItemEditorTarget(meal, item) },
                        onMoveItemUp = { item -> moveItem(meal.id, item.id, -1) },
                        onMoveItemDown = { item -> moveItem(meal.id, item.id, 1) },
                        onDeleteItem = { item -> deleteItem(meal, item) },
                    )
                }
            }
        }
    }

    mealEditorTarget?.let { target ->
        NutritionMealEditorDialog(
            existing = target.existing,
            enabled = !mutationInProgress,
            onDismiss = {
                if (!mutationInProgress) mealEditorTarget = null
            },
            onSave = { nameValue, timeMinutes, notesValue ->
                val maximumMealsMessage = context.getString(
                    R.string.nutritionMaximumMealsReached
                )
                runMutation(
                    mutation = {
                        db.runInTransaction {
                            val freshMeals = dao.getMealsOfDay(dayId)
                            if (target.existing == null) {
                                if (freshMeals.size >= NUTRITION_MAX_MEALS_PER_DAY) {
                                    throw NutritionUserVisibleException(maximumMealsMessage)
                                }
                                dao.insertMeal(
                                    NutritionMeal(
                                        dayId = dayId,
                                        orderIndex = freshMeals.size,
                                        name = nameValue,
                                        plannedTimeMinutes = timeMinutes,
                                        notes = notesValue,
                                    )
                                )
                            } else {
                                val freshMeal = checkNotNull(
                                    freshMeals.firstOrNull { it.id == target.existing.id }
                                )
                                dao.updateMeal(
                                    freshMeal.copy(
                                        name = nameValue,
                                        plannedTimeMinutes = timeMinutes,
                                        notes = notesValue,
                                    )
                                )
                            }
                            touchPlan()
                        }
                    },
                    onSuccess = {
                        mealEditorTarget = null
                        refresh()
                    },
                )
            },
        )
    }

    itemEditorTarget?.let { target ->
        NutritionItemEditorDialog(
            existing = target.existing,
            enabled = !mutationInProgress,
            onDismiss = {
                if (!mutationInProgress) itemEditorTarget = null
            },
            onSave = { draft ->
                val maximumItemsMessage = context.getString(
                    R.string.nutritionMaximumItemsReached
                )
                runMutation(
                    mutation = {
                        db.runInTransaction {
                            val freshItems = dao.getItemsOfMeal(target.meal.id)
                            if (target.existing == null) {
                                if (freshItems.size >= NUTRITION_MAX_ITEMS_PER_MEAL) {
                                    throw NutritionUserVisibleException(maximumItemsMessage)
                                }
                                dao.insertItem(
                                    draft.toItem(
                                        mealId = target.meal.id,
                                        orderIndex = freshItems.size,
                                    )
                                )
                            } else {
                                val freshItem = checkNotNull(
                                    freshItems.firstOrNull { it.id == target.existing.id }
                                )
                                dao.updateItem(
                                    draft.toItem(
                                        mealId = target.meal.id,
                                        orderIndex = freshItem.orderIndex,
                                        id = freshItem.id,
                                    )
                                )
                            }
                            touchPlan()
                        }
                    },
                    onSuccess = {
                        itemEditorTarget = null
                        refresh()
                    },
                )
            },
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.nutritionUnsavedChanges)) },
            text = { Text(stringResource(R.string.nutritionUnsavedChangesMessage)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) {
                    Text(stringResource(R.string.nutritionDiscard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private data class MealEditorTarget(val existing: NutritionMeal?)

private data class ItemEditorTarget(
    val meal: NutritionMeal,
    val existing: NutritionItem?,
)

private data class NutritionTargetInput(
    val value: String,
    val onValueChange: (String) -> Unit,
    val label: String,
    val suffix: String,
    val maximum: Double,
)

@Composable
private fun NutritionDayTargetsCard(
    enabled: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    energyKcal: String,
    onEnergyKcalChange: (String) -> Unit,
    proteinGrams: String,
    onProteinGramsChange: (String) -> Unit,
    fatGrams: String,
    onFatGramsChange: (String) -> Unit,
    carbsGrams: String,
    onCarbsGramsChange: (String) -> Unit,
    fiberGrams: String,
    onFiberGramsChange: (String) -> Unit,
    waterMl: String,
    onWaterMlChange: (String) -> Unit,
    actualTotals: NutritionTotals,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.nutritionDayTargets),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.nutritionDayNameOptional)) },
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val targetInputs = listOf(
                NutritionTargetInput(
                    energyKcal,
                    onEnergyKcalChange,
                    stringResource(R.string.nutritionCalories),
                    stringResource(R.string.nutritionKcalShort),
                    NUTRITION_MAX_ENERGY_KCAL,
                ),
                NutritionTargetInput(
                    proteinGrams,
                    onProteinGramsChange,
                    stringResource(R.string.nutritionProtein),
                    stringResource(R.string.nutritionGramShort),
                    NUTRITION_MAX_MACRO_GRAMS,
                ),
                NutritionTargetInput(
                    fatGrams,
                    onFatGramsChange,
                    stringResource(R.string.nutritionFat),
                    stringResource(R.string.nutritionGramShort),
                    NUTRITION_MAX_MACRO_GRAMS,
                ),
                NutritionTargetInput(
                    carbsGrams,
                    onCarbsGramsChange,
                    stringResource(R.string.nutritionCarbs),
                    stringResource(R.string.nutritionGramShort),
                    NUTRITION_MAX_MACRO_GRAMS,
                ),
                NutritionTargetInput(
                    fiberGrams,
                    onFiberGramsChange,
                    stringResource(R.string.nutritionFiber),
                    stringResource(R.string.nutritionGramShort),
                    NUTRITION_MAX_MACRO_GRAMS,
                ),
                NutritionTargetInput(
                    waterMl,
                    onWaterMlChange,
                    stringResource(R.string.nutritionWater),
                    stringResource(R.string.nutritionMilliliterShort),
                    NUTRITION_MAX_WATER_OR_WEIGHT,
                ),
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columnCount = if (maxWidth >= 280.dp) 2 else 1
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    targetInputs.chunked(columnCount).forEach { rowInputs ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowInputs.forEach { input ->
                                NutritionTargetField(
                                    value = input.value,
                                    onValueChange = input.onValueChange,
                                    label = input.label,
                                    suffix = input.suffix,
                                    maximum = input.maximum,
                                    enabled = enabled,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowInputs.size < columnCount) {
                                repeat(columnCount - rowInputs.size) {
                                    Box(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                label = { Text(stringResource(R.string.nutritionDayNotesOptional)) },
                enabled = enabled,
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            if (actualTotals != NutritionTotals()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    stringResource(R.string.nutritionCalculatedFromItems),
                    style = MaterialTheme.typography.titleSmall,
                )
                NutritionMetricGrid(
                    NutritionMetricValues(
                        energyKcal = actualTotals.energyKcal,
                        proteinGrams = actualTotals.proteinGrams,
                        fatGrams = actualTotals.fatGrams,
                        carbsGrams = actualTotals.carbsGrams,
                        fiberGrams = actualTotals.fiberGrams,
                        waterMl = null,
                    )
                )
            }
        }
    }
}

@Composable
private fun NutritionTargetField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suffix: String,
    maximum: Double,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(NUTRITION_MAX_NUMERIC_INPUT_LENGTH)) },
        label = { Text(label) },
        suffix = { Text(suffix) },
        enabled = enabled,
        singleLine = true,
        isError = value.isNotBlank() && nutritionNumber(value, maximum) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun NutritionEditableMealCard(
    meal: NutritionMeal,
    items: List<NutritionItem>,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (NutritionItem) -> Unit,
    onMoveItemUp: (NutritionItem) -> Unit,
    onMoveItemDown: (NutritionItem) -> Unit,
    onDeleteItem: (NutritionItem) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val totals = remember(items) { NutritionTotals.from(items) }
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    if (totals.energyKcal > 0.0) {
                        Text(
                            "${nutritionDecimal(totals.energyKcal)} ${stringResource(R.string.nutritionKcalShort)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        enabled = enabled,
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.nutritionMoreMealActions),
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.nutritionEditMeal)) },
                            enabled = enabled,
                            onClick = { menuOpen = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.nutritionDuplicateMeal)) },
                            enabled = enabled,
                            onClick = { menuOpen = false; onDuplicate() },
                        )
                        if (canMoveUp) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.moveUp)) },
                                enabled = enabled,
                                onClick = { menuOpen = false; onMoveUp() },
                            )
                        }
                        if (canMoveDown) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.moveDown)) },
                                enabled = enabled,
                                onClick = { menuOpen = false; onMoveDown() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.nutritionDeleteMeal)) },
                            enabled = enabled,
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }

            meal.notes?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                    NutritionEditableItemRow(
                        item = item,
                        enabled = enabled,
                        canMoveUp = index > 0,
                        canMoveDown = index < items.lastIndex,
                        onEdit = { onEditItem(item) },
                        onMoveUp = { onMoveItemUp(item) },
                        onMoveDown = { onMoveItemDown(item) },
                        onDelete = { onDeleteItem(item) },
                    )
                }
            }

            OutlinedButton(
                onClick = onAddItem,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(
                    stringResource(R.string.nutritionAddItem),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun NutritionEditableItemRow(
    item: NutritionItem,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = onEdit,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                val details = buildList {
                    item.quantity?.let { quantity ->
                        add(
                            buildString {
                                append(nutritionDecimal(quantity))
                                item.unit?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
                            }
                        )
                    }
                    item.weightGrams?.let {
                        add("${nutritionDecimal(it)} ${stringResource(R.string.nutritionGramShort)}")
                    }
                    item.energyKcal?.let {
                        add("${nutritionDecimal(it)} ${stringResource(R.string.nutritionKcalShort)}")
                    }
                }
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box {
            IconButton(
                onClick = { menuOpen = true },
                enabled = enabled,
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.nutritionMoreItemActions),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nutritionEditItem)) },
                    enabled = enabled,
                    onClick = { menuOpen = false; onEdit() },
                )
                if (canMoveUp) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.moveUp)) },
                        enabled = enabled,
                        onClick = { menuOpen = false; onMoveUp() },
                    )
                }
                if (canMoveDown) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.moveDown)) },
                        enabled = enabled,
                        onClick = { menuOpen = false; onMoveDown() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nutritionDeleteItem)) },
                    enabled = enabled,
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun NutritionMealEditorDialog(
    existing: NutritionMeal?,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, timeMinutes: Int?, notes: String?) -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var time by remember(existing?.id) {
        mutableStateOf(nutritionTimeText(existing?.plannedTimeMinutes).orEmpty())
    }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var showErrors by remember { mutableStateOf(false) }
    val validTime = time.isBlank() || nutritionTimeMinutes(time) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (existing == null) R.string.nutritionAddMeal else R.string.nutritionEditMeal
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        if (it.length <= NUTRITION_MAX_NAME_LENGTH) name = it
                    },
                    label = { Text(stringResource(R.string.nutritionMealName)) },
                    enabled = enabled,
                    singleLine = true,
                    isError = showErrors && name.trim().length < 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { candidate ->
                        if (candidate.length <= NUTRITION_TIME_INPUT_MAX_LENGTH &&
                            NUTRITION_PARTIAL_TIME_PATTERN.matches(candidate)
                        ) {
                            time = candidate
                        }
                    },
                    label = { Text(stringResource(R.string.nutritionMealTimeOptional)) },
                    enabled = enabled,
                    placeholder = { Text("08:30") },
                    singleLine = true,
                    isError = showErrors && !validTime,
                    supportingText = if (showErrors && !validTime) {
                        { Text(stringResource(R.string.nutritionTimeFormat)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = {
                        if (it.length <= NUTRITION_MAX_TEXT_LENGTH) notes = it
                    },
                    label = { Text(stringResource(R.string.nutritionMealNotesOptional)) },
                    enabled = enabled,
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    showErrors = true
                    if (name.trim().length >= 2 && validTime) {
                        onSave(
                            name.trim(),
                            time.takeIf { it.isNotBlank() }?.let(::nutritionTimeMinutes),
                            notes.trim().ifBlank { null },
                        )
                    }
                },
                enabled = enabled,
            ) {
                Text(stringResource(R.string.nutritionSave))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = enabled) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private data class NutritionItemDraft(
    val name: String,
    val quantity: String,
    val unit: String,
    val weightGrams: String,
    val energyKcal: String,
    val proteinGrams: String,
    val fatGrams: String,
    val carbsGrams: String,
    val fiberGrams: String,
    val notes: String,
) {
    fun isValid(): Boolean = name.trim().length in 2..NUTRITION_MAX_NAME_LENGTH &&
        unit.length <= NUTRITION_MAX_UNIT_LENGTH &&
        notes.length <= NUTRITION_MAX_TEXT_LENGTH &&
        (quantity.isBlank() || nutritionNumber(quantity, NUTRITION_MAX_QUANTITY) != null) &&
        (weightGrams.isBlank() || nutritionNumber(
            weightGrams,
            NUTRITION_MAX_WATER_OR_WEIGHT,
        ) != null) &&
        (energyKcal.isBlank() || nutritionNumber(
            energyKcal,
            NUTRITION_MAX_ENERGY_KCAL,
        ) != null) &&
        listOf(proteinGrams, fatGrams, carbsGrams, fiberGrams).all {
            it.isBlank() || nutritionNumber(it, NUTRITION_MAX_MACRO_GRAMS) != null
        }

    fun toItem(mealId: Long, orderIndex: Int, id: Long = 0) = NutritionItem(
        mealId = mealId,
        id = id,
        orderIndex = orderIndex,
        name = name.trim(),
        quantity = nutritionNumber(quantity, NUTRITION_MAX_QUANTITY),
        unit = unit.trim().ifBlank { null },
        weightGrams = nutritionNumber(weightGrams, NUTRITION_MAX_WATER_OR_WEIGHT),
        energyKcal = nutritionNumber(energyKcal, NUTRITION_MAX_ENERGY_KCAL),
        proteinGrams = nutritionNumber(proteinGrams, NUTRITION_MAX_MACRO_GRAMS),
        fatGrams = nutritionNumber(fatGrams, NUTRITION_MAX_MACRO_GRAMS),
        carbsGrams = nutritionNumber(carbsGrams, NUTRITION_MAX_MACRO_GRAMS),
        fiberGrams = nutritionNumber(fiberGrams, NUTRITION_MAX_MACRO_GRAMS),
        notes = notes.trim().ifBlank { null },
    )

    companion object {
        fun from(item: NutritionItem?) = NutritionItemDraft(
            name = item?.name.orEmpty(),
            quantity = item?.quantity.editableNumber(),
            unit = item?.unit.orEmpty(),
            weightGrams = item?.weightGrams.editableNumber(),
            energyKcal = item?.energyKcal.editableNumber(),
            proteinGrams = item?.proteinGrams.editableNumber(),
            fatGrams = item?.fatGrams.editableNumber(),
            carbsGrams = item?.carbsGrams.editableNumber(),
            fiberGrams = item?.fiberGrams.editableNumber(),
            notes = item?.notes.orEmpty(),
        )
    }
}

@Composable
private fun NutritionAdaptiveFieldPair(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= 280.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) { first() }
                Box(modifier = Modifier.weight(1f)) { second() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                first()
                second()
            }
        }
    }
}

@Composable
private fun NutritionItemEditorDialog(
    existing: NutritionItem?,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (NutritionItemDraft) -> Unit,
) {
    var draft by remember(existing?.id) { mutableStateOf(NutritionItemDraft.from(existing)) }
    var showErrors by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (existing == null) R.string.nutritionAddItem else R.string.nutritionEditItem
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = {
                        if (it.length <= NUTRITION_MAX_NAME_LENGTH) draft = draft.copy(name = it)
                    },
                    label = { Text(stringResource(R.string.nutritionItemName)) },
                    enabled = enabled,
                    singleLine = true,
                    isError = showErrors && draft.name.trim().length < 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                NutritionAdaptiveFieldPair(
                    first = {
                        NutritionDraftNumberField(
                            value = draft.quantity,
                            onValueChange = { draft = draft.copy(quantity = it) },
                            label = stringResource(R.string.nutritionQuantityOptional),
                            showError = showErrors,
                            maximum = NUTRITION_MAX_QUANTITY,
                            enabled = enabled,
                        )
                    },
                    second = {
                        OutlinedTextField(
                            value = draft.unit,
                            onValueChange = {
                                if (it.length <= NUTRITION_MAX_UNIT_LENGTH) {
                                    draft = draft.copy(unit = it)
                                }
                            },
                            label = { Text(stringResource(R.string.nutritionUnitOptional)) },
                            enabled = enabled,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
                NutritionAdaptiveFieldPair(
                    first = {
                        NutritionDraftNumberField(
                            value = draft.weightGrams,
                            onValueChange = { draft = draft.copy(weightGrams = it) },
                            label = stringResource(R.string.nutritionWeightGramsOptional),
                            suffix = stringResource(R.string.nutritionGramShort),
                            showError = showErrors,
                            maximum = NUTRITION_MAX_WATER_OR_WEIGHT,
                            enabled = enabled,
                        )
                    },
                    second = {
                        NutritionDraftNumberField(
                            value = draft.energyKcal,
                            onValueChange = { draft = draft.copy(energyKcal = it) },
                            label = stringResource(R.string.nutritionCaloriesOptional),
                            suffix = stringResource(R.string.nutritionKcalShort),
                            showError = showErrors,
                            maximum = NUTRITION_MAX_ENERGY_KCAL,
                            enabled = enabled,
                        )
                    },
                )
                NutritionAdaptiveFieldPair(
                    first = {
                        NutritionDraftNumberField(
                            value = draft.proteinGrams,
                            onValueChange = { draft = draft.copy(proteinGrams = it) },
                            label = stringResource(R.string.nutritionProteinOptional),
                            suffix = stringResource(R.string.nutritionGramShort),
                            showError = showErrors,
                            maximum = NUTRITION_MAX_MACRO_GRAMS,
                            enabled = enabled,
                        )
                    },
                    second = {
                        NutritionDraftNumberField(
                            value = draft.fatGrams,
                            onValueChange = { draft = draft.copy(fatGrams = it) },
                            label = stringResource(R.string.nutritionFatOptional),
                            suffix = stringResource(R.string.nutritionGramShort),
                            showError = showErrors,
                            maximum = NUTRITION_MAX_MACRO_GRAMS,
                            enabled = enabled,
                        )
                    },
                )
                NutritionAdaptiveFieldPair(
                    first = {
                        NutritionDraftNumberField(
                            value = draft.carbsGrams,
                            onValueChange = { draft = draft.copy(carbsGrams = it) },
                            label = stringResource(R.string.nutritionCarbsOptional),
                            suffix = stringResource(R.string.nutritionGramShort),
                            showError = showErrors,
                            maximum = NUTRITION_MAX_MACRO_GRAMS,
                            enabled = enabled,
                        )
                    },
                    second = {
                        NutritionDraftNumberField(
                            value = draft.fiberGrams,
                            onValueChange = { draft = draft.copy(fiberGrams = it) },
                            label = stringResource(R.string.nutritionFiberOptional),
                            suffix = stringResource(R.string.nutritionGramShort),
                            showError = showErrors,
                            maximum = NUTRITION_MAX_MACRO_GRAMS,
                            enabled = enabled,
                        )
                    },
                )
                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = {
                        if (it.length <= NUTRITION_MAX_TEXT_LENGTH) draft = draft.copy(notes = it)
                    },
                    label = { Text(stringResource(R.string.nutritionItemNotesOptional)) },
                    enabled = enabled,
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showErrors && !draft.isValid()) {
                    Text(
                        stringResource(R.string.nutritionCheckItemFields),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    showErrors = true
                    if (draft.isValid()) onSave(draft)
                },
                enabled = enabled,
            ) {
                Text(stringResource(R.string.nutritionSave))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = enabled) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun NutritionDraftNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    showError: Boolean,
    maximum: Double,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    suffix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(NUTRITION_MAX_NUMERIC_INPUT_LENGTH)) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        enabled = enabled,
        singleLine = true,
        isError = showError && value.isNotBlank() && nutritionNumber(value, maximum) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

private fun Double?.editableNumber(): String = this?.let(::nutritionDecimal).orEmpty()
