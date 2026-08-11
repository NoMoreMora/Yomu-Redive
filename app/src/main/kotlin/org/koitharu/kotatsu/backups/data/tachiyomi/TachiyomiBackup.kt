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
	// Tracking links (field 18). Each entry ties the manga to a record on an external tracker
	// (MyAnimeList / AniList / Kitsu / Shikimori). Imported best-effort into Kotatsu's scrobblings so
	// the link and last-read position survive; unsupported trackers are ignored.
	@ProtoNumber(18) val tracking: List<TachiyomiBackupTracking> = emptyList(),
	// Per-chapter read timestamps (field 104 in the SY/Mihon schema). Sparse — most read state
	// lives in the chapters' `read` flags instead; used only to date the imported progress.
	@ProtoNumber(104) val history: List<TachiyomiBackupHistory> = emptyList(),
)

/**
 * A tracking link on a [TachiyomiBackupManga]. Only the fields needed to relink and re-sync are
 * decoded: [syncId] identifies the tracker, [mediaId] (or the legacy [mediaIdInt]) is the record's id
 * on that tracker, and [lastChapterRead] is the read position. Score/status are tracker-specific and
 * intentionally left out of the import.
 */
@Serializable
class TachiyomiBackupTracking(
	// Tachiyomi tracker id: 1 = MyAnimeList, 2 = AniList, 3 = Kitsu, 4 = Shikimori (others unsupported).
	@ProtoNumber(1) val syncId: Int = 0,
	// Legacy 32-bit media id used by older backups; superseded by the 64-bit [mediaId] below.
	@ProtoNumber(3) val mediaIdInt: Int = 0,
	@ProtoNumber(4) val trackingUrl: String = "",
	@ProtoNumber(5) val title: String = "",
	@ProtoNumber(6) val lastChapterRead: Float = 0f,
	@ProtoNumber(7) val totalChapters: Int = 0,
	@ProtoNumber(8) val score: Float = 0f,
	@ProtoNumber(9) val status: Int = 0,
	// The tracker's own media id (64-bit). Kotatsu keys tracking on this (ScrobblingEntity.targetId).
	@ProtoNumber(100) val mediaId: Long = 0L,
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
