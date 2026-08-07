package org.koitharu.kotatsu.tracker.ui.updates

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.list.ListSelectionController
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.databinding.FragmentListBinding
import org.koitharu.kotatsu.list.ui.MangaListFragment

@AndroidEntryPoint
class UpdatesFragment : MangaListFragment() {

	override val viewModel by viewModels<UpdatesViewModel>()
	override val isSwipeRefreshEnabled = true

	override fun onViewBindingCreated(binding: FragmentListBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		// Pull-to-refresh triggers a favourites/tracking scan; keep the spinner up while it runs.
		viewModel.isRunning.observe(viewLifecycleOwner) { isRunning ->
			binding.swipeRefreshLayout.isRefreshing = isRunning
		}
	}

	override fun onScrolledToEnd() = Unit

	override fun onCreateActionMode(
		controller: ListSelectionController,
		menuInflater: MenuInflater,
		menu: Menu
	): Boolean {
		menuInflater.inflate(R.menu.mode_updates, menu)
		return super.onCreateActionMode(controller, menuInflater, menu)
	}

	override fun onActionItemClicked(controller: ListSelectionController, mode: ActionMode?, item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.action_remove -> {
				viewModel.remove(controller.snapshot())
				mode?.finish()
				true
			}

			else -> super.onActionItemClicked(controller, mode, item)
		}
	}
}
