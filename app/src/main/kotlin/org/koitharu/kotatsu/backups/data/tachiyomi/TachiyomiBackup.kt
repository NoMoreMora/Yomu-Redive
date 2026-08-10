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
	// Category references — each value is a TachiyomiBackupCategory.order.
	@ProtoNumber(17) val categories: List<Long> = emptyList(),
)

@Serializable
class TachiyomiBackupCategory(
	@ProtoNumber(1) val name: String = "",
	@ProtoNumber(2) val order: Long = 0L,
)
