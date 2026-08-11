package org.koitharu.kotatsu.backups.data.tachiyomi

import android.content.Context
import androidx.room.withTransaction
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.withContext
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.MangaEntity
import org.koitharu.kotatsu.core.db.entity.TagEntity
import org.koitharu.kotatsu.core.model.IMPORTED_SOURCE_TAG_KEY_PREFIX
import org.koitharu.kotatsu.core.model.UnknownMangaSource
import org.koitharu.kotatsu.core.network.BaseHttpClient
import org.koitharu.kotatsu.core.util.CompositeResult
import org.koitharu.kotatsu.core.util.DebugLog
import org.koitharu.kotatsu.core.util.progress.Progress
import org.koitharu.kotatsu.favourites.data.FavouriteCategoryEntity
import org.koitharu.kotatsu.favourites.data.FavouriteEntity
import org.koitharu.kotatsu.history.data.HistoryEntity
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.util.await
import org.koitharu.kotatsu.parsers.util.longHashCode
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.scrobbling.common.data.ScrobblingEntity
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerService
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.inject.Inject

/**
 * Imports a TachiyomiSY / Tachiyomi / Mihon `.tachibk` backup into the library. Aimed at users
 * switching platforms: every manga is imported even when its source doesn't exist here — it is
 * stored under [UnknownMangaSource] so it reads as [org.koitharu.kotatsu.core.model.isBroken] and
 * can be resolved with the existing Migration tools. Categories are recreated; manga without a
 * category go into a fallback "Imported" category. The last-read position is imported as a fraction
 * of the series (the broken manga has no real chapters to point at); Migration restores it onto the
 * matched source via [org.koitharu.kotatsu.alternatives.domain.MigrateUseCase].
 */
@Reusable
class TachiyomiBackupImporter @Inject constructor(
	@ApplicationContext private val context: Context,
	private val database: MangaDatabase,
	@BaseHttpClient private val okHttp: OkHttpClient,
) {

	private val protoBuf = ProtoBuf

	// Tachiyomi source id -> extension name. Baseline comes from the bundled keiyoushi snapshot (the
	// backup itself doesn't store names once the extension is uninstalled), overlaid at import time with
	// a disk-cached fetch of the live keiyoushi index. Populated at the start of import() from
	// loadSourceNames() so a freshly refreshed cache is reflected (see refreshSourceNamesCache).
	private var sourceNames: Map<Long, String> = emptyMap()

	suspend fun import(input: InputStream, progress: FlowCollector<Progress>?): CompositeResult {
		progress?.emit(Progress.INDETERMINATE)
		// Best-effort refresh of the source-name map from the live keiyoushi index before we build labels,
		// so newly added sources resolve. Fully offline-safe: any failure keeps the bundled asset + last
		// good cache. Then (re)build the lookup so a fresh cache is picked up.
		refreshSourceNamesCache()
		sourceNames = loadSourceNames()
		val bytes = GZIPInputStream(input).use { it.readBytes() }
		val backup = protoBuf.decodeFromByteArray(TachiyomiBackup.serializer(), bytes)
		DebugLog.d("Tachiyomi import: ${backup.backupManga.size} manga, ${backup.backupCategories.size} categories")
		// A human label per source id: the real extension name where known, else the site domain from
		// a manga URL. Used to tag imports so their origin is identifiable and filterable.
		val sourceLabels = buildSourceLabels(backup.backupManga)
		// Recreate categories (Tachiyomi manga reference them by their `order`).
		val categoryIdByOrder = importCategories(backup.backupCategories)
		val fallbackCategoryId = if (backup.backupManga.any { it.categories.isEmpty() }) {
			createCategory(FALLBACK_CATEGORY_TITLE)
		} else {
			null
		}
		var result = CompositeResult.EMPTY
		var p = Progress(0, backup.backupManga.size)
		progress?.emit(p)
		for (manga in backup.backupManga) {
			result += runCatchingCancellable {
				database.withTransaction {
					database.importManga(manga, categoryIdByOrder, fallbackCategoryId, sourceLabels)
				}
			}.onFailure {
				DebugLog.e("Tachiyomi import failed for '${manga.title}' (${manga.chapters.size} ch)", it)
			}
			p++
			progress?.emit(p)
		}
		DebugLog.d("Tachiyomi import finished (${backup.backupManga.size} processed)")
		return result
	}

	private suspend fun importCategories(categories: List<TachiyomiBackupCategory>): Map<Long, Long> {
		val result = HashMap<Long, Long>(categories.size)
		for (category in categories.sortedBy { it.order }) {
			result[category.order] = createCategory(category.name.ifBlank { FALLBACK_CATEGORY_TITLE })
		}
		return result
	}

	private suspend fun createCategory(title: String): Long {
		val dao = database.getFavouriteCategoriesDao()
		return dao.insert(
			FavouriteCategoryEntity(
				categoryId = 0,
				createdAt = System.currentTimeMillis(),
				sortKey = dao.getNextSortKey(),
				title = title,
				order = ListSortOrder.NEWEST.name,
				track = false,
				isVisibleInLibrary = true,
				deletedAt = 0L,
			),
		)
	}

	private suspend fun MangaDatabase.importManga(
		manga: TachiyomiBackupManga,
		categoryIdByOrder: Map<Long, Long>,
		fallbackCategoryId: Long?,
		sourceLabels: Map<Long, String>,
	) {
		val source = UnknownMangaSource.name
		val mangaId = "${manga.source}|${manga.url}".longHashCode()
		val tags = manga.genre.mapNotNullTo(ArrayList()) { genre ->
			val key = genre.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNullTo null
			TagEntity(
				id = "${key}_$source".longHashCode(),
				title = key,
				key = key,
				source = source,
				isPinned = false,
			)
		}
		// Origin tag: keeps the source column "UNKNOWN" (so isBroken/migration are unaffected) while
		// letting the user identify and filter imports by their original source. One shared tag per
		// source id.
		sourceLabels[manga.source]?.let { label ->
			val key = "$IMPORTED_SOURCE_TAG_KEY_PREFIX${manga.source}"
			tags += TagEntity(
				id = key.longHashCode(),
				title = label,
				key = key,
				source = source,
				isPinned = false,
			)
		}
		getTagsDao().upsert(tags)
		getMangaDao().upsert(
			MangaEntity(
				id = mangaId,
				title = manga.title.trim().ifEmpty { manga.url },
				altTitles = null,
				url = manga.url,
				publicUrl = manga.url,
				rating = -1f,
				isNsfw = false,
				contentRating = null,
				coverUrl = manga.thumbnailUrl.orEmpty(),
				largeCoverUrl = null,
				state = manga.status.toMangaState()?.name,
				authors = manga.author?.trim()?.takeUnless { it.isEmpty() },
				source = source,
			),
			tags,
		)
		val now = System.currentTimeMillis()
		val categoryIds = manga.categories.mapNotNull { categoryIdByOrder[it] }
			.ifEmpty { listOfNotNull(fallbackCategoryId) }
		for (categoryId in categoryIds) {
			getFavouritesDao().upsert(
				FavouriteEntity(
					mangaId = mangaId,
					categoryId = categoryId,
					sortKey = 0,
					isPinned = false,
					createdAt = now,
					deletedAt = 0L,
				),
			)
		}
		// Reading progress: the imported manga is broken and has no real chapters, so we store how far
		// through the series the user had read as a percent. Migration maps that fraction back onto the
		// matched source's chapter list (see MigrateUseCase.makeNewHistory).
		buildImportedHistory(manga, mangaId)?.let { getHistoryDao().upsert(it) }
		importTracking(manga, mangaId)
	}

	/**
	 * Best-effort import of Tachiyomi tracking links into Kotatsu's scrobblings, for the four trackers
	 * Kotatsu supports. Only the target id (the tracker's own media id) and last-read chapter are
	 * carried — enough to relink and re-sync once the user signs in to that tracker, and to survive
	 * Migration onto a real source (see MigrateUseCase). Score and per-tracker status strings are
	 * left unset; unsupported trackers (Bangumi, MangaUpdates, Komga, Suwayomi, …) are skipped.
	 */
	private suspend fun MangaDatabase.importTracking(manga: TachiyomiBackupManga, mangaId: Long) {
		if (manga.tracking.isEmpty()) {
			return
		}
		val dao = getScrobblingDao()
		for (track in manga.tracking) {
			val service = track.syncId.toScrobblerService() ?: continue
			// Prefer the 64-bit media id; fall back to the legacy 32-bit one from older backups.
			val targetId = if (track.mediaId != 0L) track.mediaId else track.mediaIdInt.toLong()
			if (targetId == 0L) {
				continue
			}
			dao.upsert(
				ScrobblingEntity(
					scrobbler = service.id,
					// No local rate id until the user signs in and Kotatsu relinks; 0 = "imported".
					id = 0,
					mangaId = mangaId,
					targetId = targetId,
					status = null,
					chapter = track.lastChapterRead.toInt().coerceAtLeast(0),
					comment = null,
					rating = 0f,
				),
			)
		}
	}

	/**
	 * Maps a Tachiyomi tracker id to the equivalent Kotatsu [ScrobblerService], or `null` when Kotatsu
	 * has no matching tracker. Note the id spaces differ between the two apps.
	 */
	private fun Int.toScrobblerService(): ScrobblerService? = when (this) {
		1 -> ScrobblerService.MAL // Tachiyomi MyAnimeList
		2 -> ScrobblerService.ANILIST
		3 -> ScrobblerService.KITSU
		4 -> ScrobblerService.SHIKIMORI
		else -> null
	}

	/**
	 * Reconstructs a [HistoryEntity] from a Tachiyomi manga's per-chapter read-state, or `null` when
	 * there is nothing read. Progress is expressed as `readingPosition / totalChapters` so it stays
	 * source-agnostic.
	 */
	private fun buildImportedHistory(manga: TachiyomiBackupManga, mangaId: Long): HistoryEntity? {
		val total = manga.chapters.size
		if (total == 0) {
			return null
		}
		// A chapter is "reached" once it is finished (read) or opened (has a saved page). The furthest
		// one by STORY chapter number is where the user left off — the source's own list order
		// (sourceOrder) is unreliable, as some sources list chapters out of numeric order.
		val reached = manga.chapters.filter { it.read || it.lastPageRead > 0L }
		val furthest = reached.maxByOrNull { it.chapterNumber } ?: return null
		val lastNumber = furthest.chapterNumber
		// The chapter to continue from: the next chapter after a finished one (Tachiyomi's "up next"),
		// or the in-progress chapter itself. Computed here from the backup's own chapters, then matched
		// onto the real source during Migration.
		val continueNumber = if (furthest.read) {
			manga.chapters.asSequence().map { it.chapterNumber }.filter { it > lastNumber }.minOrNull() ?: lastNumber
		} else {
			lastNumber
		}
		// Rough fraction for the progress bar; the exact resume point rides on [scroll] below.
		val readCount = manga.chapters.count { it.chapterNumber in 0f..lastNumber }
		val percent = (readCount.toFloat() / total).coerceIn(0f, 1f)
		val timestamp = manga.history.maxOfOrNull { it.lastRead }?.takeIf { it > 0L }
			?: manga.dateAdded.takeIf { it > 0L }
			?: System.currentTimeMillis()
		return HistoryEntity(
			mangaId = mangaId,
			createdAt = timestamp,
			updatedAt = timestamp,
			// chapterId 0 marks a source-less imported entry; [scroll] smuggles the continue-from
			// chapter NUMBER so Migration can match it on the real source (see MigrateUseCase).
			chapterId = 0L,
			page = if (furthest.read) 0 else furthest.lastPageRead.toInt(),
			scroll = continueNumber,
			percent = percent,
			chaptersCount = total,
			deletedAt = 0L,
		)
	}

	private fun Int.toMangaState(): MangaState? = when (this) {
		1 -> MangaState.ONGOING
		2, 4 -> MangaState.FINISHED
		5 -> MangaState.ABANDONED
		6 -> MangaState.PAUSED
		else -> null
	}

	/** Resolves a display label for every source id used in the backup (real name, else site domain). */
	private fun buildSourceLabels(mangaList: List<TachiyomiBackupManga>): Map<Long, String> {
		val result = HashMap<Long, String>()
		for (manga in mangaList) {
			val sid = manga.source
			if (sid in result) {
				continue
			}
			val label = sourceNames[sid]
				?: manga.url.substringAfter("://", "").substringBefore('/').takeIf { it.isNotEmpty() }
			if (label != null) {
				result[sid] = label
			}
		}
		return result
	}

	/**
	 * Builds the source-id -> name lookup: the bundled `id\tname` asset (baseline) overlaid with the
	 * disk cache written by [refreshSourceNamesCache] (cache wins on conflicts). A missing or corrupt
	 * cache simply leaves the bundled map untouched, so this always returns at least the snapshot.
	 */
	private fun loadSourceNames(): Map<Long, String> {
		val map = HashMap<Long, String>(2048)
		// Bundled snapshot first — the guaranteed baseline.
		runCatching {
			context.assets.open(SOURCE_NAMES_ASSET).bufferedReader().useLines { parseSourceNameLines(it, map) }
		}
		// Live cache overlay — same tab-separated format; entries here overwrite the snapshot.
		runCatching {
			val cache = cacheFile()
			if (cache.exists()) {
				cache.bufferedReader().useLines { parseSourceNameLines(it, map) }
			}
		}
		return map
	}

	/** Folds CRLF-safe `id\tname` lines into [into]; later lines win (used for both asset and cache). */
	private fun parseSourceNameLines(lines: Sequence<String>, into: HashMap<Long, String>) {
		for (line in lines) {
			val tab = line.indexOf('\t')
			if (tab > 0) {
				val id = line.substring(0, tab).toLongOrNull()
				// trim() guards against a stray CR if the file is written/checked out with CRLF endings.
				val name = line.substring(tab + 1).trim()
				if (id != null && name.isNotEmpty()) {
					into[id] = name
				}
			}
		}
	}

	private fun cacheFile() = File(context.cacheDir, SOURCE_NAMES_CACHE)

	/**
	 * Best-effort: when the on-disk cache is missing or older than [CACHE_TTL_MS], fetch the live
	 * keiyoushi index, parse it, and (re)write the cache as `id\tname` lines. Runs on IO and never
	 * throws — no network, a failed fetch, or an unexpected payload all degrade silently to the bundled
	 * asset plus whatever cache already exists. Fresh within the TTL window means zero network.
	 */
	private suspend fun refreshSourceNamesCache() = withContext(Dispatchers.IO) {
		runCatchingCancellable {
			val cache = cacheFile()
			val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < CACHE_TTL_MS
			if (fresh) {
				return@runCatchingCancellable
			}
			val request = Request.Builder().url(KEIYOUSHI_INDEX_URL).get().build()
			val index = okHttp.newCall(request).await().parseJson()
			val names = parseKeiyoushiIndex(index)
			// Keep the last good cache if the payload yielded nothing (e.g. an unexpected shape).
			if (names.isNotEmpty()) {
				writeCache(cache, names)
			}
		}.onFailure {
			DebugLog.e("Tachiyomi source-name refresh failed; using bundled asset + last cache", it)
		}
		Unit
	}

	/**
	 * Extracts `id.toLong() -> name` from the keiyoushi index JSON
	 * (`extensionList.extensions[].sources[]` with `id`/`name`; also tolerates a top-level `extensions`
	 * array). Blanks are skipped and the first non-blank name wins on duplicate ids.
	 */
	private fun parseKeiyoushiIndex(root: JSONObject): Map<Long, String> {
		val extensions: JSONArray = root.optJSONObject("extensionList")?.optJSONArray("extensions")
			?: root.optJSONArray("extensions")
			?: return emptyMap()
		val map = HashMap<Long, String>(extensions.length().coerceAtLeast(0) * 2)
		for (i in 0 until extensions.length()) {
			val sources = extensions.optJSONObject(i)?.optJSONArray("sources") ?: continue
			for (j in 0 until sources.length()) {
				val src = sources.optJSONObject(j) ?: continue
				val id = src.optString("id").toLongOrNull() ?: continue
				val name = src.optString("name").trim()
				if (name.isNotEmpty() && id !in map) {
					map[id] = name
				}
			}
		}
		return map
	}

	/** Writes the map as `id\tname` lines via a temp file + rename so a reader never sees a partial file. */
	private fun writeCache(cache: File, names: Map<Long, String>) {
		val tmp = File(cache.parentFile, "${cache.name}.tmp")
		tmp.bufferedWriter().use { writer ->
			for ((id, name) in names) {
				writer.append(id.toString()).append('\t').append(name).append('\n')
			}
		}
		if (cache.exists()) {
			cache.delete()
		}
		if (!tmp.renameTo(cache)) {
			// Rename can fail across some filesystems; fall back to a direct copy.
			cache.writeText(tmp.readText())
			tmp.delete()
		}
	}

	private companion object {
		private const val FALLBACK_CATEGORY_TITLE = "Imported"
		private const val SOURCE_NAMES_ASSET = "tachiyomi_sources.tsv"
		private const val SOURCE_NAMES_CACHE = "tachiyomi_sources_cache.tsv"
		private const val KEIYOUSHI_INDEX_URL =
			"https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.json"
		private val CACHE_TTL_MS = TimeUnit.DAYS.toMillis(7)
	}
}
