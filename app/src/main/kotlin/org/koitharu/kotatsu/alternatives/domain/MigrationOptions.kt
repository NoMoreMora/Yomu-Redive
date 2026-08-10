package org.koitharu.kotatsu.alternatives.domain

/**
 * User-configurable options for the migration flow, edited from the migration options sheet and
 * persisted in [org.koitharu.kotatsu.core.prefs.AppSettings]. A single snapshot is read by the
 * migration list (for matching + filtering) and passed to [MigrateUseCase] (for what data to move).
 */
data class MigrationOptions(
	// What data to carry over to the new manga.
	val migrateChapters: Boolean,
	val migrateCategories: Boolean,
	val migrateCover: Boolean,
	// Delete the original's downloaded chapters after a non-copy migration.
	val deleteDownloads: Boolean,
	// Extra keywords appended to the title when searching for a match.
	val extraKeywords: String,
	// List filters.
	val hideWithoutMatch: Boolean,
	val hideWithoutNewerChapters: Boolean,
	// Matching behaviour.
	val advancedSearch: Boolean,
	val matchByChapterCount: Boolean,
) {

	companion object {

		val DEFAULT = MigrationOptions(
			migrateChapters = true,
			migrateCategories = true,
			migrateCover = true,
			deleteDownloads = false,
			extraKeywords = "",
			hideWithoutMatch = false,
			hideWithoutNewerChapters = false,
			advancedSearch = false,
			matchByChapterCount = false,
		)
	}
}
