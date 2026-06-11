/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.searchcompose.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.magiclane.sdk.examples.searchcompose.R
import com.magiclane.sdk.examples.searchcompose.SearchViewModel

private val SearchFieldBackground = Color(0xFFEBEBEB)
private val SearchIconTint = Color(0xFF555555)
private val SearchProgressGreen = Color(0xFF2E7D32)

private fun Modifier.rightFade(color: Color, width: Dp = 48.dp): Modifier = drawWithContent {
    drawContent()
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, color),
            startX = size.width - width.toPx(),
            endX = size.width,
        ),
    )
}

@Composable
fun SearchScreen(viewModel: SearchViewModel, onFatalDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchHeader(viewModel)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                if (viewModel.categories.isNotEmpty()) {
                    CategoryRow(
                        categories = viewModel.categories,
                        selectedIndex = viewModel.selectedCategory,
                        onCategoryClick = viewModel::selectCategory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .rightFade(MaterialTheme.colorScheme.background),
                    )
                }

                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .height(6.dp)
                        .alpha(if (viewModel.isSearching) 1f else 0f),
                    color = SearchProgressGreen,
                    trackColor = SearchProgressGreen.copy(alpha = 0.3f),
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    if (viewModel.isSdkReady) {
                        val hasActiveSearch = viewModel.currentFilter.isNotBlank() ||
                            viewModel.selectedCategory != SearchViewModel.NO_CATEGORY

                        if (viewModel.results.isEmpty() && hasActiveSearch && !viewModel.isSearching) {
                            Text(
                                text = stringResource(R.string.no_results),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(16.dp),
                            )
                        } else {
                            SearchResultsList(items = viewModel.results)
                        }
                    }
                }
            }
        }

        if (!viewModel.isSdkReady) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    if (viewModel.errorMessage.isNotEmpty()) {
        ErrorDialog(
            message = viewModel.errorMessage,
            isFatal = viewModel.isFatalError,
            onDismiss = viewModel::dismissError,
            onFatalDismiss = onFatalDismiss,
        )
    }
}

@Composable
private fun SearchHeader(viewModel: SearchViewModel) {
    Surface(color = MaterialTheme.colorScheme.primary) {
        Column {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            SearchBar(
                query = viewModel.currentFilter,
                onQueryChange = { viewModel.search(it) },
                enabled = viewModel.isSdkReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(query, selection = TextRange(query.length)))
    }

    LaunchedEffect(query) {
        if (fieldValue.text != query) {
            fieldValue = TextFieldValue(query, selection = TextRange(query.length))
        }
    }

    TextField(
        value = fieldValue,
        onValueChange = { newValue ->
            fieldValue = newValue
            onQueryChange(newValue.text)
        },
        enabled = enabled,
        placeholder = {
            Text(
                text = stringResource(R.string.search_hint),
                color = SearchIconTint,
            )
        },
        leadingIcon = {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = SearchIconTint,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = SearchIconTint,
                    modifier = Modifier.clickable { onQueryChange("") },
                )
            }
        },
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = SearchFieldBackground,
            focusedContainerColor = SearchFieldBackground,
            disabledContainerColor = SearchFieldBackground,
            unfocusedTextColor = SearchIconTint,
            focusedTextColor = SearchIconTint,
            disabledTextColor = SearchIconTint,
            cursorColor = SearchIconTint,
            unfocusedIndicatorColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        shape = RoundedCornerShape(50),
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun CategoryRow(
    categories: List<SearchViewModel.CategoryItem>,
    selectedIndex: Int,
    onCategoryClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = WindowInsets.safeDrawing
            .only(WindowInsetsSides.Horizontal)
            .add(WindowInsets(left = 4.dp, right = 4.dp, top = 6.dp, bottom = 6.dp))
            .asPaddingValues(),
    ) {
        itemsIndexed(categories) { index, category ->
            CategoryChip(
                category = category,
                isSelected = index == selectedIndex,
                onClick = { onCategoryClick(index) },
            )
        }
    }
}

@Composable
private fun CategoryChip(category: SearchViewModel.CategoryItem, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        category.icon?.let { bitmap ->
            Image(
                painter = BitmapPainter(bitmap.asImageBitmap()),
                contentDescription = category.name,
                modifier = Modifier.size(48.dp),
            )
        } ?: Spacer(modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun SearchResultsList(items: List<SearchViewModel.SearchItem>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = WindowInsets.safeDrawing
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            .add(WindowInsets(left = 10.dp, right = 10.dp))
            .asPaddingValues(),
    ) {
        itemsIndexed(items) { index, item ->
            SearchResultItem(item = item)
            if (index < items.lastIndex) {
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SearchResultItem(item: SearchViewModel.SearchItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val imageBitmap: ImageBitmap? = item.image?.asImageBitmap()
        if (imageBitmap != null) {
            Image(
                painter = BitmapPainter(imageBitmap),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .size(40.dp),
            )
        } else {
            Spacer(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .size(40.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.description.isNotEmpty()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            modifier = Modifier.padding(end = 10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (item.distance.isNotEmpty()) {
                Text(
                    text = item.distance,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (item.unit.isNotEmpty()) {
                Text(
                    text = item.unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ErrorDialog(message: String, isFatal: Boolean, onDismiss: () -> Unit, onFatalDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(text = stringResource(R.string.error)) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isFatal) onFatalDismiss() else onDismiss()
                },
            ) {
                Text(stringResource(R.string.ok))
            }
        },
    )
}
