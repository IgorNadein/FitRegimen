/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 * FitRegimen-specific attribution and Material 3 UI were added in this fork.
 */
package de.tadris.fitness.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import de.tadris.fitness.BuildConfig
import de.tadris.fitness.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val fitRegimenUrl = stringResource(R.string.urlRepository)
    val upstreamRepositoryUrl = stringResource(R.string.urlUpstreamRepository)
    val upstreamAuthorUrl = stringResource(R.string.urlAuthor)
    val noticeUrl = stringResource(R.string.urlCopyrightNotices)
    val translationUrl = stringResource(R.string.urlWeblate)
    val developerEmail = stringResource(R.string.emailAuthor)
    val feedbackSubject = stringResource(R.string.emailFeedbackSubject)
    val bugSubject = stringResource(R.string.emailBugReportSubject)
    val cannotOpenLink = stringResource(R.string.aboutCannotOpenLink)
    val noEmailApp = stringResource(R.string.aboutNoEmailApp)
    val contributors = stringArrayResource(R.array.contributors)

    fun launchExternal(intent: Intent, failureMessage: String) {
        runCatching { context.startActivity(intent) }
            .onFailure {
                scope.launch { snackbarHostState.showSnackbar(failureMessage) }
            }
    }

    fun openUrl(url: String) {
        launchExternal(Intent(Intent.ACTION_VIEW, url.toUri()), cannotOpenLink)
    }

    fun sendEmail(subject: String) {
        val mailUri = "mailto:$developerEmail?subject=${Uri.encode(subject)}".toUri()
        launchExternal(Intent(Intent.ACTION_SENDTO, mailUri), noEmailApp)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.aboutTitle)) },
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
                item(key = "header") {
                    AboutHeader()
                }

                item(key = "links_title") {
                    AboutSectionTitle(stringResource(R.string.aboutLinks))
                }
                item(key = "links") {
                    AboutGroup {
                        AboutActionRow(
                            title = stringResource(R.string.aboutSourceCode),
                            supporting = fitRegimenUrl.removePrefix("https://"),
                            iconRes = R.drawable.ic_code,
                            onClick = { openUrl(fitRegimenUrl) },
                        )
                        AboutDivider()
                        AboutActionRow(
                            title = stringResource(R.string.aboutPrivacyPolicy),
                            supporting = stringResource(R.string.aboutPrivacyPolicySummary),
                            iconRes = R.drawable.ic_privacy,
                            onClick = onOpenPrivacyPolicy,
                        )
                    }
                }

                item(key = "contact_title") {
                    AboutSectionTitle(stringResource(R.string.aboutContact))
                }
                item(key = "contact") {
                    AboutGroup {
                        AboutActionRow(
                            title = stringResource(R.string.aboutSendFeedback),
                            supporting = stringResource(
                                R.string.aboutSendFeedbackSummary,
                                developerEmail,
                            ),
                            iconRes = R.drawable.ic_email,
                            onClick = { sendEmail(feedbackSubject) },
                        )
                        AboutDivider()
                        AboutActionRow(
                            title = stringResource(R.string.aboutReportBug),
                            supporting = stringResource(
                                R.string.aboutReportBugSummary,
                                developerEmail,
                            ),
                            iconRes = R.drawable.ic_bug_report,
                            onClick = { sendEmail(bugSubject) },
                        )
                    }
                }

                item(key = "authors_title") {
                    AboutSectionTitle(stringResource(R.string.aboutAuthor))
                }
                item(key = "authors") {
                    AboutGroup {
                        AboutActionRow(
                            title = stringResource(R.string.fitRegimenAuthorName),
                            supporting = stringResource(R.string.fitRegimenAuthorRole),
                            iconRes = R.drawable.ic_person,
                            onClick = { openUrl(fitRegimenUrl) },
                        )
                        AboutDivider()
                        AboutActionRow(
                            title = stringResource(R.string.authorName),
                            supporting = stringResource(R.string.fitoTrackAuthorRole),
                            iconRes = R.drawable.ic_person,
                            onClick = { openUrl(upstreamAuthorUrl) },
                        )
                    }
                }

                item(key = "upstream_title") {
                    AboutSectionTitle(stringResource(R.string.aboutUpstreamTitle))
                }
                item(key = "upstream_links") {
                    AboutGroup {
                        AboutActionRow(
                            title = stringResource(R.string.aboutUpstreamSource),
                            supporting = stringResource(R.string.aboutUpstreamSourceSummary),
                            iconRes = R.drawable.ic_code,
                            onClick = { openUrl(upstreamRepositoryUrl) },
                        )
                        AboutDivider()
                        AboutActionRow(
                            title = stringResource(R.string.aboutOpenSourceLibraries),
                            supporting = stringResource(R.string.aboutNoticesSummary),
                            iconRes = R.drawable.ic_library_books,
                            onClick = { openUrl(noticeUrl) },
                        )
                        AboutDivider()
                        AboutActionRow(
                            title = stringResource(R.string.aboutHelpTranslating),
                            supporting = stringResource(R.string.aboutHelpTranslatingSummary),
                            iconRes = R.drawable.ic_language,
                            onClick = { openUrl(translationUrl) },
                        )
                    }
                }

                item(key = "contributors_title") {
                    AboutSectionTitle(stringResource(R.string.aboutContributors))
                }
                item(key = "contributors") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = contributors.joinToString(separator = " · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.aboutUnlistedContributors),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth < 480.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AboutLogo()
                    Spacer(Modifier.height(4.dp))
                    AboutHeaderText(textAlign = TextAlign.Center)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    AboutLogo()
                    AboutHeaderText(
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutLogo() {
    Image(
        painter = painterResource(R.drawable.logo_combined),
        contentDescription = stringResource(R.string.aboutAppLogoDescription),
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape),
    )
}

@Composable
private fun AboutHeaderText(
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.aboutFitRegimenSummary),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.aboutVersionValue, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelLarge,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AboutGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        content = content,
    )
}

@Composable
private fun AboutActionRow(
    title: String,
    supporting: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                text = supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun AboutDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun AboutSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}
