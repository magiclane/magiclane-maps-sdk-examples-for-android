/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapscatalog

import android.graphics.Bitmap

/**
 * Download lifecycle of a content-store item, as shown by the catalog rows. The SDK's
 * queued/waiting states all map to [Downloading]: from the user's perspective the
 * transfer is in progress either way.
 */
enum class CatalogItemState {
    /** Not on the device and not being transferred. */
    Idle,

    /** Queued, waiting for network or actively transferring. */
    Downloading,

    /** Transfer stopped by the user, can be resumed. */
    Paused,

    /** Fully downloaded and usable offline. */
    Completed,
}

/** Loading status of the online catalog list. */
enum class CatalogStatus {
    /** The catalog request is running (or waiting for the SDK connection). */
    Loading,

    /** The catalog is loaded and displayed. */
    Ready,

    /** The catalog request failed. */
    Failed,
}

/** One downloadable map (a whole country or one region of a split country). */
data class CatalogItemUi(
    /** Content-store item id, the key used for progress/state lookups and actions. */
    val id: Long,
    /** Display name; for region items this is the bare region name ("Bavaria"). */
    val name: String,
    /** Pre-formatted total size ("154 MB"). */
    val sizeText: String,
    /** Size in bytes, used to aggregate group totals. */
    val sizeBytes: Long,
    /** True when the SDK allows deleting the local content of this item. */
    val canDelete: Boolean,
)

/** All the maps of one country: a single item, or one item per region for split countries. */
data class CatalogCountryUi(
    /** ISO country code of the country (first code reported by the SDK). */
    val countryCode: String,
    /** Country display name ("Germany"). */
    val name: String,
    /** Country flag, or null when unavailable. */
    val flag: Bitmap?,
    /** The country's items; more than one means the country is split into regions. */
    val items: List<CatalogItemUi>,
    /** Pre-formatted description line ("16 regions · 3.4 GB", or the size for single maps). */
    val detailText: String,
) {
    /** True when this country is split into multiple region maps. */
    val hasRegions: Boolean get() = items.size > 1
}

/** All the countries of one continent, as shown by the online catalog. */
data class CatalogContinentUi(
    /** Continent display name, see [ContinentMapper]. */
    val name: String,
    /** Pre-formatted country count line ("42 countries"). */
    val countriesText: String,
    /** Pre-formatted map count line ("120 maps"). */
    val mapsText: String,
    /** The continent's countries. */
    val countries: List<CatalogCountryUi>,
)

/** What the map-update card is currently saying. */
enum class MapUpdateCardMode {
    /** Idle entry point: tap to check for an update. */
    Check,

    /** A newer world map version is available: tap to start the update. */
    UpdateAvailable,

    /** The engine is checking whether an update exists; non-interactive. */
    Checking,

    /** The update is downloading, with progress. */
    Updating,

    /** A finished update is being applied by the engine; non-interactive. */
    Applying,

    /** The update is waiting for an internet connection. */
    WaitingConnection,
}
