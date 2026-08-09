package org.koitharu.kotatsu.settings

import android.os.Build
import android.os.Bundle
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.BasePreferenceFragment
import org.koitharu.kotatsu.core.util.DebugLog
import java.io.File

@AndroidEntryPoint
class DeveloperSettingsFragment : BasePreferenceFragment(R.string.developer_options) {

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_developer)
		findPreference<SwitchPreferenceCompat>(AppSettings.KEY_DEBUG_LOGGING)?.let { pref ->
			// Keep the in-memory logger in sync with the toggle so entries start/stop being captured.
			DebugLog.isEnabled = pref.isChecked
			pref.setOnPreferenceChangeListener { _, newValue ->
				DebugLog.isEnabled = newValue == true
				if (newValue == true) {
					DebugLog.d("Debug logging enabled on ${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}")
				}
				true
			}
		}
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		return when (preference.key) {
			AppSettings.KEY_EXPORT_LOG -> {
				exportLog()
				true
			}

			else -> super.onPreferenceTreeClick(preference)
		}
	}

	private fun exportLog() {
		val ctx = requireContext()
		val entries = DebugLog.dump()
		val text = buildString {
			append("Yomu Re:Dive debug log\n")
			append("App: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE)
				.append(") ").append(BuildConfig.APPLICATION_ID).append('\n')
			append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
			append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
			append("Logging enabled: ").append(DebugLog.isEnabled).append('\n')
			append("--------\n")
			append(
				entries.ifEmpty {
					"(no entries captured yet — turn on \"Debug logging\", reproduce the issue, then export again)"
				},
			)
		}
		val file = runCatching {
			val dir = File(ctx.cacheDir, "logs").apply { mkdirs() }
			File(dir, "yomu-redive-log.txt").apply { writeText(text) }
		}.getOrElse {
			Snackbar.make(listView, R.string.error_occurred, Snackbar.LENGTH_SHORT).show()
			return
		}
		val uri = FileProvider.getUriForFile(ctx, "${BuildConfig.APPLICATION_ID}.files", file)
		ShareCompat.IntentBuilder(ctx)
			.setStream(uri)
			.setType("text/plain")
			.setChooserTitle(R.string.export_log)
			.startChooser()
	}
}
