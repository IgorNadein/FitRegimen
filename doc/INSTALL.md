# Install FitRegimen

[English](#android-installation) · [Русский](#установка-на-android)

## Android installation

Requires **Android 8.0 (API 26) or newer**. No account or API key is required.

1. Open [FitRegimen Releases](https://github.com/IgorNadein/FitRegimen/releases/latest).
2. Download **FitRegimen-0.1.0.apk** from **Assets** to your phone. This is the signed
   release APK; the source ZIP and unsigned CI artifact are not installable apps.
3. Open the APK. If Android asks, allow **Install unknown apps** for the browser or
   file manager you used, then return to the installer and tap **Install**.
4. Open **FitRegimen**. Initial units/weight setup is optional for the demo.
5. Follow the [5-minute demo](demo/README.md). GPS and activity permissions are not
   needed for its manual sets and timer.

The package is `com.fitregimen.app`; it installs separately from upstream FitoTrack
and from the CI debug package `com.fitregimen.app.debug`. Updates to the release
package require the same signing key. Before replacing an existing installation
signed with a different key, export its backup; uninstalling removes its local data.

Optional verification on a computer, from the folder containing the release assets:

```sh
sha256sum -c SHA256SUMS.txt
adb install -r FitRegimen-0.1.0.apk
```

Download all files listed in `SHA256SUMS.txt` before checking it. The release signing
certificate SHA-256 fingerprint is:

```text
c4467a4fb5a75c238509f6bf2cf44db6e8ac17efc72fd358abf0f1456a3df7bd
```

## Установка на Android

Нужен **Android 8.0 (API 26) или новее**. Аккаунт и API-ключ не требуются.

1. Откройте [последний релиз](https://github.com/IgorNadein/FitRegimen/releases/latest).
2. Скачайте **FitRegimen-0.1.0.apk** из раздела **Assets** на телефон. Это подписанная
   release-сборка. Архив исходников и неподписанный артефакт CI установить нельзя.
3. Откройте APK. Если Android запросит разрешение, включите **Установку неизвестных
   приложений** для использованного браузера или файлового менеджера, затем
   вернитесь к установщику и нажмите **Установить**.
4. Запустите **FitRegimen**. Начальную настройку единиц и веса для демо можно пропустить.
5. Пройдите [демо за 5 минут](demo/README.ru.md). Для его подходов и таймера не нужны
   разрешения GPS и распознавания активности.

Пакет `com.fitregimen.app` устанавливается отдельно от FitoTrack и debug-сборки
`com.fitregimen.app.debug`. Для обновления нужна та же подпись. Если ранее установлен
APK с другой подписью, перед удалением сохраните резервную копию: удаление приложения
стирает его локальные данные. Команды проверки контрольных сумм и установки через ADB
приведены выше.

## Building from source

Use **JDK 21** and the included **Gradle 9.5.1** wrapper. Android Gradle Plugin is
**9.2.1**. Set `ANDROID_HOME` to your SDK directory, or create a local, ignored
`local.properties` with `sdk.dir=/absolute/path/to/Android/Sdk`.

```sh
sdkmanager 'platforms;android-37.0' 'build-tools;36.0.0'
./gradlew --no-daemon testDebugUnitTest assembleDebug assembleRelease
```

| Output | Use |
| --- | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | Installable development build, signed with the build machine's debug key. |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | Optimized release build; sign before installation. |
| `app/build/reports/tests/testDebugUnitTest/index.html` | JVM test report. |
| `app/build/outputs/mapping/release/mapping.txt` | R8 mapping for release diagnostics. |

The [Android workflow](../.github/workflows/android.yml) runs tests and both builds
on pushes to `master`, `v*` tags, pull requests, and manual dispatches. Artifacts
include the debug APK, unsigned release APK, R8 mapping, and test reports. Debug keys
on separate CI machines can differ; use the signed GitHub release for a stable
installation. CI currently runs JVM tests, not emulator/instrumentation tests.

## Maintainer release procedure

1. Update `versionName` and increment `versionCode` in `app/build.gradle`, update the
   changelog and release notes, then run the commands above.
2. Sign the unsigned release with your existing private keystore. Keep both the
   keystore and its password outside the repository and retain them for updates.
   Use `apksigner sign --ks /private/path/release.p12 --ks-pass file:/private/path/password
   --ks-key-alias fitregimen --out FitRegimen-VERSION.apk
   app/build/outputs/apk/release/app-release-unsigned.apk` (as one command).
3. Run `apksigner verify --verbose --print-certs FitRegimen-VERSION.apk`; install that
   exact APK on a fresh emulator/device and follow the demo through backup restore.
4. Commit the release documentation and screenshots. Push the commit, wait for a
   successful Android workflow, tag that commit, and publish its signed APK,
   `program.json`, `nutrition.json`, demo backup and `SHA256SUMS.txt` in GitHub Releases.
5. Use the same private key for future updates. CI intentionally receives no signing
   secrets and produces no automatically published release.

Action references: [Android SDK setup](https://github.com/android-actions/setup-android),
[JDK setup](https://github.com/actions/setup-java),
[Gradle setup and wrapper validation](https://github.com/gradle/actions).
