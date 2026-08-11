package org.koitharu.kotatsu.history.data

import androidx.room.withTransaction
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.toEntity
import org.koitharu.kotatsu.core.db.entity.toManga
import org.koitharu.kotatsu.core.db.entity.toMangaList
import org.koitharu.kotatsu.core.db.entity.toMangaTags
import org.koitharu.kotatsu.core.db.entity.toMangaTagsList
import org.koitharu.kotatsu.core.model.getPreferredBranch
import org.koitharu.kotatsu.core.model.MangaHistory
import org.koitharu.kotatsu.core.model.isLocal
import org.koitharu.kotatsu.core.model.isNsfw
import org.koitharu.kotatsu.core.model.toMangaSources
import org.koitharu.kotatsu.core.parser.MangaDataRepository
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.ProgressIndicatorMode
import org.koitharu.kotatsu.core.ui.util.ReversibleHandle
import org.koitharu.kotatsu.core.util.ext.mapItems
import org.koitharu.kotatsu.history.domain.model.MangaWithHistory
import org.koitharu.kotatsu.list.domain.ListFilterOption
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.list.domain.ReadingProgress
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.util.almostEquals
import org.koitharu.kotatsu.parsers.util.findById
import org.koitharu.kotatsu.parsers.util.levenshteinDistance
import org.koitharu.kotatsu.scrobbling.common.domain.Scrobbler
import org.koitharu.kotatsu.scrobbling.common.domain.tryScrobble
import org.koitharu.kotatsu.search.domain.SearchKind
import org.koitharu.kotatsu.tracker.domain.CheckNewChaptersUseCase
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class HistoryRepository @Inject constructor(
	private val db: MangaDatabase,
	private val settings: AppSettings,
	private val scrobblers: Set<@JvmSuppressWildcards Scrobbler>,
	private val mangaRepository: MangaDataRepository,
	private val localObserver: HistoryLocalObserver,
	private val newChaptersUseCaseProvider: Provider<CheckNewChaptersUseCase>,
) {

	suspend fun getList(offset: Int, limit: Int): List<Manga> {
		val entities = db.getHistoryDao().findAll(offset, limit)
		return entities.map { it.toManga() }
	}

	suspend fun search(query: String, kind: SearchKind, limit: Int): List<Manga> {
		val dao = db.getHistoryDao()
		val q = "%$query%"
		val entities = when (kind) {
			SearchKind.SIMPLE,
			SearchKind.TITLE -> dao.searchByTitle(q, limit).sortedBy { it.manga.title.levenshteinDistance(query) }

			SearchKind.AUTHOR -> dao.searchByAuthor(q, limit)
			SearchKind.TAG -> dao.searchByTag(q, limit)
		}
		return entities.toMangaList()
	}

	suspend fun getLastOrNull(): Manga? {
		val entity = db.getHistoryDao().findAll(0, 1).firstOrNull() ?: return null
		return entity.toManga()
	}

	fun observeLast(): Flow<Manga?> {
		return db.getHistoryDao().observeAll(1).map {
			val first = it.firstOrNull()
			first?.toManga()
		}
	}

	fun observeAll(): Flow<List<Manga>> {
		return db.getHistoryDao().observeAll().mapItems {
			it.toManga()
		}
	}

	fun observeAll(limit: Int): Flow<List<Manga>> {
		return db.getHistoryDao().observeAll(limit).mapItems {
			it.toManga()
		}
	}

	fun observeAllWithHistory(
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	): Flow<List<MangaWithHistory>> {
		if (ListFilterOption.Downloaded in filterOptions) {
			return localObserver.observeAll(order, filterOptions, limit)
		}
		return db.getHistoryDao().observeAll(order, filterOptions, limit).mapItems {
			MangaWithHistory(
				it.toManga(),
				it.history.toMangaHistory(),
			)
		}
	}

	fun observeOne(id: Long): Flow<MangaHistory?> {
		return db.getHistoryDao().observe(id).map {
			it?.toMangaHistory()
		}
	}

	/**
	 * Per-day reading log for the History tab. Keeps the older days when a manga is re-read on a
	 * later day (one entry per manga per day), unlike [observeAllWithHistory] which shows one row per
	 * manga. Falls back to the single-row source for the Downloaded filter.
	 */
	fun observeHistoryLog(
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int,
	): Flow<List<MangaWithHistory>> {
		if (ListFilterOption.Downloaded in filterOptions) {
			return localObserver.observeAll(order, filterOptions, limit)
		}
		return db.getHistoryLogDao().observeAll(order, filterOptions, limit).mapItems {
			MangaWithHistory(
				manga = it.toManga(),
				history = it.history.toMangaHistory(),
				id = it.history.id,
			)
		}
	}

	suspend fun addOrUpdate(manga: Manga, chapterId: Long, page: Int, scroll: Int, percent: Float, force: Boolean) {
		if (!force && shouldSkip(manga)) {
			return
		}
		assert(manga.chapters != null)
		db.withTransaction {
			mangaRepository.storeManga(manga, replaceExisting = true)
			val branch = manga.chapters?.findById(chapterId)?.branch
			val now = System.currentTimeMillis()
			val chaptersCount = manga.chapters?.count { it.branch == branch } ?: 0
			db.getHistoryDao().upsert(
				HistoryEntity(
					mangaId = manga.id,
					createdAt = now,
					updatedAt = now,
					chapterId = chapterId,
					page = page,
					scroll = scroll.toFloat(), // we migrate to int, but decide to not update database
					percent = percent,
					chaptersCount = chaptersCount,
					deletedAt = 0L,
				),
			)
			appendHistoryLog(manga.id, chapterId, page, scroll.toFloat(), percent, chaptersCount, now)
			newChaptersUseCaseProvider.get()(manga, chapterId)
			scrobblers.forEach { it.tryScrobble(manga, chapterId) }
		}
	}

	/**
	 * Appends a dated row to the per-day [history_log][org.koitharu.kotatsu.history.data.HistoryLogEntity],
	 * or updates today's row in place so a single day collapses to one entry that keeps its progress.
	 */
	private suspend fun appendHistoryLog(
		mangaId: Long,
		chapterId: Long,
		page: Int,
		scroll: Float,
		percent: Float,
		chaptersCount: Int,
		now: Long,
	) {
		val dao = db.getHistoryLogDao()
		val last = dao.findLast(mangaId)
		if (last != null && isSameLocalDay(last.createdAt, now)) {
			dao.updateProgress(
				id = last.id,
				page = page,
				chapterId = chapterId,
				scroll = scroll,
				percent = percent,
				chapters = chaptersCount,
				updatedAt = now,
			)
		} else {
			dao.insert(
				HistoryLogEntity(
					id = 0L,
					mangaId = mangaId,
					createdAt = now,
					updatedAt = now,
					chapterId = chapterId,
					page = page,
					scroll = scroll,
					percent = percent,
					deletedAt = 0L,
					chaptersCount = chaptersCount,
				),
			)
		}
	}

	private fun isSameLocalDay(a: Long, b: Long): Boolean {
		val zone = ZoneId.systemDefault()
		return Instant.ofEpochMilli(a).atZone(zone).toLocalDate() ==
			Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
	}

	suspend fun getOne(manga: Manga): MangaHistory? {
		return db.getHistoryDao().find(manga.id)?.recoverIfNeeded(manga)?.toMangaHistory()
	}

	suspend fun findSimilarByTitle(manga: Manga, limit: Int): List<MangaHistory> {
		val title = manga.title.trim()
		if (title.length < MIN_TITLE_MATCH_LENGTH) {
			return emptyList()
		}
		val result = ArrayList<MangaHistory>()
		for (candidate in db.getMangaDao().searchByTitle("%$title%", limit)) {
			if (candidate.manga.id == manga.id || !candidate.manga.title.matchesTitle(manga)) {
				continue
			}
			db.getHistoryDao().find(candidate.manga.id)?.toMangaHistory()?.let(result::add)
		}
		return result
	}

	/**
	 * The chapter the user would continue from, as a STORY chapter number, or `null` when there's no
	 * saved progress. Used by the migration screen for broken (source-less) entries, whose own chapter
	 * list is empty so "Latest" reads 0. Imported entries carry the number in [HistoryEntity.scroll]
	 * (with chapterId 0); others approximate it from the percent-based position.
	 */
	suspend fun getContinueChapterNumber(mangaId: Long): Float? {
		val entity = db.getHistoryDao().find(mangaId) ?: return null
		if (entity.chapterId == 0L && entity.scroll > 0f) {
			return entity.scroll
		}
		if (!ReadingProgress.isValid(entity.percent) || entity.chaptersCount <= 0) {
			return null
		}
		return (entity.chaptersCount * entity.percent).roundToInt().coerceIn(1, entity.chaptersCount).toFloat()
	}

	suspend fun getProgress(mangaId: Long, mode: ProgressIndicatorMode): ReadingProgress? {
		val entity = db.getHistoryDao().find(mangaId) ?: return null
		val fixedPercent = if (ReadingProgress.isCompleted(entity.percent)) 1f else entity.percent
		return ReadingProgress(
			percent = fixedPercent,
			totalChapters = entity.chaptersCount,
			mode = mode,
		).takeIf { it.isValid() }
	}

	suspend fun clear() = db.withTransaction {
		db.getHistoryDao().clear()
		db.getHistoryLogDao().clear()
	}

	suspend fun delete(manga: Manga) = db.withTransaction {
		db.getHistoryDao().delete(manga.id)
		db.getHistoryLogDao().delete(manga.id)
		mangaRepository.gcChaptersCache()
	}

	suspend fun deleteAfter(minDate: Long) = db.withTransaction {
		db.getHistoryDao().deleteAfter(minDate)
		db.getHistoryLogDao().deleteAfter(minDate)
		mangaRepository.gcChaptersCache()
	}

	suspend fun deleteNotFavorite() = db.withTransaction {
		db.getHistoryDao().deleteNotFavorite()
		db.getHistoryLogDao().deleteNotFavorite()
		mangaRepository.gcChaptersCache()
	}

	suspend fun delete(ids: Collection<Long>): ReversibleHandle {
		db.withTransaction {
			for (id in ids) {
				db.getHistoryDao().delete(id)
				db.getHistoryLogDao().delete(id)
			}
			mangaRepository.gcChaptersCache()
		}
		return ReversibleHandle {
			recover(ids)
		}
	}

	/**
	 * Try to replace one manga with another one
	 * Useful for replacing saved manga on deleting it with remote source
	 */
	suspend fun deleteOrSwap(manga: Manga, alternative: Manga?) {
		if (alternative == null || db.getMangaDao().update(alternative.toEntity()) <= 0) {
			delete(manga)
		}
	}

	suspend fun getPopularTags(limit: Int): List<MangaTag> {
		return db.getHistoryDao().findPopularTags(limit).toMangaTagsList()
	}

	suspend fun getPopularSources(limit: Int): List<MangaSource> {
		return db.getHistoryDao().findPopularSources(limit).toMangaSources()
	}

	fun shouldSkip(manga: Manga): Boolean = settings.isIncognitoModeEnabled(manga.isNsfw())

	fun observeShouldSkip(manga: Manga): Flow<Boolean> {
		return settings.observe(AppSettings.KEY_INCOGNITO_MODE, AppSettings.KEY_INCOGNITO_NSFW)
			.map { shouldSkip(manga) }
			.distinctUntilChanged()
	}

	private suspend fun recover(ids: Collection<Long>) {
		db.withTransaction {
			for (id in ids) {
				db.getHistoryDao().recover(id)
				db.getHistoryLogDao().recover(id)
			}
		}
	}

	private suspend fun HistoryEntity.recoverIfNeeded(manga: Manga): HistoryEntity {
		val chapters = manga.findRecoveryChapters(this)
		if (manga.isLocal || chapters.isNullOrEmpty() || chapters.findById(chapterId) != null) {
			return this
		}
		val newChapterIndex = estimateRecoveredChapterIndex(chapters.size) ?: return this
		val newChapterId = chapters.getOrNull(newChapterIndex)?.id ?: return this
		val newEntity = copy(
			chapterId = newChapterId,
			percent = estimateRecoveredPercent(chapters.size),
			chaptersCount = chapters.size,
		)
		db.getHistoryDao().update(newEntity)
		return newEntity
	}

	private fun Manga.findRecoveryChapters(history: HistoryEntity): List<MangaChapter>? {
		val allChapters = chapters?.takeUnless { it.isEmpty() } ?: return null
		if (history.chaptersCount > 0) {
			return allChapters.groupBy { it.branch }
				.values
				.minByOrNull { abs(it.size - history.chaptersCount) }
		}
		return getChapters(getPreferredBranch(null)).takeUnless { it.isEmpty() } ?: allChapters
	}

	private fun HistoryEntity.estimateRecoveredChapterIndex(newChaptersCount: Int): Int? {
		if (newChaptersCount <= 0 || !ReadingProgress.isValid(percent)) {
			return null
		}
		val referenceCount = chaptersCount.takeIf { it > 0 } ?: newChaptersCount
		val referenceIndex = if (ReadingProgress.isCompleted(percent)) {
			referenceCount - 1
		} else {
			// percent counts the page currently on screen as read, so a value on an exact
			// chapter boundary is ambiguous: "finished chapter i" or "still inside chapter i"
			// (single-page long-strip chapters always sit on the boundary). Resolve to
			// chapter i — never skip ahead — the preserved page/scroll belong to it anyway.
			ceil(percent.toDouble() * referenceCount).toInt() - 1
		}
		return referenceIndex.coerceIn(0, newChaptersCount - 1)
	}

	private fun HistoryEntity.estimateRecoveredPercent(newChaptersCount: Int): Float {
		if (chaptersCount <= 0 || newChaptersCount <= 0 || !ReadingProgress.isValid(percent)) {
			return percent
		}
		return (percent * chaptersCount / newChaptersCount).coerceIn(0f, 1f)
	}

	private fun HistoryWithManga.toManga() = manga.toManga(tags.toMangaTags(), null)

	private fun HistoryLogWithManga.toManga() = manga.toManga(tags.toMangaTags(), null)

	private fun String.matchesTitle(manga: Manga): Boolean {
		if (almostEquals(manga.title, TITLE_MATCH_THRESHOLD)) {
			return true
		}
		return manga.altTitles.any { altTitle ->
			altTitle.isNotBlank() && almostEquals(altTitle, TITLE_MATCH_THRESHOLD)
		}
	}

	private companion object {

		const val MIN_TITLE_MATCH_LENGTH = 4
		const val TITLE_MATCH_THRESHOLD = 0.12f
	}
}
