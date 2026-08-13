package org.koitharu.kotatsu.alternatives.ui

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.alternatives.domain.BatchMigrateUseCase
import org.koitharu.kotatsu.alternatives.domain.MigrationCoordinator
import org.koitharu.kotatsu.core.ui.CoroutineIntentService
import org.koitharu.kotatsu.core.util.ext.checkNotificationPermission
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.powerManager
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.core.util.ext.withPartialWakeLock
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject
import androidx.appcompat.R as appcompatR

/**
 * Batch-migrates a selection of manga to a chosen source. For each manga it auto-matches the best
 * title hit on the target source and migrates (or copies) it, then posts a summary notification.
 */
@AndroidEntryPoint
class MigrationService : CoroutineIntentService() {

	@Inject
	lateinit var batchMigrateUseCase: BatchMigrateUseCase

	@Inject
	lateinit var sourcesRepository: MangaSourcesRepository

	@Inject
	lateinit var migrationCoordinator: MigrationCoordinator

	private lateinit var notificationManager: NotificationManagerCompat

	override fun onCreate() {
		super.onCreate()
		notificationManager = NotificationManagerCompat.from(this)
	}

	override suspend fun IntentJobContext.processIntent(intent: Intent) {
		val ids = requireNotNull(intent.getLongArrayExtra(DATA_IDS))
		val sourceName = requireNotNull(intent.getStringExtra(DATA_SOURCE))
		val copy = intent.getBooleanExtra(DATA_COPY, false)
		val targetSource = sourcesRepository.allMangaSources.firstOrNull { it.name == sourceName } ?: return
		// Marks a migration active so in-flight downloads pause until this batch finishes (avoids overheating).
		migrationCoordinator.withMigration {
			startForeground(this)
			var migrated = 0
			for (mangaId in ids) {
				powerManager.withPartialWakeLock(TAG) {
					runCatchingCancellable {
						batchMigrateUseCase(mangaId, targetSource, copy)
					}.onSuccess { (_, match) ->
						if (match != null) {
							migrated++
						}
					}.onFailure {
						it.printStackTraceDebug()
					}
				}
			}
			if (checkNotificationPermission(CHANNEL_ID)) {
				notificationManager.notify(TAG, startId, buildSummaryNotification(migrated, ids.size))
			}
		}
	}

	override fun IntentJobContext.onError(error: Throwable) {
		if (checkNotificationPermission(CHANNEL_ID)) {
			val notification = NotificationCompat.Builder(this@MigrationService, CHANNEL_ID)
				.setContentTitle(getString(R.string.error_occurred))
				.setContentText(error.getDisplayMessage(resources))
				.setSmallIcon(android.R.drawable.stat_notify_error)
				.setAutoCancel(true)
				.setSilent(true)
				.build()
			notificationManager.notify(TAG, startId, notification)
		}
	}

	@SuppressLint("InlinedApi")
	private fun startForeground(jobContext: IntentJobContext) {
		val title = getString(R.string.manga_migration)
		val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_MIN)
			.setName(title)
			.setShowBadge(false)
			.setVibrationEnabled(false)
			.setSound(null, null)
			.setLightsEnabled(false)
			.build()
		notificationManager.createNotificationChannel(channel)

		val notification = NotificationCompat.Builder(this, CHANNEL_ID)
			.setContentTitle(title)
			.setPriority(NotificationCompat.PRIORITY_MIN)
			.setDefaults(0)
			.setSilent(true)
			.setOngoing(true)
			.setProgress(0, 0, true)
			.setSmallIcon(R.drawable.ic_stat_auto_fix)
			.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
			.setCategory(NotificationCompat.CATEGORY_PROGRESS)
			.addAction(
				appcompatR.drawable.abc_ic_clear_material,
				getString(android.R.string.cancel),
				jobContext.getCancelIntent(),
			)
			.build()

		jobContext.setForeground(
			FOREGROUND_NOTIFICATION_ID,
			notification,
			ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
		)
	}

	private fun buildSummaryNotification(migrated: Int, total: Int): Notification =
		NotificationCompat.Builder(this, CHANNEL_ID)
			.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			.setDefaults(0)
			.setSilent(true)
			.setAutoCancel(true)
			.setContentTitle(getString(R.string.migration_completed))
			.setContentText(getString(R.string.migration_result, migrated, total))
			.setSmallIcon(R.drawable.ic_stat_done)
			.build()

	companion object {

		private const val DATA_IDS = "ids"
		private const val DATA_SOURCE = "source"
		private const val DATA_COPY = "copy"
		private const val TAG = "migration"
		private const val CHANNEL_ID = "migration"
		private const val FOREGROUND_NOTIFICATION_ID = 41

		fun start(context: Context, mangaIds: Collection<Long>, sourceName: String, copy: Boolean): Boolean = try {
			val intent = Intent(context, MigrationService::class.java)
			intent.putExtra(DATA_IDS, mangaIds.toLongArray())
			intent.putExtra(DATA_SOURCE, sourceName)
			intent.putExtra(DATA_COPY, copy)
			ContextCompat.startForegroundService(context, intent)
			true
		} catch (e: Exception) {
			e.printStackTraceDebug()
			false
		}
	}
}
