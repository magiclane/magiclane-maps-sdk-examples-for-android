/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.hellomapcompose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

data class UiState(
    val isLoading: Boolean = true,
    val errorMessage: String = "",
)

class MainViewModel : ViewModel() {
    var uiState by mutableStateOf(UiState())
        private set

    fun onSdkError(message: String) {
        uiState = uiState.copy(errorMessage = message)
    }

    fun dismissError() {
        uiState = uiState.copy(errorMessage = "")
    }

    fun onMapReady() {
        uiState = uiState.copy(isLoading = false)
    }
}
