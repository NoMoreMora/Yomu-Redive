package org.koitharu.kotatsu.favourites.ui.list.filter

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.ui.sheet.AdaptiveSheetBehavior
import org.koitharu.kotatsu.core.ui.sheet.AdaptiveSheetCallback
import org.koitharu.kotatsu.core.ui.sheet.BaseAdaptiveSheet
import org.koitharu.kotatsu.core.ui.util.DefaultTextWatcher
import org.koitharu.kotatsu.core.util.ext.consumeAll
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.databinding.SheetFavouritesFilterPickerBinding
import org.koitharu.kotatsu.list.domain.ListFilterOption

@AndroidEntryPoint
class FavoritesFilterPickerSheet :
	BaseAdaptiveSheet<SheetFavouritesFilterPickerBinding>(),
	DefaultTextWatcher,
	AdaptiveSheetCallback,
	View.OnFocusChangeListener,
	TextView.OnEditorActionListener {

	private val viewModel by viewModels<FavoritesFilterSortViewModel>()

	private lateinit var listAdapter: FavoritesFilterPickerAdapter
	private var allItems: List<FavoritesFilterPickerAdapter.Item> = emptyList()
	private var searchQuery: String = ""

	private val kind: Kind
		get() = Kind.entries.getOrElse(arguments?.getInt(AppRouter.KEY_KIND) ?: 0) { Kind.GENRES }

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): SheetFavouritesFilterPickerBinding {
		return SheetFavouritesFilterPickerBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: SheetFavouritesFilterPickerBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.headerBar.setTitle(kind.titleResId)
		listAdapter = FavoritesFilterPickerAdapter(viewModel::setFilterOption)
		binding.recyclerView.adapter = listAdapter
		binding.editSearch.addTextChangedListener(this)
		binding.editSearch.onFocusChangeListener = this
		binding.editSearch.setOnEditorActionListener(this)
		combine(
			viewModel.availableOptions,
			viewModel.appliedOptions,
			::Pair,
		).observe(viewLifecycleOwner) { (available, applied) ->
			allItems = available
				.filter { kind.matches(it) }
				.map { option ->
					FavoritesFilterPickerAdapter.Item(
						option = option,
						label = option.resolveLabel(binding.root.context),
						isChecked = option in applied,
					)
				}
			applyFilter()
		}
		addSheetCallback(this, viewLifecycleOwner)
		disableFitToContents()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeMask)
		viewBinding?.recyclerView?.setPadding(
			barsInsets.left,
			barsInsets.top,
			barsInsets.right,
			barsInsets.bottom,
		)
		return insets.consumeAll(typeMask)
	}

	override fun onFocusChange(v: View?, hasFocus: Boolean) {
		setExpanded(
			isExpanded = hasFocus || isExpanded,
			isLocked = hasFocus,
		)
	}

	override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
		return if (actionId == EditorInfo.IME_ACTION_SEARCH) {
			v.clearFocus()
			true
		} else {
			false
		}
	}

	override fun afterTextChanged(s: Editable?) {
		searchQuery = s?.toString().orEmpty()
		applyFilter()
	}

	override fun onStateChanged(sheet: View, newState: Int) {
		viewBinding?.recyclerView?.isFastScrollerEnabled = newState == AdaptiveSheetBehavior.STATE_EXPANDED
	}

	private fun applyFilter() {
		val query = searchQuery.trim()
		val filtered = if (query.isEmpty()) {
			allItems
		} else {
			allItems.filter { it.label.contains(query, ignoreCase = true) }
		}
		listAdapter.submitList(filtered)
	}

	enum class Kind(@StringRes val titleResId: Int) {

		GENRES(R.string.genres) {
			override fun matches(option: ListFilterOption): Boolean =
				option is ListFilterOption.Tag || option is ListFilterOption.TagTitle
		},

		SOURCES(R.string.sources) {
			override fun matches(option: ListFilterOption): Boolean =
				option is ListFilterOption.Source
		},
		;

		abstract fun matches(option: ListFilterOption): Boolean
	}

	private companion object {

		fun ListFilterOption.resolveLabel(context: Context): CharSequence =
			titleText ?: if (titleResId != 0) context.getString(titleResId) else ""
	}
}
