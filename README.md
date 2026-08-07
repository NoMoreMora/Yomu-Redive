<div align="center">

# Yomu-Re:dive

**Yomu-Re:dive is a free and open-source manga reader for Android, focused on making the
reading experience great on foldable phones. It is a fork of
[Kotatsu-Redo](https://github.com/Kotatsu-Redo/Kotatsu-Redo) (itself a fork of
[Kotatsu](https://github.com/KotatsuApp/Kotatsu)), keeping all of its features and 1200+
online content sources while adding foldable-first reading enhancements.**

![Android 6.0](https://img.shields.io/badge/android-6.0+-brightgreen) [![License](https://img.shields.io/badge/license-GPLv3-blue)](LICENSE)

</div>

### Fork goals

<div align="left">

-   **Foldable-first reader** — take full advantage of the inner display: fold-aware
    two-page spreads, hinge/occlusion-safe layouts, and posture-aware (tabletop / book)
    reading modes.
-   **Continuity across postures** — keep your place and reading settings when folding and
    unfolding, without losing the page or re-fitting the image.
-   Maintain feature and source parity with upstream Kotatsu-Redo by regularly merging
    updates.

</div>

### Inherited features

<div align="left">

-   Online [manga catalogues](https://github.com/Kotatsu-Redo/kotatsu-parsers-redo) (with 1200+ manga sources)
-   Search manga by name, genres and more filters
-   Favorites organized by user-defined categories
-   Reading history, bookmarks and incognito mode support
-   Download manga and read it offline. Third-party CBZ archives are also supported
-   Clean and convenient Material You UI, optimized for phones, tablets and desktop
-   Standard and Webtoon-optimized customizable reader, gesture support on reading interface
-   Notifications about new chapters with updates feed, manga recommendations (with filters)
-   Integration with manga tracking services: Shikimori, AniList, MyAnimeList, Kitsu
-   Password / fingerprint-protected access to the app
-   Automatically sync app data with other devices on the same account
-   Support for older devices running Android 6.0+

</div>

### Building

```bash
./gradlew assembleDebug
```

The debug build installs under the application id `io.github.yomuredive.yomu.debug`, so it
can be installed side-by-side with Kotatsu / Kotatsu-Redo for comparison.

### Staying in sync with upstream

This repository keeps Kotatsu-Redo configured as the `upstream` remote. To pull the latest
upstream changes into your fork:

```bash
git fetch upstream
git merge upstream/devel
```

See [FORK.md](FORK.md) for the full fork lineage and the list of changes made in this fork.

### Contributing

**📌 Pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for the guidelines.**

### License

[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](http://www.gnu.org/licenses/gpl-3.0.en.html)

<div align="left">

Yomu-Re:dive is licensed under the GNU GPLv3, inherited from Kotatsu / Kotatsu-Redo. You may
copy, distribute and modify the software as long as you track changes/dates in source files.
Any modifications to or software including (via compiler) GPL-licensed code must also be made
available under the GPL along with build & install instructions.

</div>

### DMCA disclaimer

<div align="left">

The developers of this application do not have any affiliation with the content available in
the app and does not store or distribute any content. This application should be considered a
web browser, all content that can be found using this application is freely available on the
Internet. All DMCA takedown requests should be sent to the owners of the website where the
content is hosted.

</div>
