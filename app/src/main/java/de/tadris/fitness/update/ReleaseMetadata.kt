/* Added in FitRegimen. Licensed under GPLv3 or later. */
package de.tadris.fitness.update

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import java.io.IOException
import java.net.URI

internal const val UPDATE_REPOSITORY = "https://github.com/IgorNadein/FitRegimen"
internal const val UPDATE_RELEASES = "$UPDATE_REPOSITORY/releases/latest"
internal const val UPDATE_API = "https://api.github.com/repos/IgorNadein/FitRegimen/releases/latest"
internal const val MAX_UPDATE_BYTES = 200L * 1024 * 1024

internal enum class UpdateError {
    NETWORK, NO_RELEASE, RATE_LIMIT, INVALID_RELEASE, INVALID_DOWNLOAD,
    INVALID_APK, WRONG_PACKAGE, NOT_NEWER, WRONG_VERSION, WRONG_SIGNATURE, INSTALLER, BROWSER,
}

internal class UpdateException(val reason: UpdateError) : IOException(reason.name)
internal fun requireUpdate(value: Boolean, reason: UpdateError) {
    if (!value) throw UpdateException(reason)
}

internal data class AppIdentity(val packageName: String, val versionCode: Long, val signers: Set<String>)
internal data class UpdateRelease(
    val versionName: String,
    val versionCode: Long,
    val pageUrl: String,
    val apkUrl: String,
    val size: Long,
    val sha256: String,
)

/** The same numeric Version code convention as Palenok/12609; never compare display names. */
internal fun parseUpdateRelease(json: ByteArray): UpdateRelease {
    try {
        val root = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build().readTree(json)
        requireUpdate(root.isObject && root["draft"]?.isBoolean == true &&
            !root["draft"].booleanValue() && root["prerelease"]?.isBoolean == true &&
            !root["prerelease"].booleanValue(), UpdateError.INVALID_RELEASE)
        val tag = root.path("tag_name").asText()
        requireUpdate(tag.matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+(?:[-.][A-Za-z0-9.-]+)?")), UpdateError.INVALID_RELEASE)
        val codes = Regex("(?im)^Version code:[ \\t]*(\\d+)[ \\t]*$")
            .findAll(root.path("body").asText()).toList()
        val code = codes.singleOrNull()?.groupValues?.get(1)?.toLongOrNull()
        requireUpdate(code != null && code in 1..Int.MAX_VALUE.toLong(), UpdateError.INVALID_RELEASE)
        val page = root.path("html_url").asText()
        requireUpdate(page == "$UPDATE_REPOSITORY/releases/tag/$tag", UpdateError.INVALID_RELEASE)
        val name = "FitRegimen-${tag.removePrefix("v")}.apk"
        val assets = root.path("assets")
        requireUpdate(assets.isArray, UpdateError.INVALID_RELEASE)
        val apk = assets.filter { it.path("name").asText() == name }.singleOrNull()
            ?: throw UpdateException(UpdateError.INVALID_RELEASE)
        val url = apk.path("browser_download_url").asText()
        requireUpdate(url == "$UPDATE_REPOSITORY/releases/download/$tag/$name", UpdateError.INVALID_RELEASE)
        val size = apk.path("size")
        requireUpdate(size.isIntegralNumber && size.canConvertToLong() &&
            size.longValue() in 1..MAX_UPDATE_BYTES, UpdateError.INVALID_RELEASE)
        val digest = apk.path("digest").asText()
        requireUpdate(digest.matches(Regex("sha256:[a-fA-F0-9]{64}")), UpdateError.INVALID_RELEASE)
        return UpdateRelease(tag.removePrefix("v"), code!!, page, url, size.longValue(),
            digest.removePrefix("sha256:").lowercase())
    } catch (error: UpdateException) {
        throw error
    } catch (_: Exception) {
        throw UpdateException(UpdateError.INVALID_RELEASE)
    }
}

internal fun validateUpdateIdentity(installed: AppIdentity, candidate: AppIdentity, expectedCode: Long) {
    requireUpdate(candidate.packageName == installed.packageName, UpdateError.WRONG_PACKAGE)
    requireUpdate(candidate.versionCode > installed.versionCode, UpdateError.NOT_NEWER)
    requireUpdate(candidate.versionCode == expectedCode, UpdateError.WRONG_VERSION)
    requireUpdate(installed.signers.isNotEmpty() && installed.signers == candidate.signers,
        UpdateError.WRONG_SIGNATURE)
}

/** GitHub release downloads redirect to GitHub's asset CDN; HTTP and unrelated hosts are rejected. */
internal fun isUpdateTransportUrl(address: String): Boolean = runCatching {
    val uri = URI(address)
    uri.scheme == "https" && uri.userInfo == null && uri.port == -1 && uri.fragment == null &&
        uri.host in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
}.getOrDefault(false)
