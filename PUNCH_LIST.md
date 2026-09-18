# 2do2go Punch List

1. **MicroTasking → 2do2go "Refer to 2do2go" hand-off** — *design negotiated with the
   MicroTasking session and confirmed by the user (2026-09-18); code written on both sides,
   compiles, not yet verified on-device end-to-end.* The bridge is a per-user **Apps Script Web
   App**, deployed from the same Sheet-bound script every user already pastes into their own copy
   (Deploy → New deployment → Web app, documented in MicroTasking's setup instructions/README).
   MicroTasking writes continuous importance/urgency values to hidden, Protected-Range `Importance`/
   `Urgency` Sheet columns on referral (`ManagedTask.referredAt` on its side, available from any
   task-queue state - referring an already-`Started` task discards its timer as a neutral outcome,
   like Defer). Those two columns are read/written via the Web App only, never via CSV export,
   since hiding a column doesn't remove it from that export. 2do2go ingests a row only once
   they're populated (gated ingestion, see `SPEC.md` "Items"), ranks by a user-adjustable
   importance-weight setting (not a fixed formula - MicroTasking always writes raw unweighted
   values), and clears them ("Complete (for now)") or deletes the row ("Fully complete") back
   through the same Web App - progress itself stays local-only, not a sheet column.
   - Confirmed request/response contract and 2do2go's client (`SheetApiClient.kt`): see `SPEC.md`
     "Referral bridge".
   - Done on 2do2go's side: `ToDoItem` model (continuous `importance`/`urgency`, `progress`),
     gated-ingestion sync (`toDoItemsFromReferredRows` + `fetchAllPriorities`), the continuous
     `MatrixWidget` (replaces the old 4-quadrant tap dialog, used both for referral-equivalent
     ad-hoc triage and re-triage), the carousel home screen (`CarouselScreen`, replaces Lists
     overview + List detail entirely), `ItemDetailDialog` (progress slider, Complete-for-now/
     Fully-complete for sheet-backed items, Mark-complete/Delete for ad-hoc ones), and Settings
     additions (Apps Script Web App URL field, importance-weight slider, items-per-list count).
   - **Remaining**: on-device verification of the full round trip against a real deployed Web App
     (referral in MicroTasking → shows up in 2do2go → Complete-for-now hands it back → MicroTasking
     re-queues it); the combined onboarding QR for both URLs is tracked in MicroTasking's
     `PUNCH_LIST.md` item 8, not this repo's - until it lands, both URLs are pasted into Settings
     by hand.
2. **App icon polish** — current adaptive icon (checklist rows) is a first pass, not reviewed
   on-device at all densities/shapes yet.
