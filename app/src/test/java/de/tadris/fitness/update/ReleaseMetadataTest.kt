package de.tadris.fitness.update

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.Assert.*
import org.junit.Test

class ReleaseMetadataTest {
    private fun metadata(): ObjectNode = JsonMapper().readTree("""{
      "draft":false,"prerelease":false,"tag_name":"v0.2.0",
      "body":"Release notes\n\nVersion code: 1622\n",
      "html_url":"$UPDATE_REPOSITORY/releases/tag/v0.2.0",
      "assets":[{"name":"FitRegimen-0.2.0.apk","size":1234,
      "browser_download_url":"$UPDATE_REPOSITORY/releases/download/v0.2.0/FitRegimen-0.2.0.apk",
      "digest":"sha256:${"ab".repeat(32)}"}]
    }""") as ObjectNode
    private fun parse(node: ObjectNode) = parseUpdateRelease(node.toString().toByteArray())
    private fun asset(node: ObjectNode) = node["assets"][0] as ObjectNode
    private fun rejects(change: (ObjectNode) -> Unit) {
        val node = metadata().also(change)
        assertEquals(UpdateError.INVALID_RELEASE, assertThrows(UpdateException::class.java) { parse(node) }.reason)
    }
    @Test fun stableReleaseUsesNumericCodeAndExactApk() {
        val release = parse(metadata())
        assertEquals(1622L, release.versionCode)
        assertEquals("0.2.0", release.versionName)
        assertEquals(1234L, release.size)
        assertEquals("ab".repeat(32), release.sha256)
    }
    @Test fun missingDuplicateAndOverflowVersionCodesFail() {
        listOf("", "Version code: -1", "Version code: 0", "Version code: 2147483648",
            "Version code: 1622\nVersion code: 1623", "versionCode 1622").forEach { body ->
            rejects { it.put("body", body) }
        }
    }
    @Test fun windowsReleaseNotesAreAccepted() {
        assertEquals(1622L, parse(metadata().put("body", "Notes\r\n\r\nVersion code: 1622\r\n")).versionCode)
    }
    @Test fun draftsPrereleasesAndMissingFlagsFail() {
        rejects { it.put("draft", true) }; rejects { it.put("prerelease", true) }
        rejects { it.remove("draft") }; rejects { it.put("prerelease", "false") }
    }
    @Test fun releaseAndAssetMustBelongToExactRepositoryAndTag() {
        rejects { it.put("html_url", "https://github.com/attacker/FitRegimen/releases/tag/v0.2.0") }
        rejects { asset(it).put("browser_download_url", "$UPDATE_REPOSITORY/releases/download/v0.1.0/FitRegimen-0.2.0.apk") }
        rejects { asset(it).put("browser_download_url", "https://example.com/app.apk") }
        rejects { asset(it).put("name", "app-debug.apk") }
        rejects { it.put("tag_name", "../../other") }
    }
    @Test fun missingOrDuplicateMatchingAssetsFail() {
        rejects { it.putArray("assets") }
        rejects { node -> node.withArray("assets").add(asset(node).deepCopy()) }
    }
    @Test fun digestAndBoundedIntegralSizeAreRequired() {
        rejects { asset(it).remove("digest") }
        rejects { asset(it).put("digest", "sha256:abc") }
        rejects { asset(it).put("size", 0) }
        rejects { asset(it).put("size", MAX_UPDATE_BYTES + 1) }
        rejects { asset(it).put("size", 1234.5) }
        rejects { asset(it).put("size", "1234") }
    }
    @Test fun malformedOrTrailingJsonFails() {
        listOf("", "null", "[]", metadata().toString() + " {}").forEach {
            assertEquals(UpdateError.INVALID_RELEASE,
                assertThrows(UpdateException::class.java) { parseUpdateRelease(it.toByteArray()) }.reason)
        }
    }
    @Test fun redirectsOnlyAllowHttpsGitHubHosts() {
        listOf(UPDATE_API, "$UPDATE_REPOSITORY/releases/latest",
            "https://release-assets.githubusercontent.com/a?token=x", "https://objects.githubusercontent.com/a")
            .forEach { assertTrue(it, isUpdateTransportUrl(it)) }
        listOf("http://github.com/a", "https://github.com.evil.test/a", "https://evil.test/a",
            "https://user@github.com/a", "https://github.com:443/a", "https://github.com/a#b",
            "file:///tmp/app.apk", "https://github.com@evil.test/a", "not a URL")
            .forEach { assertFalse(it, isUpdateTransportUrl(it)) }
    }
    private val installed = AppIdentity("com.fitregimen.app", 1621, setOf("release-key"))
    @Test fun newerApkWithSamePackageAndSignersIsAccepted() {
        validateUpdateIdentity(installed, installed.copy(versionCode = 1622), 1622)
    }
    @Test fun incompatibleIdentityIsRejectedBeforeInstall() {
        val cases = listOf(
            installed.copy(packageName = "other", versionCode = 1622) to UpdateError.WRONG_PACKAGE,
            installed to UpdateError.NOT_NEWER,
            installed.copy(versionCode = 1620) to UpdateError.NOT_NEWER,
            installed.copy(versionCode = 1623) to UpdateError.WRONG_VERSION,
            installed.copy(versionCode = 1622, signers = emptySet()) to UpdateError.WRONG_SIGNATURE,
            installed.copy(versionCode = 1622, signers = setOf("debug-key")) to UpdateError.WRONG_SIGNATURE,
            installed.copy(versionCode = 1622, signers = setOf("release-key", "other")) to UpdateError.WRONG_SIGNATURE,
        )
        cases.forEach { (candidate, reason) ->
            assertEquals(reason, assertThrows(UpdateException::class.java) {
                validateUpdateIdentity(installed, candidate, 1622)
            }.reason)
        }
    }
}
