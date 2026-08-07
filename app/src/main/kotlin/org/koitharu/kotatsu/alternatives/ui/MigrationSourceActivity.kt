package org.koitharu.kotatsu.alternatives.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.ui.BaseActivity
import org.koitharu.kotatsu.core.ui.dialog.buildAlertDialog
import org.koitharu.kotatsu.core.util.ext.consumeAllSystemBarsInsets
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.systemBarsInsets
import org.koitharu.kotatsu.databinding.ActivityMigrationSourceBinding
import org.koitharu.kotatsu.databinding.ItemMigrationSourceBinding
import org.koitharu.kotatsu.parsers.model.MangaSource

/**
 * Full-screen source picker for migration. Lists enabled sources (preferred first), can also show
 * disabled sources to enable inline, and lets the user set a preferred source. When launched with
 * a set of manga ids ([newIntent] `mangaIds`) it runs a batch Migrate/Copy on selection; otherwise
 * it returns the chosen source name via [AppRouter.KEY_SOURCE] in the activity result.
 */
@AndroidEntryPoint
class MigrationSourceActivity : BaseActivity<ActivityMigrationSourceBinding>(), MenuProvider {

	private val viewModel by viewModels<MigrationSourceViewModel>()
	private val adapter = SourceAdapter()
	private var mangaIds: LongArray? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityMigrationSourceBinding.inflate(layoutInflater))
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		mangaIds = intent.getLongArrayExtra(EXTRA_IDS)
		viewBinding.recyclerView.setHasFixedSize(true)
		viewBinding.recyclerView.adapter = adapter
		addMenuProvider(this)
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

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_migration_source, menu)
	}

	override fun onPrepareMenu(menu: Menu) {
		menu.findItem(R.id.action_show_disabled)?.isChecked = viewModel.isShowingDisabled
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
		R.id.action_show_disabled -> {
			val newValue = !menuItem.isChecked
			menuItem.isChecked = newValue
			viewModel.setShowDisabled(newValue)
			true
		}

		else -> false
	}

	private fun onSelect(item: MigrationSourceItem) {
		if (!item.isEnabled) {
			viewModel.toggleEnabled(item.source, true) // enable the chosen source for convenience
		}
		val ids = mangaIds
		if (ids != null) {
			// proceed to the migration review list (match each, then Apply)
			router.openMigrationList(ids.toList(), item.source.name)
			finishAfterTransition()
		} else {
			setResult(RESULT_OK, Intent().putExtra(AppRouter.KEY_SOURCE, item.source.name))
			finishAfterTransition()
		}
	}

	private inner class SourceAdapter : RecyclerView.Adapter<SourceViewHolder>() {

		private val items = ArrayList<MigrationSourceItem>()

		fun submit(newItems: List<MigrationSourceItem>) {
			items.clear()
			items.addAll(newItems)
			notifyDataSetChanged()
		}

		override fun getItemCount() = items.size

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
			SourceViewHolder(ItemMigrationSourceBinding.inflate(layoutInflater, parent, false))

		override fun onBindViewHolder(holder: SourceViewHolder, position: Int) = holder.bind(items[position])
	}

	private inner class SourceViewHolder(
		private val binding: ItemMigrationSourceBinding,
	) : RecyclerView.ViewHolder(binding.root) {

		fun bind(item: MigrationSourceItem) {
			binding.textViewTitle.text = item.source.getTitle(binding.root.context)
			binding.textViewSubtitle.setText(if (item.isEnabled) R.string.enabled else R.string.disabled)
			binding.switchEnabled.isChecked = item.isEnabled
			binding.buttonPreferred.setImageResource(
				if (item.isPreferred) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off,
			)
			binding.root.setOnClickListener { onSelect(item) }
			binding.buttonPreferred.setOnClickListener { viewModel.setPreferred(item.source) }
			binding.switchEnabled.setOnClickListener { viewModel.toggleEnabled(item.source, !item.isEnabled) }
		}
	}

	companion object {

		private const val EXTRA_IDS = "ids"

		fun newIntent(context: Context, mangaIds: LongArray? = null): Intent =
			Intent(context, MigrationSourceActivity::class.java).apply {
				if (mangaIds != null) {
					putExtra(EXTRA_IDS, mangaIds)
				}
			}
	}
}
