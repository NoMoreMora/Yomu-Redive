package org.koitharu.kotatsu.alternatives.domain

import androidx.room.withTransaction
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.model.getPreferredBranch
import org.koitharu.kotatsu.core.parser.MangaDataRepository
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.ui.model.MangaOverride
import org.koitharu.kotatsu.core.util.DebugLog
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.details.domain.ProgressUpdateUseCase
import org.koitharu.kotatsu.history.data.HistoryEntity
import org.koitharu.kotatsu.history.data.HistoryLogEntity
import org.koitharu.kotatsu.history.data.toMangaHistory
import org.koitharu.kotatsu.list.domain.ReadingProgress.Companion.PROGRESS_NONE
import org.koitharu.kotatsu.local.domain.DeleteLocalMangaUseCase
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.scrobbling.common.domain.Scrobbler
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblingStatus
import org.koitharu.kotatsu.tracker.data.TrackEntity
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

class MigrateUseCase
@Inject
constructor(
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val mangaDataRepository: MangaDataRepository,
	private val database: MangaDatabase,
	private val progressUpdateUseCase: ProgressUpdateUseCase,
	private val deleteLocalMangaUseCase: DeleteLocalMangaUseCase,
	private val scrobblers: Set<@JvmSuppressWildcards Scrobbler>,
) {
	/**
	 * @param copy when `true`, the original manga is kept (favourites and history are duplicated
	 * onto [newManga] instead of moved, and tracking/scrobbling are left on the original).
	 * When `false` (default) the original is replaced: its data is moved to [newManga].
	 * @param options controls which data is carried over and whether the original's downloads are
	 * deleted afterwards. Defaults to migrating everything and keeping downloads.
	 */
	suspend operator fun invoke(
		oldManga: Manga,
		newManga: Manga,
		copy: Boolean = false,
		options: MigrationOptions = MigrationOptions.DEFAULT,
	) {
		DebugLog.d(
			"Migrate ${if (copy) "copy" else "replace"}: '${oldManga.title}' [${oldManga.source.name}] " +
				"-> '${newManga.title}' [${newManga.source.name}]",
		)
		val oldDetails = if (oldManga.chapters.isNullOrEmpty()) {
			runCatchingCancellable {
				mangaRepositoryFactory.create(oldManga.source).getDetails(oldManga)
			}.getOrDefault(oldManga)
		} else {
			oldManga
		}
		val newDetails = if (newManga.chapters.isNullOrEmpty()) {
			mangaRepositoryFactory.create(newManga.source).getDetails(newManga)
		} else {
			newManga
		}
		mangaDataRepository.storeManga(newDetails, replaceExisting = true)
		database.withTransaction {
			// replace favorites (category memberships). When categories are excluded we still add the
			// new manga to the library, but collapse to a single membership instead of replicating
			// every category the original belonged to.
			val favoritesDao = database.getFavouritesDao()
			val oldFavourites = favoritesDao.findAllRaw(oldDetails.id)
			if (oldFavourites.isNotEmpty()) {
				if (!copy) {
					favoritesDao.delete(oldManga.id)
				}
				val toCopy = if (options.migrateCategories) oldFavourites else oldFavourites.take(1)
				for (f in toCopy) {
					favoritesDao.upsert(f.copy(mangaId = newManga.id))
				}
			}
			// replace history / reading progress (the "Chapters" data option)
			val historyDao = database.getHistoryDao()
			val historyLogDao = database.getHistoryLogDao()
			val oldHistory = if (options.migrateChapters) historyDao.find(oldDetails.id) else null
			val newHistory =
				if (oldHistory != null) {
					val newHistory = makeNewHistory(oldDetails, newDetails, oldHistory)
					if (!copy) {
						historyDao.delete(oldDetails.id)
						historyLogDao.delete(oldDetails.id)
					}
					historyDao.upsert(newHistory)
					// Keep the per-day History tab (history_log) in step with the single-row history the
					// reader writes; without this the migrated manga would vanish from the History tab.
					seedHistoryLog(newHistory)
					DebugLog.d(
						"  resume chapterId=${newHistory.chapterId}, " +
							"${(newHistory.percent * 100).roundToInt()}% of ${newHistory.chaptersCount} ch",
					)
					newHistory
				} else {
					null
				}
			// track
			val tracksDao = database.getTracksDao()
			val oldTrack = tracksDao.find(oldDetails.id)
			if (!copy && oldTrack != null) {
				val lastChapter = newDetails.chapters?.lastOrNull()
				val newTrack =
					TrackEntity(
						mangaId = newDetails.id,
						lastChapterId = lastChapter?.id ?: 0L,
						newChapters = 0,
						lastCheckTime = System.currentTimeMillis(),
						lastChapterDate = lastChapter?.uploadDate ?: 0L,
						lastResult = TrackEntity.RESULT_EXTERNAL_MODIFICATION,
						lastError = null,
					)
				tracksDao.delete(oldDetails.id)
				tracksDao.upsert(newTrack)
			}
			// scrobbling (left on the original when copying)
			for (scrobbler in scrobblers) {
				if (copy) {
					break
				}
				if (!scrobbler.isEnabled) {
					continue
				}
				val prevInfo = scrobbler.getScrobblingInfoOrNull(oldDetails.id) ?: continue
				scrobbler.unregisterScrobbling(oldDetails.id)
				scrobbler.linkManga(newDetails.id, prevInfo.targetId)
				scrobbler.updateScrobblingInfo(
					mangaId = newDetails.id,
					rating = prevInfo.rating,
					status =
						prevInfo.status ?: when {
							newHistory == null -> ScrobblingStatus.PLANNED
							newHistory.percent == 1f -> ScrobblingStatus.COMPLETED
							else -> ScrobblingStatus.READING
						},
					comment = prevInfo.comment,
				)
				if (newHistory != null) {
					scrobbler.scrobble(
						manga = newDetails,
						chapterId = newHistory.chapterId,
					)
				}
			}
		}
		// Custom cover override — done outside the DB transaction because setOverride opens its own.
		if (options.migrateCover) {
			val oldOverride = mangaDataRepository.getOverride(oldManga.id)
			if (oldOverride?.coverUrl != null) {
				val newOverride = mangaDataRepository.getOverride(newDetails.id)
				mangaDataRepository.setOverride(
					newDetails,
					MangaOverride(
						coverUrl = oldOverride.coverUrl,
						title = newOverride?.title,
						contentRating = newOverride?.contentRating,
					),
				)
				if (!copy) {
					mangaDataRepository.setOverride(oldManga, null)
				}
			}
		}
		progressUpdateUseCase(newManga)
		// Delete the original's downloaded chapters after a non-copy migration. No-op (and ignored)
		// when nothing is saved locally — deleteLocalMangaUseCase throws in that case.
		if (!copy && options.deleteDownloads) {
			runCatchingCancellable {
				deleteLocalMangaUseCase(oldManga)
			}.onFailure {
				it.printStackTraceDebug()
			}
		}
	}

	/**
	 * Mirrors [history] into the per-day `history_log`, reusing today's row when one already exists so
	 * a same-day migration collapses to a single entry (matching HistoryRepository.appendHistoryLog).
	 */
	private suspend fun seedHistoryLog(history: HistoryEntity) {
		val dao = database.getHistoryLogDao()
		val last = dao.findLast(history.mangaId)
		if (last != null && isSameLocalDay(last.createdAt, history.updatedAt)) {
			dao.updateProgress(
				id = last.id,
				page = history.page,
				chapterId = history.chapterId,
				scroll = history.scroll,
				percent = history.percent,
				chapters = history.chaptersCount,
				updatedAt = history.updatedAt,
			)
		} else {
			dao.insert(
				HistoryLogEntity(
					id = 0L,
					mangaId = history.mangaId,
					createdAt = history.createdAt,
					updatedAt = history.updatedAt,
					chapterId = history.chapterId,
					page = history.page,
					scroll = history.scroll,
					percent = history.percent,
					deletedAt = 0L,
					chaptersCount = history.chaptersCount,
				),
			)
		}
	}

	private fun isSameLocalDay(a: Long, b: Long): Boolean {
		val zone = ZoneId.systemDefault()
		return Instant.ofEpochMilli(a).atZone(zone).toLocalDate() ==
			Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
	}

	private fun makeNewHistory(
		oldManga: Manga,
		newManga: Manga,
		history: HistoryEntity,
	): HistoryEntity {
		if (oldManga.chapters.isNullOrEmpty()) { // broken/source-less manga (e.g. a Tachiyomi import)
			val branch = newManga.getPreferredBranch(null)
			val chapters = checkNotNull(newManga.getChapters(branch))
			val currentChapter = when {
				// Imported entry: [scroll] holds the continue-from STORY chapter number and chapterId is
				// 0. Resume at the new source's first chapter at or after it — this matches the real
				// chapter no matter how far the series has grown since the backup, instead of scaling by
				// percent (which overshoots when the new source has many more chapters).
				history.chapterId == 0L && history.scroll > 0f ->
					chapters.filter { it.number >= history.scroll }.minByOrNull { it.number } ?: chapters.last()

				// Other broken manga carry a real percent + count: reuse the absolute reading position.
				history.chaptersCount > 0 && history.percent in 0f..1f ->
					chapters[((history.percent * history.chaptersCount).roundToInt() - 1).coerceIn(0, chapters.lastIndex)]

				history.percent in 0f..1f -> chapters[(chapters.lastIndex * history.percent).toInt()]
				else -> chapters.first()
			}
			val newChaptersCount = chapters.count { it.branch == currentChapter.branch }
			val newIndex = chapters.indexOfFirst { it.id == currentChapter.id }.coerceAtLeast(0)
			return HistoryEntity(
				mangaId = newManga.id,
				createdAt = history.createdAt,
				updatedAt = history.updatedAt,
				chapterId = currentChapter.id,
				page = history.page,
				scroll = 0f,
				percent = ((newIndex + 1).toFloat() / newChaptersCount).coerceIn(0f, 1f),
				deletedAt = 0,
				chaptersCount = newChaptersCount,
			)
		}
		val branch = oldManga.getPreferredBranch(history.toMangaHistory())
		val oldChapters = checkNotNull(oldManga.getChapters(branch))
		var index = oldChapters.indexOfFirst { it.id == history.chapterId }
		if (index < 0) {
			index =
				if (history.percent in 0f..1f) {
					(oldChapters.lastIndex * history.percent).toInt()
				} else {
					0
				}
		}
		val newChapters = checkNotNull(newManga.chapters).groupBy { it.branch }
		val newBranch =
			if (newChapters.containsKey(branch)) {
				branch
			} else {
				newManga.getPreferredBranch(null)
			}
		val newChapterId =
			checkNotNull(newChapters[newBranch])
				.let {
					val oldChapter = oldChapters[index]
					it.findByNumber(oldChapter.volume, oldChapter.number) ?: it.getOrNull(index) ?: it.last()
				}.id

		return HistoryEntity(
			mangaId = newManga.id,
			createdAt = history.createdAt,
			updatedAt = history.updatedAt,
			chapterId = newChapterId,
			page = history.page,
			scroll = history.scroll,
			percent = PROGRESS_NONE,
			deletedAt = 0,
			chaptersCount = checkNotNull(newChapters[newBranch]).size,
		)
	}

	private fun List<MangaChapter>.findByNumber(
		volume: Int,
		number: Float,
	): MangaChapter? =
		if (number <= 0f) {
			null
		} else {
			firstOrNull { it.volume == volume && it.number == number }
		}
}
