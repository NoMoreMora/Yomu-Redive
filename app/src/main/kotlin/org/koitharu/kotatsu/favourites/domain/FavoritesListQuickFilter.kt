package org.koitharu.kotatsu.favourites.domain

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.core.os.NetworkState
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.observeAsFlow
import org.koitharu.kotatsu.list.domain.ListFilterOption
import org.koitharu.kotatsu.list.domain.MangaListQuickFilter
import org.koitharu.kotatsu.parsers.model.ContentType

class FavoritesListQuickFilter @AssistedInject constructor(
	@Assisted private val categoryId: Long,
	@Assisted private val coroutineScope: CoroutineScope,
	private val settings: AppSettings,
	private val repository: FavouritesRepository,
	networkState: NetworkState,
) : MangaListQuickFilter(settings) {

	init {
		// Seed the "downloaded only" filter when the category has never been filtered yet and we
		// are currently offline. Once the user has any persisted selection their choice is kept.
		if (settings.getFavoritesFilterOrNull(categoryId) == null && !networkState.value) {
			settings.setFavoritesFilter(categoryId, serialize(setOf(ListFilterOption.Downloaded)))
		}
	}

	override val appliedOptions: StateFlow<Set<ListFilterOption>> = settings
		.observeAsFlow(settings.favoritesFilterKey(categoryId)) { getFavoritesFilter(categoryId) }
		.map(::deserialize)
		.stateIn(
			scope = coroutineScope,
			started = SharingStarted.Eagerly,
			initialValue = deserialize(settings.getFavoritesFilter(categoryId)),
		)

	override fun peekAppliedOptions(): Set<ListFilterOption> = deserialize(settings.getFavoritesFilter(categoryId))

	override fun updateFilter(transform: (Set<ListFilterOption>) -> Set<ListFilterOption>) {
		val updated = transform(peekAppliedOptions())
		settings.setFavoritesFilter(categoryId, serialize(updated))
	}

	/**
	 * Concise option set shown inline as quick-filter chips above the list.
	 */
	override suspend fun getAvailableFilterOptions(): List<ListFilterOption> = buildList {
		add(ListFilterOption.Downloaded)
		if (settings.isTrackerEnabled) {
			add(ListFilterOption.Macro.NEW_CHAPTERS)
		}
		add(ListFilterOption.Macro.COMPLETED)
		add(ListFilterOption.ContentType(ContentType.MANGA))
		add(ListFilterOption.ContentType(ContentType.MANHWA))
		add(ListFilterOption.ContentType(ContentType.MANHUA))
		repository.findPopularTagTitles(categoryId, 3).mapTo(this) {
			ListFilterOption.TagTitle(it)
		}
		repository.findPopularSources(categoryId, 3).mapTo(this) {
			ListFilterOption.Source(it)
		}
	}

	/**
	 * The full, expanded option set shown in the Filter + Sort sheet: every popular tag and source
	 * for the category, all supported flags and content types.
	 */
	suspend fun getExpandedFilterOptions(): List<ListFilterOption> = buildList {
		add(ListFilterOption.Downloaded)
		if (settings.isTrackerEnabled) {
			add(ListFilterOption.Macro.NEW_CHAPTERS)
		}
		add(ListFilterOption.Macro.COMPLETED)
		add(inProgressFilter())
		add(ListFilterOption.ContentType(ContentType.MANGA))
		add(ListFilterOption.ContentType(ContentType.MANHWA))
		add(ListFilterOption.ContentType(ContentType.MANHUA))
		add(ListFilterOption.ContentType(ContentType.COMICS))
		add(ListFilterOption.ContentType(ContentType.NOVEL))
		repository.findPopularTagTitles(categoryId, FULL_LIMIT).mapTo(this) {
			ListFilterOption.TagTitle(it)
		}
		repository.findPopularSources(categoryId, FULL_LIMIT).mapTo(this) {
			ListFilterOption.Source(it)
		}
	}

	@AssistedFactory
	interface Factory {

		fun create(categoryId: Long, coroutineScope: CoroutineScope): FavoritesListQuickFilter
	}

	companion object {

		private const val FULL_LIMIT = 100
		private const val TOKEN_DOWNLOADED = "downloaded"
		private const val TOKEN_IN_PROGRESS = "in_progress"
		private const val PREFIX_MACRO = "macro:"
		private const val PREFIX_TYPE = "type:"
		private const val PREFIX_TAG = "tag:"
		private const val PREFIX_SOURCE = "source:"

		/**
		 * "In progress" = not fully read yet. Kept as a single factory so that value equality
		 * holds across serialization, the chips and the sheet.
		 */
		fun inProgressFilter(): ListFilterOption.Inverted = ListFilterOption.Inverted(
			option = ListFilterOption.Macro.COMPLETED,
			iconResId = R.drawable.ic_state_ongoing,
			titleResId = R.string.in_progress,
			titleText = null,
		)

		fun serialize(options: Set<ListFilterOption>): Set<String> = options.mapNotNullTo(HashSet()) { option ->
			when (option) {
				ListFilterOption.Downloaded -> TOKEN_DOWNLOADED
				is ListFilterOption.Inverted -> if (option.option == ListFilterOption.Macro.COMPLETED) {
					TOKEN_IN_PROGRESS
				} else {
					null
				}

				is ListFilterOption.Macro -> PREFIX_MACRO + option.name
				is ListFilterOption.ContentType -> PREFIX_TYPE + option.contentType.name
				is ListFilterOption.TagTitle -> PREFIX_TAG + option.titleText
				is ListFilterOption.Source -> PREFIX_SOURCE + option.mangaSource.name
				else -> null
			}
		}

		fun deserialize(tokens: Set<String>): Set<ListFilterOption> = tokens.mapNotNullTo(HashSet()) { token ->
			when {
				token == TOKEN_DOWNLOADED -> ListFilterOption.Downloaded
				token == TOKEN_IN_PROGRESS -> inProgressFilter()
				token.startsWith(PREFIX_MACRO) -> runCatching {
					ListFilterOption.Macro.valueOf(token.substringAfter(PREFIX_MACRO))
				}.getOrNull()

				token.startsWith(PREFIX_TYPE) -> runCatching {
					ListFilterOption.ContentType(ContentType.valueOf(token.substringAfter(PREFIX_TYPE)))
				}.getOrNull()

				token.startsWith(PREFIX_TAG) -> ListFilterOption.TagTitle(token.substringAfter(PREFIX_TAG))
				token.startsWith(PREFIX_SOURCE) -> ListFilterOption.Source(MangaSource(token.substringAfter(PREFIX_SOURCE)))
				else -> null
			}
		}
	}
}
