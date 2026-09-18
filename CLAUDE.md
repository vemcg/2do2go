# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

2do2go is an Android app (Kotlin + Jetpack Compose), a companion to
[MicroTasking](https://github.com/vemcg/MicroTasking) (sibling repo `../MicroTasking`): a
traditional to-do list that reads the *same* Google Sheet MicroTasking uses (each tab is a list,
each checked row is an item), with priority set via an Eisenhower matrix (important x urgent)
instead of a flat scale. Where MicroTasking pushes semi-random prompts, 2do2go is pull —
no background alarms, no notifications.

Planned but not built: a "Move to To-Do" hand-off from MicroTasking that lands a task here with a
priority quadrant already set. See `PUNCH_LIST.md` item 1 and MicroTasking's `PUNCH_LIST.md`
item 8 — it needs a cross-app bridge design decision (Sheet write-back vs. on-device IPC) before
any of it gets built.

## Commands

Requires an Android SDK; `local.properties` (gitignored) must contain `sdk.dir=<path>`.

- Build debug APK: `./gradlew assembleDebug`
- Run all unit tests: `./gradlew testDebugUnitTest`
- Run one test class: `./gradlew testDebugUnitTest --tests "com.twodo2go.app.ToDoDataTest"`
- Run one test method: `./gradlew testDebugUnitTest --tests "com.twodo2go.app.ToDoDataTest.parseToDoCsv_importsOnlyCheckedRows"`
- Fast compile check without running tests: `./gradlew compileDebugKotlin`

Manually trigger a build for a non-`main` branch (pushes to other branches do **not**
auto-trigger the release workflow, same convention as MicroTasking):
`gh workflow run "Build & release APK" --ref <branch>`.

## Architecture

Three source files under `app/src/main/java/com/twodo2go/app/`:

- **`MainActivity.kt`** — the Activity plus every Compose screen: Settings (paste/QR-scan the
  Sheet URL, Sync Lists), Lists overview, List detail, the Eisenhower-matrix picker dialog
  (`QuadrantDialog`/`QuadrantRow`/`QuadrantCell`), add-item dialog, QR scanner (ML Kit barcode
  scanning, copied from MicroTasking's `QrScannerScreen`).
- **`ToDoData.kt`** — data model (`ToDoItem`, `Quadrant`), JSON read/write helpers
  (SharedPreferences-backed, no Room/DB — same convention as MicroTasking's `TaskPool.kt`), CSV
  row → item parsing (`parseToDoCsv`), and the merge-on-resync policy (`mergeImportedToDoItems`).
- **`SheetImport.kt`** — generic Google Sheet tab discovery + per-tab CSV fetch
  (`fetchSheetTabs`), adapted from MicroTasking's `MainActivity.kt` Sheet-import functions but
  kept free of any `ToDoItem`-specific mapping so it's just "give me every tab's raw CSV."

**Priority model**: not a flat field — `ToDoItem.important`/`urgent` are independent booleans set
by which quadrant of the matrix widget was tapped. `ToDoItem.priorityScore()`
(`important*2 + urgent`) is the sort key, computed on read, not stored. See `SPEC.md` for the
quadrant table and the reasoning behind the 2x importance weighting (a tunable starting point).

**Merge-on-resync policy, deliberately asymmetric with MicroTasking's**: `mergeImportedToDoItems`
only *adds* newly-checked sheet rows not already present by id — it never removes or overwrites an
existing item just because its sheet row disappeared or got unchecked, since an item already
being triaged/worked here shouldn't vanish because of an edit made elsewhere. (MicroTasking's
`mergeImportedManagedTasks` is stricter because its sheet is the authoritative *category* list;
2do2go's sheet is only ever a source of new items, never a mirror to sync down to.)

**Google Sheet import**: same mechanics as MicroTasking (tab names via the `.xlsx` export's
zipped `workbook.xml`, each tab's rows via the `gviz` CSV export, column A as the enabled
checkbox, `description`/`link` matched by header text) — but no Apps Script provisioning tooling
here, since 2do2go always points at a spreadsheet MicroTasking's own template/onboarding already
set up. Don't add sheet-provisioning scripts here; that tooling lives in MicroTasking's repo.

**Testing**: plain JUnit, no Robolectric — everything in `ToDoData.kt`/`SheetImport.kt` operates
on strings/plain objects rather than a real `Context`, so there's nothing that needs a simulated
Android framework (unlike MicroTasking's `TaskDeliveryTest`, which needs real
`SharedPreferences`).

**Docs convention**, same as MicroTasking — read before starting non-trivial work: `SPEC.md`
(intended behavior, including "Not implemented" section), `PUNCH_LIST.md` (scoped-but-not-started
feature work), `DEFECTS.md` (numbered bug write-ups, empty so far).

**Release pipeline** (`.github/workflows/release-apk.yml`, `scripts/generate_install_page.py`):
same shape as MicroTasking's (debug-signed APK via a checked-in debug keystore — a different key
than MicroTasking's, generated fresh for this repo — GitHub Release tagged `vBASE-N`, install
page with QR code(s) deployed to GitHub Pages), but trimmed: no `--template-url` step, since the
install page tells the user to reuse the Sheet they already set up for MicroTasking rather than
create a new one.
