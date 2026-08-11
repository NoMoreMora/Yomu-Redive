package org.koitharu.kotatsu.history.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.koitharu.kotatsu.core.db.TABLE_HISTORY_LOG
import org.koitharu.kotatsu.core.db.entity.MangaEntity

/**
 * Append-only, per-day reading log that backs the History tab. Unlike [HistoryEntity] (one row per
 * manga, overwritten on every read and used for resume/progress/sync), this table keeps **one row
 * per manga per local day**, so re-reading a title on a later day adds a new dated entry instead of
 * erasing the older one. It is deliberately not part of resume/cover-progress/sync/backup.
 */
@Entity(
	tableName = TABLE_HISTORY_LOG,
	foreignKeys = [
		ForeignKey(
			entity = MangaEntity::class,
			parentColumns = ["manga_id"],
			childColumns = ["manga_id"],
			onDelete = ForeignKey.CASCADE,
		),
	],
	indices = [
		Index("manga_id"),
		Index("created_at"),
	],
)
data class HistoryLogEntity(
	@PrimaryKey(autoGenerate = true)
	@ColumnInfo(name = "id") val id: Long,
	@ColumnInfo(name = "manga_id") val mangaId: Long,
	@ColumnInfo(name = "created_at") val createdAt: Long,
	@ColumnInfo(name = "updated_at") val updatedAt: Long,
	@ColumnInfo(name = "chapter_id") val chapterId: Long,
	@ColumnInfo(name = "page") val page: Int,
	@ColumnInfo(name = "scroll") val scroll: Float,
	@ColumnInfo(name = "percent") val percent: Float,
	@ColumnInfo(name = "deleted_at") val deletedAt: Long,
	@ColumnInfo(name = "chapters") val chaptersCount: Int,
)
