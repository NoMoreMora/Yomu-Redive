package org.koitharu.kotatsu.favourites.ui.list

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.alternatives.ui.MigrationService
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.ui.dialog.buildAlertDialog
import org.koitharu.kotatsu.core.ui.list.ListSelectionController
import org.koitharu.kotatsu.core.util.ext.sortedByOrdinal
import org.koitharu.kotatsu.core.util.ext.withArgs
import org.koitharu.kotatsu.databinding.FragmentListBinding
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.list.ui.MangaListFragment
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import javax.inject.Inject

@AndroidEntryPoint
class FavouritesListFragment : MangaListFragment(), PopupMenu.OnMenuItemClickListener {

	override val viewModel by viewModels<FavouritesListViewModel>()

	@Inject
	lateinit var mangaSourcesRepository: MangaSourcesRepository

	override val isSwipeRefreshEnabled = false

	val categoryId
		get() = viewModel.categoryId

	override fun onViewBindingCreated(binding: FragmentListBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.recyclerView.isVP2BugWorkaroundEnabled = true
	}

	override fun onScrolledToEnd() = viewModel.requestMoreItems()

	override fun onEmptyActionClick() = viewModel.clearFilter()

	override fun onFilterClick(view: View?) {
		val menu = PopupMenu(view?.context ?: return, view)
		menu.setOnMenuItemClickListener(this)
		val orders = ListSortOrder.FAVORITES.sortedByOrdinal()
		for ((i, item) in orders.withIndex()) {
			menu.menu.add(Menu.NONE, Menu.NONE, i, item.titleResId)
		}
		menu.show()
	}

	override fun onMenuItemClick(item: MenuItem): Boolean {
		val order = ListSortOrder.FAVORITES.sortedByOrdinal().getOrNull(item.order) ?: return false
		viewModel.setSortOrder(order)
		return true
	}

	override fun onCreateActionMode(
		controller: ListSelectionController,
		menuInflater: MenuInflater,
		menu: Menu
	): Boolean {
		menuInflater.inflate(R.menu.mode_favourites, menu)
		return super.onCreateActionMode(controller, menuInflater, menu)
	}

	override fun onActionItemClicked(controller: ListSelectionController, mode: ActionMode?, item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.action_pin -> {
				viewModel.setPinned(selectedItemsIds, isPinned = true)
				mode?.finish()
				true
			}

			R.id.action_unpin -> {
				viewModel.setPinned(selectedItemsIds, isPinned = false)
				mode?.finish()
				true
			}

			R.id.action_remove -> {
				viewModel.removeFromFavourites(selectedItemsIds)
				mode?.finish()
				true
			}

			R.id.action_mark_current -> {
				val itemsSnapshot = selectedItems
				MaterialAlertDialogBuilder(context ?: return false)
					.setTitle(item.title)
					.setMessage(R.string.mark_as_completed_prompt)
					.setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton(android.R.string.ok) { _, _ ->
						viewModel.markAsRead(itemsSnapshot)
						mode?.finish()
					}.show()
				true
			}

			R.id.action_related -> {
				val items = selectedItems
				when {
					items.isEmpty() -> Unit
					items.size == 1 -> router.openAlternatives(items.first())
					else -> showBatchMigrationSourcePicker(items)
				}
				mode?.finish()
				true
			}

			else -> super.onActionItemClicked(controller, mode, item)
		}
	}

	private fun showBatchMigrationSourcePicker(items: Set<Manga>) {
		val ids = items.map { it.id }
		lifecycleScope.launch {
			val sources = mangaSourcesRepository.getEnabledSources()
			if (sources.isEmpty()) {
				return@launch
			}
			val labels = sources.map { it.getTitle(requireContext()) }.toTypedArray()
			buildAlertDialog(requireContext()) {
				setTitle(R.string.select_source)
				setItems(labels) { _, which ->
					confirmBatchMigration(ids, sources[which])
				}
				setNegativeButton(android.R.string.cancel, null)
			}.show()
		}
	}

	private fun confirmBatchMigration(ids: List<Long>, source: MangaSource) {
		buildAlertDialog(requireContext(), isCentered = true) {
			setIcon(R.drawable.ic_replace)
			setTitle(R.string.manga_migration)
			setMessage(getString(R.string.migrate_selected_confirmation, ids.size, source.getTitle(context)))
			setNegativeButton(android.R.string.cancel, null)
			setNeutralButton(android.R.string.copy) { _, _ ->
				MigrationService.start(requireContext(), ids, source.name, copy = true)
			}
			setPositiveButton(R.string.migrate) { _, _ ->
				MigrationService.start(requireContext(), ids, source.name, copy = false)
			}
		}.show()
	}

	companion object {

		const val NO_ID = 0L

		fun newInstance(categoryId: Long) = FavouritesListFragment().withArgs(1) {
			putLong(AppRouter.KEY_ID, categoryId)
		}
	}
}
