package org.koitharu.kotatsu.reader.data

import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter

fun Manga.filterChapters(branch: String?): Manga {
	if (chapters.isNullOrEmpty()) return this
	return withChapters(chapters = chapters?.filter { it.branch == branch })
}

private fun Manga.withChapters(chapters: List<MangaChapter>?) = copy(
	chapters = chapters,
)

/**
 * A "partial" chapter has a fractional number like 6.1 or 6.5. Whole chapters keep 6.0 and
 * un-numbered chapters (specials/extras) keep 0. Used by the "hide partial chapters" feature.
 */
fun MangaChapter.isPartial(): Boolean {
	val n = number
	return n > 0f && n != n.toInt().toFloat()
}

/**
 * The chapter [delta] steps away from [currentId] in this ordered list. When [skipPartial] is true,
 * chapters with a fractional number are skipped so the reader advances straight to the next whole
 * chapter. Returns null when there is no such chapter (past the list ends, or [currentId] absent).
 */
fun List<MangaChapter>.adjacentChapter(currentId: Long, delta: Int, skipPartial: Boolean): MangaChapter? {
	val start = indexOfFirst { it.id == currentId }
	if (start < 0) return null
	if (delta == 0) return getOrNull(start)
	val step = if (delta > 0) 1 else -1
	var remaining = if (delta > 0) delta else -delta
	var i = start
	while (true) {
		i += step
		val chapter = getOrNull(i) ?: return null
		if (skipPartial && chapter.isPartial()) continue
		remaining--
		if (remaining == 0) return chapter
	}
}
