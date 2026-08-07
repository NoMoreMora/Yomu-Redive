package org.koitharu.kotatsu.alternatives.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.parsers.model.MangaSource
import javax.inject.Inject

data class MigrationSourceItem(
	val source: MangaSource,
	val isEnabled: Boolean,
	val isPreferred: Boolean,
)

@HiltViewModel
class MigrationSourceViewModel @Inject constructor(
	private val sourcesRepository: MangaSourcesRepository,
	private val settings: AppSettings,
) : BaseViewModel() {

	private val showDisabled = MutableStateFlow(false)
	private val refreshTrigger = MutableStateFlow(0)

	val content: StateFlow<List<MigrationSourceItem>> = combine(showDisabled, refreshTrigger) { show, _ ->
		buildItems(show)
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptyList())

	val isShowingDisabled: Boolean
		get() = showDisabled.value

	fun setShowDisabled(value: Boolean) {
		showDisabled.value = value
	}

	fun toggleEnabled(source: MangaSource, isEnabled: Boolean) {
		launchJob(Dispatchers.Default) {
			sourcesRepository.setSourcesEnabled(setOf(source), isEnabled)
			refreshTrigger.value++
		}
	}

	fun setPreferred(source: MangaSource) {
		settings.migrationPreferredSource = source.name
		refreshTrigger.value++
	}

	private suspend fun buildItems(showDis: Boolean): List<MigrationSourceItem> {
		val preferred = settings.migrationPreferredSource
		val items = ArrayList<MigrationSourceItem>()
		sourcesRepository.getEnabledSources()
			.sortedByDescending { it.name == preferred }
			.mapTo(items) { MigrationSourceItem(it, isEnabled = true, isPreferred = it.name == preferred) }
		if (showDis) {
			sourcesRepository.getDisabledSources()
				.mapTo(items) { MigrationSourceItem(it, isEnabled = false, isPreferred = it.name == preferred) }
		}
		return items
	}
}
