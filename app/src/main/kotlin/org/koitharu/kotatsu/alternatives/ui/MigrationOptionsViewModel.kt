package org.koitharu.kotatsu.alternatives.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.BaseViewModel
import javax.inject.Inject

/**
 * Backs the migration options sheet. Each property reads/writes directly to [AppSettings]; the
 * migration list observes those keys and re-matches/re-filters reactively.
 */
@HiltViewModel
class MigrationOptionsViewModel @Inject constructor(
	private val settings: AppSettings,
) : BaseViewModel() {

	var migrateChapters: Boolean
		get() = settings.isMigrateChapters
		set(value) { settings.isMigrateChapters = value }

	var migrateCategories: Boolean
		get() = settings.isMigrateCategories
		set(value) { settings.isMigrateCategories = value }

	var migrateCover: Boolean
		get() = settings.isMigrateCover
		set(value) { settings.isMigrateCover = value }

	var deleteDownloads: Boolean
		get() = settings.isMigrationDeleteDownloads
		set(value) { settings.isMigrationDeleteDownloads = value }

	var extraKeywords: String
		get() = settings.migrationExtraKeywords
		set(value) { settings.migrationExtraKeywords = value }

	var hideWithoutMatch: Boolean
		get() = settings.isMigrationHideUnmatched
		set(value) { settings.isMigrationHideUnmatched = value }

	var hideWithoutNewerChapters: Boolean
		get() = settings.isMigrationHideNoNewer
		set(value) { settings.isMigrationHideNoNewer = value }

	var advancedSearch: Boolean
		get() = settings.isMigrationAdvancedSearch
		set(value) { settings.isMigrationAdvancedSearch = value }

	var matchByChapterCount: Boolean
		get() = settings.isMigrationMatchByChapters
		set(value) { settings.isMigrationMatchByChapters = value }
}
