package org.koitharu.kotatsu.list.domain

import androidx.collection.ArraySet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koitharu.kotatsu.core.model.toChipModel
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.list.ui.model.QuickFilter
import org.koitharu.kotatsu.parsers.util.suspendlazy.getOrNull
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy

abstract class MangaListQuickFilter(
	private val settings: AppSettings,
) : QuickFilterListener {

	private val localFilter = MutableStateFlow<Set<ListFilterOption>>(emptySet())
	private val availableFilterOptions = suspendLazy {
		getAvailableFilterOptions()
	}

	/**
	 * Currently applied filter options. Subclasses may override this to source the value from a
	 * persistent store (see [FavoritesListQuickFilter]) — [peekAppliedOptions] and [updateFilter]
	 * must then be overridden consistently so mutations go through the same store.
	 */
	open val appliedOptions: Flow<Set<ListFilterOption>>
		get() = localFilter.asStateFlow()

	override fun setFilterOption(option: ListFilterOption, isApplied: Boolean) {
		updateFilter { current ->
			ArraySet(current).also {
				if (isApplied) {
					it.addNoConflicts(option)
				} else {
					it.remove(option)
				}
			}
		}
	}

	override fun toggleFilterOption(option: ListFilterOption) {
		updateFilter { current ->
			ArraySet(current).also {
				if (option in it) {
					it.remove(option)
				} else {
					it.addNoConflicts(option)
				}
			}
		}
	}

	override fun clearFilter() {
		updateFilter { emptySet() }
	}

	suspend fun filterItem(
		selectedOptions: Set<ListFilterOption>,
	): QuickFilter? {
		if (!settings.isQuickFilterEnabled) {
			return null
		}
		val availableOptions = availableFilterOptions.getOrNull()?.map { option ->
			option.toChipModel(isChecked = option in selectedOptions)
		}.orEmpty()
		return if (availableOptions.isNotEmpty()) {
			QuickFilter(availableOptions)
		} else {
			null
		}
	}

	protected abstract suspend fun getAvailableFilterOptions(): List<ListFilterOption>

	/**
	 * The current set of applied options as seen by the mutating methods. Backed by [localFilter]
	 * by default; persistence-backed subclasses read it from their store.
	 */
	protected open fun peekAppliedOptions(): Set<ListFilterOption> = localFilter.value

	/**
	 * Applies [transform] to the currently applied options and stores the result. The default
	 * keeps the value in-memory; persistence-backed subclasses write it through to their store.
	 */
	protected open fun updateFilter(transform: (Set<ListFilterOption>) -> Set<ListFilterOption>) {
		localFilter.value = transform(localFilter.value)
	}

	protected fun ArraySet<ListFilterOption>.addNoConflicts(option: ListFilterOption) {
		add(option)
		if (option is ListFilterOption.Inverted) {
			remove(option.option)
		} else {
			removeIf { it is ListFilterOption.Inverted && it.option == option }
		}
	}
}
