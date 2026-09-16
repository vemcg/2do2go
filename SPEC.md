# 2do2go Spec

A traditional to-do list, companion to MicroTasking, sharing MicroTasking's Google Sheet as its
list/item source.

## Lists

- Each Google Sheet tab (excluding a README tab) is one to-do list, named after the tab.
- A tab with zero rows still counts as a live, empty list (`known_lists`, refreshed on every
  successful sync) - it shows up in the overview with "0 open" rather than not existing.

## Items

- One item per checked (column A) row in a tab, matched to MicroTasking's existing sheet
  convention: column A is the enabled checkbox, `description`/`link` columns matched by header
  text (order-independent). A tab with no checkboxes at all imports every row.
- Ad-hoc items can also be added directly in the app, independent of the sheet
  (`id` prefix `local-` vs. `sheet-<list>-<description>` for imported rows).
- Re-syncing the sheet only **adds** newly-checked rows not already present by id. It never
  removes or overwrites an existing item because its sheet row disappeared, got unchecked, or
  the description changed underneath it (a changed description is a new id, so it reads as a new
  item, not an edit to the old one - same convention MicroTasking's task-pool import uses).
  Rationale: an item already living in this to-do list may be mid-progress or already triaged -
  a spreadsheet edit made somewhere else shouldn't silently delete it.

## Priority: Eisenhower matrix

Priority is two independent booleans, `important` and `urgent`, not a flat scale. Set by tapping
one of four quadrants on a 2x2 matrix widget:

|                | **Urgent**     | **Not urgent** |
|----------------|-----------------|-----------------|
| **Important**     | Do First        | Schedule        |
| **Not important** | Delegate        | Eliminate       |

Display order is a computed score (`important*2 + urgent`, i.e. Do First=3, Schedule=2,
Delegate=1, Eliminate=0), descending, ties broken by add time (older first). The 2x weighting on
importance is a starting point, tunable once there's a feel for how it should rank in practice.

A freshly-imported sheet item starts untriaged (neither important nor urgent - the Eliminate
quadrant, i.e. bottom of the list) until triaged in-app. The Sheet itself carries no
importance/urgency columns - triage is an in-app action, not a spreadsheet edit.

## Screens

1. **Settings** - paste or QR-scan the shared Google Sheet URL; "Sync Lists" re-runs the import.
2. **Lists overview** - one row per list with an open-item count.
3. **List detail** - open items sorted by priority score then add time; done items collapse into
   a "Done" section below rather than disappearing. Each row: checkbox, description, optional
   tappable link, a quadrant badge (tap to re-triage). FAB adds an ad-hoc item.

## Not implemented / explicitly out of scope for now

- **MicroTasking "Move to To-Do" hand-off.** The vision: MicroTasking gains an action that moves
  a queued task here (assigning a quadrant in the process) and then permanently excludes it from
  its own queue (via the existing `ManagedTask.neverSuggest` flag - no new field needed on that
  side). The two apps are separate installs with sandboxed storage, so this needs a real bridge -
  candidates include Sheet write-back (matches the shared-sheet framing best, but today's Sheet
  access is read-only CSV export, so this needs real Sheets API auth) or on-device IPC
  (ContentProvider/Intent hand-off, same-device only, no new Google Cloud setup). Needs its own
  design pass before building - see PUNCH_LIST.md.
- Background reminders/notifications - this is a pull list, not a nudger; no alarms, no
  permissions beyond internet/camera.
- Editing a list's sheet-backed items in bulk, reordering lists, or a priority column in the
  Sheet itself.
