/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack.
 *
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.Exercise
import de.tadris.fitness.ui.programs.ExerciseEditorSheet
import de.tadris.fitness.ui.programs.kindLabel
import de.tadris.fitness.ui.programs.rememberExerciseImage

/** Settings destination for maintaining the reusable exercise catalog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageExercisesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val exerciseDao = remember { Instance.getInstance(context).db.exerciseDao() }
    val exercises by exerciseDao.getVisibleExercisesFlow().collectAsState(initial = emptyList())

    var query by remember { mutableStateOf("") }
    var editorTarget by remember { mutableStateOf<Exercise?>(null) }
    var creatingExercise by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Exercise?>(null) }
    val filtered = remember(exercises, query) {
        filterExercisesForManagement(exercises, query)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.customExercisesTitle),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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
        floatingActionButton = {
            FloatingActionButton(onClick = { creatingExercise = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.createExercise),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.searchHint)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.clearExerciseSearch),
                            )
                        }
                    }
                } else null,
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )

            when {
                exercises.isEmpty() -> ManageExercisesEmptyState(
                    text = stringResource(R.string.hintManageExercises),
                )

                filtered.isEmpty() -> ManageExercisesEmptyState(
                    text = stringResource(R.string.noExerciseSearchResults, query.trim()),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(filtered, key = { it.id }) { exercise ->
                        ManageExerciseListItem(
                            exercise = exercise,
                            onEdit = { editorTarget = exercise },
                            onDelete = { deleteTarget = exercise },
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 80.dp))
                    }
                }
            }
        }
    }

    if (creatingExercise) {
        ExerciseEditorSheet(
            existing = null,
            onDismiss = { creatingExercise = false },
            onSaved = { creatingExercise = false },
        )
    }

    editorTarget?.let { exercise ->
        ExerciseEditorSheet(
            existing = exercise,
            onDismiss = { editorTarget = null },
            onSaved = { editorTarget = null },
        )
    }

    deleteTarget?.let { exercise ->
        val usageCount = remember(exercise.id) { exerciseDao.countUsages(exercise.id) }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.deleteExerciseTitle)) },
            text = {
                Text(
                    stringResource(
                        if (usageCount > 0) R.string.deleteExerciseInUseMessage
                        else R.string.deleteExerciseMessage,
                        exercise.name,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        exerciseDao.update(exercise.hideFromExerciseCatalog())
                        deleteTarget = null
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
private fun ManageExerciseListItem(
    exercise: Exercise,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val image by rememberExerciseImage(context, exercise.imagePath)
    ListItem(
        headlineContent = {
            Text(
                text = exercise.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = kindLabel(exercise.metricKind),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            if (image != null) {
                Image(
                    bitmap = image!!,
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
                        Text(
                            text = exercise.name.firstOrNull()?.uppercase() ?: "•",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.deleteExercise),
                )
            }
        },
        modifier = Modifier.clickable(onClick = onEdit),
    )
}

@Composable
private fun ManageExercisesEmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

internal fun filterExercisesForManagement(
    exercises: List<Exercise>,
    query: String,
): List<Exercise> {
    val normalizedQuery = query.trim()
    return exercises.filter { exercise ->
        !exercise.hidden && (
            normalizedQuery.isEmpty() ||
                exercise.name.contains(normalizedQuery, ignoreCase = true)
            )
    }
}

internal fun Exercise.hideFromExerciseCatalog(): Exercise = copy(hidden = true)
