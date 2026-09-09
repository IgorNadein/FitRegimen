# Five-minute portfolio demo

[English](README.md) · [Русский](README.ru.md)

Install the [signed APK](https://github.com/IgorNadein/FitRegimen/releases/latest)
using the [installation guide](../INSTALL.md). Download these files to the phone's
**Downloads** folder (also attached to the release):

- [program.json](https://raw.githubusercontent.com/IgorNadein/FitRegimen/master/doc/demo/program.json): two manual sets and a 20-second timer, followed by a rest day.
- [nutrition.json](https://raw.githubusercontent.com/IgorNadein/FitRegimen/master/doc/demo/nutrition.json): one fictional day with three meals and food estimates.
- [portfolio-backup.ftb](https://github.com/IgorNadein/FitRegimen/releases/download/v0.1.0/portfolio-backup.ftb): an app-exported backup of the completed demo.

These are interface fixtures, not a personal training or diet recommendation.
No account, external AI or GPS is needed. Button names below use the English UI;
the same controls are translated when another app language is selected.

## 1. Import and inspect a program

Open **Programs → + → Import program**, then select `program.json` through the
Android file picker. If **Recent** is empty, open its side menu → **Downloads**.

**Expected:** a program named **Portfolio demo** opens. Its **⋮ → Edit program**
screen has **Quick walkthrough** and **Rest day**. Tap **Quick walkthrough**, then
**Bodyweight squat** to inspect repetitions, sets and execution policy. Back out
without changing the sample to keep the walkthrough predictable.

<img src="../screenshots/portfolio/program-builder.png" width="280" alt="Program builder">

## 2. Complete the workout

On the program detail screen tap **Start program**; it schedules day 1 for today.
Then tap **Start workout** to open the overview and **Start workout** again to run it.

1. After the 3-second preparation, tap **Set complete** for the first 5-rep set.
2. Wait through the short rest/preparation, then tap **Set complete** for the second set.
3. Wait through the transition and the **Standing recovery** timer (20 seconds).
   The timed set finishes automatically.

For an interface demo, you can confirm the example sets without physically
performing them. No video download, GPS route or sensor connection is involved.

**Expected:** the completion screen reports three completed sets across two
exercises. Close it and inspect **Workouts**; the guided result is retained.
Editing the original plan later does not rewrite the completed session snapshot.

## 3. Explore nutrition and the AI import boundary

Open **Food → + → Import plan** and select `nutrition.json`.

**Expected:** **Portfolio menu** contains **Demo day** with **Breakfast**, **Lunch**
and **Dinner**. The food estimates total **1,290 kcal** (60 g protein, 34 g fat,
186 g carbohydrates). These are fictional sample values, not a full-day target.
Open a day or meal to inspect the editable tree.

For the AI workflow, open **Food → + → AI nutrition plan**. Inspect the goal,
profile and training-context controls, prompt generation and **Import finished JSON**.
You can import `nutrition.json` here instead of using the ordinary import in the
previous step. Import it only once if you want to avoid a second plan copy.
No actual AI request is necessary: the hand-authored sample uses the same format.

Training prompts are under **Programs → + → AI prompt**; their JSON responses
are imported through **Import program**. See [the exact checks and limits](../AI.md).

## 4. Back up and restore

Use a fresh demo installation when testing **Replace**, because it replaces local
app data. If you are using your normal installation, export your own backup first
or choose **Merge**.

1. Open **Settings → Database → Export Data → Backup**.
2. The `.ftb` backup is a ZIP archive. Save it using a file manager offered by Android's share sheet,
   or share it to a location from which you can download it back to the phone.
3. Return to **Import Data Backup**. Choose **Replace → Restore**, then select
   your exported ZIP (or the release's `portfolio-backup.ftb`). For existing data,
   **Merge** is the alternative to replacement.
4. Return to **Programs**, **Food** and **Workouts**.

**Expected:** the program, nutrition tree and completed workout result are available
after restoration. The bundled backup's session is dated **September 9, 2026**;
if today's program view shows no workout, open **Workouts** or select that date.
A standalone `program.json` carries the plan definition; the full backup carries
session history too. Importing JSON is not the same operation as restoring a ZIP.

For a visible replacement check on a disposable installation, export the completed
demo, rename **Portfolio demo** to **Temporary edit**, then restore using **Replace**.
The original name and the completed workout should return.

## Verification record

The release APK was exercised on a fresh Android 14 / API 34 x86_64 emulator:
program file import → builder → manual sets and timer → result/history → nutrition
import → full backup export → clear the demo app data → replace restore → result and plans checked again.
Screenshots are direct captures of that APK. [Capture details](../screenshots/portfolio/README.md).
The sample JSON files also pass the application's readers in
[PortfolioDemoTest](../../app/src/test/java/de/tadris/fitness/data/backup/PortfolioDemoTest.kt).
