package org.koitharu.kotatsu.alternatives.ui

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.alternatives.domain.MigrateUseCase
import org.koitharu.kotatsu.core.parser.MangaDataRepository
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.util.ext.MutableEventFlow
import org.koitharu.kotatsu.core.util.ext.call
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.search.domain.SearchKind
import org.koitharu.kotatsu.search.domain.SearchV2Helper
import javax.inject.Inject

sealed interface MatchState {
	data object Searching : MatchState
	data object NotFound : MatchState
	data class Matched(val manga: Manga) : MatchState
}

data class MigrationRow(
	val original: Manga,
	val match: MatchState,
)

@HiltViewModel
class MigrationListViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val mangaDataRepository: MangaDataRepository,
	private val searchHelperFactory: SearchV2Helper.Factory,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val migrateUseCase: MigrateUseCase,
	sourcesRepository: MangaSourcesRepository,
) : BaseViewModel() {

	private val ids: LongArray = savedStateHandle.get<LongArray>(MigrationListActivity.EXTRA_IDS) ?: LongArray(0)
	private val targetSource: MangaSource? = savedStateHandle.get<String>(MigrationListActivity.EXTRA_SOURCE)?.let { name ->
		sourcesRepository.allMangaSources.firstOrNull { it.name == name }
	}

	private val rows = MutableStateFlow<List<MigrationRow>>(emptyList())
	val content: StateFlow<List<MigrationRow>> = rows

	val onFinished = MutableEventFlow<Int>()

	val matchedCount: Int
		get() = rows.value.count { it.match is MatchState.Matched }

	init {
		loadAndMatch()
	}

	private fun loadAndMatch() {
		launchLoadingJob(Dispatchers.Default) {
			val originals = ArrayList<Manga>(ids.size)
			for (id in ids) {
				mangaDataRepository.findMangaById(id, withChapters = false)?.let(originals::add)
			}
			rows.value = originals.map { MigrationRow(it, MatchState.Searching) }
			val source = targetSource
			if (source == null) {
				rows.value = rows.value.map { it.copy(match = MatchState.NotFound) }
				return@launchLoadingJob
			}
			coroutineScope {
				originals.forEach { original ->
					launch {
						val match = findMatch(original, source)
						updateRow(original.id, if (match != null) MatchState.Matched(match) else MatchState.NotFound)
					}
				}
			}
		}
	}

	private suspend fun findMatch(original: Manga, source: MangaSource): Manga? {
		val hit = runCatchingCancellable {
			searchHelperFactory.create(source)
				.invoke(original.title, SearchKind.TITLE)
				?.manga
				?.firstOrNull { it.id != original.id }
		}.getOrNull() ?: return null
		return runCatchingCancellable {
			mangaRepositoryFactory.create(hit.source).getDetails(hit)
		}.getOrDefault(hit)
	}

	@Synchronized
	private fun updateRow(originalId: Long, state: MatchState) {
		rows.value = rows.value.map { if (it.original.id == originalId) it.copy(match = state) else it }
	}

	fun setMatch(originalId: Long, match: Manga) {
		updateRow(originalId, MatchState.Matched(match))
	}

	fun apply(copy: Boolean) {
		launchLoadingJob(Dispatchers.Default) {
			var migrated = 0
			for (row in rows.value) {
				val target = (row.match as? MatchState.Matched)?.manga ?: continue
				runCatchingCancellable {
					migrateUseCase(row.original, target, copy = copy)
				}.onSuccess {
					migrated++
				}.onFailure {
					it.printStackTraceDebug()
				}
			}
			onFinished.call(migrated)
		}
	}
}
