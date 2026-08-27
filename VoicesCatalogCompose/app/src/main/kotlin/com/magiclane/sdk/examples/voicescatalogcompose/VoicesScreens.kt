/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.voicescatalogcompose

import android.text.format.Formatter
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magiclane.sdk.compose.components.contentstore.ContentStoreScreen
import com.magiclane.sdk.compose.components.contentstore.OfflineVoicesTab
import com.magiclane.sdk.compose.components.contentstore.OnlineVoicesTab
import com.magiclane.sdk.compose.components.contentstore.VoiceCatalogActions

private const val OFFLINE_TAB = 0

/**
 * Root screen of the example: the library's content-store screen scaffold and the
 * voices tabs, wired to [VoicesCatalogViewModel] state and actions.
 */
@Composable
fun VoicesCatalogApp(modifier: Modifier = Modifier, viewModel: VoicesCatalogViewModel = viewModel()) {
    var selectedTab by rememberSaveable { mutableIntStateOf(OFFLINE_TAB) }

    // The catalog is loaded on demand, when the online tab is first shown. An effect
    // (not the tab-tap callback) so a restored selection also triggers the request.
    LaunchedEffect(selectedTab) {
        if (selectedTab != OFFLINE_TAB) viewModel.onOnlineTabShown()
    }

    val actions = remember(viewModel) {
        VoiceCatalogActions(
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
        title = stringResource(R.string.voices),
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
            val totalVoices = viewModel.localGroups.sumOf { it.voices.size }
            val totalBytes = viewModel.localGroups.sumOf { group -> group.voices.sumOf { it.sizeBytes } }
            OfflineVoicesTab(
                groups = viewModel.filterGroups(viewModel.localGroups),
                searchActive = viewModel.query.isNotEmpty(),
                hasLocalVoices = viewModel.localGroups.isNotEmpty(),
                summaryTitle = pluralStringResource(R.plurals.voice_count, totalVoices, totalVoices),
                summarySubtitle = Formatter.formatShortFileSize(LocalContext.current, totalBytes),
                selectedVoiceName = viewModel.selectedVoiceName,
                selectedVoiceLanguage = viewModel.selectedVoiceLanguageText,
                selectedVoiceId = viewModel.selectedVoiceId,
                ttsLanguages = viewModel.ttsLanguages,
                selectedTtsCode = viewModel.selectedTtsCode,
                onSelectTtsLanguage = viewModel::selectTtsLanguage,
                onSelectVoice = viewModel::selectVoice,
                onDeleteAllLocal = viewModel::deleteAllLocal,
                actions = actions,
                searchTtsLanguages = viewModel.filterTtsLanguages(),
            )
        } else {
            OnlineVoicesTab(
                status = viewModel.onlineStatus,
                isOnline = viewModel.isOnline,
                continents = viewModel.onlineContinents,
                selectedVoiceId = viewModel.selectedVoiceId,
                searchResults = if (viewModel.query.isEmpty()) {
                    null
                } else {
                    viewModel.filterGroups(viewModel.onlineGroups)
                },
                actions = actions,
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
