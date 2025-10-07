// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.mapselection_compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.magiclane.sdk.examples.mapselection_compose.data.TrafficEventInfo

@Composable
fun TrafficEventScreen(modifier: Modifier = Modifier, trafficEventInfo: TrafficEventInfo) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        with(trafficEventInfo) {

            ImageAndLargeTitle(img = image, title = description)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            if (lengthText.isNotEmpty()) {
                Text(text = lengthText, style = MaterialTheme.typography.titleMedium)
                ValueWithUnit(Modifier, lengthValue, lengthUnit)
            }
            if (delayText.isNotEmpty()) {
                Text(text = delayText, style = MaterialTheme.typography.titleMedium)
                ValueWithUnit(Modifier, delayValue, delayUnit)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            if (fromText.isNotEmpty()) {
                TitleDescriptionColumn(
                    title = fromText,
                    description = fromValue
                )
            }
            if (toText.isNotEmpty()) {
                TitleDescriptionColumn(
                    Modifier.padding(top = 4.dp),
                    title = toText,
                    description = toValue
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            if (validFromText.isNotEmpty()) {
                TitleDescriptionColumn(
                    title = validFromText,
                    description = validFromValue
                )
            }
            if (validUntilText.isNotEmpty()) {
                TitleDescriptionColumn(
                    Modifier.padding(top = 4.dp),
                    title = validUntilText,
                    description = validUntilValue
                )
            }

        }
    }
}

@Preview
@Composable
private fun TrafficEventScreenPrev() {
    val context = LocalContext.current
    val bmp = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.sky_night)

    MaterialTheme {
        Surface {
            TrafficEventScreen(
                Modifier, TrafficEventInfo(
                    image = bmp.asImageBitmap(),
                    description = "Road block",
                    lengthText = "Length",
                    lengthUnit = "m",
                    lengthValue = "100",
                    fromText = "From",
                    fromValue = "1234 Elm Street, Apt 5B, Springfield",
                    toText = "To",
                    toValue = "1234 Elm Street, Apt 5B, Springfield",
                    validFromText = "Valid from",
                    validFromValue = "10/10/2025",
                    validUntilText = "Valid until",
                    validUntilValue = "21/10/2025",
                )
            )
        }
    }
}
