// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.mapselection_compose

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.magiclane.sdk.examples.mapselection_compose.data.SocialReportInfo
import com.magiclane.sdk.examples.mapselection_compose.ui.theme.MapSelectionTheme

@Composable
fun SocialReportScreen(modifier: Modifier = Modifier, socialReportInfo: SocialReportInfo) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start // keep children starting; middle column will take remaining space
    ) {
        with(socialReportInfo) {

            socialReportInfo.image?.let {
                Image(
                    modifier = Modifier.size(60.dp),
                    bitmap = it,
                    contentScale = ContentScale.FillBounds,
                    contentDescription = "social report descriptive image"
                )
            }
            // Make this column take the remaining space between the image and the shape
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp), verticalArrangement = Arrangement.SpaceEvenly
            ) {
                TitleDescriptionColumn(Modifier, description, address)
                Text("${stringResource(R.string.added_on)} $date", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                Image(
                    Icons.Default.Favorite, "", modifier = Modifier
                        .size(40.dp), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                )
                Text(score, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@SuppressLint("LocalContextResourcesRead")
@Preview
@Composable
private fun SocialReportScreenPreview() {
    val context = LocalContext.current
    val bmp = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.sky_night)

    MapSelectionTheme {
        Surface {
            SocialReportScreen(
                socialReportInfo = SocialReportInfo(
                    image = bmp.asImageBitmap(),
                    description = "Speed camera",
                    address = "1234 Elm Street, Apt 5B, Springfield",
                    date = "10/10/2025",
                    score = "200"
                )
            )
        }
    }
}
