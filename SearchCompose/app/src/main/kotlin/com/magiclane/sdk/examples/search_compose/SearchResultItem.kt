// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.search_compose

// --------------------------------------------------------------------------------------------------------------------------------------------------

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --------------------------------------------------------------------------------------------------------------------------------------------------

@Composable
fun SearchResultItem(
    imageBitmap: ImageBitmap,
    text: String,
    description: String,
    distance: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "some useful description",
        )

        Column(modifier = Modifier
            .weight(1f)
            .padding(start = 16.dp)) {
            Text(
                text = text
            )
            Text (
                text = description,
                fontSize = 12.sp
            )
        }

        Column(modifier = Modifier
            .padding(start = 16.dp),
            horizontalAlignment = Alignment.End) {
            Text(
                text = distance,
                fontSize = 12.sp
            )
            Text (
                text = unit,
                fontSize = 12.sp
            )
        }
    }
}

// --------------------------------------------------------------------------------------------------------------------------------------------------
