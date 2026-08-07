package org.koitharu.kotatsu.alternatives.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.exceptions.resolve.SnackbarErrorObserver
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.parcelable.ParcelableManga
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.ui.BaseActivity
import org.koitharu.kotatsu.core.ui.BaseListAdapter
import org.koitharu.kotatsu.core.ui.dialog.buildAlertDialog
import org.koitharu.kotatsu.core.ui.dialog.setEditText
import org.koitharu.kotatsu.core.ui.list.OnListItemClickListener
import org.koitharu.kotatsu.core.util.ext.consumeAllSystemBarsInsets
import org.koitharu.kotatsu.core.util.ext.getParcelableExtraCompat
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.core.util.ext.systemBarsInsets
import org.koitharu.kotatsu.databinding.ActivityAlternativesBinding
import org.koitharu.kotatsu.list.ui.adapter.ListItemType
import org.koitharu.kotatsu.list.ui.adapter.ListStateHolderListener
import org.koitharu.kotatsu.list.ui.adapter.TypedListSpacingDecoration
import org.koitharu.kotatsu.list.ui.adapter.buttonFooterAD
import org.koitharu.kotatsu.list.ui.adapter.emptyStateListAD
import org.koitharu.kotatsu.list.ui.adapter.loadingFooterAD
import org.koitharu.kotatsu.list.ui.adapter.loadingStateAD
import org.koitharu.kotatsu.list.ui.model.ListModel
import org.koitharu.kotatsu.parsers.model.Manga
import javax.inject.Inject

@AndroidEntryPoint
class AlternativesActivity : BaseActivity<ActivityAlternativesBinding>(),
	ListStateHolderListener,
	OnListItemClickListener<MangaAlternativeModel> {

	@Inject
	lateinit var coil: ImageLoader

	private val viewModel by viewModels<AlternativesViewModel>()

	private val sourcePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == RESULT_OK) {
			viewModel.setTargetSourceByName(result.data?.getStringExtra(AppRouter.KEY_SOURCE))
		}
	}

	private val isPickMode by lazy { intent.getBooleanExtra(EXTRA_PICK, false) }

	private val sourceSearchLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == RESULT_OK) {
			val picked = result.data?.getParcelableExtraCompat<ParcelableManga>(AppRouter.KEY_MANGA)?.manga
			if (picked != null) {
				if (isPickMode) returnPicked(picked) else confirmMigration(picked)
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityAlternativesBinding.inflate(layoutInflater))
		supportActionBar?.run {
			setDisplayHomeAsUpEnabled(true)
			subtitle = viewModel.manga.title
		}
		val listAdapter = BaseListAdapter<ListModel>()
			.addDelegate(
				ListItemType.MANGA_LIST_DETAILED,
				alternativeAD(coil, this, this, if (isPickMode) R.string.select else R.string.migrate),
			)
			.addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(null))
			.addDelegate(ListItemType.FOOTER_LOADING, loadingFooterAD())
			.addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
			.addDelegate(ListItemType.FOOTER_BUTTON, buttonFooterAD(this))
		with(viewBinding.recyclerView) {
			setHasFixedSize(true)
			addItemDecoration(TypedListSpacingDecoration(context, addHorizontalPadding = false))
			adapter = listAdapter
		}

		viewModel.onError.observeEvent(this, SnackbarErrorObserver(viewBinding.recyclerView, null))
		viewModel.list.observe(this, listAdapter)
		viewModel.onMigrated.observeEvent(this) {
			Toast.makeText(this, R.string.migration_completed, Toast.LENGTH_SHORT).show()
			router.openDetails(it)
			finishAfterTransition()
		}
		viewModel.targetSource.observe(this) { source ->
			supportActionBar?.subtitle = source?.getTitle(this) ?: viewModel.manga.title
		}
		addMenuProvider(object : MenuProvider {
			override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
				menuInflater.inflate(R.menu.opt_migration, menu)
			}

			override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
				R.id.action_search -> {
					showManualSearchDialog()
					true
				}

				R.id.action_source -> {
					showSourcePickerDialog()
					true
				}

				else -> false
			}
		})
	}

	override fun onApplyWindowInsets(
		v: View,
		insets: WindowInsetsCompat
	): WindowInsetsCompat {
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

	override fun onItemClick(item: MangaAlternativeModel, view: View) {
		when (view.id) {
			R.id.chip_source -> router.openSearch(item.manga.source, viewModel.manga.title)
			R.id.button_search -> sourceSearchLauncher.launch(
				SourceSearchActivity.newIntent(this, item.manga.source, viewModel.manga.title),
			)
			R.id.button_migrate -> if (isPickMode) returnPicked(item.manga) else confirmMigration(item.manga)
			else -> if (isPickMode) returnPicked(item.manga) else router.openDetails(item.manga)
		}
	}

	private fun returnPicked(manga: Manga) {
		setResult(RESULT_OK, Intent().putExtra(AppRouter.KEY_MANGA, ParcelableManga(manga)))
		finishAfterTransition()
	}

	override fun onRetryClick(error: Throwable) = viewModel.retry()

	override fun onEmptyActionClick() = Unit

	override fun onFooterButtonClick() = viewModel.continueSearch()

	private fun confirmMigration(target: Manga) {
		buildAlertDialog(this, isCentered = true) {
			setIcon(R.drawable.ic_replace)
			setTitle(R.string.manga_migration)
			setMessage(
				getString(
					R.string.migrate_confirmation,
					viewModel.manga.title,
					viewModel.manga.source.getTitle(context),
					target.title,
					target.source.getTitle(context),
				),
			)
			setNegativeButton(android.R.string.cancel, null)
			setNeutralButton(android.R.string.copy) { _, _ ->
				viewModel.copy(target)
			}
			setPositiveButton(R.string.migrate) { _, _ ->
				viewModel.migrate(target)
			}
		}.show()
	}

	private fun showManualSearchDialog() {
		buildAlertDialog(this) {
			setTitle(R.string.search_manually)
			val editText = setEditText(InputType.TYPE_CLASS_TEXT, singleLine = true)
			editText.setText(viewModel.manga.title)
			editText.setSelection(editText.text?.length ?: 0)
			setNegativeButton(android.R.string.cancel, null)
			setPositiveButton(R.string.search) { _, _ ->
				viewModel.manualSearch(editText.text?.toString().orEmpty())
			}
		}.show()
	}

	private fun showSourcePickerDialog() {
		sourcePickerLauncher.launch(MigrationSourceActivity.newIntent(this))
	}

	companion object {

		private const val EXTRA_PICK = "pick"

		fun newPickIntent(context: Context, manga: Manga): Intent =
			Intent(context, AlternativesActivity::class.java)
				.putExtra(AppRouter.KEY_MANGA, ParcelableManga(manga))
				.putExtra(EXTRA_PICK, true)
	}
}
