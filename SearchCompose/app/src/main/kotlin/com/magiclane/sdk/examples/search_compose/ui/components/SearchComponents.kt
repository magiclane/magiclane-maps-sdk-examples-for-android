/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

package com.magiclane.sdk.examples.search_compose.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magiclane.sdk.examples.search_compose.R
import com.magiclane.sdk.examples.search_compose.SearchViewModel
import com.magiclane.sdk.examples.search_compose.ui.theme.SearchTheme

/**
 * Search screen component that contains the search bar, progress indicator, and results list.
 */
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(),
    onTextChanged: (String) -> Unit = {}
) {
    SearchTheme {
        Column(
            modifier.windowInsetsPadding(WindowInsets.systemBars)
        ) {
            Spacer(Modifier.height(16.dp))

            SearchBar(
                modifier = Modifier.padding(horizontal = 16.dp),
                onTextChanged = onTextChanged
            )

            // Progress indicator with smooth fade animation
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 3.dp)
                    .alpha(if (viewModel.displayProgress) 1f else 0f)
            )

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(15.dp)
            ) {
                // Status message display
                if (viewModel.statusMessage.isNotBlank()) {
                    Text(
                        text = viewModel.statusMessage,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Start
                    )
                }

                // Error dialog
                if (viewModel.errorMessage.isNotBlank()) {
                    ErrorDialog(viewModel = viewModel)
                }

                // Search results list
                SearchResultsList(list = viewModel.searchItems)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Search bar component with search icon and clear functionality.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    onTextChanged: (String) -> Unit = {}
) {
    var text by rememberSaveable { mutableStateOf("") }

    TextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            onTextChanged(newText)
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search icon"
            )
        },
        placeholder = {
            Text(stringResource(id = R.string.placeholder_search))
        },
        colors = TextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .heightIn(min = 56.dp)
            .fillMaxWidth()
            .shadow(elevation = 3.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(
                    onClick = {
                        text = ""
                        onTextChanged("")
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Clear search"
                    )
                }
            }
        }
    )
}

/**
 * Error dialog component for displaying error messages.
 */
@Composable
fun ErrorDialog(viewModel: SearchViewModel) {
    AlertDialog(
        text = {
            Text(text = viewModel.errorMessage)
        },
        onDismissRequest = {
            viewModel.errorMessage = ""
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.errorMessage = ""
                }
            ) {
                Text("Ok")
            }
        }
    )
}

// --------------------------------------------------------------------------------------------------------------------------------------------------
// Preview Components
// --------------------------------------------------------------------------------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFFF5F0EE)
@Composable
fun SearchBarPreview() {
    SearchTheme {
        SearchBar(Modifier.padding(8.dp))
    }
}

@Preview(widthDp = 360, heightDp = 640, showBackground = true, backgroundColor = 0xFFF5F0EE)
@Composable
fun SearchScreenPreview() {
    SearchScreen()
}
