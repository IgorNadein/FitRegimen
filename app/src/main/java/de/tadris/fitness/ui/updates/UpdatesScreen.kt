/* Added in FitRegimen. Licensed under GPLv3 or later. */
package de.tadris.fitness.ui.updates

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.tadris.fitness.BuildConfig
import de.tadris.fitness.R
import de.tadris.fitness.update.UPDATE_RELEASES
import de.tadris.fitness.update.UpdateError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(onBack: () -> Unit) {
    val vm: UpdatesViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var browserError by remember { mutableStateOf(false) }
    val release = state.release
    val available = release != null && release.versionCode > BuildConfig.VERSION_CODE
    val busy = state.phase != UpdatePhase.IDLE
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.updatesTitle)) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.actionBack))
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.updatesDescription), style = MaterialTheme.typography.bodyMedium)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.updatesCurrent, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.titleMedium)
                    Text(when {
                        !vm.supported -> stringResource(R.string.updatesDebug)
                        state.phase == UpdatePhase.CHECKING -> stringResource(R.string.updatesChecking)
                        state.error != null -> stringResource(R.string.updatesFailed)
                        available -> stringResource(R.string.updatesAvailable, release!!.versionName)
                        state.checked -> stringResource(R.string.updatesLatest)
                        else -> stringResource(R.string.updatesSummary)
                    })
                    state.error?.let { Text(stringResource(it.messageResource()), color = MaterialTheme.colorScheme.error) }
                    if (state.permissionRequired) Text(stringResource(R.string.updatesPermission))
                    when (state.phase) {
                        UpdatePhase.CHECKING -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        UpdatePhase.DOWNLOADING, UpdatePhase.CANCELLING -> {
                            LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                            Text(if (state.phase == UpdatePhase.CANCELLING) stringResource(R.string.updatesCancelling)
                                else stringResource(R.string.updatesProgress, state.progress))
                            TextButton(onClick = vm::cancel, enabled = state.phase != UpdatePhase.CANCELLING) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                        UpdatePhase.VERIFYING -> {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(stringResource(R.string.updatesVerifying))
                        }
                        UpdatePhase.IDLE -> if (available && vm.supported) {
                            if (state.apkPath != null) Button(onClick = vm::install, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.updatesInstall))
                            } else Button(onClick = vm::download, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.updatesDownload))
                            }
                        }
                    }
                    if (vm.supported) OutlinedButton(onClick = vm::check, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.updatesCheck))
                    }
                    TextButton(onClick = {
                        browserError = runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release?.pageUrl ?: UPDATE_RELEASES)))
                        }.isFailure
                    }) { Text(stringResource(R.string.updatesWhatsNew)) }
                    if (browserError) Text(stringResource(R.string.aboutCannotOpenLink), color = MaterialTheme.colorScheme.error)
                }
            }
            Text(stringResource(R.string.updatesInstallExplanation), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun UpdateError.messageResource(): Int = when (this) {
    UpdateError.NETWORK -> R.string.updatesErrorNetwork
    UpdateError.NO_RELEASE -> R.string.updatesErrorNoRelease
    UpdateError.RATE_LIMIT -> R.string.updatesErrorRateLimit
    UpdateError.INVALID_RELEASE -> R.string.updatesErrorRelease
    UpdateError.INVALID_DOWNLOAD -> R.string.updatesErrorDownload
    UpdateError.INVALID_APK -> R.string.updatesErrorApk
    UpdateError.WRONG_PACKAGE -> R.string.updatesErrorPackage
    UpdateError.NOT_NEWER -> R.string.updatesErrorOld
    UpdateError.WRONG_VERSION -> R.string.updatesErrorVersion
    UpdateError.WRONG_SIGNATURE -> R.string.updatesErrorSignature
    UpdateError.INSTALLER -> R.string.updatesErrorInstaller
    UpdateError.BROWSER -> R.string.aboutCannotOpenLink
}
