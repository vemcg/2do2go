# 2do2go Punch List

1. **MicroTasking → 2do2go "Move to To-Do" hand-off** — *scoped, not started.* MicroTasking will
   grow an action that moves a queued task here (setting its Eisenhower quadrant in the process)
   and permanently excludes it from its own queue afterward (`ManagedTask.neverSuggest = true` on
   the MicroTasking side - already exists, no new field needed). The open question is the bridge
   between the two separate, sandboxed apps:
   - **Sheet write-back**: MicroTasking appends the moved task + quadrant to a dedicated
     area/tab, 2do2go picks it up on its next sync. Best fit for "shares the same Google Sheet,"
     but today's Sheet access is read-only public CSV export - this needs real Sheets API auth
     (OAuth or a service account), a real step up from anything built so far.
   - **On-device IPC**: a ContentProvider + custom permission, or an Intent launching 2do2go with
     the task as an extra. Same-device only, no new Google Cloud setup, moderate Android plumbing.
   Needs its own design/planning pass before building - see SPEC.md "Not implemented."
2. **App icon polish** — current adaptive icon (checklist rows) is a first pass, not reviewed
   on-device at all densities/shapes yet.
