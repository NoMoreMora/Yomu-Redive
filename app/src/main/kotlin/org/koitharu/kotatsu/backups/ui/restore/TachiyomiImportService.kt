package org.koitharu.kotatsu.backups.ui.restore

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.annotation.CheckResult
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.backups.data.tachiyomi.TachiyomiBackupImporter
import org.koitharu.kotatsu.backups.ui.BaseBackupRestoreService
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.util.ext.checkNotificationPermission
import org.koitharu.kotatsu.core.util.ext.powerManager
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.core.util.ext.toUriOrNull
import org.koitharu.kotatsu.core.util.ext.withPartialWakeLock
import org.koitharu.kotatsu.core.util.progress.Progress
import java.io.FileNotFoundException
import javax.inject.Inject
import androidx.appcompat.R as appcompatR

@AndroidEntryPoint
@SuppressLint("InlinedApi")
class TachiyomiImportService : BaseBackupRestoreService() {

	override val notificationTag = TAG
	override val isRestoreService = true

	@Inject
	lateinit var importer: TachiyomiBackupImporter

	override suspend fun IntentJobContext.processIntent(intent: Intent) {
		setForeground(
			FOREGROUND_NOTIFICATION_ID,
			buildNotification(Progress.INDETERMINATE),
			ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
		)
		val source = intent.getStringExtra(AppRouter.KEY_DATA)?.toUriOrNull() ?: throw FileNotFoundException()
		powerManager.withPartialWakeLock(TAG) {
			val progress = MutableStateFlow(Progress.INDETERMINATE)
			val progressUpdateJob = if (checkNotificationPermission(CHANNEL_ID)) {
				launch {
					progress.collect {
						notificationManager.notify(FOREGROUND_NOTIFICATION_ID, buildNotification(it))
					}
				}
			} else {
				null
			}
			val result = contentResolver.openInputStream(source)?.use { input ->
				importer.import(input, progress)
			} ?: throw FileNotFoundException()
			progressUpdateJob?.cancelAndJoin()
			showResultNotification(source, result)
		}
	}

	private fun IntentJobContext.buildNotification(progress: Progress): Notification {
		return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
			.setContentTitle(getString(R.string.import_tachiyomi))
			.setPriority(NotificationCompat.PRIORITY_HIGH)
			.setDefaults(0)
			.setSilent(true)
			.setOngoing(true)
			.setProgress(
				progress.total.coerceAtLeast(0),
				progress.progress.coerceAtLeast(0),
				progress.isIndeterminate,
			)
			.setContentText(
				if (progress.isIndeterminate) {
					getString(R.string.processing_)
				} else {
					getString(R.string.fraction_pattern, progress.progress, progress.total)
				},
			)
			.setSmallIcon(android.R.drawable.stat_sys_download)
			.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
			.setCategory(NotificationCompat.CATEGORY_PROGRESS)
			.addAction(
				appcompatR.drawable.abc_ic_clear_material,
				applicationContext.getString(android.R.string.cancel),
				getCancelIntent(),
			).build()
	}

	companion object {

		private const val TAG = "TACHIYOMI_IMPORT"
		private const val FOREGROUND_NOTIFICATION_ID = 41

		@CheckResult
		fun start(context: Context, uri: Uri): Boolean = try {
			val intent = Intent(context, TachiyomiImportService::class.java)
			intent.putExtra(AppRouter.KEY_DATA, uri.toString())
			ContextCompat.startForegroundService(context, intent)
			true
		} catch (e: Exception) {
			e.printStackTraceDebug()
			false
		}
	}
}
