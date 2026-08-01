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
 */

package de.tadris.fitness.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.documentfile.provider.DocumentFile
import de.tadris.fitness.Instance
import de.tadris.fitness.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterfaceSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val instance = remember { Instance.getInstance(context) }
    val prefs = remember { instance.userPreferences }
    val sharedPrefs = remember { prefs.preferences }

    // --- Unit system ---
    var unitSystemId by remember { mutableStateOf(prefs.distanceUnitSystemId) }

    // --- Map preferences ---
    var mapStyle by remember { mutableStateOf(prefs.mapStyle) }
    var trackStyleMode by remember { mutableStateOf(prefs.trackStyleMode) }
    var trackStyle by remember { mutableStateOf(prefs.trackStyle) }
    var offlineMapDir by remember { mutableStateOf(prefs.offlineMapDirectoryName ?: "") }

    // --- Appearance ---
    var themeSetting by remember { mutableStateOf(prefs.themeSetting) }
    var useSystemColors by remember { mutableStateOf(prefs.useSystemColors) }
    var dateFormat by remember { mutableStateOf(prefs.dateFormatSetting) }
    var timeFormat by remember { mutableStateOf(prefs.timeFormatSetting) }
    var firstDayOfWeek by remember { mutableStateOf(sharedPrefs.getString("firstDayOfWeek", "system") ?: "system") }
    var energyUnit by remember { mutableStateOf(prefs.energyUnit) }

    // --- File picker ---
    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            sharedPrefs.edit().putString("offlineMapDirectoryName", uri.toString()).apply()
            offlineMapDir = uri.toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preferencesUserInterfaceTitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {

            // Language (API 33+ only — system per-app language picker)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    ClickableSettingsItem(
                        title = stringResource(R.string.language),
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            )
                        },
                    )
                }
            }

            item {
                ListPreferenceItem(
                    title = stringResource(R.string.pref_unit_system),
                    entries = stringArrayResource(R.array.pref_unit_systems),
                    entryValues = stringArrayResource(R.array.pref_unit_system_values),
                    selectedValue = unitSystemId,
                    onValueChange = { value ->
                        unitSystemId = value
                        prefs.distanceUnitSystemId = value
                    },
                )
            }

            item {
                SettingsCategory(title = stringResource(R.string.preferencesCategoryMap))
            }

            item {
                ListPreferenceItem(
                    title = stringResource(R.string.mapStyle),
                    entries = stringArrayResource(R.array.pref_map_layers),
                    entryValues = stringArrayResource(R.array.pref_map_layers_values),
                    selectedValue = mapStyle,
                    onValueChange = { value ->
                        mapStyle = value
                        prefs.mapStyle = value
                    },
                )
            }

            item {
                ListPreferenceItem(
                    title = stringResource(R.string.prefTrackStyleUsageTitle),
                    entries = stringArrayResource(R.array.pref_track_style_usage),
                    entryValues = stringArrayResource(R.array.pref_track_style_usage_values),
                    selectedValue = trackStyleMode,
                    onValueChange = { value ->
                        trackStyleMode = value
                        prefs.trackStyleMode = value
                    },
                )
            }

            item {
                ListPreferenceItem(
                    title = stringResource(R.string.trackStyle),
                    entries = stringArrayResource(R.array.pref_track_style),
                    entryValues = stringArrayResource(R.array.pref_track_style_values),
                    selectedValue = trackStyle,
                    onValueChange = { value ->
                        trackStyle = value
                        prefs.trackStyle = value
                    },
                )
            }

            item {
                ClickableSettingsItem(
                    title = stringResource(R.string.pref_map_directory),
                    supporting = offlineMapDir.ifEmpty { null },
                    onClick = { folderPickerLauncher.launch(null) },
                )
            }

            item {
                ClickableSettingsItem(
                    title = stringResource(R.string.downloadMapsTitle),
                    onClick = {
                        val mapDir = prefs.offlineMapDirectoryName
                        if (mapDir != null) {
                            val docFile = DocumentFile.fromTreeUri(context, Uri.parse(mapDir))
                            if (docFile != null && docFile.canWrite()) {
                                context.startActivity(
                                    Intent(context, DownloadMapsActivity::class.java)
                                )
                                return@ClickableSettingsItem
                            }
                        }
                        Toast.makeText(
                            context,
                            R.string.downloadMapsSpecifyDirectory,
                            Toast.LENGTH_LONG
                        ).show()
                    },
                )
            }

            item {
                SettingsCategory(title = stringResource(R.string.preferencesCategoryAppearance))
            }

            item {
                ListPreferenceItem(
                    title = stringResource(R.string.theme),
                    entries = stringArrayResource(R.array.pref_theme_setting),
                    entryValues = stringArrayResource(R.array.pref_theme_setting_values),
                    selectedValue = themeSetting,
                    onValueChange = { value ->
                        themeSetting = value
                        prefs.themeSetting = value
                        instance.themes.updateDarkModeSetting()
                    },
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SwitchSettingsItem(
                        title = stringResource(R.string.pref_use_system_colors),
                        supporting = stringResource(R.string.pref_use_system_colors_summary),
                        checked = useSystemColors,
                        onCheckedChange = { value ->
                            useSystemColors = value
                            prefs.useSystemColors = value
                        },
                    )
                }
            }

            item {
                ListPreferenceItem(
                    title = stringResource(R.string.preferenceDateFormat),
                    entries = stringArrayResource(R.array.pref_date_format),
                    entryValues = stringArrayResource(R.array.pref_date_format_values),
                    selectedValue = dateFormat,
                    onValueChange = { value ->
                        dateFormat = value
                        prefs.dateFormatSetting = value
                    },
                )
            }

            item {
                ListPreferenceItem(
                    title = stringResource(R.string.preferenceTimeFormat),
                    entries = stringArrayResource(R.array.pref_time_format),
                    entryValues = stringArrayResource(R.array.pref_time_format_values),
                    selectedValue = timeFormat,
                    onValueChange = { value ->
                        timeFormat = value
                        prefs.timeFormatSetting = value
                    },
                )
            }

            item {
                ListPreferenceItem(
                    title = stringResource(R.string.preferenceFirstDayOfWeek),
                    entries = stringArrayResource(R.array.pref_first_day_of_week),
                    entryValues = stringArrayResource(R.array.pref_first_day_of_week_values),
                    selectedValue = firstDayOfWeek,
                    onValueChange = { value ->
                        firstDayOfWeek = value
                        sharedPrefs.edit().putString("firstDayOfWeek", value).apply()
                    },
                )
            }

            item {
                ListPreferenceItem(
                    title = stringResource(R.string.pref_energy_unit),
                    entries = stringArrayResource(R.array.pref_energy_units),
                    entryValues = stringArrayResource(R.array.pref_energy_unit_values),
                    selectedValue = energyUnit,
                    onValueChange = { value ->
                        energyUnit = value
                        prefs.energyUnit = value
                    },
                )
            }
        }
    }

}
