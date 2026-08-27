/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.voicescatalog

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

/** Gender of a human guidance voice, as reported by the content-store voice parameters. */
enum class VoiceGender {
    Male,
    Female,
}

/** One downloadable human guidance voice. */
data class CatalogVoiceUi(
    /** Content-store item id, the key used for progress/state lookups and actions. */
    val id: Long,
    /** Speaker display name ("Bianca"). */
    val name: String,
    /** Gender badge shown next to the name, or null when unreported. */
    val gender: VoiceGender?,
    /** Language display name, preferring the native/endonym spelling ("Deutsch"). */
    val languageName: String,
    /** Flag of the voice's country, or null when unavailable. */
    val flag: Bitmap?,
    /** Pre-formatted total size ("12 MB"). */
    val sizeText: String,
    /** Size in bytes, used to aggregate group totals. */
    val sizeBytes: Long,
    /** True when the SDK allows deleting the local content of this voice. */
    val canDelete: Boolean,
    /**
     * True for a voice that must never be deleted (a critical engine resource such as
     * the built-in fallback voice). Unlike [canDelete] — a snapshot of the *current*
     * local content, false for a store item not yet downloaded — this is a stable
     * property of the voice, so the online rows can rely on it after a download.
     */
    val isProtected: Boolean = false,
)

/** One language offered by the device Text-to-Speech engine, a row of the expanded TTS item. */
data class CatalogTtsLanguageUi(
    /** Engine language code ("eng-IRL"), the key used for selection and to apply the language. */
    val code: String,
    /** Language display name ("English"). */
    val languageName: String,
    /** Country display name ("Ireland"), empty when unknown. */
    val countryName: String,
)

/** All the voices of one language/country pair, one expandable row of the voices lists. */
data class CatalogVoiceGroupUi(
    /** Stable group key ("countryCode|languageCode"), used for expansion tracking. */
    val key: String,
    /** ISO country code anchoring the group to a continent and its flag. */
    val countryCode: String,
    /** Group title: the language display name, falling back to the country name. */
    val languageName: String,
    /** Pre-formatted description line ("Netherlands · 3 voices"). */
    val detailText: String,
    /** Pre-formatted total size of the group ("36 MB"). */
    val sizeText: String,
    /** The group's voices. */
    val voices: List<CatalogVoiceUi>,
) {
    /** Flag of the group's country, taken from its first voice. */
    val flag: Bitmap? get() = voices.firstOrNull()?.flag
}

/** All the voice groups of one continent, as shown by the online voices catalog. */
data class CatalogVoiceContinentUi(
    /** Continent display name, see [ContinentMapper]. */
    val name: String,
    /** Pre-formatted country count line ("12 countries"). */
    val countriesText: String,
    /** Pre-formatted voice count line ("31 voices"). */
    val voicesText: String,
    /** The continent's voice groups. */
    val groups: List<CatalogVoiceGroupUi>,
)
