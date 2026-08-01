/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack and is licensed under GPLv3 or later.
 */
package de.tadris.fitness.data

import java.net.InetAddress
import java.net.URI
import java.util.Locale

/**
 * Shared trust boundary for exercise videos entered locally or received in a
 * program file. Only direct progressive-video files and HLS manifests on a
 * public HTTPS host are accepted; web pages and local/content URIs are not.
 */
internal fun isSupportedExerciseVideoUrl(value: String): Boolean = runCatching {
    val uri = URI(value.trim())
    val host = uri.host?.trim('[', ']')?.trimEnd('.')?.lowercase(Locale.ROOT).orEmpty()
    uri.scheme.equals("https", ignoreCase = true) &&
        uri.userInfo == null &&
        host.isNotBlank() &&
        !isForbiddenExerciseVideoHost(host) &&
        uri.path.orEmpty().substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.ROOT) in SUPPORTED_EXERCISE_VIDEO_EXTENSIONS
}.getOrDefault(false)

internal fun isExerciseHlsVideoUrl(value: String): Boolean = runCatching {
    isSupportedExerciseVideoUrl(value) &&
        URI(value.trim()).path.orEmpty().endsWith(".m3u8", ignoreCase = true)
}.getOrDefault(false)

private fun isForbiddenExerciseVideoHost(host: String): Boolean {
    if (host == "localhost" || host.endsWith(".localhost")) return true

    if (host.all { it.isDigit() || it == '.' }) {
        val parts = host.split('.')
        if (
            parts.size != 4 ||
            parts.any { part ->
                part.isEmpty() ||
                    (part.length > 1 && part.startsWith('0')) ||
                    (part.toIntOrNull()?.let { it !in 0..255 } ?: true)
            }
        ) {
            return true // Reject ambiguous integer/octal numeric host spellings.
        }
        val octets = parts.map(String::toInt)
        return octets[0] == 0 ||
            octets[0] == 10 ||
            octets[0] == 127 ||
            (octets[0] == 169 && octets[1] == 254) ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168) ||
            octets[0] >= 224
    }

    if (host.startsWith("0x", ignoreCase = true)) return true
    if (':' in host) {
        val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return true
        val firstByte = address.address.firstOrNull()?.toInt()?.and(0xff) ?: return true
        return address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress ||
            (firstByte and 0xfe) == 0xfc // IPv6 unique-local fc00::/7.
    }

    return false
}

private val SUPPORTED_EXERCISE_VIDEO_EXTENSIONS = setOf(
    "mp4",
    "m4v",
    "webm",
    "mkv",
    "mov",
    "3gp",
    "ts",
    "m2ts",
    "ogv",
    "flv",
    "m3u8",
)
