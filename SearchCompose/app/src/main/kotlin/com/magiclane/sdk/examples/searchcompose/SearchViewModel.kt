/*
 * SPDX-FileCopyrightText: 2025-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.searchcompose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

// The search pipeline (debounce, category filters, result mapping) is provided by the
// maps-compose library (rememberSearchState); this model only holds the error state,
// surviving configuration changes.
class SearchViewModel : ViewModel() {

    // Non-empty when an error dialog should be shown.
    var errorMessage by mutableStateOf("")
        private set

    // When true the error is fatal and the Activity should finish on dismiss.
    var isFatalError by mutableStateOf(false)
        private set

    fun showFatalError(message: String) {
        errorMessage = message
        isFatalError = true
    }

    fun showInfoError(message: String) {
        errorMessage = message
        isFatalError = false
    }

    fun dismissError() {
        errorMessage = ""
        isFatalError = false
    }
}
