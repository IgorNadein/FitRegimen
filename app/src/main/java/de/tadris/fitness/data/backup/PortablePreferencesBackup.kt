/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data.backup

import android.content.SharedPreferences
import de.tadris.fitness.data.backup.model.PortablePreferenceValueExport
import de.tadris.fitness.data.backup.model.PortablePreferencesExport
import java.io.IOException

/**
 * Serializes only settings that remain meaningful on another installation.
 *
 * OAuth tokens, Bluetooth addresses, content-URI permissions, filesystem paths,
 * migration markers and derived projection versions are intentionally excluded.
 * Profiles already own weight and stride, so their legacy preference mirrors are
 * excluded as well and are rebuilt from the restored active profile.
 */
internal object PortablePreferencesBackup {

    private val exactKeys = setOf(
        "announcementMode",
        "askForComment",
        "autoBackupInterval",
        "autoPause",
        "autoTimeoutPeriod",
        "dateFormat",
        "energyUnit",
        "firstDayOfWeek",
        "hasLowerTargetSpeedLimit",
        "hasUpperTargetSpeedLimit",
        "intervalsIncludePause",
        "lowerTargetSpeedLimit",
        "mapStyle",
        "showOnLockScreen",
        "spokenUpdateDistancePeriod2",
        "spokenUpdateTimePeriod",
        "statisticsAggregationSpan",
        "statisticsSelectedTypes",
        "themeSetting",
        "timeForCurrentSpeed",
        "timeFormat",
        "trackStyle",
        "trackStyleUsage",
        "unitSystem",
        "upperTargetSpeedLimit",
        "useAverageForCurrentSpeed",
        "useSystemColors",
        "zoomWithVolumeButtons",
    )

    private val portablePrefixes = listOf(
        "announcement_",
        "information_display_",
    )

    fun isPortableKey(key: String): Boolean =
        key in exactKeys || portablePrefixes.any(key::startsWith)

    fun export(values: Map<String, *>): PortablePreferencesExport = PortablePreferencesExport(
        values = values.entries
            .asSequence()
            .filter { (key, _) -> isPortableKey(key) }
            .filter { (key, value) -> isValidDecodedValue(key, value) }
            .mapNotNull { (key, value) -> encode(value)?.let { key to it } }
            .toMap(linkedMapOf()),
    )

    /** Keeps statistics filters attached to a custom type that was renamed on merge. */
    fun remapWorkoutTypeIds(
        export: PortablePreferencesExport,
        idMap: Map<String, String>,
    ): PortablePreferencesExport {
        if (idMap.isEmpty()) return export
        val selected = export.values["statisticsSelectedTypes"] ?: return export
        val values = selected.stringSetValue ?: return export
        return export.copy(
            values = export.values + (
                "statisticsSelectedTypes" to selected.copy(
                    stringSetValue = values.mapTo(linkedSetOf()) { idMap[it] ?: it },
                )
            ),
        )
    }

    @Throws(IOException::class)
    fun decode(export: PortablePreferencesExport): Map<String, Any> {
        if (export.values.size > MAX_PREFERENCE_COUNT) {
            throw IOException("Backup contains too many preferences")
        }
        return export.values.mapValuesTo(linkedMapOf()) { (key, value) ->
            if (!isPortableKey(key)) throw IOException("Backup contains a non-portable preference: $key")
            if (key.length > MAX_KEY_LENGTH) throw IOException("Backup preference key is too long")
            decodeValue(value).also { decoded ->
                if (!isValidDecodedValue(key, decoded)) {
                    throw IOException("Invalid value for backup preference: $key")
                }
            }
        }
    }

    /**
     * Replacement restores the backed-up settings exactly (missing keys reset to
     * app defaults). Merge only fills settings that have never been stored locally.
     */
    @Throws(IOException::class)
    fun restore(
        preferences: SharedPreferences,
        export: PortablePreferencesExport,
        replace: Boolean,
    ) {
        val values = decode(export)
        val existingKeys = preferences.all.keys
        val editor = preferences.edit()
        if (replace) {
            existingKeys.filter(::isPortableKey).forEach(editor::remove)
        }
        values.forEach { (key, value) ->
            if (!replace && key in existingKeys) return@forEach
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value as Set<String>)
                }
                else -> throw IOException("Unsupported preference value for $key")
            }
        }
        if (!editor.commit()) throw IOException("Unable to restore application preferences")
    }

    private fun encode(value: Any?): PortablePreferenceValueExport? = when (value) {
        is String -> PortablePreferenceValueExport(type = TYPE_STRING, stringValue = value)
        is Boolean -> PortablePreferenceValueExport(type = TYPE_BOOLEAN, booleanValue = value)
        is Int -> PortablePreferenceValueExport(type = TYPE_INT, intValue = value)
        is Long -> PortablePreferenceValueExport(type = TYPE_LONG, longValue = value)
        is Float -> PortablePreferenceValueExport(type = TYPE_FLOAT, floatValue = value)
        is Set<*> -> value.filterIsInstance<String>()
            .takeIf { it.size == value.size }
            ?.toSet()
            ?.let { PortablePreferenceValueExport(type = TYPE_STRING_SET, stringSetValue = it) }
        else -> null
    }

    private fun isValidDecodedValue(key: String, value: Any?): Boolean = when {
        key.startsWith("announcement_") -> value is Boolean
        key.startsWith("information_display_") -> value is String && value.isNotBlank()
        key in booleanKeys -> value is Boolean
        key in stringSetKeys -> value is Set<*> && value.all { it is String && it.isNotBlank() }
        key in intKeys -> value is Int && when (key) {
            "autoTimeoutPeriod" -> value in 0..60
            "timeForCurrentSpeed" -> value in 0..120
            "spokenUpdateTimePeriod" -> value in 0..60
            "statisticsAggregationSpan" -> value in 0..5
            else -> false
        }
        key in floatKeys -> value is Float && value.isFinite() && when (key) {
            "spokenUpdateDistancePeriod2" -> value in 0f..10f
            "lowerTargetSpeedLimit", "upperTargetSpeedLimit" -> value >= 0f
            else -> false
        }
        key in stringKeys -> value is String && value.isNotBlank() && when (key) {
            "announcementMode" -> value in setOf("always", "headphones")
            "autoBackupInterval" -> value in setOf("24", "48", "72", "168", "336")
            "dateFormat" -> value in setOf(
                "system", "yyyy-MM-dd", "yyyy.MM.dd", "yyyy/MM/dd",
                "dd-MM-yyyy", "dd.MM.yyyy", "dd/MM/yyyy", "MM/dd/yyyy",
            )
            "energyUnit" -> value in setOf("kcal", "joule")
            "firstDayOfWeek" -> value == "system" || value.toIntOrNull() in 1..7
            "themeSetting" -> value in setOf("0", "1", "2")
            "timeFormat" -> value in setOf("system", "12", "24")
            "trackStyleUsage" -> value in setOf("always", "diagram", "never")
            "unitSystem" -> value.toIntOrNull() in 1..5
            // Map/track style identifiers are extensible but must remain bounded strings.
            "mapStyle", "trackStyle" -> value.length <= 128
            else -> false
        }
        else -> false
    }

    @Throws(IOException::class)
    private fun decodeValue(value: PortablePreferenceValueExport): Any = when (value.type) {
        TYPE_STRING -> value.stringValue
            ?.takeIf { it.length <= MAX_STRING_LENGTH }
            ?: throw IOException("Invalid string preference")
        TYPE_BOOLEAN -> value.booleanValue ?: throw IOException("Invalid boolean preference")
        TYPE_INT -> value.intValue ?: throw IOException("Invalid integer preference")
        TYPE_LONG -> value.longValue ?: throw IOException("Invalid long preference")
        TYPE_FLOAT -> value.floatValue
            ?.takeIf(Float::isFinite)
            ?: throw IOException("Invalid float preference")
        TYPE_STRING_SET -> value.stringSetValue
            ?.takeIf { set ->
                set.size <= MAX_STRING_SET_SIZE && set.all { it.length <= MAX_STRING_LENGTH }
            }
            ?: throw IOException("Invalid string-set preference")
        else -> throw IOException("Unknown preference type: ${value.type}")
    }

    private const val TYPE_STRING = "string"
    private const val TYPE_BOOLEAN = "boolean"
    private const val TYPE_INT = "int"
    private const val TYPE_LONG = "long"
    private const val TYPE_FLOAT = "float"
    private const val TYPE_STRING_SET = "stringSet"
    private const val MAX_PREFERENCE_COUNT = 256
    private const val MAX_KEY_LENGTH = 128
    private const val MAX_STRING_LENGTH = 16_384
    private const val MAX_STRING_SET_SIZE = 256

    private val booleanKeys = setOf(
        "askForComment",
        "autoPause",
        "hasLowerTargetSpeedLimit",
        "hasUpperTargetSpeedLimit",
        "intervalsIncludePause",
        "showOnLockScreen",
        "useAverageForCurrentSpeed",
        "useSystemColors",
        "zoomWithVolumeButtons",
    )
    private val stringKeys = setOf(
        "announcementMode",
        "autoBackupInterval",
        "dateFormat",
        "energyUnit",
        "firstDayOfWeek",
        "mapStyle",
        "themeSetting",
        "timeFormat",
        "trackStyle",
        "trackStyleUsage",
        "unitSystem",
    )
    private val intKeys = setOf(
        "autoTimeoutPeriod",
        "spokenUpdateTimePeriod",
        "statisticsAggregationSpan",
        "timeForCurrentSpeed",
    )
    private val floatKeys = setOf(
        "lowerTargetSpeedLimit",
        "spokenUpdateDistancePeriod2",
        "upperTargetSpeedLimit",
    )
    private val stringSetKeys = setOf("statisticsSelectedTypes")
}
