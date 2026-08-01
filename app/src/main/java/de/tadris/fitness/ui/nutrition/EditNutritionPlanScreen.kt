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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.FitnessProfile
import de.tadris.fitness.data.NutritionDay
import de.tadris.fitness.data.NutritionMeal
import de.tadris.fitness.data.NutritionPlan
import de.tadris.fitness.data.TrainingProgram
import de.tadris.fitness.data.withUpdatedTimestamp
import de.tadris.fitness.ui.programs.ProgramIconBadge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class EditNutritionPlanSnapshot(
    val plan: NutritionPlan,
    val days: List<NutritionDay>,
    val mealCounts: Map<Long, Int>,
    val profileUuids: Set<String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNutritionPlanScreen(
    id: Long,
    onBack: () -> Unit,
    onPlanDeleted: () -> Unit,
    onOpenDay: (Long) -> Unit,
) {
    val context = LocalContext.current
    val instance = remember { Instance.getInstance(context) }
    val db = remember(instance) { instance.db }
    val dao = remember(db) { db.nutritionPlanDao() }
    val trainingProgramDao = remember(db) { db.trainingProgramDao() }
    val profileRepository = remember(instance) { instance.fitnessProfileRepository }
    val profiles by profileRepository.profiles.collectAsState(
        initial = remember(profileRepository) { profileRepository.getProfiles() },
    )
    val trainingPrograms by trainingProgramDao.getProgramsFlow().collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val mutationRevision by NutritionTreeMutationGuard.revision.collectAsState()
    var refreshKey by remember { mutableIntStateOf(0) }
    var snapshot by remember(id) { mutableStateOf<EditNutritionPlanSnapshot?>(null) }

    LaunchedEffect(id, refreshKey, mutationRevision) {
        snapshot = withContext(Dispatchers.IO) {
            val loadedPlan = dao.findPlanById(id) ?: return@withContext null
            val loadedDays = dao.getDaysOfPlan(id)
            EditNutritionPlanSnapshot(
                plan = loadedPlan,
                days = loadedDays,
                mealCounts = loadedDays.associate { day ->
                    day.id to dao.getMealsOfDay(day.id).size
                },
                profileUuids = dao.getProfileUuids(id).toSet(),
            )
        }
    }

    val loaded = snapshot ?: return
    val current = loaded.plan
    val days = loaded.days
    val mealCounts = loaded.mealCounts
    val persistedProfileUuids = loaded.profileUuids

    var name by rememberSaveable(id) { mutableStateOf(current.name) }
    var description by rememberSaveable(id) { mutableStateOf(current.description.orEmpty()) }
    var objective by rememberSaveable(id) { mutableStateOf(current.objective.orEmpty()) }
    var preferences by rememberSaveable(id) { mutableStateOf(current.preferences.orEmpty()) }
    var restrictions by rememberSaveable(id) { mutableStateOf(current.restrictions.orEmpty()) }
    var linkedTrainingProgramUuid by rememberSaveable(id) {
        mutableStateOf(current.linkedTrainingProgramUuid)
    }
    var selectedProfileUuids by rememberSaveable(id) {
        mutableStateOf(persistedProfileUuids.toList())
    }
    var nameError by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showTrainingProgramPicker by remember { mutableStateOf(false) }
    val mutationInProgress by NutritionTreeMutationGuard.inProgress.collectAsState()

    fun refresh() {
        refreshKey++
    }

    val isDirty = name.trim() != current.name ||
        description.trim().ifBlank { null } != current.description ||
        objective.trim().ifBlank { null } != current.objective ||
        preferences.trim().ifBlank { null } != current.preferences ||
        restrictions.trim().ifBlank { null } != current.restrictions ||
        linkedTrainingProgramUuid != current.linkedTrainingProgramUuid ||
        selectedProfileUuids.toSet() != persistedProfileUuids

    fun validatedMetadata(): NutritionPlan? {
        if (name.trim().length !in 2..NUTRITION_MAX_NAME_LENGTH) {
            nameError = true
            return null
        }
        if (listOf(description, objective, preferences, restrictions).any {
                it.length > NUTRITION_MAX_TEXT_LENGTH
            }
        ) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.nutritionTextTooLong))
            }
            return null
        }
        return current.copy(
            name = name.trim(),
            description = description.trim().ifBlank { null },
            objective = objective.trim().ifBlank { null },
            preferences = preferences.trim().ifBlank { null },
            restrictions = restrictions.trim().ifBlank { null },
            linkedTrainingProgramUuid = linkedTrainingProgramUuid,
        ).withUpdatedTimestamp()
    }

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

    fun saveMetadata(
        showMessage: Boolean = true,
        onSaved: () -> Unit = {},
    ): Boolean {
        val updated = validatedMetadata() ?: return false
        val profileUuids = selectedProfileUuids.toList()
        return runMutation(
            mutation = {
                db.runInTransaction {
                    dao.updatePlan(updated)
                    dao.replaceProfileUuids(id, profileUuids)
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

    fun touchPlan() {
        dao.touchPlan(id)
    }

    fun addDay() {
        if (mutationInProgress) return
        if (days.size >= NUTRITION_MAX_DAYS) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.nutritionMaximumDaysReached))
            }
            return
        }
        val updated = if (isDirty) validatedMetadata() ?: return else null
        val profileUuids = selectedProfileUuids.toList()
        val maximumDaysMessage = context.getString(R.string.nutritionMaximumDaysReached)
        runMutation(
            mutation = {
                db.runInTransaction<Long> {
                    updated?.let {
                        dao.updatePlan(it)
                        dao.replaceProfileUuids(id, profileUuids)
                    }
                    val freshDays = dao.getDaysOfPlan(id)
                    if (freshDays.size >= NUTRITION_MAX_DAYS) {
                        throw NutritionUserVisibleException(maximumDaysMessage)
                    }
                    val inserted = dao.insertDay(
                        NutritionDay(
                            planId = id,
                            orderIndex = freshDays.size,
                            name = context.getString(
                                R.string.nutritionDayNumber,
                                freshDays.size + 1,
                            ),
                        )
                    )
                    touchPlan()
                    inserted
                }
            },
            onSuccess = { newDayId ->
                refresh()
                onOpenDay(newDayId)
            },
        )
    }

    fun duplicateDay(day: NutritionDay) {
        if (mutationInProgress) return
        if (days.size >= NUTRITION_MAX_DAYS) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.nutritionMaximumDaysReached))
            }
            return
        }
        val maximumDaysMessage = context.getString(R.string.nutritionMaximumDaysReached)
        runMutation(
            mutation = {
                db.runInTransaction {
                    val freshDays = dao.getDaysOfPlan(id)
                    if (freshDays.size >= NUTRITION_MAX_DAYS) {
                        throw NutritionUserVisibleException(maximumDaysMessage)
                    }
                    val freshDay = checkNotNull(freshDays.firstOrNull { it.id == day.id })
                    val newDayId = dao.insertDay(
                        freshDay.copy(
                            id = 0,
                            orderIndex = freshDays.size,
                            name = freshDay.name?.let {
                                context.getString(R.string.nutritionCopyName, it)
                                    .take(NUTRITION_MAX_NAME_LENGTH)
                            },
                        )
                    )
                    dao.getMealsOfDay(freshDay.id).forEach { meal ->
                        val newMealId = dao.insertMeal(meal.copy(id = 0, dayId = newDayId))
                        dao.getItemsOfMeal(meal.id).forEach { item ->
                            dao.insertItem(item.copy(id = 0, mealId = newMealId))
                        }
                    }
                    touchPlan()
                }
            },
            onSuccess = { refresh() },
        )
    }

    fun moveDay(dayId: Long, offset: Int) {
        if (mutationInProgress) return
        runMutation(
            mutation = {
                db.runInTransaction {
                    val freshDays = dao.getDaysOfPlan(id)
                    val from = freshDays.indexOfFirst { it.id == dayId }
                    val to = from + offset
                    check(from >= 0 && to in freshDays.indices)
                    val reordered = freshDays.toMutableList().apply { add(to, removeAt(from)) }
                    dao.reorderDays(id, reordered)
                    touchPlan()
                }
            },
            onSuccess = { refresh() },
        )
    }

    fun deleteDay(day: NutritionDay) {
        if (mutationInProgress || days.size <= 1) return
        runMutation(
            mutation = {
                db.runInTransaction {
                    val freshDays = dao.getDaysOfPlan(id)
                    check(freshDays.size > 1)
                    val freshDay = checkNotNull(freshDays.firstOrNull { it.id == day.id })
                    dao.deleteDay(freshDay)
                    dao.reorderDays(id, freshDays.filterNot { it.id == freshDay.id })
                    touchPlan()
                }
            },
            onSuccess = { refresh() },
        )
    }

    fun openDay(dayId: Long) {
        if (mutationInProgress) return
        if (isDirty) {
            saveMetadata(showMessage = false) { onOpenDay(dayId) }
        } else {
            onOpenDay(dayId)
        }
    }

    BackHandler { requestBack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.nutritionEditPlan),
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
                        onClick = { saveMetadata() },
                        enabled = !mutationInProgress,
                    ) {
                        Text(stringResource(R.string.nutritionSave))
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        enabled = !mutationInProgress,
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.nutritionDeletePlan),
                        )
                    }
                },
            )
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
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "nutrition_plan_fields") {
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
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NutritionPlanIconBadge()
                            Text(
                                stringResource(R.string.nutritionPlanDetails),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                if (it.length <= NUTRITION_MAX_NAME_LENGTH) name = it
                                nameError = false
                            },
                            label = { Text(stringResource(R.string.nutritionPlanName)) },
                            enabled = !mutationInProgress,
                            singleLine = true,
                            isError = nameError,
                            supportingText = if (nameError) {
                                { Text(stringResource(R.string.nutritionEnterPlanName)) }
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = {
                                if (it.length <= NUTRITION_MAX_TEXT_LENGTH) description = it
                            },
                            label = { Text(stringResource(R.string.nutritionDescriptionOptional)) },
                            enabled = !mutationInProgress,
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = objective,
                            onValueChange = {
                                if (it.length <= NUTRITION_MAX_TEXT_LENGTH) objective = it
                            },
                            label = { Text(stringResource(R.string.nutritionObjectiveOptional)) },
                            enabled = !mutationInProgress,
                            minLines = 2,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = preferences,
                            onValueChange = {
                                if (it.length <= NUTRITION_MAX_TEXT_LENGTH) preferences = it
                            },
                            label = { Text(stringResource(R.string.nutritionPreferencesOptional)) },
                            enabled = !mutationInProgress,
                            minLines = 2,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = restrictions,
                            onValueChange = {
                                if (it.length <= NUTRITION_MAX_TEXT_LENGTH) restrictions = it
                            },
                            label = { Text(stringResource(R.string.nutritionRestrictionsOptional)) },
                            enabled = !mutationInProgress,
                            minLines = 2,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item(key = "nutrition_training_program") {
                NutritionTrainingProgramSelector(
                    programs = trainingPrograms,
                    selectedUuid = linkedTrainingProgramUuid,
                    enabled = !mutationInProgress,
                    onClick = { showTrainingProgramPicker = true },
                )
            }

            item(key = "nutrition_profiles_header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.nutritionProfiles),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.nutritionProfilesSummary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (profiles.isEmpty()) {
                item(key = "nutrition_profiles_empty") {
                    Text(
                        stringResource(R.string.nutritionNoProfiles),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(profiles, key = { "nutrition_profile_${it.id}" }) { profile ->
                    NutritionProfileRow(
                        profile = profile,
                        selected = profile.uuid in selectedProfileUuids,
                        enabled = !mutationInProgress,
                        onSelectedChange = { checked ->
                            selectedProfileUuids = if (checked) {
                                (selectedProfileUuids + profile.uuid).distinct()
                            } else {
                                selectedProfileUuids - profile.uuid
                            }
                        },
                    )
                }
            }

            item(key = "nutrition_days_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.nutritionDays),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        days.size.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            itemsIndexed(days, key = { _, day -> "nutrition_edit_day_${day.id}" }) { index, day ->
                NutritionEditDayCard(
                    day = day,
                    index = index,
                    mealCount = mealCounts[day.id] ?: 0,
                    enabled = !mutationInProgress,
                    canMoveUp = index > 0,
                    canMoveDown = index < days.lastIndex,
                    canDelete = days.size > 1,
                    onClick = { openDay(day.id) },
                    onDuplicate = { duplicateDay(day) },
                    onMoveUp = { moveDay(day.id, -1) },
                    onMoveDown = { moveDay(day.id, 1) },
                    onDelete = { deleteDay(day) },
                )
            }

            item(key = "nutrition_add_day") {
                OutlinedButton(
                    onClick = ::addDay,
                    enabled = !mutationInProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.nutritionAddDay))
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!mutationInProgress) showDeleteDialog = false
            },
            title = { Text(stringResource(R.string.nutritionDeletePlan)) },
            text = { Text(stringResource(R.string.nutritionDeletePlanMessage)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        runMutation(
                            mutation = {
                                dao.findPlanById(id)?.let(dao::deletePlan)
                            },
                            onSuccess = {
                                showDeleteDialog = false
                                onPlanDeleted()
                            },
                        )
                    },
                    enabled = !mutationInProgress,
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !mutationInProgress,
                ) {
                    Text(stringResource(R.string.cancel))
                }
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

    if (showTrainingProgramPicker) {
        NutritionTrainingProgramDialog(
            programs = trainingPrograms,
            selectedUuid = linkedTrainingProgramUuid,
            onDismiss = { showTrainingProgramPicker = false },
            onSelect = { uuid ->
                linkedTrainingProgramUuid = uuid
                showTrainingProgramPicker = false
            },
        )
    }
}

@Composable
private fun NutritionTrainingProgramSelector(
    programs: List<TrainingProgram>,
    selectedUuid: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val selected = programs.firstOrNull { it.uuid == selectedUuid }
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
                stringResource(R.string.nutritionLinkedTrainingProgram),
                style = MaterialTheme.typography.titleMedium,
            )
            if (selected == null) {
                Text(
                    stringResource(R.string.nutritionNoTrainingProgram),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProgramIconBadge(
                        iconName = selected.icon,
                        size = 40.dp,
                        iconSize = 21.dp,
                    )
                    Text(
                        selected.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FilledTonalButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.nutritionChooseTrainingProgram))
            }
        }
    }
}

@Composable
private fun NutritionProfileRow(
    profile: FitnessProfile,
    selected: Boolean,
    enabled: Boolean,
    onSelectedChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = { onSelectedChange(!selected) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, enabled = enabled, onCheckedChange = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (profile.isActive) {
                    Text(
                        stringResource(R.string.nutritionActiveProfile),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun NutritionEditDayCard(
    day: NutritionDay,
    index: Int,
    mealCount: Int,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    (index + 1).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    day.name?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.nutritionDayNumber, index + 1),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.nutritionMealCount, mealCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    enabled = enabled,
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.nutritionMoreDayActions),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.nutritionDuplicateDay)) },
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
                        text = { Text(stringResource(R.string.nutritionDeleteDay)) },
                        enabled = enabled && canDelete,
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun NutritionTrainingProgramDialog(
    programs: List<TrainingProgram>,
    selectedUuid: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutritionChooseTrainingProgram)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "nutrition_no_training_program") {
                    Surface(
                        onClick = { onSelect(null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = if (selectedUuid == null) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ) {
                        Text(
                            stringResource(R.string.nutritionNoTrainingProgram),
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
                items(programs, key = { "nutrition_training_program_${it.id}" }) { program ->
                    Surface(
                        onClick = { onSelect(program.uuid) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = if (program.uuid == selectedUuid) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProgramIconBadge(
                                iconName = program.icon,
                                size = 38.dp,
                                iconSize = 20.dp,
                            )
                            Text(
                                program.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}
