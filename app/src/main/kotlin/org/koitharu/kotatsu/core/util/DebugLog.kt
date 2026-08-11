package org.koitharu.kotatsu.core.util

import android.os.Process
import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Lightweight in-app debug logger for troubleshooting on real devices.
 *
 * [d]/[e] always mirror to Logcat (tag [TAG]) **and** are kept in an in-memory ring buffer of the
 * last [MAX_LINES] entries — capture is always on, so an exported report is never empty even if the
 * user forgot to flip the toggle first. [v] adds extra-verbose breadcrumbs only when [isEnabled].
 * The export (Settings -> Developer options) combines this buffer with a dump of the app's own
 * Logcat, so it also carries everything logged through [android.util.Log] elsewhere.
 */
object DebugLog {

	const val TAG = "YomuReDive"
	private const val MAX_LINES = 4000

	private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT)
	private val buffer = ArrayDeque<String>()

	/** Adds extra-verbose breadcrumbs (via [v]) on top of the always-captured [d]/[e] entries. */
	@Volatile
	var isEnabled = false

	fun d(message: String) {
		Log.d(TAG, message)
		append("D", message, null)
	}

	fun e(message: String, error: Throwable? = null) {
		Log.e(TAG, message, error)
		append("E", message, error)
	}

	/** Verbose breadcrumb — recorded only when [isEnabled] to avoid noise in the common case. */
	fun v(message: String) {
		if (!isEnabled) {
			return
		}
		Log.d(TAG, message)
		append("V", message, null)
	}

	private fun append(level: String, message: String, error: Throwable?) {
		val line = buildString {
			append(fmt.format(Date()))
			append(' ')
			append(level)
			append(' ')
			append(message)
			if (error != null) {
				append('\n')
				append(Log.getStackTraceString(error))
			}
		}
		synchronized(buffer) {
			buffer.addLast(line)
			while (buffer.size > MAX_LINES) {
				buffer.removeFirst()
			}
		}
	}

	fun dump(): String = synchronized(buffer) {
		buffer.joinToString("\n")
	}

	/**
	 * Reads the app's own Logcat (own PID only — no special permission needed since API 16), newest
	 * [maxChars] kept. Returns a short placeholder when Logcat can't be read on this device/ROM.
	 */
	fun dumpLogcat(maxChars: Int = 256 * 1024): String = runCatching {
		val process = ProcessBuilder("logcat", "-d", "-v", "threadtime")
			.redirectErrorStream(true)
			.start()
		val text = process.inputStream.bufferedReader().use { it.readText() }
		process.destroy()
		val mine = text.lineSequence()
			.filter { it.contains(" ${Process.myPid()} ") }
			.joinToString("\n")
			.ifBlank { text } // some ROMs already scope logcat to the caller; keep everything then
		if (mine.length > maxChars) mine.substring(mine.length - maxChars) else mine
	}.getOrElse { "(logcat unavailable on this device: ${it.message})" }

	fun clear() = synchronized(buffer) {
		buffer.clear()
	}
}
