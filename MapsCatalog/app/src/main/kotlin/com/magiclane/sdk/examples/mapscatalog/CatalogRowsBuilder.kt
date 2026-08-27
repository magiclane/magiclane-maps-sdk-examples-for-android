/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapscatalog

import android.content.Context

/**
 * Builds the flat [CatalogRow] list of the visible tab from the ViewModel state and the
 * activity's expansion state — the views analog of the Compose `OnlineMapsTab` /
 * `OfflineMapsTab` screens: the whole list is recomputed on every change and diffed
 * by the adapter.
 */
class CatalogRowsBuilder(private val context: Context, private val viewModel: MapsCatalogViewModel) {

    /** Continent names expanded on the online tab. */
    val expandedContinents = mutableSetOf<String>()

    /** Country codes expanded on the online tab. */
    val expandedOnlineCountries = mutableSetOf<String>()

    /** Country codes expanded on the offline tab. */
    val expandedOfflineCountries = mutableSetOf<String>()

    // region online tab

    /**
     * Online catalog rows: continent sections with expandable countries when browsing,
     * or a flat country-card list while searching. Connection, loading and failure
     * states replace the list.
     */
    fun buildOnlineRows(): List<CatalogRow> {
        val searching = viewModel.query.isNotEmpty()
        return when {
            !viewModel.isOnline && viewModel.onlineStatus != CatalogStatus.Ready -> listOf(
                CatalogRow.Message(
                    key = "on:no-connection",
                    text = context.getString(R.string.no_internet_connection),
                    iconRes = R.drawable.ml_wifi_off_24,
                    iconTint = MessageTint.Error,
                    showProgress = false,
                ),
            )

            viewModel.onlineStatus == CatalogStatus.Loading -> listOf(
                CatalogRow.Message(
                    key = "on:loading",
                    text = "",
                    iconRes = null,
                    iconTint = MessageTint.Muted,
                    showProgress = true,
                ),
            )

            viewModel.onlineStatus == CatalogStatus.Failed -> listOf(
                CatalogRow.Message(
                    key = "on:failed",
                    text = context.getString(R.string.content_store_failed),
                    iconRes = R.drawable.ml_error_24,
                    iconTint = MessageTint.Error,
                    showProgress = false,
                ),
            )

            searching -> buildOnlineSearchRows()

            else -> buildOnlineContinentRows()
        }
    }

    private fun buildOnlineContinentRows(): List<CatalogRow> {
        val rows = mutableListOf<CatalogRow>()
        for (continent in viewModel.onlineContinents) {
            val expanded = continent.name in expandedContinents
            val card = mutableListOf<CatalogRow>()
            card += CatalogRow.ContinentHeader(
                key = "on:continent:${continent.name}",
                cardPos = CardPos.Middle,
                topSpacingDp = 0,
                name = continent.name,
                countriesText = continent.countriesText,
                detailText = continent.mapsText,
                globeRes = continentGlobeRes(continent.name),
                expanded = expanded,
            )
            if (expanded) {
                continent.countries.forEach { country -> card += onlineCountryRows(country, showDivider = true) }
            }
            rows.appendCard(card, spacingDp = CARD_SPACING_ONLINE_DP)
        }
        return rows
    }

    private fun buildOnlineSearchRows(): List<CatalogRow> {
        val results = viewModel.filterCountries(viewModel.onlineCountries)
        if (results.isEmpty()) {
            return listOf(
                CatalogRow.Message(
                    key = "on:no-results",
                    text = context.getString(R.string.no_search_results),
                    iconRes = null,
                    iconTint = MessageTint.Muted,
                    showProgress = false,
                ),
            )
        }

        val rows = mutableListOf<CatalogRow>()
        for (country in results) {
            rows.appendCard(onlineCountryRows(country, showDivider = false), spacingDp = CARD_SPACING_ONLINE_DP)
        }
        return rows
    }

    // One country of the online list: a single swipe-to-delete row for single-map
    // countries, an expandable header with bulk actions and region rows otherwise.
    private fun onlineCountryRows(country: CatalogCountryUi, showDivider: Boolean): MutableList<CatalogRow> {
        val rows = mutableListOf<CatalogRow>()
        val downloadsEnabled = !viewModel.isUpdateActive
        val deletesEnabled = !viewModel.isUpdateActive

        if (!country.hasRegions) {
            val item = country.items.first()
            val state = viewModel.stateOf(item.id)
            rows += CatalogRow.SingleCountry(
                key = "on:item:${item.id}",
                cardPos = CardPos.Middle,
                topSpacingDp = 0,
                itemId = item.id,
                name = item.name,
                deleteTitle = country.name,
                flag = country.flag,
                sizeText = item.sizeText,
                state = state,
                progress = viewModel.progressOf(item.id),
                trailing = RegionTrailing.Action,
                downloadEnabled = downloadsEnabled,
                deleteEnabled = deletesEnabled,
                swipeEnabled = deletesEnabled && state != CatalogItemState.Idle,
                showDivider = showDivider,
            )
            return rows
        }

        val expanded = country.countryCode in expandedOnlineCountries
        val states = country.items.map { viewModel.stateOf(it.id) }
        rows += CatalogRow.CountryHeader(
            key = "on:country:${country.countryCode}",
            cardPos = CardPos.Middle,
            topSpacingDp = 0,
            countryCode = country.countryCode,
            name = country.name,
            detailText = country.detailText,
            flag = country.flag,
            expanded = expanded,
            badgeCount = states.count { it != CatalogItemState.Idle },
            showDivider = showDivider,
        )
        if (expanded) {
            rows += CatalogRow.GroupActions(
                key = "on:actions:${country.countryCode}",
                cardPos = CardPos.Middle,
                topSpacingDp = 0,
                countryCode = country.countryCode,
                anyDownloading = states.any { it == CatalogItemState.Downloading },
                anyPaused = states.any { it == CatalogItemState.Paused },
                allDownloaded = states.all { it == CatalogItemState.Completed },
                hasDeletable = states.any { it != CatalogItemState.Idle },
                downloadEnabled = downloadsEnabled,
                deleteEnabled = deletesEnabled,
                deleteTitle = country.name,
            )
            for (item in country.items) {
                val state = viewModel.stateOf(item.id)
                rows += CatalogRow.Region(
                    key = "on:item:${item.id}",
                    cardPos = CardPos.Middle,
                    topSpacingDp = 0,
                    itemId = item.id,
                    name = item.name,
                    sizeText = item.sizeText,
                    state = state,
                    progress = viewModel.progressOf(item.id),
                    trailing = RegionTrailing.Action,
                    downloadEnabled = downloadsEnabled,
                    deleteEnabled = deletesEnabled,
                    swipeEnabled = deletesEnabled && state != CatalogItemState.Idle,
                    showOfflineWhenCompleted = false,
                )
            }
        }
        return rows
    }

    // endregion

    // region offline tab

    /**
     * Offline (downloaded) maps rows: summary, map version and update cards followed by
     * the "Regions" (split countries) and "Maps" (single-map countries) sections; the
     * summary cards hide while a search is active, and an empty local list switches to
     * the empty-state message.
     */
    fun buildOfflineRows(): List<CatalogRow> =
        if (viewModel.localCountries.isEmpty()) buildOfflineEmptyRows() else buildOfflineContentRows()

    // Without local maps only the version/update cards remain, over the empty-state message.
    private fun buildOfflineEmptyRows(): List<CatalogRow> {
        val rows = mutableListOf<CatalogRow>()
        versionRow()?.let { rows.appendCard(mutableListOf(it), spacingDp = CARD_SPACING_OFFLINE_DP) }
        rows.appendCard(mutableListOf(updateRow()), spacingDp = CARD_SPACING_OFFLINE_DP)
        rows += CatalogRow.Message(
            key = "off:empty",
            text = context.getString(R.string.no_offline_maps),
            iconRes = R.drawable.ml_map_24,
            iconTint = MessageTint.Muted,
            showProgress = false,
        )
        return rows
    }

    private fun buildOfflineContentRows(): List<CatalogRow> {
        val rows = mutableListOf<CatalogRow>()
        val searching = viewModel.query.isNotEmpty()
        val deletesEnabled = !viewModel.isUpdateActive
        val countries = viewModel.filterCountries(viewModel.localCountries)

        if (!searching) {
            val totalItems = viewModel.localCountries.sumOf { it.items.size }
            val totalBytes = viewModel.localCountries.sumOf { country -> country.items.sumOf { it.sizeBytes } }
            rows.appendCard(
                mutableListOf(
                    CatalogRow.Summary(
                        key = "off:summary",
                        cardPos = CardPos.Middle,
                        topSpacingDp = 0,
                        title = context.resources.getQuantityString(R.plurals.map_count, totalItems, totalItems),
                        subtitle = viewModel.formatSize(totalBytes),
                        deleteEnabled = deletesEnabled,
                    ),
                ),
                spacingDp = CARD_SPACING_OFFLINE_DP,
            )
            versionRow()?.let { rows.appendCard(mutableListOf(it), spacingDp = CARD_SPACING_OFFLINE_DP) }
            rows.appendCard(mutableListOf(updateRow()), spacingDp = CARD_SPACING_OFFLINE_DP)
        } else if (countries.isEmpty()) {
            rows += CatalogRow.Message(
                key = "off:no-results",
                text = context.getString(R.string.no_search_results),
                iconRes = null,
                iconTint = MessageTint.Muted,
                showProgress = false,
            )
            return rows
        }

        val regionGroups = countries.filter { it.hasRegions }
        val mapGroups = countries.filter { !it.hasRegions }

        if (regionGroups.isNotEmpty()) {
            rows += CatalogRow.SectionHeader(
                key = "off:sec:regions",
                topSpacingDp = SECTION_HEADER_SPACING_DP,
                text = context.getString(R.string.regions).uppercase(),
            )
            val card = mutableListOf<CatalogRow>()
            regionGroups.forEachIndexed { index, country ->
                card += offlineRegionGroupRows(country, showDivider = index > 0, deletesEnabled = deletesEnabled)
            }
            rows.appendCard(card, spacingDp = CARD_SPACING_OFFLINE_DP)
        }

        if (mapGroups.isNotEmpty()) {
            rows += CatalogRow.SectionHeader(
                key = "off:sec:maps",
                topSpacingDp = SECTION_HEADER_SPACING_DP,
                text = context.getString(R.string.maps).uppercase(),
            )
            val card = mutableListOf<CatalogRow>()
            mapGroups.forEachIndexed { index, country ->
                val item = country.items.first()
                card += CatalogRow.SingleCountry(
                    key = "off:map:${item.id}",
                    cardPos = CardPos.Middle,
                    topSpacingDp = 0,
                    itemId = item.id,
                    name = item.name,
                    deleteTitle = item.name,
                    flag = country.flag,
                    sizeText = item.sizeText,
                    state = CatalogItemState.Idle,
                    progress = 0,
                    trailing = if (item.canDelete) RegionTrailing.Delete else RegionTrailing.None,
                    downloadEnabled = false,
                    deleteEnabled = deletesEnabled,
                    swipeEnabled = false,
                    showDivider = index > 0,
                )
            }
            rows.appendCard(card, spacingDp = CARD_SPACING_OFFLINE_DP)
        }

        return rows
    }

    // Expandable offline country with regions: header row, Delete All, one row per region.
    private fun offlineRegionGroupRows(
        country: CatalogCountryUi,
        showDivider: Boolean,
        deletesEnabled: Boolean,
    ): MutableList<CatalogRow> {
        val rows = mutableListOf<CatalogRow>()
        val expanded = country.countryCode in expandedOfflineCountries
        rows += CatalogRow.CountryHeader(
            key = "off:country:${country.countryCode}",
            cardPos = CardPos.Middle,
            topSpacingDp = 0,
            countryCode = country.countryCode,
            name = country.name,
            detailText = country.detailText,
            flag = country.flag,
            expanded = expanded,
            badgeCount = 0,
            showDivider = showDivider,
        )
        if (expanded) {
            rows += CatalogRow.GroupActions(
                key = "off:actions:${country.countryCode}",
                cardPos = CardPos.Middle,
                topSpacingDp = 0,
                countryCode = country.countryCode,
                anyDownloading = false,
                anyPaused = false,
                allDownloaded = true,
                hasDeletable = country.items.any { it.canDelete },
                downloadEnabled = false,
                deleteEnabled = deletesEnabled,
                deleteTitle = country.name,
            )
            for (item in country.items) {
                rows += CatalogRow.Region(
                    key = "off:item:${item.id}",
                    cardPos = CardPos.Middle,
                    topSpacingDp = 0,
                    itemId = item.id,
                    name = item.name,
                    sizeText = item.sizeText,
                    state = CatalogItemState.Idle,
                    progress = 0,
                    trailing = if (item.canDelete) RegionTrailing.Delete else RegionTrailing.None,
                    downloadEnabled = false,
                    deleteEnabled = deletesEnabled,
                    swipeEnabled = false,
                    showOfflineWhenCompleted = false,
                )
            }
        }
        return rows
    }

    private fun versionRow(): CatalogRow? {
        if (viewModel.mapsVersion.isEmpty()) return null
        return CatalogRow.Version(
            key = "off:version",
            cardPos = CardPos.Middle,
            topSpacingDp = 0,
            text = context.getString(R.string.map_version, viewModel.mapsVersion),
        )
    }

    // The map-update card, mirroring the maps-compose MapUpdateCard title/progress modes.
    private fun updateRow(): CatalogRow.Update {
        val mode = viewModel.updateMode
        val version = viewModel.availableUpdateVersion
        val title = when (mode) {
            MapUpdateCardMode.Checking -> context.getString(R.string.checking_for_map_update)
            MapUpdateCardMode.Applying -> context.getString(R.string.applying_map_update)

            MapUpdateCardMode.Updating ->
                // A resumed update can report progress before the server names the target
                // version; fall back to the generic title rather than "Updating maps to version ".
                if (version.isEmpty()) {
                    context.getString(R.string.updating_maps)
                } else {
                    context.getString(R.string.updating_to_version, version)
                }

            MapUpdateCardMode.WaitingConnection -> context.getString(R.string.waiting_for_internet_connection)

            MapUpdateCardMode.UpdateAvailable ->
                // The update card names the target version when the store already
                // reported it; a generic invitation otherwise.
                if (version.isEmpty()) {
                    context.getString(R.string.map_update_available)
                } else {
                    context.getString(R.string.update_maps_to_version, version)
                }

            MapUpdateCardMode.Check -> context.getString(R.string.check_for_map_update)
        }

        return CatalogRow.Update(
            key = "off:update",
            cardPos = CardPos.Middle,
            topSpacingDp = 0,
            title = title,
            clickable = mode != MapUpdateCardMode.Applying && mode != MapUpdateCardMode.Checking,
            showIndeterminate = mode == MapUpdateCardMode.Checking,
            showProgress = mode == MapUpdateCardMode.Updating || mode == MapUpdateCardMode.Applying,
            percent = if (mode == MapUpdateCardMode.Applying) PERCENT_COMPLETE else viewModel.updateProgress ?: 0,
            showCancel = mode == MapUpdateCardMode.Updating || mode == MapUpdateCardMode.WaitingConnection,
        )
    }

    // endregion

    // Appends the rows of one visual card, fixing their card-background slots
    // (Single/Top/Middle/Bottom) and giving the card its spacing from the row above.
    private fun MutableList<CatalogRow>.appendCard(card: MutableList<CatalogRow>, spacingDp: Int) {
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

    private fun CatalogRow.withCard(pos: CardPos, spacingDp: Int): CatalogRow = when (this) {
        is CatalogRow.ContinentHeader -> copy(cardPos = pos, topSpacingDp = spacingDp)
        is CatalogRow.CountryHeader -> copy(cardPos = pos, topSpacingDp = spacingDp)
        is CatalogRow.SingleCountry -> copy(cardPos = pos, topSpacingDp = spacingDp)
        is CatalogRow.GroupActions -> copy(cardPos = pos, topSpacingDp = spacingDp)
        is CatalogRow.Region -> copy(cardPos = pos, topSpacingDp = spacingDp)
        is CatalogRow.Summary -> copy(cardPos = pos, topSpacingDp = spacingDp)
        is CatalogRow.Version -> copy(cardPos = pos, topSpacingDp = spacingDp)
        is CatalogRow.Update -> copy(cardPos = pos, topSpacingDp = spacingDp)
        is CatalogRow.SectionHeader, is CatalogRow.Message -> this
    }

    private companion object {
        const val CARD_SPACING_ONLINE_DP = 10
        const val CARD_SPACING_OFFLINE_DP = 8
        const val SECTION_HEADER_SPACING_DP = 16
        const val PERCENT_COMPLETE = 100
    }
}
