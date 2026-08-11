package org.koitharu.kotatsu.alternatives.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.isBroken
import org.koitharu.kotatsu.core.model.parcelable.ParcelableManga
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.router
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
import org.koitharu.kotatsu.parsers.model.MangaSource

/**
 * TachiyomiSY-style migration review list. Entered directly from favourites (no source-picker step).
 * The target source is chosen in the toolbar dropdown (defaults to the preferred source, or the
 * most-used one when migrating away from it). Each row shows the original manga and its auto-matched
 * candidate as large cover cards; the per-row ⋮ menu offers manual search, skip, and single-row
 * migrate/copy. Tapping the candidate card opens its details.
 */
@AndroidEntryPoint
class MigrationListActivity : BaseActivity<ActivityMigrationListBinding>(), MenuProvider {

	private val viewModel by viewModels<MigrationListViewModel>()
	private val adapter = RowAdapter()
	private var pendingOriginalId = 0L

	// Sources currently shown in the dropdown (parallel to the dropdown entries; last entry is "More…").
	private var dropdownSources: List<MangaSource> = emptyList()

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

	private val sourcePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == RESULT_OK) {
			viewModel.setTargetSourceByName(result.data?.getStringExtra(AppRouter.KEY_SOURCE))
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityMigrationListBinding.inflate(layoutInflater))
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		viewBinding.recyclerView.adapter = adapter
		addMenuProvider(this)
		viewBinding.dropdownSource.setOnItemClickListener { _, _, position, _ ->
			onSourceSelected(position)
		}
		viewModel.content.observe(this) { adapter.submit(it) }
		viewModel.availableSources.observe(this) { rebuildSourceDropdown(it, viewModel.targetSource.value) }
		viewModel.targetSource.observe(this) { source ->
			rebuildSourceDropdown(viewModel.availableSources.value, source)
			viewBinding.dropdownSource.setText(source?.getTitle(this).orEmpty(), false)
		}
		viewModel.onRowMigrated.observeEvent(this) {
			Toast.makeText(this, R.string.migration_completed, Toast.LENGTH_SHORT).show()
		}
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

		R.id.action_options -> {
			router.showMigrationOptionsSheet()
			true
		}

		else -> false
	}

	private fun rebuildSourceDropdown(sources: List<MangaSource>, selected: MangaSource?) {
		dropdownSources = sources
		val entries = sources.mapTo(ArrayList(sources.size + 1)) { it.getTitle(this) }
		entries.add(getString(R.string.more_sources))
		viewBinding.dropdownSource.setAdapter(
			ArrayAdapter(this, android.R.layout.simple_list_item_1, entries),
		)
		if (selected != null) {
			viewBinding.dropdownSource.setText(selected.getTitle(this), false)
		}
	}

	private fun onSourceSelected(position: Int) {
		val source = dropdownSources.getOrNull(position)
		if (source != null) {
			viewModel.setTargetSource(source)
		} else {
			// "More sources…" — full picker (also reaches disabled sources)
			sourcePickerLauncher.launch(MigrationSourceActivity.newIntent(this))
		}
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

	private fun showRowMenu(anchor: View, row: MigrationRow) {
		PopupMenu(this, anchor).apply {
			inflate(R.menu.popup_migration_row)
			menu.findItem(R.id.action_migrate_now)?.isEnabled = row.match is MatchState.Matched && !row.skipped
			menu.findItem(R.id.action_copy_now)?.isEnabled = row.match is MatchState.Matched && !row.skipped
			menu.findItem(R.id.action_dont_migrate)?.setTitle(
				if (row.skipped) R.string.migrate else R.string.dont_migrate,
			)
			setOnMenuItemClickListener { item ->
				when (item.itemId) {
					R.id.action_search_manual -> onOverride(row.original)
					R.id.action_dont_migrate -> viewModel.setSkipped(row.original.id, !row.skipped)
					R.id.action_migrate_now -> viewModel.migrateRowNow(row.original.id, copy = false)
					R.id.action_copy_now -> viewModel.migrateRowNow(row.original.id, copy = true)
				}
				true
			}
			show()
		}
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
			binding.root.alpha = if (row.skipped) 0.4f else 1f
			binding.imageViewCoverFrom.setImageAsync(row.original.coverUrl, row.original)
			binding.textViewTitleFrom.text = row.original.title
			binding.textViewSourceFrom.text = row.original.source.getTitle(ctx)
			// Broken (e.g. Tachiyomi-imported) manga have no chapter list, so "Latest" would read 0;
			// show the continue-from chapter instead so there is a position to migrate from.
			binding.textViewLatestFrom.text = if (row.original.isBroken && row.continueChapter > 0f) {
				val n = row.continueChapter
				val label = if (n % 1f == 0f) n.toInt().toString() else n.toString()
				ctx.getString(R.string.migration_read_chapter, label)
			} else {
				ctx.getString(R.string.migration_latest_chapter, row.originalChaptersCount)
			}

			when (val match = row.match) {
				is MatchState.Searching -> {
					binding.imageViewCoverTo.setImageAsync(null, null)
					binding.progressTo.isVisible = true
					binding.textViewTitleTo.text = null
					binding.textViewSourceTo.text = null
					binding.textViewLatestTo.text = null
					binding.cardTo.isClickable = false
				}

				is MatchState.NotFound -> {
					binding.imageViewCoverTo.setImageAsync(null, null)
					binding.progressTo.isVisible = false
					binding.textViewTitleTo.text = null
					binding.textViewSourceTo.text = null
					binding.textViewLatestTo.text = null
					binding.cardTo.isClickable = false
				}

				is MatchState.Matched -> {
					binding.imageViewCoverTo.setImageAsync(match.manga.coverUrl, match.manga)
					binding.progressTo.isVisible = false
					binding.textViewTitleTo.text = match.manga.title
					binding.textViewSourceTo.text = match.manga.source.getTitle(ctx)
					binding.textViewLatestTo.text = ctx.getString(R.string.migration_latest_chapter, match.chaptersCount)
					binding.cardTo.isClickable = true
					binding.cardTo.setOnClickListener { router.openDetails(match.manga) }
				}
			}
			binding.buttonMenu.setOnClickListener { showRowMenu(it, row) }
		}
	}

	companion object {

		const val EXTRA_IDS = "migration_ids"
		const val EXTRA_SOURCE = "migration_source"

		fun newIntent(context: Context, mangaIds: LongArray, sourceName: String?): Intent =
			Intent(context, MigrationListActivity::class.java)
				.putExtra(EXTRA_IDS, mangaIds)
				.putExtra(EXTRA_SOURCE, sourceName)
	}
}
