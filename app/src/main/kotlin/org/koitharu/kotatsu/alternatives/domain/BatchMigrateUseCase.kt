package org.koitharu.kotatsu.alternatives.domain

import org.koitharu.kotatsu.core.parser.MangaDataRepository
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.search.domain.SearchKind
import org.koitharu.kotatsu.search.domain.SearchV2Helper
import javax.inject.Inject

/**
 * Auto-matches a single manga against a chosen [MangaSource] (best title match) and migrates
 * (or copies) it. Used by [org.koitharu.kotatsu.alternatives.ui.MigrationService] to migrate a
 * whole selection at once.
 */
class BatchMigrateUseCase @Inject constructor(
	private val mangaDataRepository: MangaDataRepository,
	private val searchHelperFactory: SearchV2Helper.Factory,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val migrateUseCase: MigrateUseCase,
) {

	/**
	 * @return the seed manga paired with the matched manga on [targetSource], or `null` if no
	 * match was found (in which case nothing was migrated).
	 */
	suspend operator fun invoke(mangaId: Long, targetSource: MangaSource, copy: Boolean): Pair<Manga, Manga?> {
		val seed = checkNotNull(
			mangaDataRepository.findMangaById(mangaId, withChapters = true),
		) { "Manga $mangaId not found" }
		val candidate = searchHelperFactory.create(targetSource)
			.invoke(seed.title, SearchKind.TITLE)
			?.manga
			?.firstOrNull { it.id != seed.id }
		val match = candidate?.let { c ->
			runCatchingCancellable {
				mangaRepositoryFactory.create(c.source).getDetails(c)
			}.getOrDefault(c)
		}
		if (match != null) {
			migrateUseCase(seed, match, copy = copy)
		}
		return seed to match
	}
}
