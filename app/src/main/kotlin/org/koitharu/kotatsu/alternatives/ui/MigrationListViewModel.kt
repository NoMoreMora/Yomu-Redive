package org.koitharu.kotatsu.alternatives.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.koitharu.kotatsu.alternatives.domain.MigrateUseCase
import org.koitharu.kotatsu.alternatives.domain.MigrationOptions
import org.koitharu.kotatsu.core.model.chaptersCount
import org.koitharu.kotatsu.core.parser.MangaDataRepository
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.util.ext.MutableEventFlow
import org.koitharu.kotatsu.core.util.ext.call
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.search.domain.SearchKind
import org.koitharu.kotatsu.search.domain.SearchV2Helper
import javax.inject.Inject

sealed interface MatchState {
	data object Searching : MatchState
	data object NotFound : MatchState

	data class Matched(val manga: Manga, val chaptersCount: Int) : MatchState
}

data class MigrationRow(
	val original: Manga,
	val originalChaptersCount: Int,
	val match: MatchState,
	val skipped: Boolean = false,
	/** Continue-from chapter number of a broken/imported original; 0 when unread/unknown. */
	val continueChapter: Float = 0f,
)

@HiltViewModel
class MigrationListViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val mangaDataRepository: MangaDataRepository,
	private val searchHelperFactory: SearchV2Helper.Factory,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val migrateUseCase: MigrateUseCase,
	private val sourcesRepository: MangaSourcesRepository,
	private val historyRepository: HistoryRepository,
	private val settings: AppSettings,
) : BaseViewModel() {

	private val ids: LongArray = savedStateHandle.get<LongArray>(MigrationListActivity.EXTRA_IDS) ?: LongArray(0)
	private val initialSourceName: String? = savedStateHandle.get<String>(MigrationListActivity.EXTRA_SOURCE)

	private var originals: List<Manga> = emptyList()
	// Continue-from chapter number per manga id, resolved once from saved history. Broken (imported)
	// manga have no chapter list, so this is the only way to show their reading position.
	private var continueChapters: Map<Long, Float> = emptyMap()

	private val rows = MutableStateFlow<List<MigrationRow>>(emptyList())
	private val options = MutableStateFlow(settings.migrationOptions)

	val targetSource = MutableStateFlow<MangaSource?>(null)
	val availableSources = MutableStateFlow<List<MangaSource>>(emptyList())

	/** Row selected in the tablet sidebar layout; null when nothing is selected yet. */
	val selectedRowId = MutableStateFlow<Long?>(null)

	val content: StateFlow<List<MigrationRow>> = combine(rows, options) { list, opts ->
		list.filter { row ->
			val m = row.match
			when {
				opts.hideWithoutMatch && m is MatchState.NotFound -> false
				opts.hideWithoutNewerChapters && m is MatchState.Matched &&
					m.chaptersCount <= row.originalChaptersCount -> false

				else -> true
			}
		}
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptyList())

	val onFinished = MutableEventFlow<Int>()
	val onRowMigrated = MutableEventFlow<Unit>()

	val matchedCount: StateFlow<Int> = rows.map { list ->
		list.count { !it.skipped && it.match is MatchState.Matched }
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, 0)

	private var matchJob: Job? = null
	private var lastSearchKey: SearchKey? = null

	init {
		observeOptions()
		load()
	}

	private fun observeOptions() {
		launchJob(Dispatchers.Default) {
			settings.observe(
				AppSettings.KEY_MIGRATION_CHAPTERS,
				AppSettings.KEY_MIGRATION_CATEGORIES,
				AppSettings.KEY_MIGRATION_COVER,
				AppSettings.KEY_MIGRATION_DELETE_DOWNLOADS,
				AppSettings.KEY_MIGRATION_EXTRA_KEYWORDS,
				AppSettings.KEY_MIGRATION_HIDE_UNMATCHED,
				AppSettings.KEY_MIGRATION_HIDE_NO_NEWER,
				AppSettings.KEY_MIGRATION_ADVANCED,
				AppSettings.KEY_MIGRATION_BY_CHAPTERS,
			).collect {
				options.value = settings.migrationOptions
				rematchIfNeeded()
			}
		}
	}

	private fun load() {
		launchLoadingJob(Dispatchers.Default) {
			val list = ArrayList<Manga>(ids.size)
			for (id in ids) {
				mangaDataRepository.findMangaById(id, withChapters = true)?.let(list::add)
			}
			originals = list
			continueChapters = list.mapNotNull { manga ->
				historyRepository.getContinueChapterNumber(manga.id)?.let { manga.id to it }
			}.toMap()
			rows.value = list.map { newRow(it, MatchState.Searching) }
			availableSources.value = sourcesRepository.getEnabledSources()
			targetSource.value = resolveDefaultSource(list)
			rematchIfNeeded()
		}
	}

	fun setTargetSource(source: MangaSource?) {
		if (targetSource.value == source || source == null) {
			return
		}
		targetSource.value = source
		rematchIfNeeded()
	}

	fun setTargetSourceByName(name: String?) {
		val source = name?.let { n -> sourcesRepository.allMangaSources.firstOrNull { it.name == n } } ?: return
		// A source picked via "More sources…" may be disabled; make sure it is selectable in the dropdown.
		if (availableSources.value.none { it == source }) {
			availableSources.value = availableSources.value + source
		}
		setTargetSource(source)
	}

	fun setMatch(originalId: Long, match: Manga) {
		updateRow(originalId, MatchState.Matched(match, match.chaptersCount()))
	}

	/** Marks a row as selected in the tablet sidebar layout. */
	fun selectRow(originalId: Long) {
		selectedRowId.value = originalId
	}

	fun setSkipped(originalId: Long, skipped: Boolean) {
		rows.value = rows.value.map { if (it.original.id == originalId) it.copy(skipped = skipped) else it }
	}

	fun migrateRowNow(originalId: Long, copy: Boolean) {
		val row = rows.value.firstOrNull { it.original.id == originalId } ?: return
		val target = (row.match as? MatchState.Matched)?.manga ?: return
		launchLoadingJob(Dispatchers.Default) {
			runCatchingCancellable {
				migrateUseCase(row.original, target, copy = copy, options = options.value)
			}.onSuccess {
				rows.value = rows.value.filterNot { it.original.id == originalId }
				originals = originals.filterNot { it.id == originalId }
				onRowMigrated.call(Unit)
			}.onFailure {
				it.printStackTraceDebug()
			}
		}
	}

	fun apply(copy: Boolean) {
		launchLoadingJob(Dispatchers.Default) {
			val opts = options.value
			var migrated = 0
			for (row in rows.value) {
				if (row.skipped) {
					continue
				}
				val target = (row.match as? MatchState.Matched)?.manga ?: continue
				runCatchingCancellable {
					migrateUseCase(row.original, target, copy = copy, options = opts)
				}.onSuccess {
					migrated++
				}.onFailure {
					it.printStackTraceDebug()
				}
			}
			onFinished.call(migrated)
		}
	}

	private fun rematchIfNeeded() {
		val opts = options.value
		val key = SearchKey(
			source = targetSource.value,
			advanced = opts.advancedSearch,
			byChapters = opts.matchByChapterCount,
			keywords = opts.extraKeywords.trim(),
		)
		if (key == lastSearchKey) {
			return
		}
		lastSearchKey = key
		startMatching(key)
	}

	private fun startMatching(key: SearchKey) {
		val prevJob = matchJob
		matchJob = launchLoadingJob(Dispatchers.Default) {
			prevJob?.cancelAndJoin()
			// reset to Searching, keeping any per-row skip flags
			val skipped = rows.value.associate { it.original.id to it.skipped }
			rows.value = originals.map {
				newRow(it, MatchState.Searching, skipped[it.id] == true)
			}
			val source = key.source
			if (source == null) {
				rows.value = rows.value.map { it.copy(match = MatchState.NotFound) }
				return@launchLoadingJob
			}
			coroutineScope {
				originals.forEach { original ->
					launch {
						val match = findMatch(original, source, key)
						updateRow(original.id, match ?: MatchState.NotFound)
					}
				}
			}
		}
	}

	private suspend fun findMatch(original: Manga, source: MangaSource, key: SearchKey): MatchState.Matched? {
		val baseQuery = if (key.advanced) keywords(original.title) else original.title
		val query = if (key.keywords.isNotEmpty()) "$baseQuery ${key.keywords}" else baseQuery
		val kind = if (key.advanced) SearchKind.SIMPLE else SearchKind.TITLE
		val hits = runCatchingCancellable {
			searchHelperFactory.create(source).invoke(query, kind)?.manga?.filter { it.id != original.id }
		}.getOrNull().orEmpty()
		if (hits.isEmpty()) {
			return null
		}
		// Resolving details is a network getDetails call per candidate. Matching by chapter count needs to
		// scan the top N and pick the largest; otherwise resolve just the first hit so ordinary migrations
		// keep their original single-request cost.
		if (key.byChapters) {
			val best = hits.take(CANDIDATE_LIMIT)
				.map { val details = withDetails(it); details to details.chaptersCount() }
				.maxByOrNull { it.second } ?: return null
			return MatchState.Matched(best.first, best.second)
		}
		val details = withDetails(hits.first())
		return MatchState.Matched(details, details.chaptersCount())
	}

	private suspend fun withDetails(manga: Manga): Manga = runCatchingCancellable {
		mangaRepositoryFactory.create(manga.source).getDetails(manga)
	}.getOrDefault(manga)

	@Synchronized
	private fun updateRow(originalId: Long, state: MatchState) {
		rows.value = rows.value.map { if (it.original.id == originalId) it.copy(match = state) else it }
	}

	private fun newRow(manga: Manga, match: MatchState, skipped: Boolean = false): MigrationRow {
		return MigrationRow(
			original = manga,
			originalChaptersCount = manga.chaptersCount(),
			match = match,
			skipped = skipped,
			continueChapter = continueChapters[manga.id] ?: 0f,
		)
	}

	private suspend fun resolveDefaultSource(originals: List<Manga>): MangaSource? {
		// Only ever default to an ENABLED source — never a disabled one the user removed from their
		// list (previously `preferred`/most-used could resolve to a disabled source like ComicK).
		val enabled = sourcesRepository.getEnabledSources()
		val enabledNames = enabled.mapTo(HashSet()) { it.name }
		// Compare by name — MangaSource instances loaded from the DB don't always object-equal the
		// enum instances returned by the sources repository, so a set/`in` check can miss the origin.
		val originalSourceNames = originals.mapTo(HashSet()) { it.source.name }
		// A source explicitly passed in when opening the screen wins, but only if it's enabled.
		initialSourceName?.let { name -> enabled.firstOrNull { it.name == name } }?.let { return it }
		// The saved preferred target, if it's enabled and not one we're migrating away from.
		settings.migrationPreferredSource
			?.let { name -> enabled.firstOrNull { it.name == name } }
			?.takeIf { it.name !in originalSourceNames }
			?.let { return it }
		// Most-recently-used source that is enabled and not an origin.
		sourcesRepository.getTopSources(TOP_SOURCES_LIMIT)
			.firstOrNull { it.name in enabledNames && it.name !in originalSourceNames }
			?.let { return it }
		// Any other enabled source; if the only enabled one is an origin, still fall back to it so the
		// dropdown has a selection.
		return enabled.firstOrNull { it.name !in originalSourceNames } ?: enabled.firstOrNull()
	}

	private fun keywords(title: String): String = title.split(WORD_SEPARATORS)
		.filter { it.length > 2 }
		.joinToString(" ")
		.ifEmpty { title }

	private data class SearchKey(
		val source: MangaSource?,
		val advanced: Boolean,
		val byChapters: Boolean,
		val keywords: String,
	)

	private companion object {
		// Max hits to resolve details for when matching by chapter count. Each resolve is a network
		// getDetails call, so this stays low; it bounds the "match by chapter count" scan.
		private const val CANDIDATE_LIMIT = 5
		private const val TOP_SOURCES_LIMIT = 10
		private val WORD_SEPARATORS = Regex("[^\\p{L}\\p{N}]+")
	}
}
