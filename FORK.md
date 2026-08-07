# Fork lineage

Yomu-Re:dive is a fork of **Kotatsu-Redo**, which is itself a fork of **Kotatsu**.

| Project        | Repository                                             |
| -------------- | ------------------------------------------------------ |
| Yomu-Re:dive   | this repository                                        |
| Kotatsu-Redo   | https://github.com/Kotatsu-Redo/Kotatsu-Redo (`upstream`) |
| Kotatsu        | https://github.com/KotatsuApp/Kotatsu                  |

Forked from Kotatsu-Redo at tag/commit `9.8.1` (`cb430d3`).

All of this project's code is licensed under the **GNU GPLv3**, inherited from the projects
above. In accordance with the GPL, changes made in this fork are tracked below and in the
git history.

## Purpose

Enhance the manga reading experience on **foldable phones** while maintaining feature and
source parity with upstream Kotatsu-Redo.

## Relationship to upstream

The original `origin` remote (Kotatsu-Redo) is configured as `upstream` so updates can be
merged in:

```bash
git fetch upstream
git merge upstream/devel
```

> Note: the initial clone was shallow (`--depth 1`). To merge upstream history cleanly, run
> `git fetch --unshallow upstream` first.

## Changes made in this fork

### Rebrand (initial fork commit)

| Item                | Before                          | After                        |
| ------------------- | ------------------------------- | ---------------------------- |
| App display name    | `Kotatsu-Redo`                  | `Yomu-Re:dive`               |
| Gradle project name | `Kotatsu`                       | `Yomu-Redive`                |
| Application id       | `io.github.kotatsuredo.kotatsu` | `io.github.yomuredive.yomu`  |

The internal source package namespace (`org.koitharu.kotatsu`) is intentionally left
unchanged to keep the diff against upstream small and merges clean; only the installed
application identity and user-facing name were changed. The new application id lets the fork
be installed side-by-side with Kotatsu / Kotatsu-Redo.

### Foldable enhancements

This is the focus of the fork. Existing upstream foldable support lives in
`app/src/main/kotlin/org/koitharu/kotatsu/reader/ui/ReaderActivity.kt` (`androidx.window`
`WindowInfoTracker` + `FoldingFeature`, auto double-page on unfold via the
`isReaderDoubleOnFoldable` setting).

**Done**

- _Posture-aware auto double-page_ (`ReaderActivity.kt`): the automatic side-by-side
  two-page spread now only engages in **book posture** — unfolded with a *vertical* hinge, so
  the spread lines up with the physical fold. Flip-style / horizontal-hinge (tabletop/laptop)
  postures no longer force a side-by-side spread that doesn't match the screen split.
- _Seamless continuous (webtoon) strip across fold/unfold_ (`WebtoonImageView.kt`): on an
  in-place viewport resize (moving between the Z Fold cover and inner screens, or a
  multi-window resize) each page's render scale was left pinned to the old width, so pages were
  drawn smaller than their re-measured bounds and showed black bars top and bottom. An
  `onSizeChanged` override now re-pins the per-page fit scale to the new width and re-centers at
  the current reading position, keeping the strip continuous.
- _Per-screen zoom profiles (continuous reader)_ (`WebtoonScalingFrame.kt`): the reader now
  remembers the zoom level per screen size. When the screen size changes (folding / unfolding),
  it saves the zoom you had set for the previous screen and restores the zoom previously used
  for the new one (defaulting to fit) — so you no longer have to re-zoom after each switch.
  Remembered per reading session (survives in-place fold/unfold; resets if the reader is fully
  recreated).

**Planned (next)**

- _Hinge-aware gutter_: inset a two-page spread around the hinge's bounding rect
  (`FoldingFeature.bounds`) so no artwork is lost under the physical fold. Reader-internal
  change to the double-page renderer.
- _Tabletop reading mode_: when the fold is horizontal and separating, show the page in the
  top physical half and reading controls / next-page area in the bottom half.
- _Position & zoom continuity_ for the paged reader across fold/unfold transitions (the
  continuous reader is handled above).

### Other changes

- _No sources enabled on first launch_ (`main/ui/welcome/WelcomeViewModel.kt`): the first-run
  welcome screen no longer auto-selects the device language and pre-enables its sources. A fresh
  install now starts with **no sources enabled**; the user opts in by choosing languages on the
  welcome screen or adding sources from the catalog.
- _Debug/nightly variant names_ (`src/debug`, `src/nightly` `strings.xml`): rebranded from
  "Kotatsu Dev" / "Kotatsu Nightly" to "Yomu-Re:dive Dev" / "Yomu-Re:dive Nightly".
- _"Find similar" → manga migration_ (`alternatives/*`, `favourites/ui/list/FavouritesListFragment.kt`,
  `core/nav/AppRouter.kt`, `res/menu/opt_migration.xml`): the favourites selection **Find similar**
  action now opens the migration screen (per-manga; single selection only) instead of the Related
  list. Modeled on TachiyomiSY's source migration, the screen now:
  - **auto-matches on a chosen target source** — `AlternativesUseCase` gained `targetSource`/`query`
    params; a **Select source** menu item restricts the fuzzy-title search to one source (or "All
    sources"). `AppRouter.openAlternatives(manga, source?)` can pre-select the source.
  - **manual match** — a **Search manually** menu item re-runs the search with a typed title against
    the selected source.
  - **Migrate vs Copy** — the migrate confirm dialog gained a **Copy** button. `MigrateUseCase` gained
    a `copy` flag: `copy=false` (Migrate) moves favourites/history/tracking/scrobbling to the new
    manga and removes the original; `copy=true` (Copy) duplicates favourites + history onto the new
    manga and leaves the original (and its tracking) intact.
  - **Batch migration (TachiyomiSY-style review list)** — selecting **multiple** favourites and
    choosing **Migration** opens the source screen, then a **review list** (`MigrationListActivity`):
    each row shows the original manga (left) and its auto-matched candidate on the chosen source
    (right), or "No match — tap to search". Tapping the right side opens a **cross-source search**
    (`AlternativesActivity` pick mode: all enabled sources + a "search disabled sources" button) to
    override the match. An **Apply** action migrates/copies all matched rows. A single selection
    still opens the interactive screen with manual match. In that picker each candidate card has a
    **Select** button (renamed from "Migrate" — it only sets the match; nothing is migrated until
    Apply) and a **Search** button that opens a source-scoped search (`SourceSearchActivity`)
    pre-filled with the manga title, listing **all** results on that source (`SearchKind.SIMPLE`,
    unfiltered) so a different-name/language version can be picked.
  - **Dedicated source-selection screen** (`MigrationSourceActivity`) — replaces the old popup. A
    full screen listing enabled sources (preferred first), an overflow **Show disabled sources**
    toggle that reveals disabled sources with inline **enable** switches, and a per-row **star** to
    set a **preferred** source (persisted). Selecting a source opens the review list (batch) or
    returns it to the single-manga screen.
    _(The earlier foreground `MigrationService` + `BatchMigrateUseCase` "auto-migrate all" path is
    now superseded by the review list and no longer wired.)_
  - **Naming** — the migration entry is **"Migrate"** in a manga's details (was "Alternatives") and
    **"Migration"** in the favourites selection; the old favourites **"Fix"** (auto-repair) item was
    removed (auto-repair still runs from the error screen). The details **"Find similar"** (a
    title-search discovery) is unchanged and distinct from migration.

  _Known gap (follow-up):_ page-level **bookmarks** and per-manga reader prefs are still not carried
  across a migration — doing it meaningfully needs chapter-number matching (bookmarks reference the
  old source's chapter/page ids), so it was deliberately deferred rather than blindly re-keyed.

- _Cover art caching_ (`core/image/CoverCacheInterceptor.kt`, `core/util/ext/Coil.kt`,
  `image/ui/CoverImageView.kt`, registered in `core/AppModule.kt`): an opt-in toggle in
  **Settings → Appearance → Manga list**. Cover requests are tagged (`coverCacheExtra`) with a
  stable disk-cache key equal to the cover url. When the toggle is on, a Coil `Interceptor`
  manages those entries by age: a cover older than **5 days** is evicted so the next load fetches a
  fresh copy, while a younger one is served from Coil's disk cache without hitting the network — so
  a given cover is re-downloaded **at most once** per retention window (well within "once a day").
  Only cover requests are affected; reader pages and favicons pass through, and any failure falls
  back to Coil's default behaviour. Off by default.
- _Keep favourites up to date_ (`favourites/ui/FavouritesUpdateWorker.kt`, wired in
  `settings/work/WorkScheduleManager.kt`): an opt-in toggle in the same section. A daily
  `PeriodicWorkScheduler` worker (24 h, network + battery-not-low constraints) refreshes every
  favourite — re-fetching details from the source with `CachePolicy.WRITE_ONLY` and persisting them
  via `MangaDataRepository.storeManga(replaceExisting = true)`, then warming the cover into the disk
  cache. So a favourite's cover art and metadata are **retained until it is un-favourited** (the
  manga row is kept by the favourites FK) and kept current daily. WorkManager's periodic interval
  provides the "once a day" gate; scheduling follows the toggle. Off by default.

## Build environment notes

The debug APK builds with **JDK 17 + Android SDK** (compileSdk `android-37.0`, build-tools
`35.0.0`) via `./gradlew assembleDebug`.

> Windows/JVM gotcha: in some restricted environments JDK 17's NIO self-pipe fails to start
> with "Unable to establish loopback connection" because it creates an AF_UNIX socket whose
> path (under the default temp dir) exceeds the socket-path limit. Work around it by pointing
> that socket at a short path, e.g. `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\gtmp`
> (create `C:\gtmp` first). Not needed on a normal dev machine.

## Releasing updates

The app ships with an **in-app updater** (inherited from Kotatsu, `core/github/AppUpdateRepository.kt`).
It polls the GitHub Releases of the repo named in `res/values/constants.xml`
(`github_updates_repo` → `NoMoreMora/Yomu-Redive`), finds the release whose attached `.apk` asset has
the highest version (from the release **tag**, minus a leading `v`), and offers it if it is newer than
the installed build. **No server is involved** — publishing a GitHub Release *is* the update channel.

To cut a release:

1. **Version** — bump both `versionCode` and `versionName` in `app/build.gradle`. `versionName` must be
   a plain `major.minor.patch` (no `-suffix`): the updater's `VersionId` parser treats any `-suffix` as
   a pre-release that sorts *below* the same base number, so a suffixed build would never be offered.
   The fork continues the upstream numeric line it forked from (`9.8.1` → `9.8.2` → …).
2. **Sign** — release builds are signed from a gitignored `keystore.properties` at the repo root (see
   `keystore.properties.example`; generate the keystore once with `keytool` and keep it forever — the
   signature must stay constant or updates won't install). Without that file the release build is left
   unsigned. Release applicationId is `io.github.yomuredive.yomu` (debug is a separate `.debug` app).
3. **Build** — `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.
4. **Publish** — create a GitHub Release on `NoMoreMora/Yomu-Redive`, tag it with the version
   (e.g. `9.8.2`, matching the existing tag style), write the notes (they surface in the in-app
   changelog), and **upload the signed APK as a release asset**. Installed apps pick it up on their
   next update check.

### Stable vs development update channels

Settings → About → **Update channel** (`update_channel` pref, backing `AppSettings.isUnstableUpdatesAllowed`)
lets a user pick **Stable** or **Development**:

- **Stable** (default) — the updater only offers releases whose version has no `-suffix`
  (`9.8.2`, `9.8.3`, …).
- **Development** — it also offers pre-release builds, i.e. releases whose tag carries a suffix
  (`9.8.3-beta1`). `VersionId` treats a suffix as a pre-release sorting just below the same base
  number, so a `9.8.3-beta1` supersedes `9.8.2` and is itself superseded by the final `9.8.3`.

To publish a development build, build the release variant with a suffix and mark the GitHub Release
as a pre-release:

```
./gradlew assembleRelease -PversionSuffix=-beta1     # -> 9.8.3-beta1 (needs the base bumped to 9.8.3)
```

### Fast local install while testing

For iterating on a device/emulator, skip GitHub entirely and install straight over adb:

```
./gradlew installDebug      # builds + installs the .debug app (io.github.yomuredive.yomu.debug)
```

The debug build installs **side-by-side** with a release install (different applicationId), so you
can keep a stable copy and a dev copy on the same device. `installRelease` / `installNightly` install
those variants instead (release needs the signing config from `keystore.properties`).
