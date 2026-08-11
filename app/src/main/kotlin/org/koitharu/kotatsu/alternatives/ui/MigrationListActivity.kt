package org.koitharu.kotatsu.alternatives.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as materialR
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.importedSourceName
import org.koitharu.kotatsu.core.model.parcelable.ParcelableManga
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.ui.BaseActivity
import org.koitharu.kotatsu.core.ui.dialog.buildAlertDialog
import org.koitharu.kotatsu.core.util.ext.consumeAllSystemBarsInsets
import org.koitharu.kotatsu.core.util.ext.getParcelableCompat
import org.koitharu.kotatsu.core.util.ext.getParcelableExtraCompat
import org.koitharu.kotatsu.core.util.ext.getThemeColor
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.core.util.ext.systemBarsInsets
import org.koitharu.kotatsu.databinding.ActivityMigrationListBinding
import org.koitharu.kotatsu.databinding.ItemMigrationListBinding
import org.koitharu.kotatsu.databinding.PaneMigrationFinderBinding
import org.koitharu.kotatsu.image.ui.CoverImageView
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

	// Inflated content of the tablet finder pane; null on phones, where the pane stays gone.
	private var finderBinding: PaneMigrationFinderBinding? = null

	// Tablet uses the List + sidebar layout: the row list on the left, the AlternativesFragment finder on
	// the right. Phones show the plain single-column list with the pane gone.
	private val isSidebarLayout get() = resources.getBoolean(R.bool.is_tablet)

	private val selectionColor by lazy(LazyThreadSafetyMode.NONE) {
		getThemeColor(materialR.attr.colorSurfaceVariant)
	}

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
		viewBinding.recyclerView.layoutManager = LinearLayoutManager(this)
		if (isSidebarLayout) {
			setupSidebar()
		}
		addMenuProvider(this)
		viewBinding.dropdownSource.setOnItemClickListener { _, _, position, _ ->
			onSourceSelected(position)
		}
		supportFragmentManager.setFragmentResultListener(AlternativesFragment.REQUEST_PICK, this) { _, bundle ->
			val picked = bundle.getParcelableCompat<ParcelableManga>(AppRouter.KEY_MANGA)?.manga
			val selectedId = viewModel.selectedRowId.value
			if (picked != null && selectedId != null) {
				viewModel.setMatch(selectedId, picked)
			}
		}
		viewModel.content.observe(this) {
			adapter.submit(it)
			updateFinderCount()
		}
		viewModel.matchedCount.observe(this) { updateFinderCount() }
		viewModel.selectedRowId.observe(this) { id ->
			if (isSidebarLayout) {
				adapter.setSelectedId(id)
				renderFinderFragment(id)
			}
		}
		viewModel.availableSources.observe(this) {
			rebuildSourceDropdown(it, viewModel.targetSource.value)
		}
		viewModel.targetSource.observe(this) { source ->
			rebuildSourceDropdown(viewModel.availableSources.value, source)
			viewBinding.dropdownSource.setText(source?.getTitle(this).orEmpty(), false)
			if (isSidebarLayout) {
				hostedFinderFragment()?.setTargetSourceByName(source?.name)
			}
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
		viewBinding.detailPane.updatePadding(
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
		if (viewModel.matchedCount.value == 0) {
			return
		}
		buildAlertDialog(this, isCentered = true) {
			setIcon(R.drawable.ic_replace)
			setTitle(R.string.manga_migration)
			setMessage(getString(R.string.migration_apply_confirmation, viewModel.matchedCount.value))
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

	// region Tablet List + sidebar layout

	/**
	 * Sets up the tablet List + sidebar layout: the row list on the left sharing space with the right-hand
	 * finder pane, which hosts a per-row [AlternativesFragment]. Only called when [isSidebarLayout] is true;
	 * on phones the pane stays gone and the list fills the screen.
	 */
	private fun setupSidebar() {
		val pane = viewBinding.detailPane
		(viewBinding.recyclerView.layoutParams as LinearLayout.LayoutParams).weight = 65f
		(pane.layoutParams as LinearLayout.LayoutParams).weight = 35f
		pane.isVisible = true
		renderFinderPane()
	}

	/**
	 * Inflates the "Find similar" sidebar: a compact matched-count line, a manual-search affordance, and a
	 * [FragmentContainerView][androidx.fragment.app.FragmentContainerView] that hosts a per-row
	 * [AlternativesFragment]. The toolbar keeps the target-source dropdown and APPLY action, so this pane
	 * needs neither.
	 */
	private fun renderFinderPane() {
		val pane = viewBinding.detailPane
		val binding = finderBinding ?: PaneMigrationFinderBinding.inflate(layoutInflater, pane, false).also {
			pane.removeAllViews()
			pane.addView(it.root)
			finderBinding = it
		}
		binding.buttonSearch.setOnClickListener { hostedFinderFragment()?.showManualSearch() }
		updateFinderCount()
		renderFinderFragment(viewModel.selectedRowId.value)
	}

	/** Swaps the hosted fragment for the currently-selected row, or shows the "select a manga" hint. */
	private fun renderFinderFragment(selectedId: Long?) {
		val binding = finderBinding ?: return
		val row = selectedId?.let { id -> viewModel.content.value.firstOrNull { it.original.id == id } }
		binding.buttonSearch.isEnabled = row != null
		if (row == null) {
			binding.textViewHint.isVisible = true
			binding.fragmentContainer.isVisible = false
			removeFinderFragment()
			return
		}
		binding.textViewHint.isVisible = false
		binding.fragmentContainer.isVisible = true
		supportFragmentManager.beginTransaction()
			.replace(
				binding.fragmentContainer.id,
				AlternativesFragment.newInstance(row.original, viewModel.targetSource.value),
				FINDER_FRAGMENT_TAG,
			)
			.commit()
	}

	private fun hostedFinderFragment(): AlternativesFragment? =
		supportFragmentManager.findFragmentByTag(FINDER_FRAGMENT_TAG) as? AlternativesFragment

	private fun removeFinderFragment() {
		val existing = supportFragmentManager.findFragmentByTag(FINDER_FRAGMENT_TAG) ?: return
		supportFragmentManager.beginTransaction().remove(existing).commit()
	}

	private fun updateFinderCount() {
		val binding = finderBinding ?: return
		binding.textViewCount.text = getString(
			R.string.migration_matched_count,
			viewModel.matchedCount.value,
			viewModel.content.value.size,
		)
	}

	// endregion

	/** Renders the original manga card (cover + title + source + latest/continue). */
	private fun bindOriginal(
		cover: CoverImageView,
		title: TextView,
		source: TextView,
		latest: TextView,
		row: MigrationRow,
	) {
		val ctx = cover.context
		cover.setImageAsync(row.original.coverUrl, row.original)
		title.text = row.original.title
		// Show the original source (e.g. "Manganato") for imported/broken manga instead of "Unknown".
		source.text = row.original.importedSourceName ?: row.original.source.getTitle(ctx)
		// A broken/unavailable source — a Tachiyomi import (UnknownMangaSource) OR a parser that is
		// down (e.g. MangaBuddy showing 404) — can't provide a chapter list, so "Latest" reads 0.
		// Whenever we know the reading position, show the continue-from chapter instead.
		latest.text = if (row.originalChaptersCount == 0 && row.continueChapter > 0f) {
			val n = row.continueChapter
			val label = if (n % 1f == 0f) n.toInt().toString() else n.toString()
			ctx.getString(R.string.migration_read_chapter, label)
		} else {
			ctx.getString(R.string.migration_latest_chapter, row.originalChaptersCount)
		}
	}

	/** Renders the matched-candidate card (or its searching/not-found placeholder). */
	private fun bindMatch(
		cover: CoverImageView,
		progress: View,
		title: TextView,
		source: TextView,
		latest: TextView,
		card: View,
		match: MatchState,
		original: Manga,
	) {
		val ctx = cover.context
		// In the tablet sidebar layout a row tap selects the row (opening the finder pane), so the "to"
		// card must not steal taps there. In the phone plain list, tapping the target opens the "Find
		// similar" picker so the user can match manually across all sources — even when auto-match found
		// nothing (NotFound) or picked the wrong title.
		val manualMatchOnTap = !isSidebarLayout
		when (match) {
			is MatchState.Searching -> {
				cover.setImageAsync(null, null)
				progress.isVisible = true
				title.text = null
				source.text = null
				latest.text = null
			}

			is MatchState.NotFound -> {
				cover.setImageAsync(null, null)
				progress.isVisible = false
				title.text = null
				source.text = null
				latest.text = null
			}

			is MatchState.Matched -> {
				cover.setImageAsync(match.manga.coverUrl, match.manga)
				progress.isVisible = false
				title.text = match.manga.title
				source.text = match.manga.source.getTitle(ctx)
				latest.text = ctx.getString(R.string.migration_latest_chapter, match.chaptersCount)
			}
		}
		if (manualMatchOnTap) {
			// Tap the target (in any state) to manually pick the right match from all sources.
			card.isClickable = true
			card.setOnClickListener { onOverride(original) }
		} else if (match is MatchState.Matched) {
			card.isClickable = true
			card.setOnClickListener { router.openDetails(match.manga) }
		} else {
			card.isClickable = false
			card.setOnClickListener(null)
		}
	}

	private inner class RowAdapter : RecyclerView.Adapter<DefaultRowViewHolder>() {

		private val items = ArrayList<MigrationRow>()

		/** Highlighted row in the tablet sidebar layout; null on phones / when nothing is selected. */
		var selectedId: Long? = null
			private set

		fun submit(newItems: List<MigrationRow>) {
			items.clear()
			items.addAll(newItems)
			notifyDataSetChanged()
		}

		fun setSelectedId(id: Long?) {
			if (selectedId != id) {
				selectedId = id
				notifyDataSetChanged()
			}
		}

		override fun getItemCount() = items.size

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DefaultRowViewHolder =
			DefaultRowViewHolder(ItemMigrationListBinding.inflate(layoutInflater, parent, false))

		override fun onBindViewHolder(holder: DefaultRowViewHolder, position: Int) = holder.bind(items[position])
	}

	private inner class DefaultRowViewHolder(
		private val binding: ItemMigrationListBinding,
	) : RecyclerView.ViewHolder(binding.root) {

		fun bind(row: MigrationRow) {
			binding.root.alpha = if (row.skipped) 0.4f else 1f
			bindOriginal(
				binding.imageViewCoverFrom,
				binding.textViewTitleFrom,
				binding.textViewSourceFrom,
				binding.textViewLatestFrom,
				row,
			)
			bindMatch(
				binding.imageViewCoverTo,
				binding.progressTo,
				binding.textViewTitleTo,
				binding.textViewSourceTo,
				binding.textViewLatestTo,
				binding.cardTo,
				row.match,
				row.original,
			)
			binding.buttonMenu.setOnClickListener { showRowMenu(it, row) }
			applySidebarSelection(binding.root, row)
		}
	}

	/**
	 * In the tablet sidebar layout a row is selectable: tapping it drives [MigrationListViewModel.selectRow]
	 * and the tapped row is highlighted. On phones the rows are inert (the "to" card handles taps instead).
	 */
	private fun applySidebarSelection(root: View, row: MigrationRow) {
		if (isSidebarLayout) {
			root.setBackgroundColor(if (adapter.selectedId == row.original.id) selectionColor else Color.TRANSPARENT)
			root.setOnClickListener { viewModel.selectRow(row.original.id) }
		} else {
			root.setBackgroundColor(Color.TRANSPARENT)
			root.setOnClickListener(null)
			root.isClickable = false
		}
	}

	companion object {

		const val EXTRA_IDS = "migration_ids"
		const val EXTRA_SOURCE = "migration_source"

		// Tag for the "Find similar" fragment hosted in the tablet sidebar layout.
		private const val FINDER_FRAGMENT_TAG = "finder"

		fun newIntent(context: Context, mangaIds: LongArray, sourceName: String?): Intent =
			Intent(context, MigrationListActivity::class.java)
				.putExtra(EXTRA_IDS, mangaIds)
				.putExtra(EXTRA_SOURCE, sourceName)
	}
}
