/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack
 *
 * FitoTrack is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     FitoTrack is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.ui.programs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.Exercise
import de.tadris.fitness.data.MetricKind
import de.tadris.fitness.data.ProgramExercise
import de.tadris.fitness.data.ProgramSet
import de.tadris.fitness.data.WorkoutTypeManager
import de.tadris.fitness.data.isSupportedExerciseVideoUrl
import java.util.Locale
import java.util.UUID

/**
 * Exercise catalog: pick an exercise to add into [dayId], create new ones, edit
 * existing ones. The single place that decides which metric fields an exercise has.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerScreen(
    dayId: Long,
    onBack: () -> Unit,
    onPicked: () -> Unit,
) {
    val context = LocalContext.current
    val db = remember { Instance.getInstance(context).db }
    val exerciseDao = remember { db.exerciseDao() }
    val programDao = remember { db.trainingProgramDao() }
    val exercises by exerciseDao.getVisibleExercisesFlow().collectAsState(initial = emptyList())

    var query by remember { mutableStateOf("") }
    var editorTarget by remember { mutableStateOf<ExerciseEditorTarget?>(null) }

    val filtered = remember(exercises, query) {
        if (query.isBlank()) exercises
        else exercises.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    fun addToDay(exercise: Exercise) {
        val order = programDao.getExercisesOfDay(dayId).size
        val programExerciseId = programDao.insertProgramExercise(
            ProgramExercise(dayId = dayId, orderIndex = order, exerciseId = exercise.id)
        )
        defaultSetsFor(exercise.metricKind).forEachIndexed { index, set ->
            programDao.insertSet(set.copy(programExerciseId = programExerciseId, orderIndex = index))
        }
        onPicked()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.selectExercise)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.searchHint)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cancel) + ": " +
                                    stringResource(R.string.searchHint),
                            )
                        }
                    }
                } else null,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "create") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editorTarget = ExerciseEditorTarget(existing = null)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = stringResource(R.string.createExercise),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                if (exercises.isEmpty()) {
                    item(key = "empty") {
                        ExercisePickerEmptyState(
                            message = stringResource(R.string.hintNoExercises),
                            query = null,
                        )
                    }
                } else if (filtered.isEmpty()) {
                    item(key = "no_results") {
                        ExercisePickerEmptyState(
                            message = stringResource(R.string.hintNoExercisesInDay),
                            query = query.trim(),
                        )
                    }
                }
                items(filtered, key = { it.id }) { exercise ->
                    ExercisePickerCard(
                        exercise = exercise,
                        onPick = { addToDay(exercise) },
                        onEdit = { editorTarget = ExerciseEditorTarget(existing = exercise) },
                    )
                }
            }
        }
    }

    editorTarget?.let { target ->
        ExerciseEditorSheet(
            existing = target.existing,
            onDismiss = { editorTarget = null },
            onSaved = { saved ->
                editorTarget = null
                if (target.existing == null) addToDay(saved)
            },
        )
    }
}

private data class ExerciseEditorTarget(val existing: Exercise?)

@Composable
private fun ExercisePickerCard(
    exercise: Exercise,
    onPick: () -> Unit,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val imageBitmap by rememberExerciseImage(context, exercise.imagePath)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap!!,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
            } else {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = kindLabel(exercise.metricKind),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.editExercise),
                )
            }
        }
    }
}

@Composable
private fun ExercisePickerEmptyState(
    message: String,
    query: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (query == null) Icons.Default.Add else Icons.Default.Search,
                    contentDescription = null,
                )
            }
        }
        query?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = "“$it”",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun defaultSetsFor(kind: MetricKind): List<ProgramSet> = when {
    kind.usesReps -> List(3) { ProgramSet(programExerciseId = 0, orderIndex = it, reps = 10) }
    kind.usesDistance -> listOf(ProgramSet(programExerciseId = 0, orderIndex = 0, distanceMeters = 3000.0))
    else -> listOf(ProgramSet(programExerciseId = 0, orderIndex = 0, durationMillis = 60_000))
}

/** Create/edit an [Exercise]. Fields adapt to the chosen [MetricKind]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditorSheet(
    existing: Exercise?,
    onDismiss: () -> Unit,
    onSaved: (Exercise) -> Unit,
) {
    val context = LocalContext.current
    val db = remember { Instance.getInstance(context).db }
    val exerciseDao = remember { db.exerciseDao() }
    val workoutTypes = remember { WorkoutTypeManager.getInstance().getAllTypesSorted(context) }

    val kindLocked = remember(existing) {
        existing != null && exerciseDao.countUsages(existing.id) > 0
    }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var kind by remember { mutableStateOf(existing?.metricKind ?: MetricKind.WEIGHT_REPS) }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var tips by remember { mutableStateOf(existing?.tips ?: "") }
    var videoUrl by remember { mutableStateOf(existing?.videoUrl ?: "") }
    var imagePath by remember { mutableStateOf(existing?.imagePath) }
    var workoutTypeId by remember { mutableStateOf(existing?.workoutTypeId) }
    var typeExpanded by remember { mutableStateOf(false) }
    var kindExpanded by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }
    var energySettingsExpanded by remember {
        mutableStateOf(existing?.met != null || existing?.secondsPerRep != null)
    }
    var metText by remember { mutableStateOf(existing?.met?.toEditableEstimate() ?: "") }
    var secondsPerRepText by remember {
        mutableStateOf(existing?.secondsPerRep?.toEditableEstimate() ?: "")
    }
    var metError by remember { mutableStateOf(false) }
    var secondsPerRepError by remember { mutableStateOf(false) }
    var videoUrlError by remember { mutableStateOf(false) }

    val imageBitmap by rememberExerciseImage(context, imagePath)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val extension = when (context.contentResolver.getType(uri)?.lowercase()) {
                "image/gif" -> "gif"
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val relativePath = "exercise_images/${UUID.randomUUID()}.$extension"
            val target = checkNotNull(exerciseImageFile(context, relativePath))
            target.parentFile?.mkdirs()
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                imagePath = relativePath
            } catch (ignored: Exception) {
            }
        }
    }

    fun saveExercise() {
        if (name.trim().length < 2) {
            nameError = true
            return
        }
        val resolvedMet = metText.toEstimateOrNull(0.5, 25.0)
        val resolvedSecondsPerRep = if (kind.usesReps) {
            secondsPerRepText.toEstimateOrNull(0.1, 60.0)
        } else {
            null
        }
        metError = metText.isNotBlank() && resolvedMet == null
        secondsPerRepError = kind.usesReps &&
            secondsPerRepText.isNotBlank() &&
            resolvedSecondsPerRep == null
        val resolvedVideoUrl = videoUrl.trim().ifBlank { null }
        videoUrlError = resolvedVideoUrl != null &&
            !isSupportedExerciseVideoUrl(resolvedVideoUrl)
        if (metError || secondsPerRepError) {
            energySettingsExpanded = true
        }
        if (metError || secondsPerRepError || videoUrlError) {
            return
        }
        val resolvedTypeId = if (kind.recordable) workoutTypeId else null
        val saved = if (existing == null) {
            val exercise = Exercise(
                uuid = UUID.randomUUID().toString(),
                name = name.trim(),
                metricKind = kind,
                workoutTypeId = resolvedTypeId,
                description = description.trim().ifBlank { null },
                tips = tips.trim().ifBlank { null },
                videoUrl = resolvedVideoUrl,
                imagePath = imagePath,
                met = resolvedMet,
                secondsPerRep = resolvedSecondsPerRep,
            )
            exercise.copy(id = exerciseDao.insert(exercise))
        } else {
            val updated = existing.copy(
                name = name.trim(),
                metricKind = if (kindLocked) existing.metricKind else kind,
                workoutTypeId = resolvedTypeId,
                description = description.trim().ifBlank { null },
                tips = tips.trim().ifBlank { null },
                videoUrl = resolvedVideoUrl,
                imagePath = imagePath,
                met = resolvedMet,
                secondsPerRep = resolvedSecondsPerRep,
            )
            exerciseDao.update(updated)
            updated
        }
        onSaved(saved)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                        if (existing == null) R.string.createExercise else R.string.editExercise
                    ),
                    style = MaterialTheme.typography.headlineSmall,
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
            HorizontalDivider()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text(stringResource(R.string.exerciseName)) },
                isError = nameError,
                supportingText = if (nameError) {
                    { Text(stringResource(R.string.enterName)) }
                } else null,
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            ExposedDropdownMenuBox(
                expanded = kindExpanded,
                onExpandedChange = { if (!kindLocked) kindExpanded = it },
            ) {
                OutlinedTextField(
                    value = kindLabel(kind),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.metricKindTitle)) },
                    supportingText = {
                        Text(
                            text = if (kindLocked) {
                                stringResource(R.string.kindLockedMessage)
                            } else {
                                kindExample(kind)
                            },
                        )
                    },
                    trailingIcon = if (kindLocked) null else {
                        { ExposedDropdownMenuDefaults.TrailingIcon(expanded = kindExpanded) }
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                if (!kindLocked) {
                    ExposedDropdownMenu(
                        expanded = kindExpanded,
                        onDismissRequest = { kindExpanded = false },
                    ) {
                        MetricKind.entries.forEach { candidate ->
                            DropdownMenuItem(
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = kindLabel(candidate),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            text = kindExample(candidate),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    kind = candidate
                                    kindExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            if (kind.recordable) {
                val noneLabel = stringResource(R.string.recordableNone)
                val selectedLabel = workoutTypeId
                    ?.let { id -> workoutTypes.firstOrNull { it.id == id }?.title }
                    ?: noneLabel
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.recordableAs)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(noneLabel) },
                            onClick = { workoutTypeId = null; typeExpanded = false },
                        )
                        workoutTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.title) },
                                onClick = { workoutTypeId = type.id; typeExpanded = false },
                            )
                        }
                    }
                }
            }

            val automaticEstimateLabel = stringResource(R.string.energyEstimateAutomatic)
            val energySummary = listOfNotNull(
                metText.toEstimateOrNull(0.5, 25.0)?.let { value ->
                    stringResource(R.string.energyMetValue, value.toDisplayEstimate())
                },
                secondsPerRepText.toEstimateOrNull(0.1, 60.0)
                    ?.takeIf { kind.usesReps }
                    ?.let { value ->
                        stringResource(R.string.energySecondsPerRepValue, value.toDisplayEstimate())
                    },
            ).joinToString(" · ").ifBlank { automaticEstimateLabel }

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { energySettingsExpanded = !energySettingsExpanded }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.energyEstimateTitle),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = energySummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.editExercise),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (energySettingsExpanded) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        ) {
                            val stackFields = kind.usesReps && maxWidth < 420.dp
                            val metField: @Composable (Modifier) -> Unit = { fieldModifier ->
                                EnergyEstimateTextField(
                                    value = metText,
                                    onValueChange = { metText = it; metError = false },
                                    label = stringResource(R.string.energyMetLabel),
                                    placeholder = automaticEstimateLabel,
                                    isError = metError,
                                    supportingText = if (metError) {
                                        stringResource(R.string.energyMetRange)
                                    } else null,
                                    modifier = fieldModifier,
                                )
                            }
                            val secondsField: @Composable (Modifier) -> Unit = { fieldModifier ->
                                EnergyEstimateTextField(
                                    value = secondsPerRepText,
                                    onValueChange = { secondsPerRepText = it; secondsPerRepError = false },
                                    label = stringResource(R.string.energySecondsPerRepLabel),
                                    placeholder = automaticEstimateLabel,
                                    isError = secondsPerRepError,
                                    supportingText = if (secondsPerRepError) {
                                        stringResource(R.string.energySecondsPerRepRange)
                                    } else null,
                                    modifier = fieldModifier,
                                )
                            }
                            if (stackFields) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    metField(Modifier.fillMaxWidth())
                                    secondsField(Modifier.fillMaxWidth())
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    metField(Modifier.weight(1f))
                                    if (kind.usesReps) secondsField(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.descriptionOptional)) },
                minLines = 2,
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = tips,
                onValueChange = { tips = it },
                label = { Text(stringResource(R.string.tipsOptional)) },
                minLines = 2,
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = videoUrl,
                onValueChange = {
                    videoUrl = it
                    videoUrlError = false
                },
                label = { Text(stringResource(R.string.videoUrlOptional)) },
                isError = videoUrlError,
                supportingText = if (videoUrlError) {
                    { Text(stringResource(R.string.exercise_video_url_requirements)) }
                } else null,
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            imageBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(MaterialTheme.shapes.large)
                        .clickable {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                )
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val stackPhotoActions = maxWidth < 300.dp || LocalDensity.current.fontScale >= 1.4f
                val choosePhoto: () -> Unit = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
                if (imagePath == null) {
                    OutlinedButton(
                        onClick = choosePhoto,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.addPhoto))
                    }
                } else if (stackPhotoActions) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        OutlinedButton(onClick = choosePhoto, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.addPhoto))
                        }
                        TextButton(
                            onClick = { imagePath = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.removePhoto))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = choosePhoto, modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.addPhoto),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(
                            onClick = { imagePath = null },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = stringResource(R.string.removePhoto),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            }

            HorizontalDivider()
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                val stackActions = maxWidth < 300.dp || LocalDensity.current.fontScale >= 1.5f
                if (stackActions) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(onClick = ::saveExercise, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.save))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(onClick = ::saveExercise) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnergyEstimateTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isError: Boolean,
    supportingText: String?,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        isError = isError,
        supportingText = supportingText?.let { message -> { Text(message) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    )
}

private fun String.toEstimateOrNull(min: Double, max: Double): Double? =
    trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf(Double::isFinite)
        ?.takeIf { it in min..max }

private fun Double.toEditableEstimate(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()

private fun Double.toDisplayEstimate(): String =
    String.format(Locale.getDefault(), if (this % 1.0 == 0.0) "%.0f" else "%.1f", this)
