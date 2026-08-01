/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.ui.nutrition

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.ProgramExerciseEnergyEstimator
import de.tadris.fitness.data.ProgramScheduling
import de.tadris.fitness.data.TrainingProgram
import de.tadris.fitness.data.withUpdatedTimestamp
import de.tadris.fitness.data.backup.NutritionPlanImporter
import de.tadris.fitness.ui.programs.toAiProfileContext
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionAiPromptScreen(
    onBack: () -> Unit,
    onManageProfiles: () -> Unit,
    onPlanImported: (Long) -> Unit,
) {
    val context = LocalContext.current
    val instance = remember(context) { Instance.getInstance(context) }
    val profileRepository = remember(instance) { instance.fitnessProfileRepository }
    val programDao = remember(instance) { instance.db.trainingProgramDao() }
    val profiles by profileRepository.profiles.collectAsState(
        initial = remember(profileRepository) { profileRepository.getProfiles() },
    )
    val programs by programDao.getProgramsFlow().collectAsState(initial = emptyList())
    val clipboard = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var goal by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf("") }
    var mealsPerDay by rememberSaveable { mutableStateOf("3") }
    var includeProfiles by rememberSaveable { mutableStateOf(false) }
    var selectedProfileIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var selectedTrainingProgramId by rememberSaveable { mutableStateOf<Long?>(null) }
    var allergiesAndFeatures by rememberSaveable { mutableStateOf("") }
    var preferences by rememberSaveable { mutableStateOf("") }
    var excludedFoods by rememberSaveable { mutableStateOf("") }
    var requiredFoods by rememberSaveable { mutableStateOf("") }
    var budget by rememberSaveable { mutableStateOf("") }
    var cookingTime by rememberSaveable { mutableStateOf("") }
    var extra by rememberSaveable { mutableStateOf("") }
    var generatedPrompt by remember { mutableStateOf<GeneratedNutritionAiPrompt?>(null) }
    var trainingSummary by remember { mutableStateOf<NutritionTrainingProgramSummary?>(null) }
    var summaryLoading by remember { mutableStateOf(false) }
    var importInProgress by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var pendingImportProfileUuids by remember { mutableStateOf(emptyList<String>()) }
    var pendingImportProgramUuid by remember { mutableStateOf<String?>(null) }
    var pendingImportStartDateEpochDay by remember { mutableStateOf<Long?>(null) }
    var pendingImportInputs by remember { mutableStateOf<NutritionAiPromptInputSnapshot?>(null) }

    val selectedProgram = programs.firstOrNull { it.id == selectedTrainingProgramId }
    val selectedProfiles = if (includeProfiles) {
        profiles.filter { it.id in selectedProfileIds }
    } else {
        emptyList()
    }

    LaunchedEffect(includeProfiles, profiles) {
        val availableIds = profiles.mapTo(mutableSetOf()) { it.id }
        val retained = selectedProfileIds.filterTo(mutableListOf()) { it in availableIds }
        selectedProfileIds = when {
            !includeProfiles -> retained
            retained.isNotEmpty() -> retained
            else -> profiles.firstOrNull { it.isActive }?.let { listOf(it.id) }.orEmpty()
        }
    }
    LaunchedEffect(selectedTrainingProgramId, programs) {
        if (selectedTrainingProgramId != null && selectedProgram == null) {
            selectedTrainingProgramId = null
        }
    }
    LaunchedEffect(selectedTrainingProgramId, programs) {
        val programId = selectedTrainingProgramId
        if (programId == null) {
            trainingSummary = null
            summaryLoading = false
        } else {
            summaryLoading = true
            trainingSummary = withContext(Dispatchers.IO) {
                loadNutritionTrainingSummary(instance, programId)
            }
            summaryLoading = false
        }
    }

    fun captureInputs() = NutritionAiPromptInputSnapshot(
        goal = goal,
        duration = duration,
        mealsPerDay = mealsPerDay,
        allergiesAndFeatures = allergiesAndFeatures,
        preferences = preferences,
        excludedFoods = excludedFoods,
        requiredFoods = requiredFoods,
        budget = budget,
        cookingTime = cookingTime,
        extra = extra,
        includeProfiles = includeProfiles,
        profiles = selectedProfiles.map { profile ->
            NutritionAiProfileSnapshot(profile.id, profile.toAiProfileContext())
        },
        selectedTrainingProgramId = selectedTrainingProgramId,
        trainingProgram = trainingSummary,
    )

    val currentInputs = captureInputs()
    val latestInputs = remember { AtomicReference(currentInputs) }
    SideEffect { latestInputs.set(currentInputs) }
    val profileSelectionMissing = includeProfiles && selectedProfileIds.isEmpty()
    val parsedMealsPerDay = mealsPerDay.toIntOrNull()
    val mealsPerDayValid = parsedMealsPerDay in 1..12
    val canGenerate = !profileSelectionMissing && mealsPerDayValid &&
        (!summaryLoading && (selectedTrainingProgramId == null || trainingSummary != null))
    val copiedMessage = stringResource(R.string.copied)
    val genericImportError = stringResource(R.string.nutritionAiImportFailed)

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importInProgress = true
        val profileUuids = pendingImportProfileUuids.toList()
        val programUuid = pendingImportProgramUuid
        val startDateEpochDay = pendingImportStartDateEpochDay
        val linkInputs = pendingImportInputs
        scope.launch {
            try {
                val planId = withContext(Dispatchers.IO) {
                    val container = context.contentResolver.openInputStream(uri)
                        ?.use(NutritionPlanImporter::read)
                        ?: throw IOException(genericImportError)
                    val importedPlanId = NutritionPlanImporter.import(context, container)
                    val dao = instance.db.nutritionPlanDao()
                    // A shared file is anonymous. Add private local associations only when
                    // it was imported for the still-current prompt and selection context.
                    if (linkInputs != null && linkInputs == latestInputs.get()) {
                        dao.findPlanById(importedPlanId)?.let { imported ->
                            if (programUuid != null || startDateEpochDay != null) {
                                dao.updatePlan(
                                    imported.copy(
                                        linkedTrainingProgramUuid = programUuid,
                                        startDateEpochDay = startDateEpochDay,
                                    ).withUpdatedTimestamp()
                                )
                            }
                        }
                        dao.replaceProfileUuids(importedPlanId, profileUuids)
                    }
                    importedPlanId
                }
                importInProgress = false
                onPlanImported(planId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                importInProgress = false
                importError = error.message ?: genericImportError
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nutritionAiPrompt)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.actionBack),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NutritionAiSectionCard(title = stringResource(R.string.nutritionAiBasicsTitle)) {
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it.take(MAX_NUTRITION_AI_FIELD_LENGTH) },
                    label = { Text(stringResource(R.string.nutritionAiGoalLabel)) },
                    placeholder = { Text(stringResource(R.string.nutritionAiGoalHint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth >= 400.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NutritionAiDurationField(
                                duration,
                                { duration = it.take(MAX_NUTRITION_AI_FIELD_LENGTH) },
                                Modifier.weight(1f),
                            )
                            NutritionAiMealsField(
                                mealsPerDay,
                                { mealsPerDay = it.filter(Char::isDigit).take(2) },
                                mealsPerDayValid,
                                Modifier.weight(1f),
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            NutritionAiDurationField(
                                duration,
                                { duration = it.take(MAX_NUTRITION_AI_FIELD_LENGTH) },
                                Modifier.fillMaxWidth(),
                            )
                            NutritionAiMealsField(
                                mealsPerDay,
                                { mealsPerDay = it.filter(Char::isDigit).take(2) },
                                mealsPerDayValid,
                                Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            NutritionAiProfilesSection(
                includeProfiles = includeProfiles,
                onIncludeProfilesChange = { includeProfiles = it },
                profiles = profiles,
                selectedIds = selectedProfileIds,
                onSelectedIdsChange = { selectedProfileIds = it },
                selectionMissing = profileSelectionMissing,
                onManageProfiles = onManageProfiles,
            )

            NutritionAiTrainingSection(
                programs = programs,
                selected = selectedProgram,
                onSelected = { selectedTrainingProgramId = it?.id },
            )

            NutritionAiSectionCard(title = stringResource(R.string.nutritionAiFoodRulesTitle)) {
                NutritionAiMultilineField(
                    value = allergiesAndFeatures,
                    onValueChange = {
                        allergiesAndFeatures = it.take(MAX_NUTRITION_AI_FIELD_LENGTH)
                    },
                    label = R.string.nutritionAiAllergiesLabel,
                    placeholder = R.string.nutritionAiAllergiesHint,
                )
                NutritionAiMultilineField(
                    value = preferences,
                    onValueChange = { preferences = it.take(MAX_NUTRITION_AI_FIELD_LENGTH) },
                    label = R.string.nutritionAiPreferencesLabel,
                    placeholder = R.string.nutritionAiPreferencesHint,
                )
                NutritionAiMultilineField(
                    value = excludedFoods,
                    onValueChange = { excludedFoods = it.take(MAX_NUTRITION_AI_FIELD_LENGTH) },
                    label = R.string.nutritionAiExcludedFoodsLabel,
                    placeholder = R.string.nutritionAiExcludedFoodsHint,
                )
                NutritionAiMultilineField(
                    value = requiredFoods,
                    onValueChange = { requiredFoods = it.take(MAX_NUTRITION_AI_FIELD_LENGTH) },
                    label = R.string.nutritionAiRequiredFoodsLabel,
                    placeholder = R.string.nutritionAiRequiredFoodsHint,
                )
            }

            NutritionAiSectionCard(title = stringResource(R.string.nutritionAiLogisticsTitle)) {
                NutritionAiMultilineField(
                    value = budget,
                    onValueChange = { budget = it.take(MAX_NUTRITION_AI_FIELD_LENGTH) },
                    label = R.string.nutritionAiBudgetLabel,
                    placeholder = R.string.nutritionAiBudgetHint,
                    minLines = 1,
                )
                NutritionAiMultilineField(
                    value = cookingTime,
                    onValueChange = { cookingTime = it.take(MAX_NUTRITION_AI_FIELD_LENGTH) },
                    label = R.string.nutritionAiCookingTimeLabel,
                    placeholder = R.string.nutritionAiCookingTimeHint,
                    minLines = 1,
                )
                NutritionAiMultilineField(
                    value = extra,
                    onValueChange = { extra = it.take(MAX_NUTRITION_AI_FIELD_LENGTH) },
                    label = R.string.nutritionAiExtraLabel,
                    placeholder = null,
                )
            }

            Button(
                enabled = canGenerate,
                onClick = {
                    val inputs = captureInputs()
                    generatedPrompt = GeneratedNutritionAiPrompt(
                        text = buildNutritionPrompt(
                            goal = goal.trim(),
                            duration = duration.trim(),
                            mealsPerDay = parsedMealsPerDay ?: 3,
                            allergiesAndFeatures = allergiesAndFeatures.trim(),
                            preferences = preferences.trim(),
                            excludedFoods = excludedFoods.trim(),
                            requiredFoods = requiredFoods.trim(),
                            budget = budget.trim(),
                            cookingTime = cookingTime.trim(),
                            extra = extra.trim(),
                            profileContexts = inputs.profiles.map(NutritionAiProfileSnapshot::context),
                            trainingProgram = trainingSummary,
                        ),
                        generatedFrom = inputs,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            ) {
                Text(stringResource(R.string.nutritionAiGenerate))
            }

            OutlinedButton(
                enabled = !importInProgress,
                onClick = {
                    val matchingInputs = currentInputs.takeIf {
                        generatedPrompt?.textFor(it) != null
                    }
                    pendingImportInputs = matchingInputs
                    pendingImportProfileUuids = if (matchingInputs != null) {
                        selectedProfiles.map { it.uuid }
                    } else {
                        emptyList()
                    }
                    pendingImportProgramUuid = selectedProgram?.uuid.takeIf { matchingInputs != null }
                    pendingImportStartDateEpochDay = selectedProgram
                        ?.takeIf { matchingInputs != null }
                        ?.let { program ->
                            linkedNutritionStartEpochDay(
                                program = program,
                                dayCount = trainingSummary?.durationDays ?: 0,
                                today = LocalDate.now(),
                                zoneId = ZoneId.systemDefault(),
                            )
                        }
                    importLauncher.launch(NUTRITION_IMPORT_MIME_TYPES)
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            ) {
                Text(stringResource(R.string.nutritionAiImportJson))
            }

            generatedPrompt?.textFor(currentInputs)?.let { prompt ->
                NutritionAiPromptResult(
                    prompt = prompt,
                    onCopy = {
                        clipboard.setPrimaryClip(sensitivePlainTextClip(prompt))
                        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                    },
                    onShare = { shareNutritionPrompt(context, prompt) },
                )
            }
        }
    }

    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text(stringResource(R.string.error)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { importError = null }) {
                    Text(stringResource(R.string.okay))
                }
            },
        )
    }
}

@Composable
private fun NutritionAiSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun NutritionAiDurationField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.nutritionAiDurationLabel)) },
        placeholder = { Text(stringResource(R.string.nutritionAiDurationHint)) },
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun NutritionAiMealsField(
    value: String,
    onValueChange: (String) -> Unit,
    valid: Boolean,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.nutritionAiMealsPerDayLabel)) },
        supportingText = if (valid) null else {
            { Text(stringResource(R.string.nutritionAiInvalidMeals)) }
        },
        isError = !valid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun NutritionAiMultilineField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    placeholder: Int?,
    minLines: Int = 2,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        placeholder = placeholder?.let { id -> { Text(stringResource(id)) } },
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NutritionAiProfilesSection(
    includeProfiles: Boolean,
    onIncludeProfilesChange: (Boolean) -> Unit,
    profiles: List<de.tadris.fitness.data.FitnessProfile>,
    selectedIds: List<Long>,
    onSelectedIdsChange: (List<Long>) -> Unit,
    selectionMissing: Boolean,
    onManageProfiles: () -> Unit,
) {
    NutritionAiSectionCard(title = stringResource(R.string.aiProfileContextTitle)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = includeProfiles,
                    role = Role.Switch,
                    onValueChange = onIncludeProfilesChange,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.nutritionAiUseProfiles),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.nutritionAiProfilesPrivacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = includeProfiles, onCheckedChange = null)
        }

        if (includeProfiles) {
            Text(
                text = stringResource(R.string.aiProfilesSelected, selectedIds.size),
                style = MaterialTheme.typography.labelLarge,
                color = if (selectionMissing) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            if (selectionMissing) {
                Text(
                    text = stringResource(R.string.aiSelectAtLeastOneProfile),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (profiles.isEmpty()) {
                Text(
                    text = stringResource(R.string.aiNoProfiles),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                profiles.forEach { profile ->
                    val selected = profile.id in selectedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = selected,
                                role = Role.Checkbox,
                                onValueChange = { checked ->
                                    onSelectedIdsChange(
                                        if (checked) {
                                            (selectedIds + profile.id).distinct()
                                        } else {
                                            selectedIds - profile.id
                                        }
                                    )
                                },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Checkbox(checked = selected, onCheckedChange = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (profile.isActive) {
                                Text(
                                    text = stringResource(R.string.aiActiveProfile),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
            TextButton(onClick = onManageProfiles) {
                Text(stringResource(R.string.aiManageProfiles))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NutritionAiTrainingSection(
    programs: List<TrainingProgram>,
    selected: TrainingProgram?,
    onSelected: (TrainingProgram?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    NutritionAiSectionCard(title = stringResource(R.string.nutritionAiTrainingTitle)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selected?.name ?: stringResource(R.string.nutritionAiTrainingNone),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nutritionAiTrainingNone)) },
                    onClick = {
                        onSelected(null)
                        expanded = false
                    },
                )
                programs.forEach { program ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = program.name,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onClick = {
                            onSelected(program)
                            expanded = false
                        },
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.nutritionAiTrainingSummary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NutritionAiPromptResult(
    prompt: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.nutritionAiPromptReady),
                style = MaterialTheme.typography.titleMedium,
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth < 320.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.copyPrompt))
                        }
                        OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.sharePrompt))
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.copyPrompt))
                        }
                        OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.sharePrompt))
                        }
                    }
                }
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                SelectionContainer {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 300.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}

private fun sensitivePlainTextClip(text: String): ClipData =
    ClipData.newPlainText("FitRegimen nutrition prompt", text).also { clip ->
        clip.description.extras = PersistableBundle().apply {
            val sensitiveKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ClipDescription.EXTRA_IS_SENSITIVE
            } else {
                "android.content.extra.IS_SENSITIVE"
            }
            putBoolean(sensitiveKey, true)
        }
    }

/** Keeps day N of an imported menu aligned with day N of its selected training plan. */
internal fun linkedNutritionStartEpochDay(
    program: TrainingProgram,
    dayCount: Int,
    today: LocalDate,
    zoneId: ZoneId,
): Long? {
    if (ProgramScheduling.dayIndexOf(program, dayCount, today, zoneId) == null) return null
    return ProgramScheduling.firstScheduleBounds(program, dayCount, zoneId)?.first?.toEpochDay()
}

private fun loadNutritionTrainingSummary(
    instance: Instance,
    programId: Long,
): NutritionTrainingProgramSummary {
    val programDao = instance.db.trainingProgramDao()
    val exerciseDao = instance.db.exerciseDao()
    val days = programDao.getDaysOfProgram(programId).take(MAX_NUTRITION_PROMPT_DAYS).map { day ->
        val loads = if (day.isRest) {
            emptyList()
        } else {
            programDao.getExercisesOfDay(day.id).mapNotNull { entry ->
                val exercise = exerciseDao.findById(entry.exerciseId) ?: return@mapNotNull null
                val sets = programDao.getSetsOfProgramExercise(entry.id)
                val repetitions = sets.sumOf { set ->
                    (set.repsMax ?: set.reps ?: 0).coerceAtLeast(0)
                }
                val durationSeconds = sets.sumOf { set ->
                    (set.durationMillis ?: 0L).coerceAtLeast(0L) / 1_000.0
                }
                val distanceMeters = sets.sumOf { set ->
                    set.distanceMeters?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
                }
                val weightVolumeKgReps = sets.sumOf { set ->
                    val setRepetitions = (set.repsMax ?: set.reps ?: 0).coerceAtLeast(0)
                    val weightKg = set.weightKg?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
                    setRepetitions * weightKg
                }
                val secondsPerRep = ProgramExerciseEnergyEstimator.secondsPerRep(exercise)
                val estimatedActiveSeconds = if (durationSeconds > 0.0) {
                    durationSeconds
                } else {
                    repetitions * secondsPerRep
                }
                val restSeconds = (entry.prepareSeconds.coerceAtLeast(0) +
                    (entry.transitionSeconds ?: 0).coerceAtLeast(0) +
                    (entry.restSeconds ?: 0).coerceAtLeast(0) * (sets.size - 1).coerceAtLeast(0))
                NutritionTrainingExerciseLoad(
                    metricKind = exercise.metricKind.name,
                    setCount = sets.size,
                    targetRepetitions = repetitions,
                    targetDurationSeconds = durationSeconds,
                    targetDistanceMeters = distanceMeters,
                    targetWeightVolumeKgReps = weightVolumeKgReps,
                    estimatedActiveSeconds = estimatedActiveSeconds,
                    plannedRestSeconds = restSeconds,
                    met = ProgramExerciseEnergyEstimator.met(exercise),
                )
            }
        }
        NutritionTrainingDayLoad(isRest = day.isRest, exercises = loads)
    }
    return summarizeNutritionTrainingProgram(days)
}

private fun shareNutritionPrompt(context: android.content.Context, prompt: String) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, prompt)
            },
            null,
        )
    )
}

private const val MAX_NUTRITION_AI_FIELD_LENGTH = 4_000
private val NUTRITION_IMPORT_MIME_TYPES = arrayOf(
    "application/json",
    "text/json",
    "text/plain",
    "application/octet-stream",
)
private const val MAX_NUTRITION_PROMPT_DAYS = 366
