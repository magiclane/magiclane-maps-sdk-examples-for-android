/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.searchcompose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.magiclane.sdk.compose.components.search.LandmarkList
import com.magiclane.sdk.compose.components.search.SearchBar
import com.magiclane.sdk.compose.components.search.SearchCategoryRow
import com.magiclane.sdk.compose.components.search.SearchState
import com.magiclane.sdk.compose.components.search.rememberSearchState
import com.magiclane.sdk.compose.components.search.rightFade
import com.magiclane.sdk.compose.sdk.rememberGemSdkState
import com.magiclane.sdk.compose.ui.ErrorDialog
import com.magiclane.sdk.compose.ui.LoadingOverlay
import com.magiclane.sdk.examples.searchcompose.EspressoIdlingResource
import com.magiclane.sdk.examples.searchcompose.R
import com.magiclane.sdk.examples.searchcompose.SearchViewModel

private val SearchProgressGreen = Color(0xFF2E7D32)

@Composable
fun SearchApp(viewModel: SearchViewModel, onFatalDismiss: () -> Unit) {
    val context = LocalContext.current

    // The SDK listeners and the whole search pipeline come from the maps-compose library.
    val sdkState = rememberGemSdkState()
    val searchState = rememberSearchState(
        onSearchStarted = { EspressoIdlingResource.increment() },
        onSearchCompleted = { EspressoIdlingResource.decrement() },
    )

    LaunchedEffect(sdkState.isMapDataReady) {
        if (sdkState.isMapDataReady) {
            searchState.loadCategories()
            EspressoIdlingResource.decrement()
        }
    }

    LaunchedEffect(sdkState.isTokenRejected) {
        if (sdkState.isTokenRejected) {
            viewModel.showFatalError(context.getString(R.string.invalid_token))
        }
    }

    SearchScreen(
        searchState = searchState,
        isSdkReady = sdkState.isMapDataReady,
        viewModel = viewModel,
        onFatalDismiss = onFatalDismiss,
    )
}

@Composable
fun SearchScreen(
    searchState: SearchState,
    isSdkReady: Boolean,
    viewModel: SearchViewModel,
    onFatalDismiss: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchHeader(searchState, isSdkReady)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                if (searchState.categories.isNotEmpty()) {
                    SearchCategoryRow(
                        categories = searchState.categories,
                        selectedIndex = searchState.selectedCategory,
                        onCategoryClick = searchState::selectCategory,
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
                        .alpha(if (searchState.isSearching) 1f else 0f),
                    color = SearchProgressGreen,
                    trackColor = SearchProgressGreen.copy(alpha = 0.3f),
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    if (isSdkReady) {
                        if (searchState.results.isEmpty() && searchState.hasActiveSearch && !searchState.isSearching) {
                            Text(
                                text = stringResource(R.string.no_results),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(16.dp),
                            )
                        } else {
                            LandmarkList(items = searchState.results)
                        }
                    }
                }
            }
        }

        LoadingOverlay(visible = !isSdkReady)
    }

    if (viewModel.errorMessage.isNotEmpty()) {
        ErrorDialog(
            message = viewModel.errorMessage,
            onDismiss = {
                if (viewModel.isFatalError) onFatalDismiss() else viewModel.dismissError()
            },
            cancelable = false,
        )
    }
}

@Composable
private fun SearchHeader(searchState: SearchState, isSdkReady: Boolean) {
    Surface(color = MaterialTheme.colorScheme.primary) {
        Column {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            SearchBar(
                query = searchState.query,
                onQueryChange = searchState::search,
                enabled = isSdkReady,
                placeholder = stringResource(R.string.search_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
