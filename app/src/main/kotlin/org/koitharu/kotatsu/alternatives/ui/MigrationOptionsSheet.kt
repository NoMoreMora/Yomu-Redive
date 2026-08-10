package org.koitharu.kotatsu.alternatives.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.sheet.BaseAdaptiveSheet
import org.koitharu.kotatsu.core.util.ext.consume
import org.koitharu.kotatsu.databinding.SheetMigrationOptionsBinding

@AndroidEntryPoint
class MigrationOptionsSheet :
	BaseAdaptiveSheet<SheetMigrationOptionsBinding>(),
	CompoundButton.OnCheckedChangeListener {

	private val viewModel by viewModels<MigrationOptionsViewModel>()

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = SheetMigrationOptionsBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: SheetMigrationOptionsBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.switchChapters.isChecked = viewModel.migrateChapters
		binding.switchCategories.isChecked = viewModel.migrateCategories
		binding.switchCover.isChecked = viewModel.migrateCover
		binding.switchDeleteDownloads.isChecked = viewModel.deleteDownloads
		binding.switchHideUnmatched.isChecked = viewModel.hideWithoutMatch
		binding.switchHideNoNewer.isChecked = viewModel.hideWithoutNewerChapters
		binding.switchAdvanced.isChecked = viewModel.advancedSearch
		binding.switchByChapters.isChecked = viewModel.matchByChapterCount
		binding.editKeywords.setText(viewModel.extraKeywords)

		binding.switchChapters.setOnCheckedChangeListener(this)
		binding.switchCategories.setOnCheckedChangeListener(this)
		binding.switchCover.setOnCheckedChangeListener(this)
		binding.switchDeleteDownloads.setOnCheckedChangeListener(this)
		binding.switchHideUnmatched.setOnCheckedChangeListener(this)
		binding.switchHideNoNewer.setOnCheckedChangeListener(this)
		binding.switchAdvanced.setOnCheckedChangeListener(this)
		binding.switchByChapters.setOnCheckedChangeListener(this)
		binding.editKeywords.doAfterTextChanged {
			viewModel.extraKeywords = it?.toString().orEmpty()
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		viewBinding?.scrollView?.updatePadding(
			bottom = insets.getInsets(typeMask).bottom,
		)
		return insets.consume(v, typeMask, bottom = true)
	}

	override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
		when (buttonView.id) {
			R.id.switch_chapters -> viewModel.migrateChapters = isChecked
			R.id.switch_categories -> viewModel.migrateCategories = isChecked
			R.id.switch_cover -> viewModel.migrateCover = isChecked
			R.id.switch_delete_downloads -> viewModel.deleteDownloads = isChecked
			R.id.switch_hide_unmatched -> viewModel.hideWithoutMatch = isChecked
			R.id.switch_hide_no_newer -> viewModel.hideWithoutNewerChapters = isChecked
			R.id.switch_advanced -> viewModel.advancedSearch = isChecked
			R.id.switch_by_chapters -> viewModel.matchByChapterCount = isChecked
		}
	}
}
