/*
 * SPDX-FileCopyrightText: 2025-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapselectioncompose.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Colors of the public transport station / trip views, resolved per theme through
// [com.magiclane.sdk.examples.mapselectioncompose.ptPalette].
// Realtime status: early / on time / late (the theme's onBackground = scheduled only).
val PTGrayLight = Color(0xFF767478)
val PTGrayDark = Color(0xFFB2B0B4)
val PTStatusEarlyLight = Color(0xFF2979FF)
val PTStatusEarlyDark = Color(0xFF82B1FF)
val PTStatusOnTimeLight = Color(0xFF00A344)
val PTStatusOnTimeDark = Color(0xFF69F0AE)
val PTStatusLateLight = Color(0xFFD50000)
val PTStatusLateDark = Color(0xFFFF6E6E)

// Warning-level service alerts / medium crowding (severe reuses the "late" color).
val PTAlertWarningLight = Color(0xFFED6C02)
val PTAlertWarningDark = Color(0xFFFFB74D)
