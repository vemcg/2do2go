# 2do2go Spec

A traditional to-do list, companion to MicroTasking, sharing MicroTasking's Google Sheet as its
list/item source. Unlike a plain mirror of the sheet, an item only reaches 2do2go once it's been
explicitly **referred** here from MicroTasking's task queue - see "Referral bridge" below for the
full lifecycle, negotiated directly between this repo's and MicroTasking's Claude Code sessions
and confirmed by the user on both sides (2026-09-18).

## Lists

- Each Google Sheet tab (excluding a README tab) is one to-do list, named after the tab.
- A tab with zero rows still counts as a live, empty list (`known_lists`, refreshed on every
  successful sync) - it shows up in the carousel (see "Screens") with "0 open" rather than not
  existing.

## Items

- **Ingestion is gated on referral.** A Sheet-sourced item only enters 2do2go once its hidden
  `Importance`/`Urgency` columns are populated - checked via the Apps Script Web App at sync time,
  never via the CSV/gviz export (see "Referral bridge"). A checked-but-unreferred row stays
  MicroTasking-only and never appears here at all; column A (enabled checkbox) and
  `description`/`link` (header-text matched, order-independent) are still read via the existing
  CSV/gviz path once a row does qualify.
- Ad-hoc items can also be added directly in the app, independent of the sheet
  (`id` prefix `local-` vs. `sheet-<list>-<description>` for referred rows). These have no
  underlying sheet row, so their priority and completion live locally only (see "Screens").
- Re-syncing the sheet only **adds** newly-qualifying rows not already present by id. It never
  removes or overwrites an existing item because its sheet row disappeared, its priority got
  cleared elsewhere, or the description changed underneath it (a changed description is a new id,
  so it reads as a new item, not an edit to the old one) - **except** the app's own "Complete (for
  now)" action (see "Referral bridge"), which removes its own item immediately rather than waiting
  for a resync. Rationale: an item already living in this to-do list may be mid-progress - a
  spreadsheet edit made somewhere else shouldn't silently delete it.

## Priority: Eisenhower matrix

Priority is two independent continuous values, `importance` and `urgency` (each `0f..1f`), not
four fixed quadrants and not booleans. Set by the exact tap/drag position on a square matrix
widget (`MatrixWidget`) - top-left is most important+urgent ("Do First"), matching the visual
layout below, just continuous instead of four discrete cells:

|                | **Urgent**     | **Not urgent** |
|----------------|-----------------|-----------------|
| **Important**     | Do First        | Schedule        |
| **Not important** | Delegate        | Eliminate       |

Display order is a computed score, `importance * importanceWeight + urgency`, descending, ties
broken by add time (older first). **`importanceWeight` is a user Settings value, not a hardcoded
formula** - MicroTasking always writes raw, unweighted importance/urgency; the weighting that
turns those into a ranking is entirely 2do2go's concern, adjustable in Settings (default `2.0`,
i.e. the old fixed `important*2 + urgent` ratio as a starting point, range `0.5`-`4.0`).

`Quadrant`/`quadrant()` still exist as a coarse 0.5-threshold bucketing of the continuous values,
used only for badge label/color - the real ranking always uses the continuous score above.

2do2go keeps its **own** matrix widget too (same continuous behavior, not the old 4-quadrant tap),
used for:
- **Ad-hoc/local items**, which have no sheet row to have arrived pre-triaged on - a fresh one
  starts at `(0, 0)` (Eliminate) until triaged in-app.
- **Re-triaging** any item afterward (open it, drag the marker). This only updates the local copy
  - it does not write back to the Sheet even for a Sheet-sourced item, consistent with "the sheet
  is a source of new items, not a mirror to sync down to" (see "Items").

Since ingestion is gated on referral, a Sheet-sourced item never arrives untriaged - referral
itself requires a matrix touch on MicroTasking's side.

## Referral bridge (MicroTasking → Sheet → 2do2go)

1. User adds a task to a Sheet tab (existing MicroTasking behavior).
2. MicroTasking selects it into its task queue (existing behavior).
3. From the queue, **at any time and in any task state** (not gated to pre-Start - this was
   negotiated between both apps' sessions and confirmed directly by the user after an initial
   disagreement between the two sessions' interpretations), the user hits a "Refer to 2do2go"
   action. Referring an already-`Started` task discards its in-progress timer as a neutral outcome
   - like Defer, it counts as neither Complete nor Abandoned.
4. Referral shows MicroTasking's continuous Eisenhower-matrix widget; the exact touch point
   becomes two raw floats (`importance`, `urgency`), written via the shared Apps Script Web App
   (below) to two columns to the right of the sheet's normal columns (A-C: checkbox, description,
   link). Those two columns are **hidden and Protected-Range-restricted** in the Sheets UI -
   columns A-C stay normal and user-editable as always.
5. Any row with importance/urgency set is excluded from MicroTasking's own queue selection going
   forward - checked against the Apps Script-sourced state, not just a local flag, so it's correct
   even from a second device or fresh install.
6. 2do2go syncs (manual "Sync Lists" or periodic), reading each tab's plain columns via the
   existing CSV/gviz export and each tab's importance/urgency via the Apps Script endpoint only
   (CSV/gviz export includes hidden columns' raw values regardless of Sheets-UI hidden state,
   which would defeat "invisible to the user"). Fetching happens at the app's normal sync
   boundaries (manual "Sync Lists" + any future periodic sync) and the result is persisted
   locally between syncs - no new per-action/live network dependency beyond that.
7. From an item's detail view, the user can set a 0-100% progress value (2do2go-local only, never
   written to the Sheet - a `Progress` column was considered and dropped, since linked removal
   below already prevents any staleness a synced column would have solved) and either:
   - **Complete (for now)**: clears the row's importance/urgency via the Web App's clear-priority
     endpoint (freeing it back up for MicroTasking's queue) and removes 2do2go's own local copy of
     the item immediately - linked removal, not waiting for a resync. Only offered on
     Sheet-sourced items; ad-hoc/local ones have nothing to hand back.
   - **Fully complete**: deletes the row entirely via the Web App's delete-row endpoint (same
     shift-up-rows convention MicroTasking's own `onEdit` description-clear already uses) and
     removes 2do2go's local copy. Ad-hoc/local items get an equivalent "Mark complete"/"Delete"
     pair instead, since there's no Sheet row for those endpoints to act on.

**Bridge mechanism**: a single Apps Script Web App, owned and deployed by MicroTasking's repo
(extends the already-bound `scripts/populate_google_sheet.js`, `doGet`/`doPost` endpoints). Both
apps call it over plain HTTPS - no OAuth, no Google Cloud project change, in either app.

**Row identity** for every Apps Script call is `(tab name, description text)`, resolved
server-side by the script - never a cached row index, since MicroTasking's existing row-delete
logic shifts rows up and would make a cached index unsafe.

**Confirmed contract** (2026-09-18, as deployed in MicroTasking's `scripts/populate_google_sheet.js`):
- `GET {webAppUrl}?action=getPriorities` → `{"ok":true,"rows":[{"category","description",
  "importance","urgency"}, ...]}` - every currently-referred row across every tab in **one** call,
  not one call per tab. 2do2go groups the result by `category` (tab name) itself.
- `POST {webAppUrl}` with a JSON body `{"action":"setPriority"|"clearPriority"|"deleteRow",
  "category","description",["importance","urgency" for setPriority]}` → `{"ok":true}` or
  `{"ok":false,"error":"..."}`.
- The Web App is deployed **per-user**, from the same Sheet-bound Apps Script editor each user
  already has (Deploy → New deployment → Web app, Execute as Me, Anyone with the link) - a
  one-time manual step, documented in MicroTasking's README/setup instructions. 2do2go's Settings
  screen has a manual paste field for the resulting URL (see "Screens"); a combined-QR that
  carries both the Sheet URL and the Web App URL in one scan is tracked in MicroTasking's
  `PUNCH_LIST.md` item 8 and not yet built - the manual field is the interim path.

2do2go's client (`SheetApiClient.kt`: `fetchAllPriorities`, `clearSheetPriority`, `deleteSheetRow`)
implements this contract.

## Screens

1. **Settings** - paste/QR-scan the shared Google Sheet URL; the Apps Script Web App URL; "Sync
   Lists" re-runs the import; importance-weight slider (`0.5`-`4.0`, default `2.0`); items-per-list
   count (`1`-`10`, default `5`, a single global setting).
2. **Carousel** (home screen, and the *only* list screen - there is no separate "see everything"
   list-detail view) - a horizontal, swipeable page per list, each showing that list's top N open
   items by priority score. A page indicator (dots) shows position; the FAB adds an ad-hoc item to
   whichever list's page is currently showing. A tab with zero qualifying (referred) items still
   shows up, empty, when swiped to.
3. **Item detail** (dialog, opened by tapping a row) - description, tappable link, the continuous
   matrix widget (re-triage in place, local-only), a progress slider. Sheet-backed items show
   Complete-(for-now)/Fully-complete; ad-hoc items show Mark-complete/Delete instead.
4. **Add item** (dialog) - description plus the continuous matrix widget; always adds to whichever
   list/page is currently showing on the carousel.
5. **QR scanner** - unchanged, scans the Sheet URL into Settings.

## Not implemented / explicitly out of scope for now

- **On-device verification of the referral round-trip.** Both apps' code is written and compiles;
  neither side has been exercised end-to-end against a real deployed Web App yet. See
  PUNCH_LIST.md item 1.
- **Combined onboarding QR** carrying both the Sheet URL and the Web App URL in one scan - tracked
  in MicroTasking's `PUNCH_LIST.md` item 8. Until then, both URLs are pasted into Settings
  separately.
- Background reminders/notifications - this is a pull list, not a nudger; no alarms, no
  permissions beyond internet/camera.
- Bulk-editing a list's sheet-backed items, reordering lists, or a *user-visible* priority column
  in the Sheet itself (the hidden/protected importance/urgency columns are internal plumbing, not
  a user-facing feature).
- Per-list top-N override (currently one global setting only).

## Open questions (for later)

- Failure-state UX when a write-back call (complete-for-now, fully-complete, or the referral write
  itself on MicroTasking's side) fails (offline, Web App misconfigured/undeployed, etc.) - current
  behavior is "show an error message, leave local state unchanged, let the user retry"; whether
  that's sufficient or needs queue-and-retry is undecided.
- Exact importance-weight default/range and items-per-list default/range above are a first
  proposal, not user-validated in practice.
