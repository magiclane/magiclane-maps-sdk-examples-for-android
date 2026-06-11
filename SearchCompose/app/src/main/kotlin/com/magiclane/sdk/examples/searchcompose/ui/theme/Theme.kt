/*
 * SPDX-FileCopyrightText: 2025-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.searchcompose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = MagicLanePrimaryDark,
    onPrimary = MagicLaneOnPrimary,
    background = MagicLaneBackgroundDark,
    onBackground = MagicLaneOnBackgroundDark,
    surface = MagicLaneSurfaceDark,
    onSurface = MagicLaneOnSurfaceDark,
)

private val LightColorScheme = lightColorScheme(
    primary = MagicLanePrimary,
    onPrimary = MagicLaneOnPrimary,
    background = MagicLaneBackground,
    onBackground = MagicLaneOnBackground,
    surface = MagicLaneSurface,
    onSurface = MagicLaneOnSurface,
)

@Composable
fun SearchComposeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
