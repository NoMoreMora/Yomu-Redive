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

**Planned (next)**

- _Hinge-aware gutter_: inset a two-page spread around the hinge's bounding rect
  (`FoldingFeature.bounds`) so no artwork is lost under the physical fold. Reader-internal
  change to the double-page renderer.
- _Tabletop reading mode_: when the fold is horizontal and separating, show the page in the
  top physical half and reading controls / next-page area in the bottom half.
- _Position & zoom continuity_ across fold/unfold transitions.

> Note: none of the fork's code has been compiled in the authoring environment (no
> JDK/Android SDK there). Build and test with Android Studio or JDK 17 + Android SDK before
> release.
