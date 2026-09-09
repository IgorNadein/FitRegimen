/* Added in FitRegimen. Licensed under GPLv3 or later. */
package de.tadris.fitness.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal class UpdateTransfer {
    suspend fun latest(): UpdateRelease = withContext(Dispatchers.IO) {
        request(UPDATE_API, "application/vnd.github+json") { connection ->
            when (connection.responseCode) {
                404 -> throw UpdateException(UpdateError.NO_RELEASE)
                403, 429 -> throw UpdateException(UpdateError.RATE_LIMIT)
            }
            requireUpdate(connection.responseCode == 200, UpdateError.NETWORK)
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    requireUpdate(output.size() + count <= 2 * 1024 * 1024, UpdateError.INVALID_RELEASE)
                    output.write(buffer, 0, count)
                }
            }
            parseUpdateRelease(output.toByteArray())
        }
    }

    suspend fun download(directory: File, release: UpdateRelease, progress: (Int) -> Unit,
                         validate: (File) -> Unit): File = withContext(Dispatchers.IO) {
        requireUpdate(directory.isDirectory || directory.mkdirs(), UpdateError.INVALID_DOWNLOAD)
        val partial = File(directory, "FitRegimen-${release.versionCode}.part")
        val target = File(directory, "FitRegimen-${release.versionCode}.apk")
        try {
            request(release.apkUrl, "application/octet-stream") { connection ->
                requireUpdate(connection.responseCode == 200, UpdateError.NETWORK)
                connection.inputStream.use { input ->
                    copyUpdate(input, partial, release.size, release.sha256, progress)
                }
            }
            currentCoroutineContext().ensureActive()
            validate(partial)
            currentCoroutineContext().ensureActive()
            requireUpdate(!target.exists() || target.delete(), UpdateError.INVALID_DOWNLOAD)
            requireUpdate(partial.renameTo(target), UpdateError.INVALID_DOWNLOAD)
            // Only this updater's files, never the general cache or user exports.
            directory.listFiles()?.filter { it != target }?.forEach { it.delete() }
            target
        } finally {
            partial.delete()
        }
    }

    private suspend fun <T> request(address: String, accept: String,
                                    block: suspend (HttpURLConnection) -> T): T {
        var current = address
        repeat(6) {
            currentCoroutineContext().ensureActive()
            requireUpdate(isUpdateTransportUrl(current), UpdateError.NETWORK)
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", accept)
                setRequestProperty("User-Agent", "FitRegimen-Android-Updater")
                setRequestProperty("Accept-Encoding", "identity")
            }
            try {
                if (connection.responseCode in setOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location")
                        ?: throw UpdateException(UpdateError.NETWORK)
                    current = URL(URL(current), location).toString()
                } else {
                    return block(connection)
                }
            } finally {
                connection.disconnect()
            }
        }
        throw UpdateException(UpdateError.NETWORK)
    }
}

/** Shared by download and cache validation; partial or modified bytes never become installable. */
internal suspend fun copyUpdate(input: InputStream, partial: File, expectedSize: Long,
                                expectedSha256: String, progress: (Int) -> Unit) {
    requireUpdate(expectedSize in 1..MAX_UPDATE_BYTES, UpdateError.INVALID_DOWNLOAD)
    val hash = MessageDigest.getInstance("SHA-256")
    var total = 0L
    var previous = -1
    try {
        partial.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                requireUpdate(total <= expectedSize, UpdateError.INVALID_DOWNLOAD)
                hash.update(buffer, 0, count)
                output.write(buffer, 0, count)
                val percent = (total * 100 / expectedSize).toInt()
                if (percent != previous) { progress(percent); previous = percent }
            }
        }
        currentCoroutineContext().ensureActive()
        requireUpdate(total == expectedSize && hash.digest().hexDigest().equals(expectedSha256, true),
            UpdateError.INVALID_DOWNLOAD)
    } catch (error: Exception) {
        partial.delete()
        throw error
    }
}

internal fun ByteArray.hexDigest(): String = joinToString("") { "%02x".format(it) }
internal fun verifyUpdateFile(file: File, release: UpdateRelease) {
    requireUpdate(file.isFile && file.length() == release.size, UpdateError.INVALID_DOWNLOAD)
    val hash = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            hash.update(buffer, 0, count)
        }
    }
    requireUpdate(hash.digest().hexDigest().equals(release.sha256, true), UpdateError.INVALID_DOWNLOAD)
}
