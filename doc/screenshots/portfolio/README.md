# Current FitRegimen screens

Captured on 2026-09-09 from the signed **0.1.0 release APK**, package
`com.fitregimen.app`, on a fresh Android 14 / API 34 x86_64 emulator.
Resolution: 1080 × 1920; app language: English; light theme. Android's status-bar
demo mode sets the clock to 10:00. Screens are direct `adb exec-out screencap -p`
outputs, without compositing or generated UI.

The data comes from [program.json](../../demo/program.json) and
[nutrition.json](../../demo/nutrition.json). These are fictional sample fixtures.

| File | Reproduce |
| --- | --- |
| `program-builder.png` | Import program → ⋮ → Edit program. |
| `program-sets.png` | Editor → Quick walkthrough → Bodyweight squat. |
| `workout.png` | Start program → Start workout → Start workout → first active manual set. |
| `result.png` | Complete the two manual sets and the 20-second timer. |
| `nutrition.png` | Import nutrition plan → scroll to the meal cards. |
| `ai-import.png` | Food → + → AI nutrition plan → scroll to Generate prompt / Import finished JSON. |

The older numbered images in the parent directory and upstream store metadata are
historical assets. The four READMEs use this directory for the portfolio gallery.
