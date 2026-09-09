# In-app APK updates

Available from **FitRegimen 0.2.0** in **Settings → Updates**. Users with 0.1.0 install
0.2.0 manually once, over the existing signed app. Future releases can then be
checked, downloaded and passed to the Android installer from this screen.

## User flow

1. Opening the update screen requests the latest stable release from
   `https://api.github.com/repos/IgorNadein/FitRegimen/releases/latest`.
2. A newer numeric Android version enables **Download update**. Downloading is
   explicit, displays progress and can be cancelled. Leaving the screen cancels the
   operation; rotation keeps it. A stalled socket may take up to its 15-second timeout
   to notice cancellation. Partial files are removed.
3. **Install update** checks the file again. If necessary, Android opens its
   per-app **Install unknown apps** setting. Allow FitRegimen, return and tap
   **Install update** again. Android displays its own confirmation dialog.
4. Installing over the existing release with the same signing key preserves app data.
   Declining the installer does not uninstall or clear the app. Debug builds use a
   separate package and show an explanation instead of update controls.

The app does not check at startup, schedule background checks, download automatically,
or install silently. Checking an already current version shows that no update is needed.
An offline check displays a retryable error. Verified downloads can be reused after
reopening the screen, provided the latest release metadata still matches them.

## Release contract

- Publish a stable, non-draft release in **IgorNadein/FitRegimen**, with tag `vX.Y.Z`.
- Increment `versionCode` in `app/build.gradle`; do not infer Android update ordering
  from the human-facing version name. Put this exact standalone field in release notes:

  ```text
  Version code: 1622
  ```

- Attach exactly one `FitRegimen-X.Y.Z.apk` under that tag. Its package must be
  `com.fitregimen.app`, its versionCode must match the notes, and it must be signed
  with the existing release key. Keep that key and its password outside Git.
- The asset must have a nonzero size of at most 200 MiB and a `sha256:` digest in
  GitHub API metadata. The updater fails closed if metadata or digest is missing.
- Mark the stable release latest. CI APK artifacts alone do not constitute a release.
  The [installation guide](INSTALL.md) describes signing and publishing.

The updater accepts only the expected repository/tag/asset URLs and HTTPS redirects
on GitHub hosts. It limits metadata to 2 MiB and APK data to the declared size, verifies
SHA-256, and checks the parsed APK package, numeric version and signing certificates
against the installed app. It repeats file and identity checks before installation.
Only `cache/updates/` is exposed through the existing FileProvider. SHA-256 detects
corrupt bytes; matching the installed signing certificate also rejects APKs signed
with another key. Android remains responsible for final signature/install verification.

## Privacy

GitHub and its asset CDN receive normal HTTPS request information, including the
network IP address and an app-specific User-Agent. No authentication token, account,
profile, nutrition, workout, route, or backup data is attached to these requests.
The downloaded APK stays in the local app cache; the app shares its URI with the
Android package installer only when the user requests installation.

## Implementation and verification

The flow and `Version code:` release-note convention follow the existing **Palenok**
and **12609** projects by Igor Nadein. FitRegimen implements the same approach using
its own Compose navigation, lifecycle ViewModel, Jackson parser and FileProvider.
Palenok was reviewed locally at `android/app/src/main/java/org/palenok/palenok_lba/`
(`GitHubUpdates.kt` and `UpdatesSettings.kt`); 12609 at
`offline-beauty-crm/app/src/main/java/com/offlinebeautycrm/MainActivity.kt`.

JVM tests cover release parsing, missing or hostile metadata, transport URL filtering,
package/version/signature mismatches, download corruption and truncation, cancellation,
progress, and cache modification. Android installer behavior is verified manually;
CI runs JVM tests and builds, not device tests.

For a disposable emulator bootstrap build only, the version defaults can be overridden
with `-PfitregimenVersionCode=1620 -PfitregimenVersionName=0.0.0-update-test`.
Sign it with the same key, then exercise the updater against a newer published release.
Never publish the bootstrap APK or use it to downgrade an existing user installation.

References: [GitHub releases API](https://docs.github.com/en/rest/releases/releases)
and [Android install-source permission](https://android-developers.googleblog.com/2017/08/making-it-safer-to-get-apps-on-android-o.html).
