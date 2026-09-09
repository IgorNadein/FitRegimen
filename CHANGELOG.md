# FitRegimen changelog

This changelog covers the FitRegimen fork. Upstream activity recording and its
history belong to [FitoTrack](https://codeberg.org/jannis/FitoTrack/releases) by
Jannis Scheibe and contributors. FitRegimen release numbers start at 0.1.0;
the previous development APK inherited upstream's 16.2 label. Android versionCode
continues increasing (1621) to preserve the update ordering.

## [0.1.0] — 2026-09-09

First downloadable FitRegimen portfolio release.

### FitRegimen features included

- Training-program builder with workout/rest days, exercise catalog, sets, timing,
  execution policies, scheduling, and portable JSON import/export (formats v1–v3).
- Guided workout sessions with persisted state, manual confirmation, timers,
  recorder-linked results, and history snapshots independent of plan edits.
- Nutrition plans with days, meals, foods, energy/macronutrients, profile/program
  links, manual editing and a versioned JSON format.
- AI prompt preparation and validated import of externally generated training and
  nutrition plans; optional profile context, explicit copy/share, and import errors.
- Extended backups covering programs, nutrition, exercise catalog/media, profiles,
  session history and portable settings, with merge/replace restoration.
- Updated Compose navigation, modal-sheet sizing and native gesture handling.

### Fixed

- Full-backup writer/reader disagreement on Kotlin boolean names: active profiles
  and rest days now survive a round trip. Legacy `active` / `rest` fields remain
  readable. Added regression tests after finding this during the release walkthrough.

### Portfolio and delivery

- README in English, Russian, German and Spanish with explicit upstream attribution.
- Current program-builder, workout, nutrition and AI-import screenshots.
- Short demo program, example nutrition plan, restorable demo backup and walkthrough.
- GitHub Actions JVM tests, debug/release builds, APK artifacts and test reports.
- Signed release APK, installation guide and SHA-256 checksums.
- Regression checks for the actual demo JSON files through the app's import readers.

### Scope and limitations

- AI generation happens in an external assistant chosen by the user; the app prepares
  prompts and checks imported data. It does not validate personal suitability of plans.
- Core planning and manual/timed workouts work offline; optional remote services do not.
- CI runs JVM tests; the release demo is checked manually on an Android emulator.
- Existing upstream deprecation/resource and R8 service-provider warnings remain;
  this release does not claim a warning-free lint/build audit.

[0.1.0]: https://github.com/IgorNadein/FitRegimen/releases/tag/v0.1.0
