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
 * FitoTrack is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package de.tadris.fitness.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import de.tadris.fitness.R

/**
 * A UI-less bridge that immediately opens Android's system sharesheet.
 *
 * Keeping this as an Activity preserves the existing export call sites while avoiding an
 * app-owned intermediary dialog or screen.
 */
class ShareFileActivity : FitoTrackActivity() {

    companion object {
        const val EXTRA_FILE_URI = "file_uri"
        const val EXTRA_FILE_MIME = "mime_type"
    }

    override fun getActivityThemeRes(): Int = R.style.AppThemeNoActionbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableTransitionAnimation()

        val sourceUri = parseSourceUri(intent.getStringExtra(EXTRA_FILE_URI))
        if (sourceUri == null) {
            Toast.makeText(this, R.string.fileNotFound, Toast.LENGTH_LONG).show()
            finishWithoutAnimation()
            return
        }

        val resolverMimeType = runCatching { contentResolver.getType(sourceUri) }.getOrNull()
        val mimeType = resolveShareMimeType(
            explicitMimeType = intent.getStringExtra(EXTRA_FILE_MIME),
            resolverMimeType = resolverMimeType,
        )
        val displayName = resolveShareFileName(
            providerDisplayName = null,
            uriLastPathSegment = sourceUri.lastPathSegment,
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            setDataAndType(sourceUri, mimeType)
            clipData = ClipData.newUri(contentResolver, displayName, sourceUri)
            putExtra(Intent.EXTRA_STREAM, sourceUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(Intent.createChooser(sendIntent, getString(R.string.shareFile)))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.sharingFailed, Toast.LENGTH_LONG).show()
        } finally {
            finishWithoutAnimation()
        }
    }

    private fun finishWithoutAnimation() {
        finish()
        disableTransitionAnimation()
    }

    @Suppress("DEPRECATION")
    private fun disableTransitionAnimation() {
        overridePendingTransition(0, 0)
    }
}

internal const val DEFAULT_SHARE_FILE_NAME = "fitregimen-export"

internal fun parseSourceUri(rawUri: String?): Uri? = rawUri
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.let { runCatching { Uri.parse(it) }.getOrNull() }
    ?.takeIf { !it.scheme.isNullOrBlank() }

internal fun resolveShareMimeType(
    explicitMimeType: String?,
    resolverMimeType: String?,
): String = explicitMimeType
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: resolverMimeType
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    ?: "application/octet-stream"

internal fun resolveShareFileName(
    providerDisplayName: String?,
    uriLastPathSegment: String?,
): String = sequenceOf(providerDisplayName, uriLastPathSegment)
    .mapNotNull { candidate ->
        candidate
            ?.trim()
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.takeIf { it.isNotEmpty() && it != "." && it != ".." }
    }
    .firstOrNull()
    ?: DEFAULT_SHARE_FILE_NAME
