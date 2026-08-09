package org.koitharu.kotatsu.details.domain

import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koitharu.kotatsu.core.util.DebugLog
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device (ML Kit) translation of manga descriptions into the device language.
 * Models are downloaded on first use and then work offline. Returns null when translation is
 * unnecessary (already in the target language) or unavailable, so callers keep the original text.
 */
class DescriptionTranslator @Inject constructor() {

	suspend fun translateToDeviceLanguage(text: String): String? {
		if (text.isBlank()) return null
		val target = TranslateLanguage.fromLanguageTag(Locale.getDefault().language) ?: return null
		val sourceTag = identifyLanguage(text) ?: return null
		val source = TranslateLanguage.fromLanguageTag(sourceTag) ?: return null
		if (source == target) return null
		val translator = Translation.getClient(
			TranslatorOptions.Builder()
				.setSourceLanguage(source)
				.setTargetLanguage(target)
				.build(),
		)
		return try {
			translator.downloadModelIfNeeded().awaitTask()
			translator.translate(text).awaitTask()
		} catch (e: Exception) {
			DebugLog.e("DescriptionTranslator failed ($sourceTag->${Locale.getDefault().language})", e)
			null
		} finally {
			translator.close()
		}
	}

	private suspend fun identifyLanguage(text: String): String? {
		val client = LanguageIdentification.getClient()
		return try {
			client.identifyLanguage(text).awaitTask().takeUnless { it == "und" }
		} catch (e: Exception) {
			DebugLog.e("Language identification failed", e)
			null
		} finally {
			client.close()
		}
	}

	private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
		addOnSuccessListener { cont.resume(it) }
		addOnFailureListener { cont.resumeWithException(it) }
		addOnCanceledListener { cont.cancel() }
	}
}
