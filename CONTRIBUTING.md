## Contributing to Yomu-Re:dive

Thanks for your interest in improving Yomu-Re:dive! The project's focus is a great reading
experience on foldable phones, so foldable/UX contributions are especially welcome.

+ If you want to **fix bugs** or **implement features** that **already have an [issue](https://github.com/NoMoreMora/Yomu-Redive/issues):** please comment on the issue and/or assign it to yourself.
+ If you want to **implement a new feature:** open an issue or discussion first so we can agree on scope before you invest time.
+ Manga **sources** are provided by the parsers library the app depends on — new sources should be contributed there rather than to this repository.

**Refactoring** and **developer-experience improvements** are also welcome. Please stick to the following principles:

+ **Performance matters.** When choosing between source-code beauty and performance, performance should win.
+ **Avoid adding new dependencies** unless required. APK size matters.
+ **Keep foldable behavior intact.** If a change touches the reader or layout, verify it in both folded and unfolded (book/tabletop) postures.
+ **Match the existing code style.** The project keeps the internal package structure of its upstream to make merging upstream updates easier — follow the surrounding conventions.
