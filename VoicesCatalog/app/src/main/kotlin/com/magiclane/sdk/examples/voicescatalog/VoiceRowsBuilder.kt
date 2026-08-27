/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.voicescatalog

import android.content.Context

/**
 * Builds the flat [VoiceRow] list of the visible tab from the ViewModel state and the
 * activity's expansion state — the views analog of the Compose `OnlineVoicesTab` /
 * `OfflineVoicesTab` screens: the whole list is recomputed on every change and diffed
 * by the adapter.
 */
class VoiceRowsBuilder(private val context: Context, private val viewModel: VoicesCatalogViewModel) {

    /** Continent names expanded on the online tab. */
    val expandedContinents = mutableSetOf<String>()

    /** Group keys expanded on the online tab. */
    val expandedOnlineGroups = mutableSetOf<String>()

    /** Group keys expanded on the offline tab. */
    val expandedOfflineGroups = mutableSetOf<String>()

    /** Whether the offline Text-to-Speech item is expanded. */
    var ttsExpanded = false

    // region online tab

    /**
     * Online voices rows: continent sections with expandable language groups when
     * browsing, or a flat voice card while searching. Connection, loading and failure
     * states replace the list.
     */
    fun buildOnlineRows(): List<VoiceRow> = when {
        !viewModel.isOnline && viewModel.onlineStatus != CatalogStatus.Ready -> listOf(
            VoiceRow.Message(
                key = "on:no-connection",
                text = context.getString(R.string.no_internet_connection),
                iconRes = R.drawable.ml_wifi_off_24,
                iconTint = MessageTint.Error,
                showProgress = false,
            ),
        )

        viewModel.onlineStatus == CatalogStatus.Loading -> listOf(
            VoiceRow.Message(
                key = "on:loading",
                text = "",
                iconRes = null,
                iconTint = MessageTint.Muted,
                showProgress = true,
            ),
        )

        viewModel.onlineStatus == CatalogStatus.Failed -> listOf(
            VoiceRow.Message(
                key = "on:failed",
                text = context.getString(R.string.content_store_failed),
                iconRes = R.drawable.ml_error_24,
                iconTint = MessageTint.Error,
                showProgress = false,
            ),
        )

        viewModel.query.isNotEmpty() -> buildOnlineSearchRows()

        else -> buildOnlineContinentRows()
    }

    private fun buildOnlineContinentRows(): List<VoiceRow> {
        val rows = mutableListOf<VoiceRow>()
        for (continent in viewModel.onlineContinents) {
            val expanded = continent.name in expandedContinents
            val card = mutableListOf<VoiceRow>()
            card += VoiceRow.ContinentHeader(
                key = "on:continent:${continent.name}",
                cardPos = CardPos.Middle,
                topSpacingDp = 0,
                name = continent.name,
                countriesText = continent.countriesText,
                detailText = continent.voicesText,
                globeRes = continentGlobeRes(continent.name),
                expanded = expanded,
            )
            if (expanded) {
                continent.groups.forEach { group -> card += onlineGroupRows(group) }
            }
            rows.appendCard(card, spacingDp = CARD_SPACING_ONLINE_DP)
        }
        return rows
    }

    private fun buildOnlineSearchRows(): List<VoiceRow> {
        val groups = viewModel.filterGroups(viewModel.onlineGroups)
        if (groups.isEmpty()) {
            return listOf(
                VoiceRow.Message(
                    key = "on:no-results",
                    text = context.getString(R.string.no_search_results),
                    iconRes = null,
                    iconTint = MessageTint.Muted,
                    showProgress = false,
                ),
            )
        }

        // Each matching country/language group keeps its header (the country context a
        // flat voice list would lose), with its matching voices always expanded below.
        val rows = mutableListOf<VoiceRow>()
        for (group in groups) {
            val states = group.voices.map { viewModel.stateOf(it.id) }
            val card = mutableListOf<VoiceRow>()
            card += VoiceRow.GroupHeader(
                key = "on:group:${group.key}",
                cardPos = CardPos.Middle,
                topSpacingDp = 0,
                groupKey = group.key,
                name = group.languageName,
                detailText = group.detailText,
                sizeText = group.sizeText,
                flag = group.flag,
                expanded = true,
                badgeCount = states.count { it != CatalogItemState.Idle },
                selected = false,
                showDivider = false,
            )
            card += VoiceRow.GroupActions(
                key = "on:actions:${group.key}",
                cardPos = CardPos.Middle,
                topSpacingDp = 0,
                groupKey = group.key,
                anyDownloading = states.any { it == CatalogItemState.Downloading },
                anyPaused = states.any { it == CatalogItemState.Paused },
                allDownloaded = states.all { it == CatalogItemState.Completed },
                hasDeletable = group.voices.any { isDeletable(it) },
                deleteTitle = group.languageName,
            )
            group.voices.forEach { voice -> card += onlineVoiceRow(voice, showFlag = false, showDivider = true) }
            rows.appendCard(card, spacingDp = CARD_SPACING_ONLINE_DP)
        }
        return rows
    }

    // One expandable language group of the online list: header, bulk actions, voice rows.
    private fun onlineGroupRows(group: CatalogVoiceGroupUi): MutableList<VoiceRow> {
        val rows = mutableListOf<VoiceRow>()
        val expanded = group.key in expandedOnlineGroups
        val states = group.voices.map { viewModel.stateOf(it.id) }

        rows += VoiceRow.GroupHeader(
            key = "on:group:${group.key}",
            cardPos = CardPos.Middle,
            topSpacingDp = 0,
            groupKey = group.key,
            name = group.languageName,
            detailText = group.detailText,
            sizeText = group.sizeText,
            flag = group.flag,
            expanded = expanded,
            badgeCount = states.count { it != CatalogItemState.Idle },
            selected = false,
            showDivider = true,
        )
        if (expanded) {
            rows += VoiceRow.GroupActions(
                key = "on:actions:${group.key}",
                cardPos = CardPos.Middle,
                topSpacingDp = 0,
                groupKey = group.key,
                anyDownloading = states.any { it == CatalogItemState.Downloading },
                anyPaused = states.any { it == CatalogItemState.Paused },
                allDownloaded = states.all { it == CatalogItemState.Completed },
                hasDeletable = group.voices.any { isDeletable(it) },
                deleteTitle = group.languageName,
            )
            group.voices.forEach { voice -> rows += onlineVoiceRow(voice, showFlag = false, showDivider = true) }
        }
        return rows
    }

    // A voice with local content can be deleted — except the applied voice and a
    // protected one. Derived from the live download state, not the canDelete snapshot
    // taken at catalog load, before any download of this session.
    private fun isDeletable(voice: CatalogVoiceUi): Boolean = viewModel.stateOf(voice.id) != CatalogItemState.Idle &&
        !voice.isProtected &&
        voice.id != viewModel.selectedVoiceId

    // One voice of the online list: the download state machine, traded for a delete
    // button once downloaded (the applied voice shows the selected check instead, a
    // protected voice keeps the downloaded badge).
    private fun onlineVoiceRow(voice: CatalogVoiceUi, showFlag: Boolean, showDivider: Boolean): VoiceRow.Voice {
        val state = viewModel.stateOf(voice.id)
        val deletable = isDeletable(voice)
        return VoiceRow.Voice(
            key = "on:voice:${voice.id}",
            cardPos = CardPos.Middle,
            topSpacingDp = 0,
            voiceId = voice.id,
            name = voice.name,
            gender = voice.gender,
            languageName = voice.languageName,
            sizeText = voice.sizeText,
            state = state,
            progress = viewModel.progressOf(voice.id),
            trailing = when {
                state == CatalogItemState.Completed && voice.id == viewModel.selectedVoiceId ->
                    VoiceTrailing.SelectedCheck

                state == CatalogItemState.Completed && deletable -> VoiceTrailing.Delete

                else -> VoiceTrailing.Action
            },
            flag = voice.flag,
            showFlag = showFlag,
            selectable = false,
            swipeEnabled = deletable,
            showDivider = showDivider,
            showOfflineWhenCompleted = true,
        )
    }

    // endregion

    // region offline tab

    /**
     * Offline (downloaded) voices rows: the summary card with the applied-voice
     * indicator, the expandable Text-to-Speech item and the language groups; while a
     * search is active the matching engine languages and the matching voice groups
     * (headers always expanded) replace them.
     */
    fun buildOfflineRows(): List<VoiceRow> =
        if (viewModel.query.isNotEmpty()) buildOfflineSearchRows() else buildOfflineBrowseRows()

    private fun buildOfflineBrowseRows(): List<VoiceRow> {
        val rows = mutableListOf<VoiceRow>()
        val groups = viewModel.localGroups
        val hasLocalVoices = groups.isNotEmpty()

        if (hasLocalVoices) {
            val totalVoices = groups.sumOf { it.voices.size }
            val totalBytes = groups.sumOf { group -> group.voices.sumOf { it.sizeBytes } }
            // No delete-all button when it would have nothing to delete: every local
            // voice is either protected (Michael) or the applied one.
            val hasDeletableVoices = groups.any { group ->
                group.voices.any { it.canDelete && it.id != viewModel.selectedVoiceId }
            }
            rows.appendCard(
                mutableListOf(
                    VoiceRow.Summary(
                        key = "off:summary",
                        cardPos = CardPos.Middle,
                        topSpacingDp = 0,
                        title = context.resources.getQuantityString(R.plurals.voice_count, totalVoices, totalVoices),
                        subtitle = viewModel.formatSize(totalBytes),
                        selectedVoiceName = viewModel.selectedVoiceName,
                        selectedVoiceLanguage = viewModel.selectedVoiceLanguageText,
                        showDeleteAll = hasDeletableVoices,
                    ),
                ),
                spacingDp = CARD_SPACING_OFFLINE_DP,
            )
        }

        rows.appendCard(ttsCardRows(), spacingDp = CARD_SPACING_OFFLINE_DP)

        if (hasLocalVoices) {
            rows += VoiceRow.SectionHeader(
                key = "off:sec:voices",
                topSpacingDp = SECTION_HEADER_SPACING_DP,
                text = context.getString(R.string.human_voices).uppercase(),
            )
            val card = mutableListOf<VoiceRow>()
            groups.forEachIndexed { index, group ->
                card += offlineGroupRows(group, showDivider = index > 0)
            }
            rows.appendCard(card, spacingDp = CARD_SPACING_OFFLINE_DP)
        } else {
            rows += VoiceRow.Message(
                key = "off:empty",
                text = context.getString(R.string.no_offline_voices),
                iconRes = R.drawable.ml_voice_24,
                iconTint = MessageTint.Muted,
                showProgress = false,
            )
        }

        return rows
    }

    // The expandable Text-to-Speech item: header plus the device engine's languages.
    private fun ttsCardRows(): MutableList<VoiceRow> {
        val rows = mutableListOf<VoiceRow>()
        val languages = viewModel.ttsLanguages
        rows += VoiceRow.TtsHeader(
            key = "off:tts",
            cardPos = CardPos.Middle,
            topSpacingDp = 0,
            expanded = ttsExpanded,
            hasLanguages = languages.isNotEmpty(),
        )
        if (ttsExpanded && languages.isNotEmpty()) {
            for (language in languages) {
                rows += VoiceRow.TtsLanguage(
                    key = "off:tts:${language.code}",
                    cardPos = CardPos.Middle,
                    topSpacingDp = 0,
                    code = language.code,
                    languageName = language.languageName,
                    countryName = language.countryName,
                    selected = language.code == viewModel.selectedTtsCode,
                    indentDp = TTS_LANGUAGE_INDENT_DP,
                    showDivider = true,
                )
            }
        }
        return rows
    }

    // Expandable offline language group: header row, Delete All, one row per voice.
    private fun offlineGroupRows(group: CatalogVoiceGroupUi, showDivider: Boolean): MutableList<VoiceRow> {
        val rows = mutableListOf<VoiceRow>()
        val expanded = group.key in expandedOfflineGroups
        rows += VoiceRow.GroupHeader(
            key = "off:group:${group.key}",
            cardPos = CardPos.Middle,
            topSpacingDp = 0,
            groupKey = group.key,
            name = group.languageName,
            detailText = group.detailText,
            sizeText = group.sizeText,
            flag = group.flag,
            expanded = expanded,
            badgeCount = 0,
            selected = group.voices.any { it.id == viewModel.selectedVoiceId },
            showDivider = showDivider,
        )
        if (expanded) {
            if (group.voices.any { it.canDelete && it.id != viewModel.selectedVoiceId }) {
                rows += VoiceRow.GroupActions(
                    key = "off:actions:${group.key}",
                    cardPos = CardPos.Middle,
                    topSpacingDp = 0,
                    groupKey = group.key,
                    anyDownloading = false,
                    anyPaused = false,
                    allDownloaded = true,
                    hasDeletable = true,
                    deleteTitle = group.languageName,
                )
            }
            group.voices.forEach { voice ->
                rows += offlineVoiceRow(voice, showFlag = false, showDivider = true)
            }
        }
        return rows
    }

    // One downloaded voice: tapping the row applies it; the trailing slot shows the
    // selection check when applied, the delete button otherwise.
    private fun offlineVoiceRow(voice: CatalogVoiceUi, showFlag: Boolean, showDivider: Boolean): VoiceRow.Voice =
        VoiceRow.Voice(
            key = "off:voice:${voice.id}",
            cardPos = CardPos.Middle,
            topSpacingDp = 0,
            voiceId = voice.id,
            name = voice.name,
            gender = voice.gender,
            languageName = voice.languageName,
            sizeText = voice.sizeText,
            state = CatalogItemState.Idle,
            progress = 0,
            trailing = when {
                voice.id == viewModel.selectedVoiceId -> VoiceTrailing.SelectedCheck
                voice.canDelete -> VoiceTrailing.Delete
                else -> VoiceTrailing.None
            },
            flag = voice.flag,
            showFlag = showFlag,
            selectable = true,
            swipeEnabled = false,
            showDivider = showDivider,
            showOfflineWhenCompleted = false,
        )

    // Search results of the offline tab: the matching device engine languages, and the
    // matching voices grouped under their country/language headers (always expanded).
    private fun buildOfflineSearchRows(): List<VoiceRow> {
        val groups = viewModel.filterGroups(viewModel.localGroups)
        val ttsLanguages = viewModel.filterTtsLanguages()

        if (groups.isEmpty() && ttsLanguages.isEmpty()) {
            return listOf(
                VoiceRow.Message(
                    key = "off:no-results",
                    text = context.getString(R.string.no_search_results),
                    iconRes = null,
                    iconTint = MessageTint.Muted,
                    showProgress = false,
                ),
            )
        }

        val rows = mutableListOf<VoiceRow>()

        if (ttsLanguages.isNotEmpty()) {
            rows += VoiceRow.SectionHeader(
                key = "off:sec:tts",
                topSpacingDp = 0,
                text = context.getString(R.string.text_to_speech).uppercase(),
            )
            val card = ttsLanguages.mapIndexed { index, language ->
                VoiceRow.TtsLanguage(
                    key = "off:tts:${language.code}",
                    cardPos = CardPos.Middle,
                    topSpacingDp = 0,
                    code = language.code,
                    languageName = language.languageName,
                    countryName = language.countryName,
                    selected = language.code == viewModel.selectedTtsCode,
                    indentDp = SEARCH_RESULT_INDENT_DP,
                    showDivider = index > 0,
                )
            }
            rows.appendCard(card.toMutableList(), spacingDp = CARD_SPACING_OFFLINE_DP)
        }

        if (groups.isNotEmpty()) {
            rows += VoiceRow.SectionHeader(
                key = "off:sec:voices",
                topSpacingDp = if (rows.isEmpty()) 0 else CARD_SPACING_OFFLINE_DP,
                text = context.getString(R.string.human_voices).uppercase(),
            )
            for (group in groups) {
                val card = mutableListOf<VoiceRow>()
                card += VoiceRow.GroupHeader(
                    key = "off:group:${group.key}",
                    cardPos = CardPos.Middle,
                    topSpacingDp = 0,
                    groupKey = group.key,
                    name = group.languageName,
                    detailText = group.detailText,
                    sizeText = group.sizeText,
                    flag = group.flag,
                    expanded = true,
                    badgeCount = 0,
                    selected = group.voices.any { it.id == viewModel.selectedVoiceId },
                    showDivider = false,
                )
                if (group.voices.any { it.canDelete && it.id != viewModel.selectedVoiceId }) {
                    card += VoiceRow.GroupActions(
                        key = "off:actions:${group.key}",
                        cardPos = CardPos.Middle,
                        topSpacingDp = 0,
                        groupKey = group.key,
                        anyDownloading = false,
                        anyPaused = false,
                        allDownloaded = true,
                        hasDeletable = true,
                        deleteTitle = group.languageName,
                    )
                }
                group.voices.forEach { voice ->
                    card += offlineVoiceRow(voice, showFlag = false, showDivider = true)
                }
                rows.appendCard(card, spacingDp = CARD_SPACING_OFFLINE_DP)
            }
        }

        return rows
    }

    // endregion

    // Appends the rows of one visual card, fixing their card-background slots
    // (Single/Top/Middle/Bottom) and giving the card its spacing from the row above.
    private fun MutableList<VoiceRow>.appendCard(card: MutableList<VoiceRow>, spacingDp: Int) {
        if (card.isEmpty()) return
        val positioned = card.mapIndexed { index, row ->
            val pos = when {
                card.size == 1 -> CardPos.Single
                index == 0 -> CardPos.Top
                index == card.lastIndex -> CardPos.Bottom
                else -> CardPos.Middle
            }
            row.withCard(
                pos,
                if (index == 0 && isNotEmpty()) {
                    spacingDp
                } else if (index == 0) {
                    0
                } else {
                    row.topSpacingDp
                },
            )
        }
        addAll(positioned)
    }

    private fun VoiceRow.withCard(pos: CardPos, spacingDp: Int): VoiceRow = when (this) {
        is VoiceRow.ContinentHeader -> copy(cardPos = pos, topSpacingDp = spacingDp)
        is VoiceRow.GroupHeader -> copy(cardPos = pos, topSpacingDp = spacingDp)
        is VoiceRow.GroupActions -> copy(cardPos = pos, topSpacingDp = spacingDp)
        is VoiceRow.Voice -> copy(cardPos = pos, topSpacingDp = spacingDp)
        is VoiceRow.Summary -> copy(cardPos = pos, topSpacingDp = spacingDp)
        is VoiceRow.TtsHeader -> copy(cardPos = pos, topSpacingDp = spacingDp)
        is VoiceRow.TtsLanguage -> copy(cardPos = pos, topSpacingDp = spacingDp)
        is VoiceRow.SectionHeader, is VoiceRow.Message -> this
    }

    private companion object {
        const val CARD_SPACING_ONLINE_DP = 10
        const val CARD_SPACING_OFFLINE_DP = 8
        const val SECTION_HEADER_SPACING_DP = 16

        // Aligns the expanded TTS language rows with the header texts
        // (16dp padding + 30dp icon + 14dp gap).
        const val TTS_LANGUAGE_INDENT_DP = 60

        // Search-result TTS rows sit at the card edge.
        const val SEARCH_RESULT_INDENT_DP = 16
    }
}
