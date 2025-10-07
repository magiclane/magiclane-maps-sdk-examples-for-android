// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.rangefinder_compose

// --------------------------------------------------------------------------------------------------------------------------------------------------

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --------------------------------------------------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RangeItem(
    imageResourceId: Int,
    text: String,
    borderColor: Color,
    enabled: MutableState<Boolean>,
    onItemClick: ()->Unit,
    onItemLongClick: ()->Unit,
    onDeleteItemClick: ()->Unit
) {
    Box(modifier = Modifier.padding(8.dp).combinedClickable(onClick = { onItemClick() }, onLongClick = { onItemLongClick() }))
    {
        Surface(
            shadowElevation = 1.dp,
            shape = MaterialTheme.shapes.small,
            color = Color.LightGray,
            border = BorderStroke(
                width = 1.5.dp,
                color = if (enabled.value) borderColor else Color.Gray
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(imageResourceId),
                    modifier = Modifier.padding(start = 10.dp).size(InputChipDefaults.AvatarSize),
                    contentDescription = null
                )
                Text(text,
                     modifier = Modifier.padding(8.dp),
                     fontSize = 14.sp)

                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    Modifier.padding(end = 10.dp).size(InputChipDefaults.AvatarSize).clickable { onDeleteItemClick() }
                )
            }
        }
    }
}

// --------------------------------------------------------------------------------------------------------------------------------------------------
