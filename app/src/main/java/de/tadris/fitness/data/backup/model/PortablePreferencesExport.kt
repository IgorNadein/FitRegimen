/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data.backup.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * User-facing, device-independent settings stored in a full backup.
 *
 * Values stay explicitly typed because Android's SharedPreferences distinguishes
 * Int, Long and Float at read time. A nullable container in DataContainer also
 * distinguishes an old backup (which had no settings section) from a new backup
 * whose user simply kept every setting at its default value.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PortablePreferencesExport(
    val values: Map<String, PortablePreferenceValueExport> = emptyMap(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PortablePreferenceValueExport(
    val type: String = "",
    val stringValue: String? = null,
    val booleanValue: Boolean? = null,
    val intValue: Int? = null,
    val longValue: Long? = null,
    val floatValue: Float? = null,
    val stringSetValue: Set<String>? = null,
)
