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
import androidx.recyclerview.widget.GridLayoutManager
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
import org.koitharu.kotatsu.core.util.ext.getParcelableExtraCompat
import org.koitharu.kotatsu.core.util.ext.getThemeColor
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.core.util.ext.systemBarsInsets
import org.koitharu.kotatsu.databinding.ActivityMigrationListBinding
import org.koitharu.kotatsu.databinding.ItemMigrationCandidateBinding
import org.koitharu.kotatsu.databinding.ItemMigrationListBinding
import org.koitharu.kotatsu.databinding.ItemMigrationListCarouselBinding
import org.koitharu.kotatsu.databinding.ItemMigrationListWideBinding
import org.koitharu.kotatsu.databinding.PaneMigrationDetailBinding
import org.koitharu.kotatsu.databinding.PaneMigrationSidebarBinding
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

	// Inflated content of the right-hand pane (master-detail = design 3, sidebar = design 4); null when
	// the current layout has no pane, so modes 0/1/2/5 leave the pane empty and gone.
	private var detailBinding: PaneMigrationDetailBinding? = null
	private var sidebarBinding: PaneMigrationSidebarBinding? = null

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
		val mode = if (resources.getBoolean(R.bool.is_tablet)) viewModel.tabletLayout else 0
		configureLayout(mode)
		addMenuProvider(this)
		viewBinding.dropdownSource.setOnItemClickListener { _, _, position, _ ->
			onSourceSelected(position)
		}
		viewModel.content.observe(this) {
			adapter.submit(it)
			updateSidebarCount()
			if (adapter.layoutMode == LAYOUT_MASTER_DETAIL) {
				renderDetailPane(viewModel.selectedRowId.value)
			}
		}
		viewModel.matchedCount.observe(this) { updateSidebarCount() }
		viewModel.selectedRowId.observe(this) { id ->
			if (adapter.layoutMode == LAYOUT_MASTER_DETAIL) {
				adapter.setSelectedId(id)
				renderDetailPane(id)
			}
		}
		viewModel.availableSources.observe(this) {
			rebuildSourceDropdown(it, viewModel.targetSource.value)
			rebuildSidebarDropdown(it, viewModel.targetSource.value)
		}
		viewModel.targetSource.observe(this) { source ->
			rebuildSourceDropdown(viewModel.availableSources.value, source)
			rebuildSidebarDropdown(viewModel.availableSources.value, source)
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

	// region Layout switching (tablet)

	/**
	 * Applies one of the 5 tablet layouts (see the Developer options selector). Sets the RecyclerView
	 * layout manager, the adapter view type, and shows/hides the right-hand pane. For modes 0/1/2/5 the
	 * pane is emptied and gone, so they render exactly like the phone layout.
	 */
	private fun configureLayout(mode: Int) {
		adapter.layoutMode = mode
		val pane = viewBinding.detailPane
		viewBinding.recyclerView.layoutManager = if (mode == LAYOUT_GRID) {
			GridLayoutManager(this, 2)
		} else {
			LinearLayoutManager(this)
		}
		detailBinding = null
		sidebarBinding = null
		pane.removeAllViews()
		when (mode) {
			LAYOUT_MASTER_DETAIL -> {
				setPaneWeights(recyclerWeight = 40f, paneWeight = 60f)
				pane.isVisible = true
				renderDetailPane(viewModel.selectedRowId.value)
			}

			LAYOUT_SIDEBAR -> {
				setPaneWeights(recyclerWeight = 65f, paneWeight = 35f)
				pane.isVisible = true
				renderSidebar()
			}

			else -> {
				setPaneWeights(recyclerWeight = 1f, paneWeight = 0f)
				pane.isVisible = false
			}
		}
		adapter.notifyDataSetChanged()
	}

	private fun setPaneWeights(recyclerWeight: Float, paneWeight: Float) {
		(viewBinding.recyclerView.layoutParams as LinearLayout.LayoutParams).weight = recyclerWeight
		(viewBinding.detailPane.layoutParams as LinearLayout.LayoutParams).weight = paneWeight
		viewBinding.recyclerView.requestLayout()
		viewBinding.detailPane.requestLayout()
	}

	private fun renderDetailPane(selectedId: Long?) {
		val pane = viewBinding.detailPane
		val binding = detailBinding ?: PaneMigrationDetailBinding.inflate(layoutInflater, pane, false).also {
			pane.removeAllViews()
			pane.addView(it.root)
			detailBinding = it
		}
		val row = selectedId?.let { id -> viewModel.content.value.firstOrNull { it.original.id == id } }
		if (row == null) {
			binding.textViewEmpty.isVisible = true
			binding.scrollContent.isVisible = false
			return
		}
		binding.textViewEmpty.isVisible = false
		binding.scrollContent.isVisible = true
		binding.root.alpha = if (row.skipped) 0.6f else 1f
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
		)
		val canMigrate = row.match is MatchState.Matched && !row.skipped
		binding.buttonMigrate.isEnabled = canMigrate
		binding.buttonCopy.isEnabled = canMigrate
		binding.buttonMigrate.setOnClickListener { viewModel.migrateRowNow(row.original.id, copy = false) }
		binding.buttonCopy.setOnClickListener { viewModel.migrateRowNow(row.original.id, copy = true) }
		binding.buttonSkip.setText(if (row.skipped) R.string.migrate else R.string.dont_migrate)
		binding.buttonSkip.setOnClickListener { viewModel.setSkipped(row.original.id, !row.skipped) }
		binding.buttonSearch.setOnClickListener { onOverride(row.original) }
	}

	private fun renderSidebar() {
		val pane = viewBinding.detailPane
		val binding = sidebarBinding ?: PaneMigrationSidebarBinding.inflate(layoutInflater, pane, false).also {
			pane.removeAllViews()
			pane.addView(it.root)
			sidebarBinding = it
		}
		rebuildSidebarDropdown(viewModel.availableSources.value, viewModel.targetSource.value)
		binding.dropdownSource.setOnItemClickListener { _, _, position, _ -> onSourceSelected(position) }
		binding.buttonApply.setOnClickListener { showApplyDialog() }
		binding.buttonCopy.setOnClickListener {
			if (viewModel.matchedCount.value > 0) {
				viewModel.apply(copy = true)
			}
		}
		updateSidebarCount()
	}

	private fun rebuildSidebarDropdown(sources: List<MangaSource>, selected: MangaSource?) {
		val binding = sidebarBinding ?: return
		val entries = sources.mapTo(ArrayList(sources.size + 1)) { it.getTitle(this) }
		entries.add(getString(R.string.more_sources))
		binding.dropdownSource.setAdapter(
			ArrayAdapter(this, android.R.layout.simple_list_item_1, entries),
		)
		if (selected != null) {
			binding.dropdownSource.setText(selected.getTitle(this), false)
		}
	}

	private fun updateSidebarCount() {
		val binding = sidebarBinding ?: return
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
	) {
		val ctx = cover.context
		when (match) {
			is MatchState.Searching -> {
				cover.setImageAsync(null, null)
				progress.isVisible = true
				title.text = null
				source.text = null
				latest.text = null
				card.isClickable = false
			}

			is MatchState.NotFound -> {
				cover.setImageAsync(null, null)
				progress.isVisible = false
				title.text = null
				source.text = null
				latest.text = null
				card.isClickable = false
			}

			is MatchState.Matched -> {
				cover.setImageAsync(match.manga.coverUrl, match.manga)
				progress.isVisible = false
				title.text = match.manga.title
				source.text = match.manga.source.getTitle(ctx)
				latest.text = ctx.getString(R.string.migration_latest_chapter, match.chaptersCount)
				card.isClickable = true
				card.setOnClickListener { router.openDetails(match.manga) }
			}
		}
	}

	private abstract inner class BaseRowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		abstract fun bind(row: MigrationRow)
	}

	private inner class RowAdapter : RecyclerView.Adapter<BaseRowViewHolder>() {

		private val items = ArrayList<MigrationRow>()

		/** One of the LAYOUT_* constants; drives [getItemViewType] so the whole list uses one design. */
		var layoutMode: Int = 0

		/** Highlighted row in the master-detail layout (design 3). */
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

		override fun getItemViewType(position: Int) = layoutMode

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseRowViewHolder = when (viewType) {
			LAYOUT_WIDE -> WideRowViewHolder(ItemMigrationListWideBinding.inflate(layoutInflater, parent, false))
			LAYOUT_CAROUSEL -> CarouselRowViewHolder(
				ItemMigrationListCarouselBinding.inflate(layoutInflater, parent, false),
			)

			else -> DefaultRowViewHolder(ItemMigrationListBinding.inflate(layoutInflater, parent, false))
		}

		override fun onBindViewHolder(holder: BaseRowViewHolder, position: Int) = holder.bind(items[position])
	}

	private inner class DefaultRowViewHolder(
		private val binding: ItemMigrationListBinding,
	) : BaseRowViewHolder(binding.root) {

		override fun bind(row: MigrationRow) {
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
			)
			binding.buttonMenu.setOnClickListener { showRowMenu(it, row) }
			applyMasterDetailSelection(binding.root, row)
		}
	}

	private inner class WideRowViewHolder(
		private val binding: ItemMigrationListWideBinding,
	) : BaseRowViewHolder(binding.root) {

		override fun bind(row: MigrationRow) {
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
			)
			binding.buttonMenu.setOnClickListener { showRowMenu(it, row) }
		}
	}

	private inner class CarouselRowViewHolder(
		private val binding: ItemMigrationListCarouselBinding,
	) : BaseRowViewHolder(binding.root) {

		private val candidateAdapter = CandidateAdapter()

		init {
			binding.recyclerCandidates.adapter = candidateAdapter
		}

		override fun bind(row: MigrationRow) {
			binding.root.alpha = if (row.skipped) 0.4f else 1f
			bindOriginal(
				binding.imageViewCoverFrom,
				binding.textViewTitleFrom,
				binding.textViewSourceFrom,
				binding.textViewLatestFrom,
				row,
			)
			binding.buttonMenu.setOnClickListener { showRowMenu(it, row) }
			when (val match = row.match) {
				is MatchState.Matched -> {
					binding.textViewStatus.isVisible = false
					binding.recyclerCandidates.isVisible = true
					candidateAdapter.submit(row.original.id, match.candidates, match.manga.id)
				}

				is MatchState.Searching -> {
					binding.textViewStatus.isVisible = true
					binding.textViewStatus.setText(R.string.migration_searching)
					binding.recyclerCandidates.isVisible = false
					candidateAdapter.submit(row.original.id, emptyList(), null)
				}

				is MatchState.NotFound -> {
					binding.textViewStatus.isVisible = true
					binding.textViewStatus.setText(R.string.migration_no_match)
					binding.recyclerCandidates.isVisible = false
					candidateAdapter.submit(row.original.id, emptyList(), null)
				}
			}
		}
	}

	private inner class CandidateAdapter : RecyclerView.Adapter<CandidateViewHolder>() {

		private val items = ArrayList<Pair<Manga, Int>>()
		private var originalId = 0L
		private var selectedMangaId: Long? = null

		fun submit(originalId: Long, candidates: List<Pair<Manga, Int>>, selectedMangaId: Long?) {
			this.originalId = originalId
			this.selectedMangaId = selectedMangaId
			items.clear()
			items.addAll(candidates)
			notifyDataSetChanged()
		}

		override fun getItemCount() = items.size

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
			CandidateViewHolder(ItemMigrationCandidateBinding.inflate(layoutInflater, parent, false))

		override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
			val (manga, chapters) = items[position]
			holder.bind(originalId, manga, chapters, manga.id == selectedMangaId)
		}
	}

	private inner class CandidateViewHolder(
		private val binding: ItemMigrationCandidateBinding,
	) : RecyclerView.ViewHolder(binding.root) {

		fun bind(originalId: Long, manga: Manga, chaptersCount: Int, selected: Boolean) {
			val ctx = binding.root.context
			binding.imageViewCover.setImageAsync(manga.coverUrl, manga)
			binding.textViewTitle.text = manga.title
			binding.textViewLatest.text = ctx.getString(R.string.migration_latest_chapter, chaptersCount)
			binding.cardCandidate.strokeWidth =
				if (selected) ctx.resources.getDimensionPixelSize(R.dimen.selection_stroke_width) else 0
			binding.root.setOnClickListener { viewModel.selectCandidate(originalId, manga) }
		}
	}

	private fun applyMasterDetailSelection(root: View, row: MigrationRow) {
		if (adapter.layoutMode == LAYOUT_MASTER_DETAIL) {
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

		// Tablet layout modes; index-aligned with R.array.migration_tablet_layouts / the pref entry values.
		private const val LAYOUT_GRID = 1
		private const val LAYOUT_WIDE = 2
		private const val LAYOUT_MASTER_DETAIL = 3
		private const val LAYOUT_SIDEBAR = 4
		private const val LAYOUT_CAROUSEL = 5

		fun newIntent(context: Context, mangaIds: LongArray, sourceName: String?): Intent =
			Intent(context, MigrationListActivity::class.java)
				.putExtra(EXTRA_IDS, mangaIds)
				.putExtra(EXTRA_SOURCE, sourceName)
	}
}
