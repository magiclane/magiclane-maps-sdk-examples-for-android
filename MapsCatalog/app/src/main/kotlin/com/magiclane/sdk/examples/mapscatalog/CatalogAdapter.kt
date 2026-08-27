/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapscatalog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.progressindicator.LinearProgressIndicator

/** Item- and group-level callbacks of the catalog rows, implemented by the activity. */
interface CatalogRowCallbacks {
    fun onContinentToggle(name: String)
    fun onCountryToggle(countryCode: String)
    fun onDownload(itemId: Long)
    fun onPause(itemId: Long)
    fun onResume(itemId: Long)
    fun onDeleteRequest(itemId: Long, title: String)
    fun onDownloadAll(countryCode: String)
    fun onPauseAll(countryCode: String)
    fun onResumeAll(countryCode: String)
    fun onDeleteAllRequest(countryCode: String, title: String)
    fun onUpdateCardTapped()
    fun onCancelUpdate()
    fun onDeleteAllLocalRequest()
}

/**
 * Renders the flat [CatalogRow] list. Rows carry their own card-background slot and
 * spacing; the list is diffed by row key so a progress tick only rebinds the affected
 * row (the views analog of the Compose per-row recomposition).
 */
class CatalogAdapter(
    private val callbacks: CatalogRowCallbacks,
) : ListAdapter<CatalogRow, CatalogAdapter.RowHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is CatalogRow.ContinentHeader -> TYPE_CONTINENT
        is CatalogRow.CountryHeader -> TYPE_COUNTRY_HEADER
        is CatalogRow.SingleCountry -> TYPE_SINGLE_COUNTRY
        is CatalogRow.GroupActions -> TYPE_GROUP_ACTIONS
        is CatalogRow.Region -> TYPE_REGION
        is CatalogRow.Summary -> TYPE_SUMMARY
        is CatalogRow.Version -> TYPE_VERSION
        is CatalogRow.Update -> TYPE_UPDATE
        is CatalogRow.SectionHeader -> TYPE_SECTION_HEADER
        is CatalogRow.Message -> TYPE_MESSAGE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
        val inflater = LayoutInflater.from(parent.context)
        val layout = when (viewType) {
            TYPE_CONTINENT -> R.layout.row_continent_header
            TYPE_COUNTRY_HEADER -> R.layout.row_country_header
            TYPE_SINGLE_COUNTRY -> R.layout.row_single_country
            TYPE_GROUP_ACTIONS -> R.layout.row_group_actions
            TYPE_REGION -> R.layout.row_region
            TYPE_SUMMARY -> R.layout.row_summary_card
            TYPE_VERSION -> R.layout.row_version_card
            TYPE_UPDATE -> R.layout.row_update_card
            TYPE_SECTION_HEADER -> R.layout.row_section_header
            else -> R.layout.row_message
        }
        return RowHolder(inflater.inflate(layout, parent, false), callbacks)
    }

    override fun onBindViewHolder(holder: RowHolder, position: Int) = holder.bind(getItem(position))

    /** True when the row at [position] offers swipe-to-delete. */
    fun isSwipeable(position: Int): Boolean = when (val row = getItem(position)) {
        is CatalogRow.SingleCountry -> row.swipeEnabled
        is CatalogRow.Region -> row.swipeEnabled
        else -> false
    }

    /** Routes a released swipe of the row at [position] to the delete confirmation. */
    fun onSwipeDeleteRequested(position: Int) {
        when (val row = getItem(position)) {
            is CatalogRow.SingleCountry -> callbacks.onDeleteRequest(row.itemId, row.deleteTitle)
            is CatalogRow.Region -> callbacks.onDeleteRequest(row.itemId, row.name)
            else -> Unit
        }
    }

    class RowHolder(itemView: View, private val callbacks: CatalogRowCallbacks) : RecyclerView.ViewHolder(itemView) {

        private val primaryColor = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorPrimary)
        private val errorColor = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorError)
        private val onSurfaceColor =
            MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurface)
        private val onSurfaceVariantColor =
            MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurfaceVariant)
        private val pausedColor = itemView.context.getColor(R.color.catalog_paused)
        private val completedColor = itemView.context.getColor(R.color.catalog_completed)
        private val disabledColor = ColorUtils.setAlphaComponent(onSurfaceColor, DISABLED_ALPHA)

        fun bind(row: CatalogRow) {
            applyCardBackground(row)
            when (row) {
                is CatalogRow.ContinentHeader -> bindContinent(row)
                is CatalogRow.CountryHeader -> bindCountryHeader(row)
                is CatalogRow.SingleCountry -> bindSingleCountry(row)
                is CatalogRow.GroupActions -> bindGroupActions(row)
                is CatalogRow.Region -> bindRegion(row)
                is CatalogRow.Summary -> bindSummary(row)
                is CatalogRow.Version -> bindVersion(row)
                is CatalogRow.Update -> bindUpdate(row)
                is CatalogRow.SectionHeader -> bindSectionHeader(row)
                is CatalogRow.Message -> bindMessage(row)
            }
        }

        private fun applyCardBackground(row: CatalogRow) {
            val background = when (row.cardPos) {
                CardPos.Single -> R.drawable.bg_card_single
                CardPos.Top -> R.drawable.bg_card_top
                CardPos.Middle -> R.drawable.bg_card_middle
                CardPos.Bottom -> R.drawable.bg_card_bottom
                CardPos.None -> 0
            }
            if (background != 0) itemView.setBackgroundResource(background) else itemView.background = null

            (itemView.layoutParams as? RecyclerView.LayoutParams)?.let { params ->
                params.topMargin = (row.topSpacingDp * itemView.resources.displayMetrics.density).toInt()
                itemView.layoutParams = params
            }
        }

        private fun bindContinent(row: CatalogRow.ContinentHeader) {
            itemView.findViewById<ImageView>(R.id.continent_globe).setImageResource(row.globeRes)
            itemView.findViewById<TextView>(R.id.continent_name).text = row.name
            itemView.findViewById<TextView>(R.id.continent_countries).text = row.countriesText
            itemView.findViewById<TextView>(R.id.continent_detail).text = row.detailText
            bindChevron(itemView.findViewById(R.id.continent_chevron), row.expanded)
            itemView.setOnClickListener { callbacks.onContinentToggle(row.name) }
        }

        private fun bindCountryHeader(row: CatalogRow.CountryHeader) {
            itemView.findViewById<View>(R.id.row_divider).isVisible = row.showDivider
            bindFlag(itemView.findViewById(R.id.country_flag), row.flag)
            itemView.findViewById<TextView>(R.id.country_name).text = row.name
            itemView.findViewById<TextView>(R.id.country_detail).text = row.detailText
            val badge = itemView.findViewById<TextView>(R.id.country_badge)
            badge.isVisible = row.badgeCount > 0 && !row.expanded
            badge.text = row.badgeCount.toString()
            bindChevron(itemView.findViewById(R.id.country_chevron), row.expanded)
            itemView.setOnClickListener { callbacks.onCountryToggle(row.countryCode) }
        }

        private fun bindSingleCountry(row: CatalogRow.SingleCountry) {
            itemView.findViewById<View>(R.id.row_divider).isVisible = row.showDivider
            bindFlag(itemView.findViewById(R.id.item_flag), row.flag)
            itemView.findViewById<TextView>(R.id.item_name).text = row.name
            bindStatusText(
                itemView.findViewById(R.id.item_status),
                row.state,
                row.progress,
                row.sizeText,
                showOfflineWhenCompleted = true,
            )
            val action = itemView.findViewById<ItemActionButton>(R.id.item_action)
            val delete = itemView.findViewById<ImageView>(R.id.item_delete)
            action.isVisible = row.trailing == RegionTrailing.Action
            delete.isVisible = row.trailing == RegionTrailing.Delete
            if (row.trailing == RegionTrailing.Action) {
                action.bind(row.state, row.progress, row.downloadEnabled)
                action.onDownload = { callbacks.onDownload(row.itemId) }
                action.onPause = { callbacks.onPause(row.itemId) }
                action.onResume = { callbacks.onResume(row.itemId) }
            }
            if (row.trailing == RegionTrailing.Delete) {
                bindDeleteButton(delete, row.deleteEnabled) { callbacks.onDeleteRequest(row.itemId, row.deleteTitle) }
            }
            itemView.setOnClickListener(null)
        }

        private fun bindGroupActions(row: CatalogRow.GroupActions) {
            val container = itemView.findViewById<LinearLayout>(R.id.group_actions_container)
            val primary = itemView.findViewById<LinearLayout>(R.id.action_primary)
            val primaryIcon = itemView.findViewById<ImageView>(R.id.action_primary_icon)
            val primaryText = itemView.findViewById<TextView>(R.id.action_primary_text)
            val deleteAll = itemView.findViewById<LinearLayout>(R.id.action_delete_all)
            val deleteAllIcon = itemView.findViewById<ImageView>(R.id.action_delete_all_icon)
            val deleteAllText = itemView.findViewById<TextView>(R.id.action_delete_all_text)
            val spacer = itemView.findViewById<View>(R.id.action_spacer)

            fun stylePrimary(textRes: Int, iconRes: Int, enabled: Boolean, onClick: () -> Unit) {
                primary.isVisible = true
                primaryText.setText(textRes)
                primaryIcon.setImageResource(iconRes)
                val color = if (enabled) primaryColor else disabledColor
                primaryText.setTextColor(color)
                primaryIcon.setColorFilter(color)
                primary.setOnClickListener { if (enabled) onClick() }
            }

            val showDelete: Boolean
            when {
                row.anyDownloading -> {
                    stylePrimary(R.string.pause_all, R.drawable.ml_pause_24, enabled = true) {
                        callbacks.onPauseAll(row.countryCode)
                    }
                    showDelete = false
                }

                row.anyPaused -> {
                    stylePrimary(R.string.resume_all, R.drawable.ml_play_24, row.downloadEnabled) {
                        callbacks.onResumeAll(row.countryCode)
                    }
                    showDelete = row.hasDeletable
                }

                else -> {
                    if (!row.allDownloaded) {
                        stylePrimary(R.string.download_all, R.drawable.ml_download_24, row.downloadEnabled) {
                            callbacks.onDownloadAll(row.countryCode)
                        }
                    } else {
                        primary.isVisible = false
                    }
                    showDelete = row.hasDeletable
                }
            }

            deleteAll.isVisible = showDelete
            spacer.isVisible = showDelete && primary.isVisible
            container.gravity = if (row.anyDownloading) {
                android.view.Gravity.CENTER
            } else {
                android.view.Gravity.CENTER_VERTICAL
            }
            if (showDelete) {
                val color = if (row.deleteEnabled) errorColor else disabledColor
                deleteAllText.setTextColor(color)
                deleteAllIcon.setColorFilter(color)
                deleteAll.setOnClickListener {
                    if (row.deleteEnabled) callbacks.onDeleteAllRequest(row.countryCode, row.deleteTitle)
                }
            }
        }

        private fun bindRegion(row: CatalogRow.Region) {
            itemView.findViewById<TextView>(R.id.region_name).text = row.name
            bindStatusText(
                itemView.findViewById(R.id.region_status),
                row.state,
                row.progress,
                row.sizeText,
                row.showOfflineWhenCompleted,
            )
            val action = itemView.findViewById<ItemActionButton>(R.id.region_action)
            val delete = itemView.findViewById<ImageView>(R.id.region_delete)
            action.isVisible = row.trailing == RegionTrailing.Action
            delete.isVisible = row.trailing == RegionTrailing.Delete
            if (row.trailing == RegionTrailing.Action) {
                action.bind(row.state, row.progress, row.downloadEnabled)
                action.onDownload = { callbacks.onDownload(row.itemId) }
                action.onPause = { callbacks.onPause(row.itemId) }
                action.onResume = { callbacks.onResume(row.itemId) }
            }
            if (row.trailing == RegionTrailing.Delete) {
                bindDeleteButton(delete, row.deleteEnabled) { callbacks.onDeleteRequest(row.itemId, row.name) }
            }
            itemView.setOnClickListener(null)
        }

        private fun bindSummary(row: CatalogRow.Summary) {
            itemView.findViewById<TextView>(R.id.summary_title).text = row.title
            itemView.findViewById<TextView>(R.id.summary_subtitle).text = row.subtitle
            itemView.findViewById<ImageView>(R.id.summary_icon)
                .background.setTint(ColorUtils.setAlphaComponent(onSurfaceVariantColor, SUMMARY_ICON_ALPHA))
            bindDeleteButton(itemView.findViewById(R.id.summary_delete), row.deleteEnabled) {
                callbacks.onDeleteAllLocalRequest()
            }
        }

        private fun bindVersion(row: CatalogRow.Version) {
            itemView.findViewById<TextView>(R.id.version_text).text = row.text
        }

        private fun bindUpdate(row: CatalogRow.Update) {
            itemView.findViewById<TextView>(R.id.update_title).text = row.title
            itemView.findViewById<LinearProgressIndicator>(R.id.update_indeterminate).isVisible = row.showIndeterminate
            itemView.findViewById<View>(R.id.update_progress_row).isVisible = row.showProgress
            if (row.showProgress) {
                itemView.findViewById<LinearProgressIndicator>(R.id.update_progress).progress = row.percent
                itemView.findViewById<TextView>(R.id.update_percent).text =
                    itemView.context.getString(R.string.progress_percent, row.percent)
            }
            val cancel = itemView.findViewById<View>(R.id.update_cancel)
            cancel.isVisible = row.showCancel
            cancel.setOnClickListener { callbacks.onCancelUpdate() }
            itemView.setOnClickListener(
                if (row.clickable) View.OnClickListener { callbacks.onUpdateCardTapped() } else null,
            )
            itemView.isClickable = row.clickable
        }

        private fun bindSectionHeader(row: CatalogRow.SectionHeader) {
            itemView.findViewById<TextView>(R.id.section_header_text).text = row.text
        }

        private fun bindMessage(row: CatalogRow.Message) {
            itemView.findViewById<ProgressBar>(R.id.message_progress).isVisible = row.showProgress
            val icon = itemView.findViewById<ImageView>(R.id.message_icon)
            icon.isVisible = row.iconRes != null
            row.iconRes?.let {
                icon.setImageResource(it)
                icon.setColorFilter(if (row.iconTint == MessageTint.Error) errorColor else onSurfaceVariantColor)
            }
            val text = itemView.findViewById<TextView>(R.id.message_text)
            text.isVisible = row.text.isNotEmpty()
            text.text = row.text
        }

        // region shared binders

        private fun bindChevron(chevron: ImageView, expanded: Boolean) {
            chevron.rotation = if (expanded) CHEVRON_EXPANDED_DEGREES else 0f
            chevron.contentDescription =
                chevron.context.getString(if (expanded) R.string.collapse else R.string.expand)
        }

        private fun bindFlag(flagView: ShapeableImageView, flag: android.graphics.Bitmap?) {
            if (flag != null) {
                flagView.setImageBitmap(flag)
                flagView.background = null
                flagView.clearColorFilter()
                flagView.setPadding(0, 0, 0, 0)
            } else {
                // Globe placeholder on the muted circle, like the Compose FlagCircle.
                val padding = (FLAG_PLACEHOLDER_PADDING_DP * flagView.resources.displayMetrics.density).toInt()
                flagView.setImageResource(R.drawable.ml_globe_24)
                flagView.setPadding(padding, padding, padding, padding)
                flagView.setColorFilter(onSurfaceVariantColor)
                flagView.setBackgroundResource(R.drawable.bg_circle)
                flagView.background.setTint(
                    MaterialColors.getColor(flagView, com.google.android.material.R.attr.colorSurfaceContainerHighest),
                )
            }
        }

        private fun bindDeleteButton(button: ImageView, enabled: Boolean, onClick: () -> Unit) {
            val accent = if (enabled) errorColor else disabledColor
            button.setColorFilter(accent)
            button.background.setTint(ColorUtils.setAlphaComponent(accent, BUTTON_BACKGROUND_ALPHA))
            button.setOnClickListener { if (enabled) onClick() }
        }

        // Status line of a catalog item row: "Paused · 154 MB" when paused, "45% · 154 MB"
        // while downloading, "Offline · 154 MB" when completed (where requested), plain
        // size otherwise. Colored per state.
        private fun bindStatusText(
            view: TextView,
            state: CatalogItemState,
            progress: Int,
            sizeText: String,
            showOfflineWhenCompleted: Boolean,
        ) {
            val context = view.context
            val (text, color) = when {
                state == CatalogItemState.Paused ->
                    "${context.getString(R.string.paused)} · $sizeText" to pausedColor

                state == CatalogItemState.Downloading -> "$progress% · $sizeText" to primaryColor

                state == CatalogItemState.Completed && showOfflineWhenCompleted ->
                    "${context.getString(R.string.offline)} · $sizeText" to completedColor

                else -> sizeText to onSurfaceVariantColor
            }
            view.text = text
            view.setTextColor(color)
        }

        // endregion
    }

    private companion object {
        const val TYPE_CONTINENT = 0
        const val TYPE_COUNTRY_HEADER = 1
        const val TYPE_SINGLE_COUNTRY = 2
        const val TYPE_GROUP_ACTIONS = 3
        const val TYPE_REGION = 4
        const val TYPE_SUMMARY = 5
        const val TYPE_VERSION = 6
        const val TYPE_UPDATE = 7
        const val TYPE_SECTION_HEADER = 8
        const val TYPE_MESSAGE = 9

        const val CHEVRON_EXPANDED_DEGREES = 90f
        const val DISABLED_ALPHA = 97 // 0.38 * 255
        const val BUTTON_BACKGROUND_ALPHA = 38 // 0.15 * 255
        const val SUMMARY_ICON_ALPHA = 20 // 0.08 * 255
        const val FLAG_PLACEHOLDER_PADDING_DP = 12f

        val DIFF = object : DiffUtil.ItemCallback<CatalogRow>() {
            override fun areItemsTheSame(oldItem: CatalogRow, newItem: CatalogRow): Boolean = oldItem.key == newItem.key

            override fun areContentsTheSame(oldItem: CatalogRow, newItem: CatalogRow): Boolean = oldItem == newItem

            // A non-null payload lets the animator rebind in place instead of
            // cross-fading the row on every progress tick.
            override fun getChangePayload(oldItem: CatalogRow, newItem: CatalogRow): Any = Unit
        }
    }
}
