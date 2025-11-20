// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.search_compose.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.magiclane.sdk.examples.search_compose.data.SearchResult

@Composable
fun SearchResultsList(
    list: List<SearchResult>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(all = 15.dp),
    ) {
        items(
            items = list
        ) { searchResult ->
            SearchResultItem(
                imageBitmap = searchResult.imageBitmap,
                text = searchResult.text,
                description = searchResult.description,
                distance = searchResult.distance,
                unit = searchResult.unit,
                modifier = Modifier.padding(top = 2.dp, bottom = 5.dp)
            )
            Divider(color = Color.Black, thickness = 1.dp)
        }
    }
}

// --------------------------------------------------------------------------------------------------------------------------------------------------
