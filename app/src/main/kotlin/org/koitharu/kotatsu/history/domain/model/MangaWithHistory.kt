package org.koitharu.kotatsu.history.domain.model

import org.koitharu.kotatsu.core.model.MangaHistory
import org.koitharu.kotatsu.parsers.model.Manga

data class MangaWithHistory(
	val manga: Manga,
	val history: MangaHistory,
	/** Per-day log row id used by the History tab as a distinct list key; 0 for one-per-manga sources. */
	val id: Long = 0L,
)
