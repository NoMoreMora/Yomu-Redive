# Changelog

All notable changes to **Yomu-Re:dive** are documented here. Yomu-Re:dive is a foldable-focused
fork of [Kotatsu-Redo](https://github.com/Kotatsu-Redo/Kotatsu-Redo). Fork releases are tagged
`<upstream-base>-yomu<n>`, where the base is the upstream Kotatsu-Redo version this build tracks.

## [9.8.1-yomu1] - 2026-08-07

First versioned Yomu-Re:dive release. Based on Kotatsu-Redo `9.8.1` (`cb430d3`).

### Added — foldable reading

- **Posture-aware auto double-page**: the automatic side-by-side two-page spread now only engages
  in book posture (unfolded with a vertical hinge), so it lines up with the physical fold and no
  longer triggers in tabletop/laptop postures.
- **Seamless continuous (webtoon) strip across fold/unfold**: fixed the black bars that appeared
  top and bottom after moving between the cover and inner screens — each page's fit scale is now
  re-pinned to the new width and re-centered on resize.
- **Per-screen zoom profiles (continuous reader)**: the reader remembers the zoom you set per
  screen size and restores it when folding/unfolding, instead of resetting to fit each time.

### Added — manga migration (TachiyomiSY-style)

- **Batch migration from Favourites**: select multiple favourites and choose **Migration** to
  migrate them together (no more one-at-a-time).
- **Dedicated source-selection screen**: lists enabled sources (preferred first), lets you reveal
  and enable disabled sources inline, and set a preferred source.
- **Review list**: shows each manga as `from → to` with an auto-matched candidate on the chosen
  source; tap the right side to override via a cross-source search. Nothing is migrated until you
  press **Apply** (Migrate or Copy).
- **Per-candidate source search**: each candidate card has a **Search** button (above **Select**)
  that opens a source-scoped search pre-filled with the title, listing all results on that source
  so different-name/language versions can be picked.
- **Larger cover art** in the migration review list and the source-search results.

### Added — caching (Settings → Appearance → Manga list)

- **Cache cover art**: keeps browsed cover art cached for 5 days and refreshes it at most once a
  day (opt-in).
- **Keep favourites up to date**: retains cover art and details for favourite manga until they are
  removed from favourites, refreshing them once a day via a background worker (opt-in).

### Changed

- Rebranded from Kotatsu-Redo to **Yomu-Re:dive** — app name, Gradle project name (`Yomu-Redive`),
  and applicationId (`io.github.yomuredive.yomu`, installable side-by-side with upstream). The
  internal source namespace (`org.koitharu.kotatsu`) is intentionally unchanged to keep upstream
  merges clean.
- **No sources enabled on first launch**: a fresh install no longer auto-enables sources for the
  device language; you opt in from the welcome screen or the catalog.

See [FORK.md](FORK.md) for implementation detail and per-file notes.
