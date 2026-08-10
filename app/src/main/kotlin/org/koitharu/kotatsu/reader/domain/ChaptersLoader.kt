package org.koitharu.kotatsu.reader.domain

import android.util.LongSparseArray
import androidx.annotation.CheckResult
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koitharu.kotatsu.core.exceptions.resolve.CaptchaAutoResolveCoordinator
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.details.data.MangaDetails
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.reader.data.adjacentChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.reader.ui.pager.ReaderPage
import javax.inject.Inject

private const val PAGES_TRIM_THRESHOLD = 120

@ViewModelScoped
class ChaptersLoader @Inject constructor(
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val captchaAutoResolveCoordinator: CaptchaAutoResolveCoordinator,
	private val settings: AppSettings,
) {

	private val chapters = LongSparseArray<MangaChapter>()
	private val chapterPages = ChapterPages()
	private val mutex = Mutex()

	val size: Int
		get() = chapters.size()

	suspend fun init(manga: MangaDetails) = mutex.withLock {
		chapters.clear()
		manga.allChapters.forEach {
			chapters.put(it.id, it)
		}
	}

	suspend fun loadPrevNextChapter(manga: MangaDetails, currentId: Long, isNext: Boolean): Boolean {
		// When "hide partial chapters" is on, skip fractional chapters (6.1, 6.5) while auto-advancing
		// so the reader goes straight from chapter 6 to 7, matching the chapter list.
		val newChapter = manga.allChapters.adjacentChapter(
			currentId = currentId,
			delta = if (isNext) 1 else -1,
			skipPartial = settings.isHidePartialChapters,
		) ?: return false
		val newPages = loadChapter(newChapter.id, mayStartVerification = false)
		mutex.withLock {
			if (chapterPages.chaptersSize > 1) {
				// trim pages
				if (chapterPages.size > PAGES_TRIM_THRESHOLD) {
					if (isNext) {
						chapterPages.removeFirst()
					} else {
						chapterPages.removeLast()
					}
				}
			}
			if (isNext) {
				chapterPages.addLast(newChapter.id, newPages)
			} else {
				chapterPages.addFirst(newChapter.id, newPages)
			}
		}
		return true
	}

	@CheckResult
	suspend fun loadSingleChapter(chapterId: Long): Boolean {
		val pages = loadChapter(chapterId, mayStartVerification = true)
		return mutex.withLock {
			chapterPages.clear()
			chapterPages.addLast(chapterId, pages)
			pages.isNotEmpty()
		}
	}

	fun peekChapter(chapterId: Long): MangaChapter? = chapters[chapterId]

	fun hasPages(chapterId: Long): Boolean {
		return chapterId in chapterPages
	}

	fun getPages(chapterId: Long): List<MangaPage> = synchronized(chapterPages) {
		return chapterPages.subList(chapterId).map { it.toMangaPage() }
	}

	fun getPagesCount(chapterId: Long): Int {
		return chapterPages.size(chapterId)
	}

	fun last() = chapterPages.last()

	fun first() = chapterPages.first()

	fun snapshot() = chapterPages.toList()

	private suspend fun loadChapter(
		chapterId: Long,
		mayStartVerification: Boolean,
	): List<ReaderPage> {
		val chapter = checkNotNull(chapters[chapterId]) { "Requested chapter not found" }
		val repo = mangaRepositoryFactory.create(chapter.source)
		val pages = captchaAutoResolveCoordinator.runWithVerification(
			source = chapter.source,
			mayStartVerification = mayStartVerification,
		) {
			repo.getPages(chapter)
		}
		return pages.mapIndexed { index, page ->
			ReaderPage(page, index, chapterId)
		}
	}
}
