package org.koitharu.kotatsu.core.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Lightweight in-app debug logger for troubleshooting on real devices.
 *
 * Messages always mirror to Logcat (tag [TAG]). When [isEnabled] (bound to the developer setting)
 * they are also kept in an in-memory ring buffer of the last [MAX_LINES] entries, which the user
 * can export from Settings -> Developer options and send back for diagnosis.
 */
object DebugLog {

	const val TAG = "YomuReDive"
	private const val MAX_LINES = 2000

	private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT)
	private val buffer = ArrayDeque<String>()

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

	private fun append(level: String, message: String, error: Throwable?) {
		if (!isEnabled) {
			return
		}
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

	fun clear() = synchronized(buffer) {
		buffer.clear()
	}
}
