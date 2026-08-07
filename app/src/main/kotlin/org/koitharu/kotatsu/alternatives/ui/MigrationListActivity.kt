package org.koitharu.kotatsu.alternatives.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.parcelable.ParcelableManga
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.ui.BaseActivity
import org.koitharu.kotatsu.core.ui.dialog.buildAlertDialog
import org.koitharu.kotatsu.core.util.ext.consumeAllSystemBarsInsets
import org.koitharu.kotatsu.core.util.ext.getParcelableExtraCompat
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.core.util.ext.systemBarsInsets
import org.koitharu.kotatsu.databinding.ActivityMigrationListBinding
import org.koitharu.kotatsu.databinding.ItemMigrationListBinding
import org.koitharu.kotatsu.parsers.model.Manga

/**
 * TachiyomiSY-style migration review list. Each row shows the original manga (left) and its
 * auto-matched candidate on the chosen source (right). Tapping the right side opens the
 * cross-source search to pick a different match. "Apply" migrates (or copies) all matched rows.
 */
@AndroidEntryPoint
class MigrationListActivity : BaseActivity<ActivityMigrationListBinding>(), MenuProvider {

	private val viewModel by viewModels<MigrationListViewModel>()
	private val adapter = RowAdapter()
	private var pendingOriginalId = 0L

	private val overrideLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == RESULT_OK) {
			val picked = result.data?.getParcelableExtraCompat<ParcelableManga>(AppRouter.KEY_MANGA)?.manga
			val originalId = pendingOriginalId
			if (picked != null && originalId != 0L) {
				viewModel.setMatch(originalId, picked)
			}
		}
		pendingOriginalId = 0L
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityMigrationListBinding.inflate(layoutInflater))
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		viewBinding.recyclerView.adapter = adapter
		addMenuProvider(this)
		viewModel.content.observe(this) { adapter.submit(it) }
		viewModel.onFinished.observeEvent(this) { count ->
			Toast.makeText(
				this,
				getString(R.string.migration_result, count, viewModel.content.value.size),
				Toast.LENGTH_SHORT,
			).show()
			finishAfterTransition()
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		viewBinding.recyclerView.updatePadding(
			left = barsInsets.left,
			right = barsInsets.right,
			bottom = barsInsets.bottom,
		)
		viewBinding.appbar.updatePadding(
			left = barsInsets.left,
			right = barsInsets.right,
			top = barsInsets.top,
		)
		return insets.consumeAllSystemBarsInsets()
	}

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_migration_list, menu)
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
		R.id.action_apply -> {
			showApplyDialog()
			true
		}

		else -> false
	}

	private fun showApplyDialog() {
		if (viewModel.matchedCount == 0) {
			return
		}
		buildAlertDialog(this, isCentered = true) {
			setIcon(R.drawable.ic_replace)
			setTitle(R.string.manga_migration)
			setMessage(getString(R.string.migration_apply_confirmation, viewModel.matchedCount))
			setNegativeButton(android.R.string.cancel, null)
			setNeutralButton(android.R.string.copy) { _, _ -> viewModel.apply(copy = true) }
			setPositiveButton(R.string.migrate) { _, _ -> viewModel.apply(copy = false) }
		}.show()
	}

	private fun onOverride(original: Manga) {
		pendingOriginalId = original.id
		overrideLauncher.launch(AlternativesActivity.newPickIntent(this, original))
	}

	private inner class RowAdapter : RecyclerView.Adapter<RowViewHolder>() {

		private val items = ArrayList<MigrationRow>()

		fun submit(newItems: List<MigrationRow>) {
			items.clear()
			items.addAll(newItems)
			notifyDataSetChanged()
		}

		override fun getItemCount() = items.size

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
			RowViewHolder(ItemMigrationListBinding.inflate(layoutInflater, parent, false))

		override fun onBindViewHolder(holder: RowViewHolder, position: Int) = holder.bind(items[position])
	}

	private inner class RowViewHolder(
		private val binding: ItemMigrationListBinding,
	) : RecyclerView.ViewHolder(binding.root) {

		fun bind(row: MigrationRow) {
			val ctx = binding.root.context
			binding.imageViewCoverFrom.setImageAsync(row.original.coverUrl, row.original)
			binding.textViewTitleFrom.text = row.original.title
			binding.textViewSourceFrom.text = row.original.source.getTitle(ctx)
			when (val match = row.match) {
				is MatchState.Searching -> {
					binding.imageViewCoverTo.setImageAsync(null, null)
					binding.textViewTitleTo.setText(R.string.migration_searching)
					binding.textViewSourceTo.text = null
				}

				is MatchState.NotFound -> {
					binding.imageViewCoverTo.setImageAsync(null, null)
					binding.textViewTitleTo.setText(R.string.migration_no_match)
					binding.textViewSourceTo.text = null
				}

				is MatchState.Matched -> {
					binding.imageViewCoverTo.setImageAsync(match.manga.coverUrl, match.manga)
					binding.textViewTitleTo.text = match.manga.title
					binding.textViewSourceTo.text = match.manga.source.getTitle(ctx)
				}
			}
			binding.layoutTo.setOnClickListener { onOverride(row.original) }
		}
	}

	companion object {

		const val EXTRA_IDS = "migration_ids"
		const val EXTRA_SOURCE = "migration_source"

		fun newIntent(context: Context, mangaIds: LongArray, sourceName: String): Intent =
			Intent(context, MigrationListActivity::class.java)
				.putExtra(EXTRA_IDS, mangaIds)
				.putExtra(EXTRA_SOURCE, sourceName)
	}
}
