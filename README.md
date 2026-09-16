# 2do2go

A companion app to [MicroTasking](https://github.com/vemcg/MicroTasking): a traditional
to-do list, sharing the same Google Sheet (each tab is a list, each checked row is an item),
prioritized with an Eisenhower matrix (important x urgent) instead of a flat priority field.

MicroTasking nudges you to do things periodically/semi-randomly from a pool. 2do2go is the
opposite mode: a pull, browse-and-pick list you check when you're ready to plan, not when
you're prompted.

## Status

Initial scaffold: Sheet import (lists = tabs, items = checked rows), Eisenhower-matrix
priority triage, checkbox completion, ad-hoc item add. See [SPEC.md](SPEC.md) for the full
intended feature set and [PUNCH_LIST.md](PUNCH_LIST.md) for what's next.

## Structure

- `app/` — Android application module (Kotlin + Jetpack Compose)
  - `MainActivity.kt` — Activity + Compose screens
  - `ToDoData.kt` — data model, persistence, priority/sort logic
  - `SheetImport.kt` — Google Sheet tab discovery + CSV fetch (generic, no app-specific mapping)
- `settings.gradle.kts`, `build.gradle.kts` — Gradle project config
- `.github/workflows/release-apk.yml` — CI: build, GitHub release, install page + QR on GitHub Pages
- `scripts/generate_install_page.py` — install/onboarding page generator

## License

Copyright (c) 2026 Vern McGeorge. All rights reserved. See [LICENSE](LICENSE).
