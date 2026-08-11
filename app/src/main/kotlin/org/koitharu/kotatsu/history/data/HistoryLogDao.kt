package org.koitharu.kotatsu.history.data

import android.database.DatabaseUtils.sqlEscapeString
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
import org.koitharu.kotatsu.core.db.MangaQueryBuilder
import org.koitharu.kotatsu.core.db.TABLE_HISTORY_LOG
import org.koitharu.kotatsu.list.domain.ListFilterOption
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.list.domain.ReadingProgress.Companion.PROGRESS_COMPLETED

/**
 * DAO for the append-only, per-day [HistoryLogEntity]. Mirrors the parts of [HistoryDao] the History
 * tab needs (filtered/sorted observation), but never groups by manga — every dated entry is a row.
 */
@Dao
abstract class HistoryLogDao : MangaQueryBuilder.ConditionCallback {

	fun observeAll(
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int,
	): Flow<List<HistoryLogWithManga>> = observeAllImpl(
		MangaQueryBuilder(TABLE_HISTORY_LOG, this)
			.join("LEFT JOIN manga ON history_log.manga_id = manga.manga_id")
			.where("history_log.deleted_at = 0")
			.filters(filterOptions)
			.orderBy(
				orderBy = when (order) {
					ListSortOrder.LAST_READ -> "history_log.updated_at DESC"
					ListSortOrder.LONG_AGO_READ -> "history_log.updated_at ASC"
					ListSortOrder.NEWEST -> "history_log.created_at DESC"
					ListSortOrder.OLDEST -> "history_log.created_at ASC"
					ListSortOrder.PROGRESS -> "history_log.percent DESC"
					ListSortOrder.UNREAD -> "history_log.percent ASC"
					ListSortOrder.ALPHABETIC -> "manga.title"
					ListSortOrder.ALPHABETIC_REVERSE -> "manga.title DESC"
					ListSortOrder.NEW_CHAPTERS -> "IFNULL((SELECT chapters_new FROM tracks WHERE tracks.manga_id = manga.manga_id), 0) DESC"
					ListSortOrder.UPDATED -> "IFNULL((SELECT last_chapter_date FROM tracks WHERE tracks.manga_id = manga.manga_id), 0) DESC"
					else -> throw IllegalArgumentException("Sort order $order is not supported")
				},
			)
			.limit(limit)
			.build(),
	)

	@Query("SELECT * FROM history_log WHERE manga_id = :mangaId AND deleted_at = 0 ORDER BY created_at DESC LIMIT 1")
	abstract suspend fun findLast(mangaId: Long): HistoryLogEntity?

	@Insert
	abstract suspend fun insert(entity: HistoryLogEntity): Long

	@Query(
		"UPDATE history_log SET page = :page, chapter_id = :chapterId, scroll = :scroll, percent = :percent, updated_at = :updatedAt, chapters = :chapters, deleted_at = 0 WHERE id = :id",
	)
	abstract suspend fun updateProgress(
		id: Long,
		page: Int,
		chapterId: Long,
		scroll: Float,
		percent: Float,
		chapters: Int,
		updatedAt: Long,
	): Int

	suspend fun delete(mangaId: Long) = setDeletedAt(mangaId, System.currentTimeMillis())

	suspend fun recover(mangaId: Long) = setDeletedAt(mangaId, 0L)

	suspend fun deleteAfter(minDate: Long) = setDeletedAtAfter(minDate, System.currentTimeMillis())

	suspend fun deleteNotFavorite() = setDeletedAtNotFavorite(System.currentTimeMillis())

	suspend fun clear() = setDeletedAtAfter(0L, System.currentTimeMillis())

	@Query("DELETE FROM history_log WHERE deleted_at != 0 AND deleted_at < :maxDeletionTime")
	abstract suspend fun gc(maxDeletionTime: Long)

	@Query("UPDATE history_log SET deleted_at = :deletedAt WHERE manga_id = :mangaId")
	protected abstract suspend fun setDeletedAt(mangaId: Long, deletedAt: Long)

	@Query("UPDATE history_log SET deleted_at = :deletedAt WHERE created_at >= :minDate AND deleted_at = 0")
	protected abstract suspend fun setDeletedAtAfter(minDate: Long, deletedAt: Long)

	@Query("UPDATE history_log SET deleted_at = :deletedAt WHERE deleted_at = 0 AND NOT EXISTS(SELECT * FROM favourites WHERE history_log.manga_id = favourites.manga_id)")
	protected abstract suspend fun setDeletedAtNotFavorite(deletedAt: Long)

	@Transaction
	@RawQuery(observedEntities = [HistoryLogEntity::class])
	protected abstract fun observeAllImpl(query: SupportSQLiteQuery): Flow<List<HistoryLogWithManga>>

	override fun getCondition(option: ListFilterOption): String? = when (option) {
		is ListFilterOption.Favorite -> "EXISTS(SELECT * FROM favourites WHERE history_log.manga_id = favourites.manga_id AND category_id = ${option.category.id})"
		ListFilterOption.Macro.COMPLETED -> "history_log.percent >= $PROGRESS_COMPLETED"
		ListFilterOption.Macro.NEW_CHAPTERS -> "(SELECT chapters_new FROM tracks WHERE tracks.manga_id = history_log.manga_id) > 0"
		ListFilterOption.Macro.FAVORITE -> "EXISTS(SELECT * FROM favourites WHERE history_log.manga_id = favourites.manga_id)"
		ListFilterOption.Macro.NSFW -> "manga.nsfw = 1"
		is ListFilterOption.Tag -> "EXISTS(SELECT * FROM manga_tags WHERE history_log.manga_id = manga_tags.manga_id AND tag_id = ${option.tagId})"
		ListFilterOption.Downloaded -> "EXISTS(SELECT * FROM local_index WHERE local_index.manga_id = history_log.manga_id)"
		is ListFilterOption.Source -> "manga.source = ${sqlEscapeString(option.mangaSource.name)}"
		else -> null
	}
}
