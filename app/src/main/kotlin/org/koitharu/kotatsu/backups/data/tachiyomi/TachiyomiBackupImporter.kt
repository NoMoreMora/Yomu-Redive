package org.koitharu.kotatsu.backups.data.tachiyomi

import androidx.room.withTransaction
import dagger.Reusable
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.protobuf.ProtoBuf
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.MangaEntity
import org.koitharu.kotatsu.core.db.entity.TagEntity
import org.koitharu.kotatsu.core.model.UnknownMangaSource
import org.koitharu.kotatsu.core.util.CompositeResult
import org.koitharu.kotatsu.core.util.progress.Progress
import org.koitharu.kotatsu.favourites.data.FavouriteCategoryEntity
import org.koitharu.kotatsu.favourites.data.FavouriteEntity
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.util.longHashCode
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import java.io.InputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject

/**
 * Imports a TachiyomiSY / Tachiyomi / Mihon `.tachibk` backup into the library. Aimed at users
 * switching platforms: every manga is imported even when its source doesn't exist here — it is
 * stored under [UnknownMangaSource] so it reads as [org.koitharu.kotatsu.core.model.isBroken] and
 * can be resolved with the existing Migration tools. Categories are recreated; manga without a
 * category go into a fallback "Imported" category. Reading progress is not imported (part 1).
 */
@Reusable
class TachiyomiBackupImporter @Inject constructor(
	private val database: MangaDatabase,
) {

	private val protoBuf = ProtoBuf

	suspend fun import(input: InputStream, progress: FlowCollector<Progress>?): CompositeResult {
		progress?.emit(Progress.INDETERMINATE)
		val bytes = GZIPInputStream(input).use { it.readBytes() }
		val backup = protoBuf.decodeFromByteArray(TachiyomiBackup.serializer(), bytes)
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
					database.importManga(manga, categoryIdByOrder, fallbackCategoryId)
				}
			}
			p++
			progress?.emit(p)
		}
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
	) {
		val source = UnknownMangaSource.name
		val mangaId = "${manga.source}|${manga.url}".longHashCode()
		val tags = manga.genre.mapNotNull { genre ->
			val key = genre.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			TagEntity(
				id = "${key}_$source".longHashCode(),
				title = key,
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
	}

	private fun Int.toMangaState(): MangaState? = when (this) {
		1 -> MangaState.ONGOING
		2, 4 -> MangaState.FINISHED
		5 -> MangaState.ABANDONED
		6 -> MangaState.PAUSED
		else -> null
	}

	private companion object {
		private const val FALLBACK_CATEGORY_TITLE = "Imported"
	}
}
