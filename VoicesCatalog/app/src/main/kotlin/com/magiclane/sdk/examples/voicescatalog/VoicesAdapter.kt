/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.voicescatalog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.imageview.ShapeableImageView

/** Item- and group-level callbacks of the voices rows, implemented by the activity. */
interface VoiceRowCallbacks {
    fun onContinentToggle(name: String)
    fun onGroupToggle(groupKey: String)
    fun onTtsToggle()
    fun onSelectTtsLanguage(code: String)
    fun onSelectVoice(voiceId: Long)
    fun onDownload(voiceId: Long)
    fun onPause(voiceId: Long)
    fun onDeleteRequest(voiceId: Long, title: String)
    fun onDownloadAll(groupKey: String)
    fun onPauseAll(groupKey: String)
    fun onResumeAll(groupKey: String)
    fun onDeleteAllRequest(groupKey: String, title: String)
    fun onDeleteAllLocalRequest()
}

/**
 * Renders the flat [VoiceRow] list. Rows carry their own card-background slot and
 * spacing; the list is diffed by row key so a progress tick only rebinds the affected
 * row (the views analog of the Compose per-row recomposition).
 */
class VoicesAdapter(
    private val callbacks: VoiceRowCallbacks,
) : ListAdapter<VoiceRow, VoicesAdapter.RowHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is VoiceRow.ContinentHeader -> TYPE_CONTINENT
        is VoiceRow.GroupHeader -> TYPE_GROUP_HEADER
        is VoiceRow.GroupActions -> TYPE_GROUP_ACTIONS
        is VoiceRow.Voice -> TYPE_VOICE
        is VoiceRow.Summary -> TYPE_SUMMARY
        is VoiceRow.TtsHeader -> TYPE_TTS_HEADER
        is VoiceRow.TtsLanguage -> TYPE_TTS_LANGUAGE
        is VoiceRow.SectionHeader -> TYPE_SECTION_HEADER
        is VoiceRow.Message -> TYPE_MESSAGE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
        val inflater = LayoutInflater.from(parent.context)
        val layout = when (viewType) {
            TYPE_CONTINENT -> R.layout.row_continent_header
            TYPE_GROUP_HEADER -> R.layout.row_voice_group_header
            TYPE_GROUP_ACTIONS -> R.layout.row_group_actions
            TYPE_VOICE -> R.layout.row_voice
            TYPE_SUMMARY -> R.layout.row_voices_summary_card
            TYPE_TTS_HEADER -> R.layout.row_tts_header
            TYPE_TTS_LANGUAGE -> R.layout.row_tts_language
            TYPE_SECTION_HEADER -> R.layout.row_section_header
            else -> R.layout.row_message
        }
        return RowHolder(inflater.inflate(layout, parent, false), callbacks)
    }

    override fun onBindViewHolder(holder: RowHolder, position: Int) = holder.bind(getItem(position))

    /** True when the row at [position] offers swipe-to-delete. */
    fun isSwipeable(position: Int): Boolean = (getItem(position) as? VoiceRow.Voice)?.swipeEnabled == true

    /** Routes a released swipe of the row at [position] to the delete confirmation. */
    fun onSwipeDeleteRequested(position: Int) {
        (getItem(position) as? VoiceRow.Voice)?.let { row ->
            callbacks.onDeleteRequest(row.voiceId, row.name)
        }
    }

    class RowHolder(itemView: View, private val callbacks: VoiceRowCallbacks) : RecyclerView.ViewHolder(itemView) {

        private val primaryColor = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorPrimary)
        private val errorColor = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorError)
        private val onSurfaceVariantColor =
            MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurfaceVariant)
        private val pausedColor = itemView.context.getColor(R.color.catalog_paused)
        private val completedColor = itemView.context.getColor(R.color.catalog_completed)
        private val maleColor = itemView.context.getColor(R.color.voice_male)
        private val femaleColor = itemView.context.getColor(R.color.voice_female)

        fun bind(row: VoiceRow) {
            applyCardBackground(row)
            when (row) {
                is VoiceRow.ContinentHeader -> bindContinent(row)
                is VoiceRow.GroupHeader -> bindGroupHeader(row)
                is VoiceRow.GroupActions -> bindGroupActions(row)
                is VoiceRow.Voice -> bindVoice(row)
                is VoiceRow.Summary -> bindSummary(row)
                is VoiceRow.TtsHeader -> bindTtsHeader(row)
                is VoiceRow.TtsLanguage -> bindTtsLanguage(row)
                is VoiceRow.SectionHeader -> bindSectionHeader(row)
                is VoiceRow.Message -> bindMessage(row)
            }
        }

        private fun applyCardBackground(row: VoiceRow) {
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

        private fun bindContinent(row: VoiceRow.ContinentHeader) {
            itemView.findViewById<ImageView>(R.id.continent_globe).setImageResource(row.globeRes)
            itemView.findViewById<TextView>(R.id.continent_name).text = row.name
            itemView.findViewById<TextView>(R.id.continent_countries).text = row.countriesText
            itemView.findViewById<TextView>(R.id.continent_detail).text = row.detailText
            bindChevron(itemView.findViewById(R.id.continent_chevron), row.expanded)
            itemView.setOnClickListener { callbacks.onContinentToggle(row.name) }
        }

        private fun bindGroupHeader(row: VoiceRow.GroupHeader) {
            itemView.findViewById<View>(R.id.row_divider).isVisible = row.showDivider
            bindFlag(itemView.findViewById(R.id.group_flag), row.flag)
            itemView.findViewById<ImageView>(R.id.group_selected_badge).isVisible = row.selected
            itemView.findViewById<TextView>(R.id.group_name).text = row.name
            itemView.findViewById<TextView>(R.id.group_detail).text = row.detailText
            itemView.findViewById<TextView>(R.id.group_size).text = row.sizeText
            val badge = itemView.findViewById<TextView>(R.id.group_badge)
            badge.isVisible = row.badgeCount > 0 && !row.expanded
            badge.text = row.badgeCount.toString()
            bindChevron(itemView.findViewById(R.id.group_chevron), row.expanded)
            itemView.setOnClickListener { callbacks.onGroupToggle(row.groupKey) }
        }

        private fun bindGroupActions(row: VoiceRow.GroupActions) {
            val container = itemView.findViewById<LinearLayout>(R.id.group_actions_container)
            val primary = itemView.findViewById<LinearLayout>(R.id.action_primary)
            val primaryIcon = itemView.findViewById<ImageView>(R.id.action_primary_icon)
            val primaryText = itemView.findViewById<TextView>(R.id.action_primary_text)
            val deleteAll = itemView.findViewById<LinearLayout>(R.id.action_delete_all)
            val spacer = itemView.findViewById<View>(R.id.action_spacer)

            fun stylePrimary(textRes: Int, iconRes: Int, onClick: () -> Unit) {
                primary.isVisible = true
                primaryText.setText(textRes)
                primaryIcon.setImageResource(iconRes)
                primaryText.setTextColor(primaryColor)
                primaryIcon.setColorFilter(primaryColor)
                primary.setOnClickListener { onClick() }
            }

            val showDelete: Boolean
            when {
                row.anyDownloading -> {
                    stylePrimary(R.string.pause_all, R.drawable.ml_pause_24) { callbacks.onPauseAll(row.groupKey) }
                    showDelete = false
                }

                row.anyPaused -> {
                    stylePrimary(R.string.resume_all, R.drawable.ml_play_24) { callbacks.onResumeAll(row.groupKey) }
                    showDelete = row.hasDeletable
                }

                else -> {
                    if (!row.allDownloaded) {
                        stylePrimary(R.string.download_all, R.drawable.ml_download_24) {
                            callbacks.onDownloadAll(row.groupKey)
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
                deleteAll.setOnClickListener { callbacks.onDeleteAllRequest(row.groupKey, row.deleteTitle) }
            }
        }

        private fun bindVoice(row: VoiceRow.Voice) {
            val divider = itemView.findViewById<View>(R.id.row_divider)
            divider.isVisible = row.showDivider
            (divider.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                params.marginStart =
                    ((if (row.showFlag) DIVIDER_INDENT_FLAG_DP else DIVIDER_INDENT_DP) * density()).toInt()
                divider.layoutParams = params
            }

            val flag = itemView.findViewById<ShapeableImageView>(R.id.voice_flag)
            flag.isVisible = row.showFlag
            if (row.showFlag) bindFlag(flag, row.flag)

            itemView.findViewById<TextView>(R.id.voice_name).text = row.name
            val genderBadge = itemView.findViewById<ImageView>(R.id.voice_gender)
            genderBadge.isVisible = row.gender != null
            row.gender?.let { gender ->
                val accent = if (gender == VoiceGender.Female) femaleColor else maleColor
                genderBadge.setImageResource(
                    if (gender == VoiceGender.Female) R.drawable.ml_female_24 else R.drawable.ml_male_24,
                )
                genderBadge.setColorFilter(accent)
                genderBadge.background.setTint(ColorUtils.setAlphaComponent(accent, BADGE_BACKGROUND_ALPHA))
                genderBadge.contentDescription = genderBadge.context.getString(
                    if (gender == VoiceGender.Female) R.string.voice_gender_female else R.string.voice_gender_male,
                )
            }

            val language = itemView.findViewById<TextView>(R.id.voice_language)
            language.isVisible = row.languageName.isNotEmpty()
            language.text = row.languageName
            bindStatusText(
                itemView.findViewById(R.id.voice_status),
                row.state,
                row.progress,
                row.sizeText,
                row.showOfflineWhenCompleted,
            )

            val action = itemView.findViewById<ItemActionButton>(R.id.voice_action)
            val delete = itemView.findViewById<ImageView>(R.id.voice_delete)
            val selected = itemView.findViewById<ImageView>(R.id.voice_selected)
            action.isVisible = row.trailing == VoiceTrailing.Action
            delete.isVisible = row.trailing == VoiceTrailing.Delete
            selected.isVisible = row.trailing == VoiceTrailing.SelectedCheck
            when (row.trailing) {
                VoiceTrailing.Action -> {
                    action.bind(row.state, row.progress)
                    action.onDownload = { callbacks.onDownload(row.voiceId) }
                    action.onPause = { callbacks.onPause(row.voiceId) }
                    action.onResume = { callbacks.onDownload(row.voiceId) }
                }

                VoiceTrailing.Delete -> {
                    delete.setColorFilter(errorColor)
                    delete.background.setTint(ColorUtils.setAlphaComponent(errorColor, BADGE_BACKGROUND_ALPHA))
                    delete.setOnClickListener { callbacks.onDeleteRequest(row.voiceId, row.name) }
                }

                VoiceTrailing.SelectedCheck -> {
                    selected.setColorFilter(primaryColor)
                    selected.background.setTint(ColorUtils.setAlphaComponent(primaryColor, BADGE_BACKGROUND_ALPHA))
                }

                VoiceTrailing.None -> Unit
            }

            itemView.setOnClickListener(
                if (row.selectable) View.OnClickListener { callbacks.onSelectVoice(row.voiceId) } else null,
            )
            itemView.isClickable = row.selectable
        }

        private fun bindSummary(row: VoiceRow.Summary) {
            itemView.findViewById<TextView>(R.id.summary_title).text = row.title
            itemView.findViewById<TextView>(R.id.summary_subtitle).text = row.subtitle
            itemView.findViewById<ImageView>(R.id.summary_icon)
                .background.setTint(ColorUtils.setAlphaComponent(onSurfaceVariantColor, SUMMARY_ICON_ALPHA))
            val delete = itemView.findViewById<ImageView>(R.id.summary_delete)
            delete.isVisible = row.showDeleteAll
            delete.setColorFilter(errorColor)
            delete.background.setTint(ColorUtils.setAlphaComponent(errorColor, BADGE_BACKGROUND_ALPHA))
            delete.setOnClickListener { callbacks.onDeleteAllLocalRequest() }
            itemView.findViewById<TextView>(R.id.selected_voice_name).text = row.selectedVoiceName
            val languageView = itemView.findViewById<TextView>(R.id.selected_voice_language)
            languageView.isVisible = row.selectedVoiceLanguage.isNotEmpty()
            languageView.text = row.selectedVoiceLanguage
        }

        private fun bindTtsHeader(row: VoiceRow.TtsHeader) {
            val chevron = itemView.findViewById<ImageView>(R.id.tts_chevron)
            chevron.isVisible = row.hasLanguages
            bindChevron(chevron, row.expanded)
            itemView.setOnClickListener { callbacks.onTtsToggle() }
        }

        private fun bindTtsLanguage(row: VoiceRow.TtsLanguage) {
            val divider = itemView.findViewById<View>(R.id.row_divider)
            divider.isVisible = row.showDivider
            (divider.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                params.marginStart = (row.indentDp * density()).toInt()
                divider.layoutParams = params
            }
            val content = itemView.findViewById<LinearLayout>(R.id.tts_language_content)
            content.updatePadding(left = (row.indentDp * density()).toInt())

            itemView.findViewById<TextView>(R.id.tts_language_name).text = row.languageName
            val country = itemView.findViewById<TextView>(R.id.tts_country_name)
            country.isVisible = row.countryName.isNotEmpty()
            country.text = row.countryName
            val selected = itemView.findViewById<ImageView>(R.id.tts_selected)
            selected.isVisible = row.selected
            if (row.selected) {
                selected.setColorFilter(primaryColor)
                selected.background.setTint(ColorUtils.setAlphaComponent(primaryColor, BADGE_BACKGROUND_ALPHA))
            }
            itemView.setOnClickListener { callbacks.onSelectTtsLanguage(row.code) }
        }

        private fun bindSectionHeader(row: VoiceRow.SectionHeader) {
            itemView.findViewById<TextView>(R.id.section_header_text).text = row.text
        }

        private fun bindMessage(row: VoiceRow.Message) {
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

        private fun density(): Float = itemView.resources.displayMetrics.density

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
                val padding = (FLAG_PLACEHOLDER_PADDING_DP * density()).toInt()
                flagView.setImageResource(R.drawable.ml_globe_24)
                flagView.setPadding(padding, padding, padding, padding)
                flagView.setColorFilter(onSurfaceVariantColor)
                flagView.setBackgroundResource(R.drawable.bg_circle)
                flagView.background.setTint(
                    MaterialColors.getColor(flagView, com.google.android.material.R.attr.colorSurfaceContainerHighest),
                )
            }
        }

        // Status line of a voice row: "Paused · 12 MB" when paused, "45% · 12 MB" while
        // downloading, "Offline · 12 MB" when completed (where requested), plain size
        // otherwise. Colored per state.
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
        const val TYPE_GROUP_HEADER = 1
        const val TYPE_GROUP_ACTIONS = 2
        const val TYPE_VOICE = 3
        const val TYPE_SUMMARY = 4
        const val TYPE_TTS_HEADER = 5
        const val TYPE_TTS_LANGUAGE = 6
        const val TYPE_SECTION_HEADER = 7
        const val TYPE_MESSAGE = 8

        const val CHEVRON_EXPANDED_DEGREES = 90f
        const val BADGE_BACKGROUND_ALPHA = 36 // 0.14 * 255
        const val SUMMARY_ICON_ALPHA = 20 // 0.08 * 255
        const val FLAG_PLACEHOLDER_PADDING_DP = 12f
        const val DIVIDER_INDENT_DP = 16f
        const val DIVIDER_INDENT_FLAG_DP = 62f

        val DIFF = object : DiffUtil.ItemCallback<VoiceRow>() {
            override fun areItemsTheSame(oldItem: VoiceRow, newItem: VoiceRow): Boolean = oldItem.key == newItem.key

            override fun areContentsTheSame(oldItem: VoiceRow, newItem: VoiceRow): Boolean = oldItem == newItem

            // A non-null payload lets the animator rebind in place instead of
            // cross-fading the row on every progress tick.
            override fun getChangePayload(oldItem: VoiceRow, newItem: VoiceRow): Any = Unit
        }
    }
}
