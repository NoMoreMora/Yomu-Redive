package org.koitharu.kotatsu.alternatives.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.core.model.parcelable.ParcelableManga
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.ui.BaseActivity
import org.koitharu.kotatsu.core.util.ext.consumeAllSystemBarsInsets
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.systemBarsInsets
import org.koitharu.kotatsu.databinding.ActivitySourceSearchBinding
import org.koitharu.kotatsu.databinding.ItemSourceSearchBinding
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource

/**
 * Source-scoped search screen for the migration match-picker. Opens with the manga title pre-filled,
 * searches only the given source, and returns the tapped result as the chosen match (activity result).
 */
@AndroidEntryPoint
class SourceSearchActivity : BaseActivity<ActivitySourceSearchBinding>() {

	private val viewModel by viewModels<SourceSearchViewModel>()
	private val adapter = ResultAdapter()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivitySourceSearchBinding.inflate(layoutInflater))
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		viewBinding.recyclerView.setHasFixedSize(true)
		viewBinding.recyclerView.adapter = adapter
		with(viewBinding.searchView) {
			setQuery(viewModel.initialQuery, false)
			setOnQueryTextListener(object : SearchView.OnQueryTextListener {
				override fun onQueryTextSubmit(query: String?): Boolean {
					viewModel.search(query.orEmpty())
					clearFocus()
					return true
				}

				override fun onQueryTextChange(newText: String?): Boolean = false
			})
		}
		viewModel.content.observe(this) { adapter.submit(it) }
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

	private fun onSelect(manga: Manga) {
		setResult(RESULT_OK, Intent().putExtra(AppRouter.KEY_MANGA, ParcelableManga(manga)))
		finishAfterTransition()
	}

	private inner class ResultAdapter : RecyclerView.Adapter<ResultViewHolder>() {

		private val items = ArrayList<Manga>()

		fun submit(newItems: List<Manga>) {
			items.clear()
			items.addAll(newItems)
			notifyDataSetChanged()
		}

		override fun getItemCount() = items.size

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
			ResultViewHolder(ItemSourceSearchBinding.inflate(layoutInflater, parent, false))

		override fun onBindViewHolder(holder: ResultViewHolder, position: Int) = holder.bind(items[position])
	}

	private inner class ResultViewHolder(
		private val binding: ItemSourceSearchBinding,
	) : RecyclerView.ViewHolder(binding.root) {

		fun bind(manga: Manga) {
			binding.imageViewCover.setImageAsync(manga.coverUrl, manga)
			binding.textViewTitle.text = manga.title
			binding.root.setOnClickListener { onSelect(manga) }
		}
	}

	companion object {

		const val EXTRA_SOURCE = "source"
		const val EXTRA_QUERY = "query"

		fun newIntent(context: Context, source: MangaSource, query: String): Intent =
			Intent(context, SourceSearchActivity::class.java)
				.putExtra(EXTRA_SOURCE, source.name)
				.putExtra(EXTRA_QUERY, query)
	}
}
