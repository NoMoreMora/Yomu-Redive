package org.koitharu.kotatsu.favourites.ui.list.filter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.observeAsFlow
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.util.ext.sortedByOrdinal
import org.koitharu.kotatsu.favourites.domain.FavoritesListQuickFilter
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.favourites.ui.list.FavouritesListFragment.Companion.NO_ID
import org.koitharu.kotatsu.list.domain.ListFilterOption
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.list.domain.QuickFilterListener
import javax.inject.Inject

@HiltViewModel
class FavoritesFilterSortViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val settings: AppSettings,
	private val repository: FavouritesRepository,
	quickFilterFactory: FavoritesListQuickFilter.Factory,
) : BaseViewModel(), QuickFilterListener {

	val categoryId: Long = savedStateHandle[AppRouter.KEY_ID] ?: NO_ID

	private val quickFilter = quickFilterFactory.create(categoryId, viewModelScope + Dispatchers.Default)

	val sortOrders: List<ListSortOrder> = ListSortOrder.FAVORITES.sortedByOrdinal()

	val appliedOptions: StateFlow<Set<ListFilterOption>> = quickFilter.appliedOptions

	val availableOptions = MutableStateFlow<List<ListFilterOption>>(emptyList())

	val sortOrder: StateFlow<ListSortOrder?> = if (categoryId == NO_ID) {
		settings.observeAsFlow(AppSettings.KEY_FAVORITES_ORDER) { allFavoritesSortOrder }
	} else {
		repository.observeCategory(categoryId)
			.withErrorHandling()
			.map { it?.order }
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

	init {
		launchLoadingJob(Dispatchers.Default) {
			availableOptions.value = quickFilter.getExpandedFilterOptions()
		}
	}

	override fun setFilterOption(option: ListFilterOption, isApplied: Boolean) =
		quickFilter.setFilterOption(option, isApplied)

	override fun toggleFilterOption(option: ListFilterOption) = quickFilter.toggleFilterOption(option)

	override fun clearFilter() = quickFilter.clearFilter()

	fun setSortOrder(order: ListSortOrder) {
		if (categoryId == NO_ID) {
			settings.allFavoritesSortOrder = order
		} else {
			launchJob(Dispatchers.Default) {
				repository.setCategoryOrder(categoryId, order)
			}
		}
	}

	/**
	 * Clears every applied filter and restores the default sort order for this category.
	 */
	fun reset() {
		clearFilter()
		setSortOrder(DEFAULT_SORT_ORDER)
	}

	companion object {

		val DEFAULT_SORT_ORDER = ListSortOrder.NEWEST
	}
}
