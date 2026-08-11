package org.koitharu.kotatsu.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the append-only, per-day `history_log` table that backs the History tab, and seeds it with
 * one row per existing `history` row (using the last-read time as the day). The `history` table is
 * left untouched, so resume/cover-progress/sync/backup keep working exactly as before.
 */
class Migration28To29 : Migration(28, 29) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL(
			"""CREATE TABLE IF NOT EXISTS `history_log` (
				`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
				`manga_id` INTEGER NOT NULL,
				`created_at` INTEGER NOT NULL,
				`updated_at` INTEGER NOT NULL,
				`chapter_id` INTEGER NOT NULL,
				`page` INTEGER NOT NULL,
				`scroll` REAL NOT NULL,
				`percent` REAL NOT NULL,
				`deleted_at` INTEGER NOT NULL,
				`chapters` INTEGER NOT NULL,
				FOREIGN KEY(`manga_id`) REFERENCES `manga`(`manga_id`) ON UPDATE NO ACTION ON DELETE CASCADE
			)""",
		)
		db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_log_manga_id` ON `history_log` (`manga_id`)")
		db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_log_created_at` ON `history_log` (`created_at`)")
		db.execSQL(
			"""INSERT INTO history_log (manga_id, created_at, updated_at, chapter_id, page, scroll, percent, deleted_at, chapters)
				SELECT manga_id, updated_at, updated_at, chapter_id, page, scroll, percent, deleted_at, chapters FROM history""",
		)
	}
}
