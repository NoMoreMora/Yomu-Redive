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
  - **Batch migration** — selecting **multiple** favourites and choosing "Find similar" opens a source
    picker, then Migrate/Copy **all** of them at once via a foreground `MigrationService`
    (`BatchMigrateUseCase` auto-matches each manga's best title hit on the chosen source; progress +
    summary notification). A single selection still opens the interactive screen with manual match.

  _Known gap (follow-up):_ page-level **bookmarks** and per-manga reader prefs are still not carried
  across a migration — doing it meaningfully needs chapter-number matching (bookmarks reference the
  old source's chapter/page ids), so it was deliberately deferred rather than blindly re-keyed.

## Build environment notes

The debug APK builds with **JDK 17 + Android SDK** (compileSdk `android-37.0`, build-tools
`35.0.0`) via `./gradlew assembleDebug`.

> Windows/JVM gotcha: in some restricted environments JDK 17's NIO self-pipe fails to start
> with "Unable to establish loopback connection" because it creates an AF_UNIX socket whose
> path (under the default temp dir) exceeds the socket-path limit. Work around it by pointing
> that socket at a short path, e.g. `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\gtmp`
> (create `C:\gtmp` first). Not needed on a normal dev machine.
