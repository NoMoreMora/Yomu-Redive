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

_Planned / in progress — this is the focus of the fork. Existing upstream foldable support
lives in `app/src/main/kotlin/org/koitharu/kotatsu/reader/ui/ReaderActivity.kt`
(`androidx.window` `WindowInfoTracker` + `FoldingFeature`, auto double-page on unfold via the
`isReaderDoubleOnFoldable` setting)._
