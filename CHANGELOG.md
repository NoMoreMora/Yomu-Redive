# Changelog

All notable changes to **Yomu-Re:dive** are documented here. Yomu-Re:dive is a foldable-focused
fork of [Kotatsu-Redo](https://github.com/Kotatsu-Redo/Kotatsu-Redo). Releases use plain
`major.minor.patch` numbers continuing the upstream line the fork forked from, so the in-app
GitHub-releases updater treats each one as a normal upgrade.

## [9.8.3-beta6] - 2026-08-09

Development (pre-release) build for the in-app **Development** update channel.

### Added

- **"Show CAPTCHA notifications" toggle** (Settings → Notifications), **off by default**. It gates the
  single notification choke point, so the "CAPTCHA required" notification no longer fires from the
  background new-chapter tracker or from passive cover-image loads that hit Cloudflare. The CAPTCHA
  prompt still appears interactively when you open or read a source that needs one, and per-source
  CAPTCHA-solving is unaffected.

## [9.8.3-beta5] - 2026-08-09

Development (pre-release) build for the in-app **Development** update channel.

### Changed — Migration reworked (TachiyomiSY-style)

- **Big cover cards.** The migration review list now shows each manga as a large cover-forward card
  (cover, title, source, "Latest: N") on both sides with a chevron between — much easier to
  recognize by art. No more "no match" status text; an unmatched candidate is just an empty
  placeholder.
- **Per-row ⋮ menu**: **Search manually** (cross-source search), **Don't migrate** (skip/dim the
  row), **Migrate now**, **Copy now** (act on that one row).
- **Direct entry + source dropdown.** Picking Migration on multiple favourites goes straight to the
  review list; a **top-bar dropdown** chooses the target source (with **More sources…** to reach
  disabled ones). It defaults to your preferred source, or the most-used source when migrating away
  from it.
- **Tap the candidate card** to open its details page (back returns to the list).
- **Migration options** (new ⚙ sheet): choose data to migrate (chapters, categories, custom cover),
  delete the original's downloads after migration, add extra search keywords, hide entries without a
  match / without newer chapters, advanced (keyword) search, and match by chapter number.

## [9.8.3-beta4] - 2026-08-09

Development (pre-release) build for the in-app **Development** update channel.

### Changed

- **Hide partial chapters** now also applies while **reading**: with the toggle on, the reader skips
  fractional chapters (6.1, 6.5, …) when auto-advancing and on next/previous, so it goes straight
  from chapter 6 to 7 — matching the chapter list (previously the reader still opened them).
- Renamed the toggle from "Hide partial chapters (.5)" to just **Hide partial chapters**.

## [9.8.3-beta3] - 2026-08-09

Development (pre-release) build for the in-app **Development** update channel.

### Added

- **Hide partial chapters** toggle (details page → chapters menu, next to Reverse / Grid view):
  hides chapters with a fractional number like 6.1 or 6.5, keeping only whole-numbered chapters.
  Useful when a source posts chapter parts (6.1, 6.2 …) and later replaces them with the full
  chapter 6. Off by default; whole chapters and un-numbered chapters are always shown.

## [9.8.3-beta2] - 2026-08-09

Development (pre-release) build for the in-app **Development** update channel.

### Fixed

- **MangaK.io NSFW toggle now works.** It previously tagged adult titles as ADULT and filtered on
  that rating, which let the app-wide "Hide NSFW content" setting strip them even when the per-source
  toggle was on — so turning it on revealed nothing. The toggle now filters on genre tags and leaves
  items unrated, making it the sole control for MangaK.io NSFW visibility regardless of the global
  setting. Also widened the adult genre set (adds ecchi, doujinshi, soft-yaoi, adult-content).

## [9.8.3-beta1] - 2026-08-09

Development (pre-release) build for the in-app **Development** update channel.

### Added

- **Developer options** (Settings → Developer options): debug logging with an exportable log
  file for troubleshooting, plus an **Experimental features** section for opt-in unstable features.
- **Auto-translate descriptions** (experimental): translates a manga's description into your device
  language on the details page, with a **Show original** toggle. Runs on-device — it downloads a
  small language model on first use, then works offline. Enable under Developer options →
  Experimental features.
- **Sort Favourites by unread chapters** (most / fewest), using tracked new-chapter counts.
- **MangaK.io NSFW toggle** (source settings): hides NSFW manga; disabled by default.

### Changed

- **New mascot launcher icon** and refined app name (**Yomu Re:Dive**).
- **Consolidated sources**: merged 13 MangaReader-based sources (MangaPuma, MangaForest, BoxManhwa,
  MangaSaga, MangaFab, MangaMonk, MangaCute, MangaXYZ, MangaBuddy, MangaBuddyMe, ManhuaSite,
  MangaSpin, ManhuaNow) into **MangaK.io** and removed them from the sources list.
- Manga sources now build from the **yomu-redive-parsers** fork (local composite build, JitPack for
  releases), so source-specific options can be maintained while still merging upstream parser
  updates.

### Fixed

- **Continuous-reader zoom retention**: the cover screen no longer loses its zoom level after
  folding/unfolding, and the default zoom is preserved on first open.
- **Webtoon resume fix** (experimental): returning to a continuous-reader chapter no longer skips
  ahead by a screenful on large/foldable displays — the position is now captured from the top of the
  screen, matching how it is restored. Enable under Developer options → Experimental features.

## [9.8.2] - 2026-08-07

First versioned Yomu-Re:dive release. Forked from Kotatsu-Redo `9.8.1` (`cb430d3`).

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

### Added — updates

- **Pull-to-refresh on the Updated tab**: swiping down at the top now triggers an immediate scan of
  your favourites for new chapters (the same tracker scan the Feed uses), with the spinner showing
  while it runs.
- **Update channel** selector (Settings → About): choose **Stable** (only plain-numbered releases)
  or **Development** (also receive pre-release `-beta` builds via the in-app GitHub-releases
  updater). Replaces the old "Allow unstable updates" toggle.

### Changed

- Rebranded from Kotatsu-Redo to **Yomu-Re:dive** — app name, Gradle project name (`Yomu-Redive`),
  and applicationId (`io.github.yomuredive.yomu`, installable side-by-side with upstream). The
  internal source namespace (`org.koitharu.kotatsu`) is intentionally unchanged to keep upstream
  merges clean.
- **No sources enabled on first launch**: a fresh install no longer auto-enables sources for the
  device language; you opt in from the welcome screen or the catalog.

See [FORK.md](FORK.md) for implementation detail and per-file notes.
