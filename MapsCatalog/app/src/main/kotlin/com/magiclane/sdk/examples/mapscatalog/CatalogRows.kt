/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapscatalog

import android.graphics.Bitmap
import androidx.annotation.DrawableRes

/**
 * How a row participates in the rounded "card" look: consecutive rows of one visual
 * card get [Top]/[Middle]/[Bottom] backgrounds, a standalone card gets [Single].
 */
enum class CardPos {
    Single,
    Top,
    Middle,
    Bottom,

    /** No card background at all (section headers, centered messages). */
    None,
}

/** Trailing control of a region row. */
enum class RegionTrailing {
    /** The download/pause/resume state machine ([ItemActionButton]). */
    Action,

    /** A delete button (offline rows). */
    Delete,

    /** Nothing (offline region of a map that cannot be deleted). */
    None,
}

/**
 * One row of the catalog list. The visible list is rebuilt from the ViewModel state on
 * every change and diffed by [key], the views analog of the Compose screens'
 * declarative rebuild.
 */
sealed class CatalogRow {
    /** Unique, stable identity of the row inside the current list. */
    abstract val key: String

    /** Card-background slot of the row. */
    abstract val cardPos: CardPos

    /** Extra spacing above the row (dp), the space between cards. */
    abstract val topSpacingDp: Int

    /** Expandable continent card header of the online catalog. */
    data class ContinentHeader(
        override val key: String,
        override val cardPos: CardPos,
        override val topSpacingDp: Int,
        val name: String,
        val countriesText: String,
        val detailText: String,
        @DrawableRes val globeRes: Int,
        val expanded: Boolean,
    ) : CatalogRow()

    /** Header row of a country that is split into regions. */
    data class CountryHeader(
        override val key: String,
        override val cardPos: CardPos,
        override val topSpacingDp: Int,
        val countryCode: String,
        val name: String,
        val detailText: String,
        val flag: Bitmap?,
        val expanded: Boolean,
        val badgeCount: Int,
        val showDivider: Boolean,
    ) : CatalogRow()

    /** Row of a country with a single map: flag, name, status line and action button. */
    data class SingleCountry(
        override val key: String,
        override val cardPos: CardPos,
        override val topSpacingDp: Int,
        val itemId: Long,
        val name: String,
        val deleteTitle: String,
        val flag: Bitmap?,
        val sizeText: String,
        val state: CatalogItemState,
        val progress: Int,
        val trailing: RegionTrailing,
        val downloadEnabled: Boolean,
        val deleteEnabled: Boolean,
        val swipeEnabled: Boolean,
        val showDivider: Boolean,
    ) : CatalogRow()

    /** Bulk-action row shown atop an expanded country's regions. */
    data class GroupActions(
        override val key: String,
        override val cardPos: CardPos,
        override val topSpacingDp: Int,
        val countryCode: String,
        val anyDownloading: Boolean,
        val anyPaused: Boolean,
        val allDownloaded: Boolean,
        val hasDeletable: Boolean,
        val downloadEnabled: Boolean,
        val deleteEnabled: Boolean,
        val deleteTitle: String,
    ) : CatalogRow()

    /** Region sub-item row of an expanded country. */
    data class Region(
        override val key: String,
        override val cardPos: CardPos,
        override val topSpacingDp: Int,
        val itemId: Long,
        val name: String,
        val sizeText: String,
        val state: CatalogItemState,
        val progress: Int,
        val trailing: RegionTrailing,
        val downloadEnabled: Boolean,
        val deleteEnabled: Boolean,
        val swipeEnabled: Boolean,
        val showOfflineWhenCompleted: Boolean,
    ) : CatalogRow()

    /** Offline-tab summary card: total map count and size with a delete-all button. */
    data class Summary(
        override val key: String,
        override val cardPos: CardPos,
        override val topSpacingDp: Int,
        val title: String,
        val subtitle: String,
        val deleteEnabled: Boolean,
    ) : CatalogRow()

    /** Card reporting the installed offline map version. Display only. */
    data class Version(
        override val key: String,
        override val cardPos: CardPos,
        override val topSpacingDp: Int,
        val text: String,
    ) : CatalogRow()

    /** Press-to-check entry point for the road-map update, with in-place progress. */
    data class Update(
        override val key: String,
        override val cardPos: CardPos,
        override val topSpacingDp: Int,
        val title: String,
        val clickable: Boolean,
        val showIndeterminate: Boolean,
        val showProgress: Boolean,
        val percent: Int,
        val showCancel: Boolean,
    ) : CatalogRow()

    /** Uppercase section header ("REGIONS" / "MAPS") above a card group. */
    data class SectionHeader(
        override val key: String,
        override val topSpacingDp: Int,
        val text: String,
    ) : CatalogRow() {
        override val cardPos: CardPos get() = CardPos.None
    }

    /** Centered icon + message row for empty, offline, loading and failure states. */
    data class Message(
        override val key: String,
        val text: String,
        @DrawableRes val iconRes: Int?,
        val iconTint: MessageTint,
        val showProgress: Boolean,
    ) : CatalogRow() {
        override val cardPos: CardPos get() = CardPos.None
        override val topSpacingDp: Int get() = 0
    }
}

/** Tint of a [CatalogRow.Message] icon. */
enum class MessageTint { Error, Muted }

/** Returns the hemisphere globe drawable of [continentName], or the generic globe. */
@DrawableRes
fun continentGlobeRes(continentName: String): Int = when (continentName) {
    "Europe", "Africa" -> R.drawable.ml_globe_europe_africa_24
    "North America", "South America" -> R.drawable.ml_globe_americas_24
    "Asia", "Oceania" -> R.drawable.ml_globe_asia_australia_24
    else -> R.drawable.ml_globe_24
}
