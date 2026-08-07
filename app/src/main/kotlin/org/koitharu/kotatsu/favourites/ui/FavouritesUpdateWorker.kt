package org.koitharu.kotatsu.favourites.ui

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.koitharu.kotatsu.core.model.isLocal
import org.koitharu.kotatsu.core.parser.CachingMangaRepository
import org.koitharu.kotatsu.core.parser.MangaDataRepository
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.ext.awaitUniqueWorkInfoByName
import org.koitharu.kotatsu.core.util.ext.coverCacheExtra
import org.koitharu.kotatsu.core.util.ext.mangaSourceExtra
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.settings.work.PeriodicWorkScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Daily background refresh for favourite manga, opt-in via [AppSettings.isFavouritesCacheEnabled].
 *
 * For every favourite it re-fetches the manga details straight from the source (bypassing the
 * in-memory cache) and persists them with [MangaDataRepository.storeManga] so the stored cover url,
 * title, description and tags stay up to date and are retained until the manga is un-favourited.
 * The cover image itself is warmed into Coil's disk cache so it survives even if the source later
 * changes or removes it. WorkManager's periodic interval provides the "once a day" gate.
 */
@HiltWorker
class FavouritesUpdateWorker @AssistedInject constructor(
	@Assisted appContext: Context,
	@Assisted params: WorkerParameters,
	private val coil: ImageLoader,
	private val favouritesRepository: FavouritesRepository,
	private val dataRepository: MangaDataRepository,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val settings: AppSettings,
) : CoroutineWorker(appContext, params) {

	override suspend fun doWork(): Result {
		if (!settings.isFavouritesCacheEnabled) {
			return Result.success()
		}
		val favourites = favouritesRepository.getAllManga()
		if (favourites.isEmpty()) {
			return Result.success()
		}
		val semaphore = Semaphore(MAX_PARALLELISM)
		coroutineScope {
			favourites.map { manga ->
				async {
					semaphore.withPermit {
						refreshManga(manga)
					}
				}
			}.awaitAll()
		}
		return Result.success()
	}

	private suspend fun refreshManga(manga: Manga) {
		runCatchingCancellable {
			if (manga.isLocal) {
				return@runCatchingCancellable
			}
			val repository = mangaRepositoryFactory.create(manga.source)
			val details = if (repository is CachingMangaRepository) {
				repository.getDetails(manga, CachePolicy.WRITE_ONLY)
			} else {
				repository.getDetails(manga)
			}
			dataRepository.storeManga(details, replaceExisting = true)
			val coverUrl = details.largeCoverUrl ?: details.coverUrl
			if (!coverUrl.isNullOrEmpty()) {
				coil.execute(
					ImageRequest.Builder(applicationContext)
						.data(coverUrl)
						.mangaSourceExtra(details.source)
						.coverCacheExtra(coverUrl)
						.build(),
				)
			}
		}.onFailure { it.printStackTraceDebug() }
	}

	@Reusable
	class Scheduler @Inject constructor(
		private val workManager: WorkManager,
	) : PeriodicWorkScheduler {

		override suspend fun schedule() {
			val constraints = Constraints.Builder()
				.setRequiredNetworkType(NetworkType.CONNECTED)
				.setRequiresBatteryNotLow(true)
				.build()
			val request = PeriodicWorkRequestBuilder<FavouritesUpdateWorker>(24, TimeUnit.HOURS)
				.setConstraints(constraints)
				.addTag(TAG)
				.setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
				.build()
			workManager
				.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
				.await()
		}

		override suspend fun unschedule() {
			workManager
				.cancelUniqueWork(TAG)
				.await()
		}

		override suspend fun isScheduled(): Boolean {
			return workManager
				.awaitUniqueWorkInfoByName(TAG)
				.any { !it.state.isFinished }
		}
	}

	private companion object {

		const val TAG = "favourites_update"
		const val MAX_PARALLELISM = 4
	}
}
