// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.search_compose

// --------------------------------------------------------------------------------------------------------------------------------------------------

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.search_compose.ui.theme.SearchTheme
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util

// --------------------------------------------------------------------------------------------------------------------------------------------------

class MainActivity : ComponentActivity()
{
    private val REQUEST_PERMISSIONS = 110

    private lateinit var viewModel: SearchViewModel

    private var searchService = SearchService(
        onCompleted = { results, errorCode, _ ->
            if (errorCode != GemError.Cancel)
            {
                viewModel.displayProgress = false
            }

            when (errorCode)
            {
                GemError.NoError ->
                {
                    // No error encountered, we can handle the results.
                    viewModel.refresh(results)
                    if (results.isEmpty())
                    {
                        viewModel.statusMessage = "No search results"
                    }
                    else
                    {
                        viewModel.statusMessage = ""
                    }

                }
                GemError.Cancel ->
                {
                    // The search action was cancelled.
                }
                GemError.Busy ->
                {
                    viewModel.refresh(results)
                    viewModel.statusMessage = "Requested operation cannot be performed. Internal limit reached. Please use an API token in order to avoid this error."
                }
                GemError.NotFound ->
                {
                    viewModel.refresh(results)
                    viewModel.statusMessage = "No search results"
                }
                else ->
                {
                    // There was a problem at computing the search operation.
                    viewModel.refresh(results)
                    viewModel.statusMessage = GemError.getMessage(errorCode)
                }
            }
        }
    )

    // ----------------------------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        setContent {
            SearchTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    viewModel = viewModel()
                    viewModel.size = resources.getDimension(R.dimen.image_size).toInt()

                    if (!Util.isInternetConnected(this))
                    {
                        viewModel.statusMessage = "Please connect to the internet!"
                    }

                    SearchScreen(viewModel = viewModel, onTextChanged = { text -> applyFilter(text) })
                }
            }

            if (!GemSdk.initSdkWithDefaults(this))
            {
                // The SDK initialization was not completed.
                finish()
            }
        }

        /// MAGIC LANE
        SdkSettings.onApiTokenRejected = {
            /*
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one.
             */
            viewModel.errorMessage = "Token rejected!"
        }

        SdkSettings.onConnectionStatusUpdated = { connected ->
            viewModel.connected = connected

            if (viewModel.filter.isBlank())
            {
                if (connected)
                {
                    viewModel.statusMessage = ""
                }
                else
                {
                    viewModel.statusMessage = "Please connect to the internet!"
                }
            }
        }

        /*
        The SDK initialization completed with success, but for the search action to be executed
        the app needs some permissions.
        Not requesting this permissions or not granting them will make the search to not work.
         */
        requestPermissions()

        onBackPressedDispatcher.addCallback(this /* lifecycle owner */, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    // ----------------------------------------------------------------------------------------------------------------------------------------------

    override fun onPause()
    {
        super.onPause()

        if (isFinishing)
        {
            GemSdk.release() // Release the SDK.
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------------------

    private fun requestPermissions(): Boolean
    {
        val permissions = arrayListOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

        return PermissionsHelper.requestPermissions(
            REQUEST_PERMISSIONS,
            this,
            permissions.toTypedArray()
        )
    }

    // ----------------------------------------------------------------------------------------------------------------------------------------------

    private fun search(filter: String) = SdkCall.postAsync {
        // Cancel any search that is in progress now.
        Log.d("SEARCH", filter)
        searchService.cancelSearch()

        if (filter.isBlank())
        {
            viewModel.refresh(arrayListOf())
            if (!Util.isInternetConnected(this))
            {
                viewModel.statusMessage = "Please connect to the internet!"
            }
            else
            {
                viewModel.statusMessage = ""
            }
        }
        else
        {
            val position = PositionService.position
            viewModel.reference = if (position?.isValid() == true) {
                position.coordinates
            } else {
                Coordinates(51.5072, 0.1276) // center London
            }

            searchService.searchByFilter(filter, viewModel.reference)
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------------------

    private fun applyFilter(filter: String)
    {
        val newFilter = filter.trim()
        if (newFilter != viewModel.filter)
        {
            viewModel.filter = newFilter
            viewModel.displayProgress = newFilter.isNotBlank() && viewModel.connected

            // Search the requested filter.
            search(newFilter)
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------------------
}

// --------------------------------------------------------------------------------------------------------------------------------------------------

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

// --------------------------------------------------------------------------------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SearchTheme {
        Greeting("Android")
    }
}

// --------------------------------------------------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    onTextChanged: (String) -> Unit = {}
) {
    var text by rememberSaveable { mutableStateOf("") }

    TextField(value = text,
        onValueChange = { txt ->
            text = txt
            onTextChanged(txt) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        placeholder = {
            Text(stringResource(id = R.string.placeholder_search))
        },
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White
        ),
        modifier = modifier
            .heightIn(min = 56.dp)
            .fillMaxWidth()
            .shadow(elevation = 3.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { text = ""
                    onTextChanged("")
                    })
                {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null
                    )
                }
            }
        }
    )
}

// --------------------------------------------------------------------------------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFFF5F0EE)
@Composable
fun SearchBarPreview() {
    SearchTheme { SearchBar(Modifier.padding(8.dp)) }
}

// --------------------------------------------------------------------------------------------------------------------------------------------------

@Composable
fun ErrorDialog(viewModel: SearchViewModel)
{
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

@Composable
fun SearchScreen(modifier: Modifier = Modifier,
                 viewModel: SearchViewModel = viewModel(),
                 onTextChanged: (String) -> Unit = {}) {
    SearchTheme {
        Column(modifier) {
            Spacer(Modifier.height(16.dp))
            SearchBar(Modifier.padding(horizontal = 16.dp), onTextChanged)

            val alpha = if (viewModel.displayProgress) 1f else 0f
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth(1f)
                    .padding(horizontal = 16.dp)
                    .padding(top = 3.dp)
                    .alpha(alpha))

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                color = Color.White,
                shape = RoundedCornerShape(15.dp)
            )
            {
                if (viewModel.statusMessage.isNotBlank())
                {
                    Text(
                        text = viewModel.statusMessage,
                        modifier = Modifier.padding(all = 16.dp),
                        textAlign = TextAlign.Start
                    )
                }

                if (viewModel.errorMessage.isNotBlank())
                {
                    ErrorDialog(viewModel)
                }

                SearchResultsList(
                    list = viewModel.searchItems
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// --------------------------------------------------------------------------------------------------------------------------------------------------

@Preview(widthDp = 360, heightDp = 640, showBackground = true, backgroundColor = 0xFFF5F0EE)
@Composable
fun SearchScreenPreview() {
    SearchScreen()
}

// --------------------------------------------------------------------------------------------------------------------------------------------------
