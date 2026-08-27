/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.weathercompose.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Backgrounds of the forecast screens, matching the Weather (XML) example: a light blue
// sky tone while the selected location is in daylight and a darker one during the night.
val ForecastBackgroundDay = Color(0xFF74A0D6)
val ForecastBackgroundNight = Color(0xFF415785)

// Fallback color of a weather warning whose provider did not deliver one (same orange as
// the highlight color used by the XML example).
val WarningFallback = Color(0xFFFF6200)
