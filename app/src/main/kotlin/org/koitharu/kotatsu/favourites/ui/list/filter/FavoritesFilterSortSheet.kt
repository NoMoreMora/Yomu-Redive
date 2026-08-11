package org.koitharu.kotatsu.favourites.ui.list.filter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.toChipModel
import org.koitharu.kotatsu.core.ui.sheet.BaseAdaptiveSheet
import org.koitharu.kotatsu.core.ui.widgets.ChipsView
import org.koitharu.kotatsu.core.util.ext.consume
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.databinding.SheetFavouritesFilterBinding
import org.koitharu.kotatsu.list.domain.ListFilterOption
import org.koitharu.kotatsu.list.domain.ListSortOrder

@AndroidEntryPoint
class FavoritesFilterSortSheet :
	BaseAdaptiveSheet<SheetFavouritesFilterBinding>(),
	ChipsView.OnChipClickListener,
	View.OnClickListener {

	private val viewModel by viewModels<FavoritesFilterSortViewModel>()

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = SheetFavouritesFilterBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: SheetFavouritesFilterBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.chipsSort.onChipClickListener = this
		binding.chipsFlags.onChipClickListener = this
		binding.chipsTypes.onChipClickListener = this
		binding.chipsGenres.onChipClickListener = this
		binding.chipsSources.onChipClickListener = this
		binding.buttonReset.setOnClickListener(this)
		binding.buttonDone.setOnClickListener(this)

		viewModel.sortOrder.observe(viewLifecycleOwner, ::onSortOrderChanged)
		combine(
			viewModel.availableOptions,
			viewModel.appliedOptions,
			::Pair,
		).observe(viewLifecycleOwner) { (available, applied) ->
			onFilterOptionsChanged(available, applied)
		}
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
		}
	}

	override fun onChipClick(chip: Chip, data: Any?) {
		when (data) {
			is ListSortOrder -> viewModel.setSortOrder(data)
			is ListFilterOption -> viewModel.toggleFilterOption(data)
		}
	}

	private fun onSortOrderChanged(order: ListSortOrder?) {
		val binding = viewBinding ?: return
		binding.chipsSort.setChips(
			viewModel.sortOrders.map { sortOrder ->
				ChipsView.ChipModel(
					titleResId = sortOrder.titleResId,
					isChecked = sortOrder == order,
					data = sortOrder,
				)
			},
		)
	}

	private fun onFilterOptionsChanged(available: List<ListFilterOption>, applied: Set<ListFilterOption>) {
		val binding = viewBinding ?: return
		val flags = ArrayList<ChipsView.ChipModel>()
		val types = ArrayList<ChipsView.ChipModel>()
		val genres = ArrayList<ChipsView.ChipModel>()
		val sources = ArrayList<ChipsView.ChipModel>()
		for (option in available) {
			val chip = option.toChipModel(isChecked = option in applied)
			when (option) {
				is ListFilterOption.ContentType -> types.add(chip)
				is ListFilterOption.Tag,
				is ListFilterOption.TagTitle -> genres.add(chip)

				is ListFilterOption.Source -> sources.add(chip)
				else -> flags.add(chip)
			}
		}
		binding.chipsFlags.setChips(flags)
		binding.chipsTypes.setChips(types)
		binding.chipsGenres.setChips(genres)
		binding.chipsSources.setChips(sources)
		binding.titleFlags.isVisible = flags.isNotEmpty()
		binding.chipsFlags.isVisible = flags.isNotEmpty()
		binding.titleTypes.isVisible = types.isNotEmpty()
		binding.chipsTypes.isVisible = types.isNotEmpty()
		binding.titleGenres.isVisible = genres.isNotEmpty()
		binding.chipsGenres.isVisible = genres.isNotEmpty()
		binding.titleSources.isVisible = sources.isNotEmpty()
		binding.chipsSources.isVisible = sources.isNotEmpty()
	}
}
