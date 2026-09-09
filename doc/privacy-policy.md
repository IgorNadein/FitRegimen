# FitRegimen privacy information

FitRegimen is designed primarily as a local, offline app and does not require a FitRegimen account. Optional network features are described below.

## Data stored by the app

Profiles, body measurements, workout plans, workout history, recorded routes, heart-rate and activity data, backups, and exercise media are stored on your device or in a location you choose. The FitRegimen developer does not operate an app backend that automatically receives this data. If Android system backup is enabled, the operating system may also include app data in a device or account backup controlled by the OS provider, not the FitRegimen developer.

## Developer data collection

FitRegimen contains no advertising or analytics SDK. The app does not automatically send personal, fitness, or location data to the FitRegimen developer.

## Permissions

Location, physical activity, Bluetooth, notifications, foreground service, wake lock, vibration, network, and legacy storage access support features such as workout recording, sensors, maps, media playback, exports, and background operation. Android controls the runtime permissions you grant, and you can revoke them in system settings.

## Network and external services

Optional features may contact third parties: online map-tile servers, exercise-video hosts, OpenStreetMap authentication and upload, links and email apps, and HTTP export destinations you configure. These services receive the data needed for the request and apply their own policies. A configured automatic export can send workout or backup files after recording. If you choose an http:// destination, that transfer may be unencrypted.

## App updates

Opening Updates contacts GitHub to check the latest release. Downloading an update contacts GitHub and its asset servers. They receive ordinary request information, including your IP address; no profile, nutrition, workout or backup data is sent. APK files remain in the app cache and are shared with the Android installer only when you request installation. Android controls permission to install from FitRegimen.

[Update mechanism](updates.md).

## Sharing, files, and backups

Sharing or export happens when you choose it or configure an export target. App-created backups and exercise media remain in local or user-selected storage until you share or export them. After data is sent or shared, the recipient or external provider controls its copy.

## AI prompts

FitRegimen does not contact an AI service or send data automatically: prompts are created on your device. If you include profiles, goals, preferences, restrictions, or a selected training program, those details can appear in the prompt. They leave the app only when you copy or share the prompt with a service you choose; that service’s privacy policy then applies. The prompt asks the service to use personal details only for calculations and not reproduce them in the finished file, but FitRegimen cannot guarantee how a third-party service behaves. Review the selected data before sending.

## Your control

You can edit or delete app records and profiles, remove files from chosen storage, revoke system permissions, or clear the app’s data. Clearing local data cannot recall copies that were already exported or shared.

FitRegimen is based on FitoTrack by Jannis Scheibe and contributors. This document
describes the FitRegimen fork; its [source code](https://github.com/IgorNadein/FitRegimen)
is available for inspection.
