/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.voicescatalog

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

/** Trailing control of a voice row. */
enum class VoiceTrailing {
    /** The download/pause/resume state machine ([ItemActionButton]). */
    Action,

    /** A delete button. */
    Delete,

    /** The check badge marking the applied voice. */
    SelectedCheck,

    /** Nothing (a voice that is neither selected nor deletable). */
    None,
}

/**
 * One row of the voices list. The visible list is rebuilt from the ViewModel state on
 * every change and diffed by [key], the views analog of the Compose screens'
 * declarative rebuild.
 */
sealed class VoiceRow {
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
    ) : VoiceRow()

    /** Header row of a language/country voice group. */
    data class GroupHeader(
        override val key: String,
        override val cardPos: CardPos,
        override val topSpacingDp: Int,
        val groupKey: String,
        val name: String,
        val detailText: String,
        val sizeText: String,
        val flag: Bitmap?,
        val expanded: Boolean,
        val badgeCount: Int,
        val selected: Boolean,
        val showDivider: Boolean,
    ) : VoiceRow()

    /** Bulk-action row shown atop an expanded group's voices. */
    data class GroupActions(
        override val key: String,
        override val cardPos: CardPos,
        override val topSpacingDp: Int,
        val groupKey: String,
        val anyDownloading: Boolean,
        val anyPaused: Boolean,
        val allDownloaded: Boolean,
        val hasDeletable: Boolean,
        val deleteTitle: String,
    ) : VoiceRow()

    /** One voice: name with gender badge, language and status lines, trailing control. */
    data class Voice(
        override val key: String,
        override val cardPos: CardPos,
        override val topSpacingDp: Int,
        val voiceId: Long,
        val name: String,
        val gender: VoiceGender?,
        val languageName: String,
        val sizeText: String,
        val state: CatalogItemState,
        val progress: Int,
        val trailing: VoiceTrailing,
        val flag: Bitmap?,
        val showFlag: Boolean,
        val selectable: Boolean,
        val swipeEnabled: Boolean,
        val showDivider: Boolean,
        val showOfflineWhenCompleted: Boolean,
    ) : VoiceRow()

    /** Offline-tab summary card with the applied-voice indicator. */
    data class Summary(
        override val key: String,
        override val cardPos: CardPos,
        override val topSpacingDp: Int,
        val title: String,
        val subtitle: String,
        val selectedVoiceName: String,
        val selectedVoiceLanguage: String,
        val showDeleteAll: Boolean,
    ) : VoiceRow()

    /** Header of the expandable Text-to-Speech item. */
    data class TtsHeader(
        override val key: String,
        override val cardPos: CardPos,
        override val topSpacingDp: Int,
        val expanded: Boolean,
        val hasLanguages: Boolean,
    ) : VoiceRow()

    /** One language of the expanded Text-to-Speech item (or of the search results). */
    data class TtsLanguage(
        override val key: String,
        override val cardPos: CardPos,
        override val topSpacingDp: Int,
        val code: String,
        val languageName: String,
        val countryName: String,
        val selected: Boolean,
        val indentDp: Int,
        val showDivider: Boolean,
    ) : VoiceRow()

    /** Uppercase section header ("HUMAN VOICES" / "TEXT-TO-SPEECH") above a card group. */
    data class SectionHeader(
        override val key: String,
        override val topSpacingDp: Int,
        val text: String,
    ) : VoiceRow() {
        override val cardPos: CardPos get() = CardPos.None
    }

    /** Centered icon + message row for empty, offline, loading and failure states. */
    data class Message(
        override val key: String,
        val text: String,
        @DrawableRes val iconRes: Int?,
        val iconTint: MessageTint,
        val showProgress: Boolean,
    ) : VoiceRow() {
        override val cardPos: CardPos get() = CardPos.None
        override val topSpacingDp: Int get() = 0
    }
}

/** Tint of a [VoiceRow.Message] icon. */
enum class MessageTint { Error, Muted }

/** Returns the hemisphere globe drawable of [continentName], or the generic globe. */
@DrawableRes
fun continentGlobeRes(continentName: String): Int = when (continentName) {
    "Europe", "Africa" -> R.drawable.ml_globe_europe_africa_24
    "North America", "South America" -> R.drawable.ml_globe_americas_24
    "Asia", "Oceania" -> R.drawable.ml_globe_asia_australia_24
    else -> R.drawable.ml_globe_24
}
