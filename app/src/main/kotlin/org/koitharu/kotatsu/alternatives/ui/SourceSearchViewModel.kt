package org.koitharu.kotatsu.alternatives.ui

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.search.domain.SearchKind
import org.koitharu.kotatsu.search.domain.SearchV2Helper
import javax.inject.Inject

/**
 * Searches a single [MangaSource] for a free-text query and returns ALL results (no title-similarity
 * filtering — SearchKind.SIMPLE), so the user can find other versions of a manga (different name or
 * language) on that source. Used by the migration match-picker's per-card "Search" button.
 */
@HiltViewModel
class SourceSearchViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val searchHelperFactory: SearchV2Helper.Factory,
	sourcesRepository: MangaSourcesRepository,
) : BaseViewModel() {

	private val source: MangaSource? = savedStateHandle.get<String>(SourceSearchActivity.EXTRA_SOURCE)?.let { name ->
		sourcesRepository.allMangaSources.firstOrNull { it.name == name }
	}
	val initialQuery: String = savedStateHandle.get<String>(SourceSearchActivity.EXTRA_QUERY).orEmpty()

	private val results = MutableStateFlow<List<Manga>>(emptyList())
	val content: StateFlow<List<Manga>> = results

	init {
		if (initialQuery.isNotBlank()) {
			search(initialQuery)
		}
	}

	fun search(query: String) {
		val src = source ?: return
		launchLoadingJob(Dispatchers.Default) {
			val list = runCatchingCancellable {
				searchHelperFactory.create(src).invoke(query, SearchKind.SIMPLE)?.manga
			}.getOrNull().orEmpty()
			results.value = list
		}
	}
}
