/*
 * Copyright (c) 2026 FitRegimen contributors
 *
 * This file is part of FitRegimen and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.tadris.fitness.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.aboutPrivacyPolicy)) },
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
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val sidePadding = ((maxWidth - 760.dp) / 2).coerceAtLeast(16.dp)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = sidePadding,
                    top = 12.dp,
                    end = sidePadding,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "intro") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.privacyPolicyIntro),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
                item(key = "local") {
                    PrivacySection(
                        title = stringResource(R.string.privacyPolicyLocalTitle),
                        body = stringResource(R.string.privacyPolicyLocalBody),
                    )
                }
                item(key = "collection") {
                    PrivacySection(
                        title = stringResource(R.string.privacyPolicyCollectionTitle),
                        body = stringResource(R.string.privacyPolicyCollectionBody),
                    )
                }
                item(key = "permissions") {
                    PrivacySection(
                        title = stringResource(R.string.privacyPolicyPermissionsTitle),
                        body = stringResource(R.string.privacyPolicyPermissionsBody),
                    )
                }
                item(key = "network") {
                    PrivacySection(
                        title = stringResource(R.string.privacyPolicyNetworkTitle),
                        body = stringResource(R.string.privacyPolicyNetworkBody),
                    )
                }
                item(key = "sharing") {
                    PrivacySection(
                        title = stringResource(R.string.privacyPolicySharingTitle),
                        body = stringResource(R.string.privacyPolicySharingBody),
                    )
                }
                item(key = "ai") {
                    PrivacySection(
                        title = stringResource(R.string.privacyPolicyAiTitle),
                        body = stringResource(R.string.privacyPolicyAiBody),
                    )
                }
                item(key = "control") {
                    PrivacySection(
                        title = stringResource(R.string.privacyPolicyControlTitle),
                        body = stringResource(R.string.privacyPolicyControlBody),
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacySection(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
