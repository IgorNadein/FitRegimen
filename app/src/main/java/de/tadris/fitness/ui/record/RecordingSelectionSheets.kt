/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack.
 */
package de.tadris.fitness.ui.record

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.tadris.fitness.Instance
import de.tadris.fitness.R
import de.tadris.fitness.data.RecordingType
import de.tadris.fitness.recording.information.InformationManager
import de.tadris.fitness.recording.information.RecordingInformation
import de.tadris.fitness.util.BluetoothDevicePreferences

internal fun hasBluetoothPickerPermissions(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT,
    ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_SCAN,
    ) == PackageManager.PERMISSION_GRANTED
}

/** Material 3 replacement for the legacy platform Bluetooth device dialog. */
@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BluetoothDevicePickerSheet(
    onDismiss: () -> Unit,
    onBluetoothUnavailable: () -> Unit,
    onDeviceSelected: (BluetoothDevice) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val adapter = remember(context) {
        context.getSystemService(BluetoothManager::class.java)?.adapter
    }
    val devices = remember { mutableStateListOf<BluetoothDevice>() }
    var isScanning by remember { mutableStateOf(true) }
    var scanFailed by remember { mutableStateOf(false) }
    val selectedAddress = remember(context) {
        BluetoothDevicePreferences(context).getAddress(BluetoothDevicePreferences.DEVICE_HEART_RATE)
    }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun addressOf(device: BluetoothDevice): String =
        runCatching { device.address }.getOrDefault("")

    fun addDevice(device: BluetoothDevice) {
        val address = addressOf(device)
        if (address.isNotBlank() && devices.none { addressOf(it) == address }) {
            devices.add(device)
        }
    }

    DisposableEffect(adapter) {
        if (adapter == null || !adapter.isEnabled) {
            isScanning = false
            onBluetoothUnavailable()
            onDispose { }
        } else {
            runCatching {
                adapter.bondedDevices
                    .sortedBy { device ->
                        runCatching { device.name }.getOrNull().orEmpty().lowercase()
                    }
                    .forEach(::addDevice)
            }.onFailure {
                scanFailed = true
            }

            val scanner = runCatching { adapter.bluetoothLeScanner }.getOrNull()
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    mainHandler.post { addDevice(result.device) }
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    mainHandler.post { results.forEach { addDevice(it.device) } }
                }

                override fun onScanFailed(errorCode: Int) {
                    mainHandler.post {
                        scanFailed = true
                        isScanning = false
                    }
                }
            }

            if (scanner == null) {
                isScanning = false
            } else {
                runCatching {
                    scanner.startScan(
                        emptyList(),
                        ScanSettings.Builder()
                            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                            .build(),
                        callback,
                    )
                }.onFailure {
                    scanFailed = true
                    isScanning = false
                }
            }

            onDispose {
                runCatching { scanner?.stopScan(callback) }
                mainHandler.removeCallbacksAndMessages(null)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        SheetHeader(
            title = stringResource(R.string.selectBluetoothDevice),
            onClose = onDismiss,
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_bluetooth),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
        )

        if (devices.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp)
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Text(
                        text = stringResource(R.string.scanning),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                Text(
                    text = stringResource(
                        if (scanFailed) {
                            R.string.recording_picker_bluetooth_scan_failed
                        } else {
                            R.string.recording_picker_no_bluetooth_devices
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (scanFailed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                if (isScanning) {
                    item(key = "scan_status") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.scanning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (scanFailed) {
                    item(key = "scan_failed") {
                        Text(
                            text = stringResource(R.string.recording_picker_bluetooth_scan_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                }
                items(devices, key = { addressOf(it) }) { device ->
                    val address = addressOf(device)
                    val name = runCatching { device.name }
                        .getOrNull()
                        ?.takeUnless { it == "null" || it.isBlank() }
                        ?: stringResource(R.string.unknown)
                    val selected = selectedAddress.isNotBlank() && selectedAddress == address
                    ListItem(
                        headlineContent = { Text(name) },
                        supportingContent = { Text(address) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_bluetooth),
                                contentDescription = null,
                            )
                        },
                        trailingContent = {
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = stringResource(R.string.recording_picker_selected),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        modifier = Modifier
                            .semantics {
                                role = Role.RadioButton
                                this.selected = selected
                            }
                            .clickable { onDeviceSelected(device) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

/** Material 3 replacement for the legacy platform metric-selection dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordingInformationPickerSheet(
    recordingType: RecordingType,
    slot: Int,
    onDismiss: () -> Unit,
    onInformationSelected: (RecordingInformation) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val information = remember(recordingType, context) {
        InformationManager(recordingType, context).displayableInformation
    }
    val selectedId = remember(recordingType, slot, context) {
        runCatching {
            Instance.getInstance(context)
                .userPreferences
                .recordingScreenInformationPreferences
                .getIdOfDisplayedInformation(recordingType.id, slot)
        }.getOrNull()
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        SheetHeader(
            title = stringResource(R.string.recording_picker_metric_title),
            subtitle = stringResource(R.string.recording_picker_metric_slot, slot + 1),
            onClose = onDismiss,
        )

        if (information.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.recording_picker_no_metrics),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                items(information, key = RecordingInformation::id) { item ->
                    val selected = item.id == selectedId
                    ListItem(
                        headlineContent = { Text(item.getTitle()) },
                        leadingContent = {
                            RadioButton(selected = selected, onClick = null)
                        },
                        modifier = Modifier
                            .semantics {
                                role = Role.RadioButton
                                this.selected = selected
                            }
                            .clickable { onInformationSelected(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(
    title: String,
    onClose: () -> Unit,
    subtitle: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 4.dp, end = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.invoke()
        if (leadingIcon != null) Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
        }
    }
}
