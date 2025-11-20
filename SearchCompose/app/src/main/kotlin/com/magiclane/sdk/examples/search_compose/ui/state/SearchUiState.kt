/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

package com.magiclane.sdk.examples.search_compose.ui.state

import com.magiclane.sdk.examples.search_compose.data.SearchResult


data class SearchUiState(
    val searchResults: List<SearchResult> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = false,
    val isConnected: Boolean = false,
    val statusMessage: String = "",
    val errorMessage: String = ""
) {
    val showProgress: Boolean
        get() = isLoading && query.isNotBlank() && isConnected
    
    val showEmptyState: Boolean
        get() = searchResults.isEmpty() && query.isNotBlank() && !isLoading
}
