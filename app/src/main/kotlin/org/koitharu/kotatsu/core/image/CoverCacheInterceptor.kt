package org.koitharu.kotatsu.core.image

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.ext.coverCacheKey
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider

/**
 * Managed disk-cache policy for manga cover art, opt-in via [AppSettings.isCoverArtCacheEnabled].
 *
 * When enabled, a browsed cover is retained on disk for up to [RETENTION_MS]. A cover older than
 * that is evicted here so the following load fetches a fresh copy; a younger cover is served
 * straight from Coil's disk cache without touching the network, so any given cover is
 * re-downloaded at most once per retention window (comfortably within "at most once a day").
 *
 * Only requests tagged by [org.koitharu.kotatsu.core.util.ext.coverCacheExtra] are affected;
 * reader pages, favicons and everything else pass through untouched. Any failure degrades to the
 * default Coil behaviour.
 */
class CoverCacheInterceptor @Inject constructor(
	private val settings: AppSettings,
	private val imageLoaderProvider: Provider<ImageLoader>,
) : Interceptor {

	override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
		val key = chain.request.extras[coverCacheKey]
		if (key != null && settings.isCoverArtCacheEnabled) {
			imageLoaderProvider.get().diskCache?.let { diskCache ->
				runCatchingCancellable {
					val age = diskCache.entryAgeMillis(key)
					if (age != null && age > RETENTION_MS) {
						diskCache.remove(key)
					}
				}.onFailure { it.printStackTraceDebug() }
			}
		}
		return chain.proceed()
	}

	private fun DiskCache.entryAgeMillis(key: String): Long? {
		val snapshot = openSnapshot(key) ?: return null
		return snapshot.use {
			val lastModified = it.data.toFile().lastModified()
			if (lastModified <= 0L) null else System.currentTimeMillis() - lastModified
		}
	}

	private companion object {

		val RETENTION_MS: Long = TimeUnit.DAYS.toMillis(5)
	}
}
