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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.TrainingProgram
import de.tadris.fitness.data.backup.ProgramImporter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingProgramsScreen(
    onBack: () -> Unit,
    onOpenProgram: (Long) -> Unit,
    onProgramCreated: (Long) -> Unit,
) {
    val context = LocalContext.current
    val dao = remember { Instance.getInstance(context).db.trainingProgramDao() }
    val programs by dao.getProgramsFlow().collectAsState(initial = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val container = context.contentResolver.openInputStream(uri)?.use { ProgramImporter.read(it) }
                ?: throw java.io.IOException("Cannot open file")
            val id = ProgramImporter.import(context, container)
            onOpenProgram(id)
        } catch (e: Exception) {
            importError = e.message ?: context.getString(R.string.errorImportFailed)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trainingPrograms)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch("*/*") }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_storage),
                            contentDescription = stringResource(R.string.importProgram),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.createTrainingProgram))
            }
        },
    ) { padding ->
        if (programs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.hintNoPrograms),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(contentPadding = padding) {
                items(programs, key = { it.id }) { program ->
                    TrainingProgramListItem(program = program, onClick = { onOpenProgram(program.id) })
                }
            }
        }
    }

    importError?.let { msg ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text(stringResource(R.string.error)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { importError = null }) { Text(stringResource(R.string.okay)) }
            },
        )
    }

    if (showCreateDialog) {
        CreateTrainingProgramDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, cycleLengthDays ->
                val program = TrainingProgram(
                    uuid = UUID.randomUUID().toString(),
                    name = name,
                    cycleLengthDays = cycleLengthDays,
                    createdAt = System.currentTimeMillis(),
                )
                val id = dao.insertProgram(program)
                showCreateDialog = false
                onProgramCreated(id)
            },
        )
    }
}

@Composable
private fun TrainingProgramListItem(program: TrainingProgram, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(program.name) },
        supportingContent = program.description?.takeIf { it.isNotBlank() }?.let { desc -> { Text(desc) } },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun CreateTrainingProgramDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, cycleLengthDays: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var cycleLength by remember { mutableStateOf("7") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var cycleLengthError by remember { mutableStateOf<String?>(null) }

    val enterNameMsg = stringResource(R.string.enterName)
    val invalidNumberMsg = stringResource(R.string.errorEnterValidNumber)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.createTrainingProgram)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text(stringResource(R.string.programName)) },
                    isError = nameError != null,
                    supportingText = nameError?.let { e -> { Text(e) } },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = cycleLength,
                    onValueChange = { cycleLength = it; cycleLengthError = null },
                    label = { Text(stringResource(R.string.cycleLengthDays)) },
                    isError = cycleLengthError != null,
                    supportingText = cycleLengthError?.let { e -> { Text(e) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.trim().length <= 2) { nameError = enterNameMsg; return@TextButton }
                val days = cycleLength.toIntOrNull()
                if (days == null || days < 1 || days > 90) { cycleLengthError = invalidNumberMsg; return@TextButton }
                onCreate(name.trim(), days)
            }) { Text(stringResource(R.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
