package org.koitharu.kotatsu.alternatives.ui

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.parcelable.ParcelableManga
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.ui.BaseFragment
import org.koitharu.kotatsu.core.ui.BaseListAdapter
import org.koitharu.kotatsu.core.ui.dialog.buildAlertDialog
import org.koitharu.kotatsu.core.ui.dialog.setEditText
import org.koitharu.kotatsu.core.ui.list.OnListItemClickListener
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.databinding.FragmentAlternativesBinding
import org.koitharu.kotatsu.list.ui.adapter.ListItemType
import org.koitharu.kotatsu.list.ui.adapter.ListStateHolderListener
import org.koitharu.kotatsu.list.ui.adapter.TypedListSpacingDecoration
import org.koitharu.kotatsu.list.ui.adapter.buttonFooterAD
import org.koitharu.kotatsu.list.ui.adapter.emptyStateListAD
import org.koitharu.kotatsu.list.ui.adapter.loadingFooterAD
import org.koitharu.kotatsu.list.ui.adapter.loadingStateAD
import org.koitharu.kotatsu.list.ui.model.ListModel
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import javax.inject.Inject

/**
 * Embeddable variant of [AlternativesActivity]'s multi-source "Find similar" search, always in pick
 * mode. Hosted in the tablet Migration "List + sidebar" layout (design 4): picking a candidate is
 * reported back to the host via the Fragment Result API ([REQUEST_PICK]) so the host can set it as the
 * selected row's migration match. Reuses [AlternativesViewModel], seeded from the fragment arguments.
 */
@AndroidEntryPoint
class AlternativesFragment : BaseFragment<FragmentAlternativesBinding>(),
	ListStateHolderListener,
	OnListItemClickListener<MangaAlternativeModel> {

	@Inject
	lateinit var coil: ImageLoader

	private val viewModel by viewModels<AlternativesViewModel>()

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentAlternativesBinding =
		FragmentAlternativesBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: FragmentAlternativesBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		val listAdapter = BaseListAdapter<ListModel>()
			.addDelegate(
				ListItemType.MANGA_LIST_DETAILED,
				alternativeAD(coil, viewLifecycleOwner, this, R.string.select),
			)
			.addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(null))
			.addDelegate(ListItemType.FOOTER_LOADING, loadingFooterAD())
			.addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
			.addDelegate(ListItemType.FOOTER_BUTTON, buttonFooterAD(this))
		with(binding.recyclerView) {
			setHasFixedSize(true)
			addItemDecoration(TypedListSpacingDecoration(context, addHorizontalPadding = false))
			adapter = listAdapter
		}
		viewModel.list.observe(viewLifecycleOwner, listAdapter)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	override fun onItemClick(item: MangaAlternativeModel, view: View) {
		when (view.id) {
			R.id.chip_source -> router.openSearch(item.manga.source, viewModel.manga.title)
			R.id.button_search -> showManualSearch()
			else -> returnPicked(item.manga)
		}
	}

	override fun onRetryClick(error: Throwable) = viewModel.retry()

	override fun onEmptyActionClick() = Unit

	override fun onFooterButtonClick() = viewModel.continueSearch()

	/** Opens the same "search a different title" dialog used by [AlternativesActivity]. */
	fun showManualSearch() {
		buildAlertDialog(requireContext()) {
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

	/** Passthrough so the host can re-filter the search when the toolbar target source changes. */
	fun setTargetSourceByName(name: String?) {
		viewModel.setTargetSourceByName(name)
	}

	private fun returnPicked(manga: Manga) {
		setFragmentResult(REQUEST_PICK, bundleOf(AppRouter.KEY_MANGA to ParcelableManga(manga)))
	}

	companion object {

		const val REQUEST_PICK = "alternatives_pick"

		fun newInstance(manga: Manga, source: MangaSource?) = AlternativesFragment().apply {
			arguments = bundleOf(
				AppRouter.KEY_MANGA to ParcelableManga(manga),
				AppRouter.KEY_SOURCE to source?.name,
			)
		}
	}
}
