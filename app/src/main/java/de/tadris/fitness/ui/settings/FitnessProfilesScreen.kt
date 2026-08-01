/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack.
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.FitnessProfile
import de.tadris.fitness.data.FitnessProfileDraft
import de.tadris.fitness.data.FitnessProfileField
import de.tadris.fitness.data.FitnessProfileValidationReason
import de.tadris.fitness.data.FitnessProfileValidator
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/** Material 3 destination for personal measurements and the active-person switcher. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitnessProfilesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        Instance.getInstance(context.applicationContext).fitnessProfileRepository
    }
    val profiles by repository.profiles.collectAsStateWithLifecycle(
        initialValue = remember(repository) { repository.getProfiles() },
    )
    val activeProfile by repository.activeProfile.collectAsStateWithLifecycle(
        initialValue = remember(repository) { repository.getActiveProfile() },
    )

    var creating by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var deleteTarget by remember { mutableStateOf<FitnessProfile?>(null) }
    var transientError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val activeProfileId = activeProfile?.id ?: profiles.firstOrNull(FitnessProfile::isActive)?.id

    val saveFailed = stringResource(R.string.fitness_profile_save_failed)
    val deleteFailed = stringResource(R.string.fitness_profile_delete_failed)
    val activateFailed = stringResource(R.string.fitness_profile_activate_failed)

    LaunchedEffect(transientError) {
        transientError?.let { message ->
            snackbarHostState.showSnackbar(message)
            transientError = null
        }
    }

    fun setActive(profile: FitnessProfile) {
        if (profile.id == activeProfileId) return
        runCatching { repository.setActive(profile.id) }
            .onFailure { transientError = activateFailed }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fitness_profiles_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.actionBack),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.fitness_profile_add),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(profiles, key = FitnessProfile::id) { profile ->
                FitnessProfileCard(
                    profile = profile,
                    active = profile.id == activeProfileId,
                    onActivate = { setActive(profile) },
                    onEdit = { editingId = profile.id },
                    onDelete = { deleteTarget = profile },
                )
            }
        }
    }

    if (creating) {
        FitnessProfileEditorSheet(
            existing = null,
            onDismiss = { creating = false },
            onSave = { draft ->
                runCatching { repository.create(draft) }
                    .onSuccess { creating = false }
                    .onFailure { transientError = saveFailed }
            },
        )
    }

    profiles.firstOrNull { it.id == editingId }?.let { profile ->
        FitnessProfileEditorSheet(
            existing = profile,
            onDismiss = { editingId = null },
            onSave = { draft ->
                runCatching { repository.update(profile.id, draft) }
                    .onSuccess { editingId = null }
                    .onFailure { transientError = saveFailed }
            },
        )
    }

    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.fitness_profile_delete_title, profile.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.fitness_profile_delete_message))
                    if (profile.id == activeProfileId || profiles.size == 1) {
                        Text(stringResource(R.string.fitness_profile_delete_active_message))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching { repository.delete(profile.id) }
                            .onSuccess { deleteTarget = null }
                            .onFailure {
                                deleteTarget = null
                                transientError = deleteFailed
                            }
                    },
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun FitnessProfileCard(
    profile: FitnessProfile,
    active: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = active,
                onClick = onActivate,
                role = Role.RadioButton,
            ),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 400.dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!compact) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = profile.name.firstOrNull()?.uppercase() ?: "•",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (active) ActiveProfileBadge()
                    Text(
                        text = fitnessProfileSummary(profile),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                RadioButton(
                    selected = active,
                    onClick = null,
                    modifier = Modifier.clearAndSetSemantics { },
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(
                                R.string.fitness_profile_more_actions,
                                profile.name,
                            ),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.fitness_profile_edit)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.fitness_profile_delete)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveProfileBadge() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(
                text = stringResource(R.string.fitness_profile_active),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun fitnessProfileSummary(profile: FitnessProfile): String {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    val parts = buildList {
        profile.birthDate()?.let { add(it.format(dateFormatter)) }
        profile.heightCm?.let { add("${formatProfileNumber(it)} ${stringResource(R.string.centimeters_short)}") }
        profile.weightKg?.let { add("${formatProfileNumber(it)} ${stringResource(R.string.kilograms_short)}") }
        profile.stepLengthCm?.let {
            add(
                "${stringResource(R.string.fitness_profile_step_length)} " +
                    "${formatProfileNumber(it)} ${stringResource(R.string.centimeters_short)}"
            )
        }
    }
    return parts.joinToString(" · ").ifEmpty {
        stringResource(R.string.fitness_profile_no_measurements)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FitnessProfileEditorSheet(
    existing: FitnessProfile?,
    onDismiss: () -> Unit,
    onSave: (FitnessProfileDraft) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var birthDateEpochDay by remember(existing?.id) { mutableStateOf(existing?.birthDateEpochDay) }
    var height by remember(existing?.id) { mutableStateOf(existing?.heightCm.toEditableProfileNumber()) }
    var weight by remember(existing?.id) { mutableStateOf(existing?.weightKg.toEditableProfileNumber()) }
    var stepLength by remember(existing?.id) { mutableStateOf(existing?.stepLengthCm.toEditableProfileNumber()) }
    var gender by remember(existing?.id) { mutableStateOf(existing?.gender) }
    var fitnessLevel by remember(existing?.id) { mutableStateOf(existing?.fitnessLevel) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showValidation by remember { mutableStateOf(false) }
    val initialAttributes = remember(existing?.id, existing?.customAttributesJson) {
        decodeProfileAttributes(existing?.customAttributesJson).mapIndexed { index, row ->
            row.copy(id = index)
        }
    }
    var nextAttributeId by remember(existing?.id) { mutableIntStateOf(initialAttributes.size) }
    val attributes = remember(existing?.id) {
        mutableStateListOf<ProfileAttributeRow>().apply {
            addAll(initialAttributes)
        }
    }

    val heightValue = height.toProfileDoubleOrNull()
    val weightValue = weight.toProfileDoubleOrNull()
    val stepLengthValue = stepLength.toProfileDoubleOrNull()
    val attributesHaveBlank = attributes.any { it.key.isBlank() || it.value.isBlank() }
    val attributesHaveDuplicates = attributes
        .map { it.key.trim().lowercase(Locale.ROOT) }
        .filter(String::isNotEmpty)
        .let { keys -> keys.distinct().size != keys.size }
    val customAttributesJson = encodeProfileAttributes(attributes)
    val draft = FitnessProfileDraft(
        name = name,
        birthDateEpochDay = birthDateEpochDay,
        heightCm = heightValue,
        weightKg = weightValue,
        stepLengthCm = stepLengthValue,
        gender = gender,
        fitnessLevel = fitnessLevel,
        notes = notes,
        customAttributesJson = customAttributesJson,
    )
    val allValidationErrors = FitnessProfileValidator.validate(draft)
    val validationErrors = if (showValidation) allValidationErrors else emptyList()
    fun hasError(field: FitnessProfileField) = validationErrors.any { it.field == field }
    fun reason(field: FitnessProfileField) = validationErrors.firstOrNull { it.field == field }?.reason
    val numericParseError = (height.isNotBlank() && heightValue == null) ||
        (weight.isNotBlank() && weightValue == null) ||
        (stepLength.isNotBlank() && stepLengthValue == null)
    val formHasErrors = numericParseError ||
        attributesHaveBlank ||
        attributesHaveDuplicates ||
        allValidationErrors.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        if (existing == null) R.string.fitness_profile_create
                        else R.string.fitness_profile_edit
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 20.dp,
                    end = 20.dp,
                    bottom = 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            item {
                val nameError = hasError(FitnessProfileField.NAME)
                RoundedProfileTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.fitness_profile_name),
                    singleLine = true,
                    isError = nameError,
                    supportingText = when (reason(FitnessProfileField.NAME)) {
                        FitnessProfileValidationReason.REQUIRED -> stringResource(R.string.fitness_profile_required)
                        FitnessProfileValidationReason.TOO_LONG -> stringResource(R.string.fitness_profile_name_too_long)
                        else -> null
                    },
                )
            }

            item {
                ProfileBirthDateField(
                    epochDay = birthDateEpochDay,
                    isError = hasError(FitnessProfileField.BIRTH_DATE),
                    onClick = { showDatePicker = true },
                    onClear = { birthDateEpochDay = null },
                )
            }

            item {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val useStackedMeasurementFields = maxWidth < 300.dp ||
                        LocalDensity.current.fontScale >= 1.3f
                    val heightField: @Composable (Modifier) -> Unit = { modifier ->
                    RoundedProfileTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = stringResource(R.string.fitness_profile_height),
                        suffix = stringResource(R.string.centimeters_short),
                        singleLine = true,
                        keyboardType = KeyboardType.Decimal,
                        isError = showValidation && (hasError(FitnessProfileField.HEIGHT) ||
                            height.isNotBlank() && heightValue == null),
                        supportingText = if (showValidation && (hasError(FitnessProfileField.HEIGHT) ||
                                height.isNotBlank() && heightValue == null)
                        ) stringResource(R.string.fitness_profile_height_range) else null,
                        modifier = modifier,
                    )
                    }
                    val weightField: @Composable (Modifier) -> Unit = { modifier ->
                    RoundedProfileTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = stringResource(R.string.fitness_profile_weight),
                        suffix = stringResource(R.string.kilograms_short),
                        singleLine = true,
                        keyboardType = KeyboardType.Decimal,
                        isError = showValidation && (hasError(FitnessProfileField.WEIGHT) ||
                            weight.isNotBlank() && weightValue == null),
                        supportingText = if (showValidation && (hasError(FitnessProfileField.WEIGHT) ||
                                weight.isNotBlank() && weightValue == null)
                        ) stringResource(R.string.fitness_profile_weight_range) else null,
                        modifier = modifier,
                    )
                    }
                    if (useStackedMeasurementFields) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            heightField(Modifier.fillMaxWidth())
                            weightField(Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            heightField(Modifier.weight(1f))
                            weightField(Modifier.weight(1f))
                        }
                    }
                }
            }

            item {
                val estimatedStepLength = estimateWalkingStepLengthCm(heightValue, gender)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    RoundedProfileTextField(
                        value = stepLength,
                        onValueChange = { stepLength = it },
                        label = stringResource(R.string.fitness_profile_step_length),
                        suffix = stringResource(R.string.centimeters_short),
                        singleLine = true,
                        keyboardType = KeyboardType.Decimal,
                        isError = showValidation && (hasError(FitnessProfileField.STEP_LENGTH) ||
                            stepLength.isNotBlank() && stepLengthValue == null),
                        supportingText = if (showValidation && (hasError(FitnessProfileField.STEP_LENGTH) ||
                                stepLength.isNotBlank() && stepLengthValue == null)
                        ) stringResource(R.string.fitness_profile_step_length_range) else null,
                    )
                    TextButton(
                        enabled = estimatedStepLength != null,
                        onClick = {
                            estimatedStepLength?.let { estimate ->
                                stepLength = formatProfileNumber(estimate)
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.fitness_profile_estimate_step_length),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = stringResource(R.string.fitness_profile_estimate_step_length_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                ProfileOptionalDropdown(
                    label = stringResource(R.string.fitness_profile_gender),
                    value = gender,
                    options = profileGenderOptions(gender),
                    onValueChange = { gender = it },
                )
            }

            item {
                ProfileOptionalDropdown(
                    label = stringResource(R.string.fitness_profile_level),
                    value = fitnessLevel,
                    options = profileLevelOptions(fitnessLevel),
                    onValueChange = { fitnessLevel = it },
                )
            }

            item {
                RoundedProfileTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = stringResource(R.string.fitness_profile_notes),
                    minLines = 3,
                    maxLines = 7,
                    isError = hasError(FitnessProfileField.NOTES),
                    supportingText = if (hasError(FitnessProfileField.NOTES)) {
                        stringResource(R.string.fitness_profile_notes_too_long)
                    } else null,
                )
            }

            item {
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.fitness_profile_extra_parameters),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(
                        onClick = {
                            attributes += ProfileAttributeRow(id = nextAttributeId++, key = "", value = "")
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = stringResource(R.string.fitness_profile_add_parameter),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            itemsIndexed(attributes, key = { _, row -> row.id }) { index, row ->
                ProfileAttributeEditorRow(
                    row = row,
                    showError = showValidation && (row.key.isBlank() || row.value.isBlank() ||
                        attributes.count { it.key.trim().equals(row.key.trim(), ignoreCase = true) } > 1),
                    onChange = { attributes[index] = it },
                    onRemove = { attributes.removeAt(index) },
                )
            }

            if (showValidation && (attributesHaveBlank || attributesHaveDuplicates ||
                    hasError(FitnessProfileField.CUSTOM_ATTRIBUTES))
            ) {
                item {
                    Text(
                        text = stringResource(
                            when {
                                attributesHaveBlank -> R.string.fitness_profile_parameter_required
                                attributesHaveDuplicates -> R.string.fitness_profile_parameter_duplicate
                                else -> R.string.fitness_profile_invalid_value
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (showValidation && formHasErrors) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.fitness_profile_validation_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }

            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ProfileEditorActions(
                onDismiss = onDismiss,
                onSave = {
                    showValidation = true
                    val normalized = FitnessProfileValidator.normalize(draft)
                    if (!formHasErrors) onSave(normalized)
                },
            )
        }
    }

    if (showDatePicker) {
        ProfileBirthDatePickerDialog(
            initialEpochDay = birthDateEpochDay,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                birthDateEpochDay = it
                showDatePicker = false
            },
        )
    }
}

@Composable
private fun ProfileEditorActions(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        val stackActions = maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.3f
        if (stackActions) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.save),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.cancel),
                        maxLines = 1,
                    )
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.widthIn(min = 120.dp),
                ) {
                    Text(
                        text = stringResource(R.string.save),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundedProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProfileBirthDateField(
    epochDay: Long?,
    isError: Boolean,
    onClick: () -> Unit,
    onClear: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    }
    val value = epochDay?.let { LocalDate.ofEpochDay(it).format(formatter) }.orEmpty()
    val chooseDescription = stringResource(R.string.fitness_profile_choose_birth_date)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.fitness_profile_birth_date)) },
                trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                isError = isError,
                supportingText = if (isError) {
                    { Text(stringResource(R.string.fitness_profile_birth_date_invalid)) }
                } else null,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(role = Role.Button, onClick = onClick)
                    .semantics { contentDescription = chooseDescription },
            )
        }
        if (epochDay != null) {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.fitness_profile_clear_birth_date))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileBirthDatePickerDialog(
    initialEpochDay: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val initialDate = initialEpochDay?.let(LocalDate::ofEpochDay) ?: today.minusYears(25)
    val selectableDates = remember(today) {
        val firstDateMillis = today.minusYears(130)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val todayMillis = today
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis in firstDateMillis..todayMillis

            override fun isSelectableYear(year: Int): Boolean =
                year in (today.year - 130)..today.year
        }
    }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = (today.year - 130)..today.year,
        selectableDates = selectableDates,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = pickerState.selectedDateMillis != null,
                onClick = {
                    pickerState.selectedDateMillis?.let { selectedMillis ->
                        onConfirm(
                            Instant.ofEpochMilli(selectedMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toEpochDay()
                        )
                    }
                },
            ) {
                Text(stringResource(R.string.okay))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

private data class ProfileSelectOption(val value: String?, val labelRes: Int, val customLabel: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileOptionalDropdown(
    label: String,
    value: String?,
    options: List<ProfileSelectOption>,
    onValueChange: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = options.firstOrNull { it.value == value } ?: options.first()
    val selectedLabel = selectedOption.customLabel ?: stringResource(selectedOption.labelRes)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.customLabel ?: stringResource(option.labelRes)) },
                    onClick = {
                        onValueChange(option.value)
                        expanded = false
                    },
                    trailingIcon = if (option.value == value) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun profileGenderOptions(current: String?): List<ProfileSelectOption> = buildList {
    add(ProfileSelectOption(null, R.string.fitness_profile_gender_unspecified))
    add(ProfileSelectOption("female", R.string.fitness_profile_gender_female))
    add(ProfileSelectOption("male", R.string.fitness_profile_gender_male))
    add(ProfileSelectOption("other", R.string.fitness_profile_gender_other))
    if (current != null && none { it.value == current }) {
        add(ProfileSelectOption(current, R.string.fitness_profile_gender_other, current))
    }
}

@Composable
private fun profileLevelOptions(current: String?): List<ProfileSelectOption> = buildList {
    add(ProfileSelectOption(null, R.string.fitness_profile_level_unspecified))
    add(ProfileSelectOption("beginner", R.string.fitness_profile_level_beginner))
    add(ProfileSelectOption("intermediate", R.string.fitness_profile_level_intermediate))
    add(ProfileSelectOption("advanced", R.string.fitness_profile_level_advanced))
    if (current != null && none { it.value == current }) {
        add(ProfileSelectOption(current, R.string.fitness_profile_level_unspecified, current))
    }
}

private data class ProfileAttributeRow(
    val id: Int = 0,
    val key: String,
    val value: String,
)

@Composable
private fun ProfileAttributeEditorRow(
    row: ProfileAttributeRow,
    showError: Boolean,
    onChange: (ProfileAttributeRow) -> Unit,
    onRemove: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 420.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    RoundedProfileTextField(
                        value = row.key,
                        onValueChange = { onChange(row.copy(key = it)) },
                        label = stringResource(R.string.fitness_profile_parameter_name),
                        singleLine = true,
                        isError = showError,
                        modifier = Modifier.weight(1f),
                    )
                    ProfileAttributeDeleteButton(onRemove)
                }
                RoundedProfileTextField(
                    value = row.value,
                    onValueChange = { onChange(row.copy(value = it)) },
                    label = stringResource(R.string.fitness_profile_parameter_value),
                    singleLine = true,
                    isError = showError,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                RoundedProfileTextField(
                    value = row.key,
                    onValueChange = { onChange(row.copy(key = it)) },
                    label = stringResource(R.string.fitness_profile_parameter_name),
                    singleLine = true,
                    isError = showError,
                    modifier = Modifier.weight(0.9f),
                )
                RoundedProfileTextField(
                    value = row.value,
                    onValueChange = { onChange(row.copy(value = it)) },
                    label = stringResource(R.string.fitness_profile_parameter_value),
                    singleLine = true,
                    isError = showError,
                    modifier = Modifier.weight(1.1f),
                )
                ProfileAttributeDeleteButton(onRemove)
            }
        }
    }
}

@Composable
private fun ProfileAttributeDeleteButton(onRemove: () -> Unit) {
    IconButton(
        onClick = onRemove,
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = stringResource(R.string.fitness_profile_remove_parameter),
        )
    }
}

private fun decodeProfileAttributes(value: String?): List<ProfileAttributeRow> {
    val objectValue = value?.let { raw ->
        runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
    } ?: return emptyList()
    return objectValue.entries.map { (key, jsonValue) ->
        val displayValue = (jsonValue as? JsonPrimitive)?.contentOrNull ?: jsonValue.toString()
        ProfileAttributeRow(key = key, value = displayValue)
    }
}

private fun encodeProfileAttributes(rows: List<ProfileAttributeRow>): String? {
    if (rows.isEmpty()) return null
    return buildJsonObject {
        rows.forEach { row ->
            if (row.key.isNotBlank()) put(row.key.trim(), JsonPrimitive(row.value.trim()))
        }
    }.toString()
}

private fun String.toProfileDoubleOrNull(): Double? =
    trim().takeIf(String::isNotEmpty)?.replace(',', '.')?.toDoubleOrNull()

private fun Double?.toEditableProfileNumber(): String = this?.let(::formatProfileNumber).orEmpty()

internal fun estimateWalkingStepLengthCm(heightCm: Double?, gender: String?): Double? {
    if (heightCm == null || !heightCm.isFinite() || heightCm !in 30.0..280.0) return null
    val factor = when (gender?.trim()?.lowercase(Locale.ROOT)) {
        "male" -> BigDecimal("0.415")
        "female" -> BigDecimal("0.413")
        else -> BigDecimal("0.414")
    }
    return BigDecimal.valueOf(heightCm)
        .multiply(factor)
        .setScale(1, RoundingMode.HALF_UP)
        .toDouble()
}

internal fun formatProfileNumber(value: Double): String =
    BigDecimal.valueOf(value)
        .setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
