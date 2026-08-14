package org.koitharu.kotatsu.favourites.ui.list.filter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.koitharu.kotatsu.databinding.ItemCheckableNewBinding
import org.koitharu.kotatsu.list.domain.ListFilterOption

class FavoritesFilterPickerAdapter(
	private val onToggle: (ListFilterOption, Boolean) -> Unit,
) : ListAdapter<FavoritesFilterPickerAdapter.Item, FavoritesFilterPickerAdapter.ViewHolder>(DiffCallback()) {

	data class Item(
		val option: ListFilterOption,
		val label: CharSequence,
		val isChecked: Boolean,
	)

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val binding = ItemCheckableNewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return ViewHolder(binding)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		holder.bind(getItem(position))
	}

	inner class ViewHolder(
		private val binding: ItemCheckableNewBinding,
	) : RecyclerView.ViewHolder(binding.root) {

		private var boundItem: Item? = null

		init {
			binding.root.setOnClickListener {
				boundItem?.let { item -> onToggle(item.option, !item.isChecked) }
			}
		}

		fun bind(item: Item) {
			boundItem = item
			binding.root.text = item.label
			binding.root.isChecked = item.isChecked
		}
	}

	private class DiffCallback : DiffUtil.ItemCallback<Item>() {

		override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean =
			oldItem.option == newItem.option

		override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean =
			oldItem.isChecked == newItem.isChecked && oldItem.label == newItem.label
	}
}
