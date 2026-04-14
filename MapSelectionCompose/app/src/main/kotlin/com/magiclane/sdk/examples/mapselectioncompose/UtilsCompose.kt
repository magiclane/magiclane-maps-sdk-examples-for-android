/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapselectioncompose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magiclane.sdk.examples.mapselectioncompose.ui.theme.MapSelectionTheme

@Composable
fun TitleDescriptionColumn(modifier: Modifier = Modifier, title: String? = null, description: String? = null) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        title?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 3) }
        if (description?.isNotEmpty() == true) {
            Text(description, style = MaterialTheme.typography.bodyMedium, color = Color(128, 128, 128, 255))
        }
    }
}

@Composable
fun ImageAndLargeTitle(modifier: Modifier = Modifier, img: ImageBitmap? = null, title: String? = null) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        img?.let {
            Image(
                it,
                "Descriptive image",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .clip(RectangleShape)
                    .size(60.dp),
            )
        }
        title?.let {
            Text(
                modifier = Modifier.weight(1f),
                text = it,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun TopAppBar(
    modifier: Modifier = Modifier,
    toolbarColor: Color,
    title: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    iconOnClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        color = toolbarColor,
    ) {
        Row(
            modifier = Modifier
                .padding(4.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconOnClick?.let { onBack ->
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(60.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back_24dp),
                        contentDescription = "Back arrow, navigate back",
                        tint = titleColor,
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = titleColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun ValueWithUnit(modifier: Modifier = Modifier, value: String? = null, unit: String? = null) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        value?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 50.sp,
                modifier = Modifier.alignByBaseline(),
                fontWeight = FontWeight.Bold,
            )
        }
        unit?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

@Preview
@Composable
private fun TopAppBarPreview() {
    MapSelectionTheme {
        TopAppBar(
            title = stringResource(
                R.string.app_name,
            ),
            toolbarColor = MaterialTheme.colorScheme.primary,
            iconOnClick = {
            },
        )
    }
}

@Preview
@Composable
private fun TitleDescriptionColumnPrev() {
    MapSelectionTheme {
        TitleDescriptionColumn(title = stringResource(R.string.app_name), description = "aaaa")
    }
}

@Preview
@Composable
private fun ImageAndLargeTitlePrev() {
    val context = LocalContext.current
    val bmp = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.sky_night)

    MapSelectionTheme {
        ImageAndLargeTitle(img = bmp.asImageBitmap(), title = stringResource(R.string.app_name))
    }
}

@Preview
@Composable
private fun ValueWithUnitPrev() {
    MaterialTheme {
        ValueWithUnit(value = "100", unit = "km/h")
    }
}
