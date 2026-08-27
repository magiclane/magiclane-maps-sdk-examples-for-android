/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapscatalogcompose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magiclane.sdk.compose.components.contentstore.ContentStoreScreen
import com.magiclane.sdk.compose.components.contentstore.MapCatalogActions
import com.magiclane.sdk.compose.components.contentstore.OfflineMapsTab
import com.magiclane.sdk.compose.components.contentstore.OnlineMapsTab

private const val OFFLINE_TAB = 0

/**
 * Root screen of the example: the library's content-store screen scaffold and tabs,
 * wired to [MapsCatalogViewModel] state and actions.
 */
@Composable
fun MapsCatalogApp(modifier: Modifier = Modifier, viewModel: MapsCatalogViewModel = viewModel()) {
    var selectedTab by rememberSaveable { mutableIntStateOf(OFFLINE_TAB) }

    // The catalog is loaded on demand, when the online tab is first shown. An effect
    // (not the tab-tap callback) so a restored selection also triggers the request.
    LaunchedEffect(selectedTab) {
        if (selectedTab != OFFLINE_TAB) viewModel.onOnlineTabShown()
    }

    val actions = remember(viewModel) {
        MapCatalogActions(
            stateOf = viewModel::stateOf,
            progressOf = viewModel::progressOf,
            onDownload = viewModel::download,
            onPause = viewModel::pause,
            onDelete = viewModel::delete,
            onDownloadAll = viewModel::downloadAll,
            onPauseAll = viewModel::pauseAll,
            onResumeAll = viewModel::resumeAll,
            onDeleteAll = viewModel::deleteAll,
        )
    }

    ContentStoreScreen(
        title = stringResource(R.string.offline_maps),
        query = viewModel.query,
        onQueryChange = { viewModel.query = it },
        selectedTab = selectedTab,
        onTabSelected = { index ->
            if (selectedTab != index) {
                selectedTab = index
                viewModel.query = ""
            }
        },
        modifier = modifier,
        searchPlaceholder = stringResource(R.string.search_hint),
    ) { tabIndex ->
        if (tabIndex == OFFLINE_TAB) {
            OfflineMapsTab(
                countries = viewModel.filterCountries(viewModel.localCountries),
                searchActive = viewModel.query.isNotEmpty(),
                hasLocalMaps = viewModel.localCountries.isNotEmpty(),
                mapsVersion = viewModel.mapsVersion,
                updateMode = viewModel.updateMode,
                updateProgress = viewModel.updateProgress,
                availableUpdateVersion = viewModel.availableUpdateVersion,
                onUpdateCardTapped = viewModel::onUpdateCardTapped,
                onCancelUpdate = viewModel::cancelMapsUpdate,
                onDeleteAllLocal = viewModel::deleteAllLocal,
                actions = actions,
                deletesEnabled = !viewModel.isUpdateActive,
            )
        } else {
            OnlineMapsTab(
                status = viewModel.onlineStatus,
                isOnline = viewModel.isOnline,
                continents = viewModel.onlineContinents,
                searchResults = if (viewModel.query.isEmpty()) {
                    null
                } else {
                    viewModel.filterCountries(viewModel.onlineCountries)
                },
                actions = actions,
                downloadsEnabled = !viewModel.isUpdateActive,
                deletesEnabled = !viewModel.isUpdateActive,
            )
        }
    }

    if (viewModel.errorMessage.isNotEmpty()) {
        MessageDialog(
            title = stringResource(R.string.error),
            message = viewModel.errorMessage,
            onDismiss = { viewModel.errorMessage = "" },
        )
    }
    if (viewModel.infoMessage.isNotEmpty()) {
        MessageDialog(
            title = stringResource(R.string.info),
            message = viewModel.infoMessage,
            onDismiss = { viewModel.infoMessage = "" },
        )
    }
}

@Composable
fun MessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        title = { Text(title) },
        text = { Text(message) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
    )
}
