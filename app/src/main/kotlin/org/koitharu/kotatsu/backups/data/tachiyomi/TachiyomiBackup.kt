package org.koitharu.kotatsu.backups.data.tachiyomi

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Minimal subset of the TachiyomiSY / Tachiyomi / Mihon `.tachibk` backup schema needed to import a
 * library. The file is a gzip-compressed protobuf; unknown fields (sources, preferences, tracking,
 * SY extras, etc.) are ignored during decoding. Field numbers were verified against a real
 * TachiyomiSY backup — note chapters are field 16 and categories are field 17 (SY numbering).
 */
@Serializable
class TachiyomiBackup(
	@ProtoNumber(1) val backupManga: List<TachiyomiBackupManga> = emptyList(),
	@ProtoNumber(2) val backupCategories: List<TachiyomiBackupCategory> = emptyList(),
)

@Serializable
class TachiyomiBackupManga(
	@ProtoNumber(1) val source: Long = 0L,
	@ProtoNumber(2) val url: String = "",
	@ProtoNumber(3) val title: String = "",
	@ProtoNumber(5) val author: String? = null,
	@ProtoNumber(6) val description: String? = null,
	@ProtoNumber(7) val genre: List<String> = emptyList(),
	@ProtoNumber(8) val status: Int = 0,
	@ProtoNumber(9) val thumbnailUrl: String? = null,
	@ProtoNumber(13) val dateAdded: Long = 0L,
	@ProtoNumber(16) val chapters: List<TachiyomiBackupChapter> = emptyList(),
	// Category references — each value is a TachiyomiBackupCategory.order.
	@ProtoNumber(17) val categories: List<Long> = emptyList(),
	// Per-chapter read timestamps (field 104 in the SY/Mihon schema). Sparse — most read state
	// lives in the chapters' `read` flags instead; used only to date the imported progress.
	@ProtoNumber(104) val history: List<TachiyomiBackupHistory> = emptyList(),
)

/**
 * A single chapter inside a [TachiyomiBackupManga]. Only the fields needed to reconstruct the
 * last-read position are decoded: [read] marks a finished chapter, [lastPageRead] catches an
 * in-progress one, and [sourceOrder] gives the canonical reading order (0 = newest chapter).
 */
@Serializable
class TachiyomiBackupChapter(
	@ProtoNumber(1) val url: String = "",
	@ProtoNumber(2) val name: String = "",
	@ProtoNumber(4) val read: Boolean = false,
	@ProtoNumber(6) val lastPageRead: Long = 0L,
	@ProtoNumber(9) val chapterNumber: Float = 0f,
	@ProtoNumber(10) val sourceOrder: Long = 0L,
)

@Serializable
class TachiyomiBackupHistory(
	// The read chapter's url.
	@ProtoNumber(1) val url: String = "",
	@ProtoNumber(2) val lastRead: Long = 0L,
)

@Serializable
class TachiyomiBackupCategory(
	@ProtoNumber(1) val name: String = "",
	@ProtoNumber(2) val order: Long = 0L,
)
