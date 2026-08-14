package org.koitharu.kotatsu.favourites.ui.list.filter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.LinearLayout
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import com.google.android.material.materialswitch.MaterialSwitch
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.ui.sheet.BaseAdaptiveSheet
import org.koitharu.kotatsu.core.util.ext.consume
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.databinding.ItemFilterSwitchBinding
import org.koitharu.kotatsu.databinding.SheetFavouritesFilterBinding
import org.koitharu.kotatsu.list.domain.ListFilterOption
import org.koitharu.kotatsu.list.domain.ListSortOrder

@AndroidEntryPoint
class FavoritesFilterSortSheet :
	BaseAdaptiveSheet<SheetFavouritesFilterBinding>(),
	View.OnClickListener,
	AdapterView.OnItemSelectedListener {

	private val viewModel by viewModels<FavoritesFilterSortViewModel>()

	private val sortOrders: List<ListSortOrder>
		get() = viewModel.sortOrders

	private var boundSortOrder: ListSortOrder? = null
	private var flagSwitches: Map<ListFilterOption, MaterialSwitch> = emptyMap()
	private var typeSwitches: Map<ListFilterOption, MaterialSwitch> = emptyMap()
	private var genreOptions: List<ListFilterOption> = emptyList()
	private var sourceOptions: List<ListFilterOption> = emptyList()

	private val switchCheckListener = CompoundButton.OnCheckedChangeListener { button, isChecked ->
		(button.tag as? ListFilterOption)?.let { viewModel.setFilterOption(it, isChecked) }
	}

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = SheetFavouritesFilterBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: SheetFavouritesFilterBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.buttonReset.setOnClickListener(this)
		binding.buttonDone.setOnClickListener(this)
		binding.rowGenres.setOnClickListener(this)
		binding.rowSources.setOnClickListener(this)

		binding.spinnerOrder.adapter = ArrayAdapter(
			binding.spinnerOrder.context,
			android.R.layout.simple_spinner_dropdown_item,
			android.R.id.text1,
			sortOrders.map { binding.spinnerOrder.context.getString(it.titleResId) },
		)
		val currentOrder = viewModel.sortOrder.value
		boundSortOrder = currentOrder
		val initialIndex = sortOrders.indexOf(currentOrder)
		if (initialIndex >= 0) {
			binding.spinnerOrder.setSelection(initialIndex, false)
		}
		binding.spinnerOrder.onItemSelectedListener = this

		viewModel.sortOrder.observe(viewLifecycleOwner, ::onSortOrderChanged)
		viewModel.availableOptions.observe(viewLifecycleOwner, ::onAvailableOptionsChanged)
		viewModel.appliedOptions.observe(viewLifecycleOwner, ::onAppliedOptionsChanged)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		viewBinding?.layoutBottom?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
			bottomMargin = insets.getInsets(typeMask).bottom
		}
		return insets.consume(v, typeMask, bottom = true)
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_reset -> viewModel.reset()
			R.id.button_done -> dismiss()
			R.id.row_genres -> router.showFavoritesFilterPickerSheet(
				viewModel.categoryId,
				FavoritesFilterPickerSheet.Kind.GENRES,
			)

			R.id.row_sources -> router.showFavoritesFilterPickerSheet(
				viewModel.categoryId,
				FavoritesFilterPickerSheet.Kind.SOURCES,
			)
		}
	}

	override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
		if (parent.id == R.id.spinner_order) {
			val order = sortOrders.getOrNull(position) ?: return
			if (order != boundSortOrder) {
				viewModel.setSortOrder(order)
			}
		}
	}

	override fun onNothingSelected(parent: AdapterView<*>?) = Unit

	private fun onSortOrderChanged(order: ListSortOrder?) {
		val binding = viewBinding ?: return
		boundSortOrder = order
		val index = sortOrders.indexOf(order)
		if (index >= 0 && binding.spinnerOrder.selectedItemPosition != index) {
			binding.spinnerOrder.setSelection(index, false)
		}
	}

	private fun onAvailableOptionsChanged(available: List<ListFilterOption>) {
		val binding = viewBinding ?: return
		val flags = ArrayList<ListFilterOption>()
		val types = ArrayList<ListFilterOption>()
		val genres = ArrayList<ListFilterOption>()
		val sources = ArrayList<ListFilterOption>()
		for (option in available) {
			when (option) {
				is ListFilterOption.ContentType -> types.add(option)
				is ListFilterOption.Tag,
				is ListFilterOption.TagTitle -> genres.add(option)

				is ListFilterOption.Source -> sources.add(option)
				else -> flags.add(option)
			}
		}
		val applied = viewModel.appliedOptions.value
		flagSwitches = buildSwitchRows(binding.containerFlags, flags, applied)
		typeSwitches = buildSwitchRows(binding.containerTypes, types, applied)
		genreOptions = genres
		sourceOptions = sources

		binding.titleFlags.isVisible = flags.isNotEmpty()
		binding.containerFlags.isVisible = flags.isNotEmpty()
		binding.titleTypes.isVisible = types.isNotEmpty()
		binding.containerTypes.isVisible = types.isNotEmpty()
		binding.titleGenres.isVisible = genres.isNotEmpty()
		binding.rowGenres.isVisible = genres.isNotEmpty()
		binding.titleSources.isVisible = sources.isNotEmpty()
		binding.rowSources.isVisible = sources.isNotEmpty()

		updateSummaries(applied)
	}

	private fun onAppliedOptionsChanged(applied: Set<ListFilterOption>) {
		for ((option, switch) in flagSwitches) {
			switch.setCheckedSilently(option in applied)
		}
		for ((option, switch) in typeSwitches) {
			switch.setCheckedSilently(option in applied)
		}
		updateSummaries(applied)
	}

	private fun updateSummaries(applied: Set<ListFilterOption>) {
		val binding = viewBinding ?: return
		val genresSelected = genreOptions.count { it in applied }
		binding.textGenresSummary.text = if (genresSelected > 0) {
			resources.getQuantityString(R.plurals.selected_count, genresSelected, genresSelected)
		} else {
			getString(R.string.any)
		}
		val sourcesSelected = sourceOptions.count { it in applied }
		binding.textSourcesSummary.text = if (sourcesSelected > 0) {
			resources.getQuantityString(R.plurals.selected_count, sourcesSelected, sourcesSelected)
		} else {
			getString(R.string.all)
		}
	}

	private fun buildSwitchRows(
		container: LinearLayout,
		options: List<ListFilterOption>,
		applied: Set<ListFilterOption>,
	): Map<ListFilterOption, MaterialSwitch> {
		container.removeAllViews()
		val map = LinkedHashMap<ListFilterOption, MaterialSwitch>(options.size)
		val inflater = LayoutInflater.from(container.context)
		for (option in options) {
			val switch = ItemFilterSwitchBinding.inflate(inflater, container, false).root
			switch.text = option.titleText ?: container.context.getString(option.titleResId)
			switch.setCompoundDrawablesRelativeWithIntrinsicBounds(option.iconResId, 0, 0, 0)
			switch.tag = option
			switch.isChecked = option in applied
			switch.setOnCheckedChangeListener(switchCheckListener)
			container.addView(switch)
			map[option] = switch
		}
		return map
	}

	private fun MaterialSwitch.setCheckedSilently(checked: Boolean) {
		if (isChecked == checked) {
			return
		}
		setOnCheckedChangeListener(null)
		isChecked = checked
		setOnCheckedChangeListener(switchCheckListener)
	}
}
