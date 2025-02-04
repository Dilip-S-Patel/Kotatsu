package org.koitharu.kotatsu.filter.ui.sheet

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.annotation.IdRes
import androidx.core.view.isGone
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentManager
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.GenericSortOrder
import org.koitharu.kotatsu.core.model.SortDirection
import org.koitharu.kotatsu.core.model.titleResId
import org.koitharu.kotatsu.core.ui.sheet.BaseAdaptiveSheet
import org.koitharu.kotatsu.core.ui.widgets.ChipsView
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.getDisplayName
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.parentView
import org.koitharu.kotatsu.core.util.ext.showDistinct
import org.koitharu.kotatsu.core.util.ext.textAndVisible
import org.koitharu.kotatsu.databinding.SheetFilterBinding
import org.koitharu.kotatsu.filter.ui.FilterOwner
import org.koitharu.kotatsu.filter.ui.model.FilterProperty
import org.koitharu.kotatsu.filter.ui.tags.TagsCatalogSheet
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import java.util.Locale
import com.google.android.material.R as materialR

class FilterSheetFragment : BaseAdaptiveSheet<SheetFilterBinding>(),
	AdapterView.OnItemSelectedListener,
	ChipsView.OnChipClickListener, MaterialButtonToggleGroup.OnButtonCheckedListener {

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): SheetFilterBinding {
		return SheetFilterBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: SheetFilterBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		if (dialog == null) {
			binding.layoutBody.updatePadding(top = binding.layoutBody.paddingBottom)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				binding.scrollView.scrollIndicators = 0
			}
		}
		val filter = requireFilter()
		filter.filterSortOrder.observe(viewLifecycleOwner, this::onSortOrderChanged)
		filter.filterSortDirection.observe(viewLifecycleOwner, this::onSortDirectionChanged)
		filter.filterLocale.observe(viewLifecycleOwner, this::onLocaleChanged)
		filter.filterTags.observe(viewLifecycleOwner, this::onTagsChanged)
		filter.filterTagsExcluded.observe(viewLifecycleOwner, this::onTagsExcludedChanged)
		filter.filterState.observe(viewLifecycleOwner, this::onStateChanged)
		filter.filterContentRating.observe(viewLifecycleOwner, this::onContentRatingChanged)

		binding.spinnerLocale.onItemSelectedListener = this
		binding.spinnerOrder.onItemSelectedListener = this
		binding.chipsState.onChipClickListener = this
		binding.chipsContentRating.onChipClickListener = this
		binding.chipsGenres.onChipClickListener = this
		binding.chipsGenresExclude.onChipClickListener = this
		binding.layoutSortDirection.addOnButtonCheckedListener(this)
	}

	override fun onButtonChecked(group: MaterialButtonToggleGroup?, checkedId: Int, isChecked: Boolean) {
		if (isChecked) {
			setSortDirection(getSortDirection(checkedId) ?: return)
		}
	}

	override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
		val filter = requireFilter()
		when (parent.id) {
			R.id.spinner_order -> {
				val genericOrder = filter.filterSortOrder.value.availableItems[position]
				val direction = getSortDirection(requireViewBinding().layoutSortDirection.checkedButtonId)
				filter.setSortOrder(genericOrder[direction ?: SortDirection.DESC])
			}

			R.id.spinner_locale -> filter.setLanguage(filter.filterLocale.value.availableItems[position])
		}
	}

	override fun onNothingSelected(parent: AdapterView<*>?) = Unit

	override fun onChipClick(chip: Chip, data: Any?) {
		val filter = requireFilter()
		when (data) {
			is MangaState -> filter.setState(data, !chip.isChecked)
			is MangaTag -> if (chip.parentView?.id == R.id.chips_genresExclude) {
				filter.setTagExcluded(data, !chip.isChecked)
			} else {
				filter.setTag(data, !chip.isChecked)
			}

			is ContentRating -> filter.setContentRating(data, !chip.isChecked)
			null -> TagsCatalogSheet.show(getChildFragmentManager(), chip.parentView?.id == R.id.chips_genresExclude)
		}
	}

	private fun onSortOrderChanged(value: FilterProperty<GenericSortOrder>) {
		val b = viewBinding ?: return
		b.textViewOrderTitle.isGone = value.isEmpty()
		b.cardOrder.isGone = value.isEmpty()
		if (value.isEmpty()) {
			return
		}
		val selected = value.selectedItems.single()
		b.spinnerOrder.adapter = ArrayAdapter(
			b.spinnerOrder.context,
			android.R.layout.simple_spinner_dropdown_item,
			android.R.id.text1,
			value.availableItems.map { b.spinnerOrder.context.getString(it.titleResId) },
		)
		val selectedIndex = value.availableItems.indexOf(selected)
		if (selectedIndex >= 0) {
			b.spinnerOrder.setSelection(selectedIndex, false)
		}
	}

	private fun onSortDirectionChanged(value: FilterProperty<SortDirection>) {
		val b = viewBinding ?: return
		b.layoutSortDirection.isGone = value.isEmpty()
		if (value.isEmpty()) {
			return
		}
		val selected = value.selectedItems.single()
		b.buttonOrderAsc.isEnabled = SortDirection.ASC in value.availableItems
		b.buttonOrderDesc.isEnabled = SortDirection.DESC in value.availableItems
		b.layoutSortDirection.removeOnButtonCheckedListener(this)
		b.layoutSortDirection.check(
			when (selected) {
				SortDirection.ASC -> R.id.button_order_asc
				SortDirection.DESC -> R.id.button_order_desc
			},
		)
		b.layoutSortDirection.addOnButtonCheckedListener(this)
	}

	private fun onLocaleChanged(value: FilterProperty<Locale?>) {
		val b = viewBinding ?: return
		b.textViewLocaleTitle.isGone = value.isEmpty()
		b.cardLocale.isGone = value.isEmpty()
		if (value.isEmpty()) {
			return
		}
		val selected = value.selectedItems.singleOrNull()
		b.spinnerLocale.adapter = ArrayAdapter(
			b.spinnerLocale.context,
			android.R.layout.simple_spinner_dropdown_item,
			android.R.id.text1,
			value.availableItems.map { it.getDisplayName(b.spinnerLocale.context) },
		)
		val selectedIndex = value.availableItems.indexOf(selected)
		if (selectedIndex >= 0) {
			b.spinnerLocale.setSelection(selectedIndex, false)
		}
	}

	private fun onTagsChanged(value: FilterProperty<MangaTag>) {
		val b = viewBinding ?: return
		b.textViewGenresTitle.isGone = value.isEmpty()
		b.chipsGenres.isGone = value.isEmpty()
		b.textViewGenresHint.textAndVisible = value.error?.getDisplayMessage(resources)
		if (value.isEmpty()) {
			return
		}
		val chips = ArrayList<ChipsView.ChipModel>(value.selectedItems.size + value.availableItems.size + 1)
		value.selectedItems.mapTo(chips) { tag ->
			ChipsView.ChipModel(
				title = tag.title,
				isChecked = true,
				data = tag,
			)
		}
		value.availableItems.mapNotNullTo(chips) { tag ->
			if (tag !in value.selectedItems) {
				ChipsView.ChipModel(
					title = tag.title,
					isChecked = false,
					data = tag,
				)
			} else {
				null
			}
		}
		chips.add(
			ChipsView.ChipModel(
				title = getString(R.string.more),
				icon = materialR.drawable.abc_ic_menu_overflow_material,
			),
		)
		b.chipsGenres.setChips(chips)
	}

	private fun onTagsExcludedChanged(value: FilterProperty<MangaTag>) {
		val b = viewBinding ?: return
		b.textViewGenresExcludeTitle.isGone = value.isEmpty()
		b.chipsGenresExclude.isGone = value.isEmpty()
		if (value.isEmpty()) {
			return
		}
		val chips = ArrayList<ChipsView.ChipModel>(value.selectedItems.size + value.availableItems.size + 1)
		value.selectedItems.mapTo(chips) { tag ->
			ChipsView.ChipModel(
				tint = 0,
				title = tag.title,
				icon = 0,
				isChecked = true,
				data = tag,
			)
		}
		value.availableItems.mapNotNullTo(chips) { tag ->
			if (tag !in value.selectedItems) {
				ChipsView.ChipModel(
					title = tag.title,
					isChecked = false,
					data = tag,
				)
			} else {
				null
			}
		}
		chips.add(
			ChipsView.ChipModel(
				title = getString(R.string.more),
				icon = materialR.drawable.abc_ic_menu_overflow_material,
			),
		)
		b.chipsGenresExclude.setChips(chips)
	}

	private fun onStateChanged(value: FilterProperty<MangaState>) {
		val b = viewBinding ?: return
		b.textViewStateTitle.isGone = value.isEmpty()
		b.chipsState.isGone = value.isEmpty()
		if (value.isEmpty()) {
			return
		}
		val chips = value.availableItems.map { state ->
			ChipsView.ChipModel(
				title = getString(state.titleResId),
				isChecked = state in value.selectedItems,
				data = state,
			)
		}
		b.chipsState.setChips(chips)
	}

	private fun onContentRatingChanged(value: FilterProperty<ContentRating>) {
		val b = viewBinding ?: return
		b.textViewContentRatingTitle.isGone = value.isEmpty()
		b.chipsContentRating.isGone = value.isEmpty()
		if (value.isEmpty()) {
			return
		}
		val chips = value.availableItems.map { contentRating ->
			ChipsView.ChipModel(
				title = getString(contentRating.titleResId),
				isChecked = contentRating in value.selectedItems,
				data = contentRating,
			)
		}
		b.chipsContentRating.setChips(chips)
	}

	private fun requireFilter() = (requireActivity() as FilterOwner).filter

	private fun setSortDirection(direction: SortDirection) {
		val filter = requireFilter()
		val currentOrder = filter.filterSortOrder.value.selectedItems.singleOrNull() ?: return
		val newOrder = currentOrder[direction]
		filter.setSortOrder(newOrder)
	}

	private fun getSortDirection(@IdRes buttonId: Int): SortDirection? = when (buttonId) {
		R.id.button_order_asc -> SortDirection.ASC
		R.id.button_order_desc -> SortDirection.DESC
		else -> null
	}

	companion object {

		private const val TAG = "FilterSheet"

		fun show(fm: FragmentManager) = FilterSheetFragment().showDistinct(fm, TAG)
	}
}
