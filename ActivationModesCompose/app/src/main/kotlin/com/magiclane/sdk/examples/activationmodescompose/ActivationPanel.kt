/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.activationmodescompose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.magiclane.sdk.compose.components.common.PanelTopBar

private val StatusGreen = Color(0xFF2E7D32)
private val StatusRed = Color(0xFFC62828)

/**
 * The example's control panel: activation / connection status, then one section per way the SDK can become (or stop
 * being) activated. Everything it shows lives in [ActivationViewModel]; [isOnline] comes from the SDK connection
 * status reported to the Compose SDK state.
 */
@Composable
fun ActivationPanel(viewModel: ActivationViewModel, isOnline: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PanelTopBar(title = stringResource(R.string.panel_title))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusSection(viewModel, isOnline)
                HorizontalDivider()
                ConnectivitySection(viewModel)
                HorizontalDivider()
                OfflineActivationSection(viewModel)
                HorizontalDivider()
                OfflineDeactivationSection(viewModel)
                HorizontalDivider()
                ResetSection(viewModel)
            }
        }
    }
}

// -- status -------------------------------------------------------------------------------------------------------------------

@Composable
private fun StatusSection(viewModel: ActivationViewModel, isOnline: Boolean) {
    StatusLine(
        label = stringResource(R.string.status_activation),
        good = viewModel.isActivated,
        goodText = stringResource(R.string.status_activated),
        badText = stringResource(R.string.status_not_activated),
    )
    // Never show "online" while the application disallows the connection, even if a status callback is late.
    StatusLine(
        label = stringResource(R.string.status_connection),
        good = viewModel.allowOnline && isOnline,
        goodText = stringResource(R.string.status_online),
        badText = stringResource(R.string.status_offline),
    )
    // The application id (the token's audience) is what the manual offline calls need. SdkSettings derives it from the
    // token given at initialization, so the application does not have to keep the token around.
    Row {
        Text(text = stringResource(R.string.status_app_id) + " : ", fontWeight = FontWeight.Bold)
        Text(text = viewModel.applicationId ?: stringResource(R.string.status_no_token))
    }
    Text(
        text = stringResource(
            R.string.status_last_notification,
            viewModel.lastNotification.ifEmpty { stringResource(R.string.notification_none) },
        ),
    )
    if (viewModel.message.isNotEmpty()) {
        Text(
            text = viewModel.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun StatusLine(label: String, good: Boolean, goodText: String, badText: String) {
    Row {
        Text(text = "$label : ", fontWeight = FontWeight.Bold)
        Text(
            text = if (good) goodText else badText,
            color = if (good) StatusGreen else StatusRed,
            fontWeight = FontWeight.Bold,
        )
    }
}

// -- 1. auto-activation -----------------------------------------------------------------------------------------------------------

@Composable
private fun ConnectivitySection(viewModel: ActivationViewModel) {
    SectionTitle(stringResource(R.string.section_auto))
    Hint(stringResource(R.string.section_auto_hint))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = viewModel.allowOnline, onCheckedChange = { viewModel.setConnectionAllowed(it) })
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.allow_connection))
    }
}

// -- 2. offline activation --------------------------------------------------------------------------------------------------------

@Composable
private fun OfflineActivationSection(viewModel: ActivationViewModel) {
    SectionTitle(stringResource(R.string.section_offline_activation))
    Hint(stringResource(R.string.section_offline_activation_hint))

    OutlinedTextField(
        value = viewModel.licenseKeyInput,
        onValueChange = { viewModel.licenseKeyInput = it },
        label = { Text(stringResource(R.string.license_key_label)) },
        placeholder = { Text(stringResource(R.string.license_key_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { viewModel.getActivationRequestBlob() }) {
        Text(stringResource(R.string.get_activation_blob))
    }

    if (viewModel.activationBlob.isNotEmpty()) {
        BlobViews(
            viewModel = viewModel,
            blob = viewModel.activationBlob,
            method = "POST",
            responseKeyName = stringResource(R.string.offline_activation_key_label),
        )
        OutlinedTextField(
            value = viewModel.offlineActivationKeyInput,
            onValueChange = { viewModel.offlineActivationKeyInput = it },
            label = { Text(stringResource(R.string.offline_activation_key_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { viewModel.completeOfflineActivation() }) {
            Text(stringResource(R.string.complete_offline_activation))
        }
    }
}

// -- 3. offline deactivation ------------------------------------------------------------------------------------------------------

@Composable
private fun OfflineDeactivationSection(viewModel: ActivationViewModel) {
    SectionTitle(stringResource(R.string.section_offline_deactivation))
    Hint(stringResource(R.string.section_offline_deactivation_hint))

    Button(onClick = { viewModel.getDeactivationRequestBlob() }) {
        Text(stringResource(R.string.get_deactivation_blob))
    }

    if (viewModel.deactivationBlob.isNotEmpty()) {
        BlobViews(
            viewModel = viewModel,
            blob = viewModel.deactivationBlob,
            method = "DELETE",
            responseKeyName = stringResource(R.string.offline_deactivation_key_label),
        )
        OutlinedTextField(
            value = viewModel.offlineDeactivationKeyInput,
            onValueChange = { viewModel.offlineDeactivationKeyInput = it },
            label = { Text(stringResource(R.string.offline_deactivation_key_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { viewModel.completeOfflineDeactivation() }) {
            Text(stringResource(R.string.complete_offline_deactivation))
        }
    }
}

// -- 4. reset ---------------------------------------------------------------------------------------------------------------------

@Composable
private fun ResetSection(viewModel: ActivationViewModel) {
    SectionTitle(stringResource(R.string.section_reset))
    Hint(stringResource(R.string.section_reset_hint))
    Button(onClick = { viewModel.resetToFirstRun() }) {
        Text(stringResource(R.string.reset_to_first_run))
    }
    Spacer(Modifier.height(8.dp))
}

// -- shared pieces ----------------------------------------------------------------------------------------------------------------

/** The three ways a request blob reaches Magic Lane Services: QR code for the companion app, raw text, REST request. */
@Composable
private fun BlobViews(viewModel: ActivationViewModel, blob: String, method: String, responseKeyName: String) {
    Text(stringResource(R.string.scan_with_companion_app))
    QrCode(text = blob, size = 260.dp)

    Text(stringResource(R.string.raw_blob))
    Monospace(blob)

    Text(stringResource(R.string.rest_request_intro))
    if (viewModel.activationServiceUrlInput.isBlank()) {
        Hint(stringResource(R.string.rest_services_list_hint))
        Monospace(stringResource(R.string.services_list_url))
    }
    OutlinedTextField(
        value = viewModel.activationServiceUrlInput,
        onValueChange = { viewModel.activationServiceUrlInput = it },
        label = { Text(stringResource(R.string.activation_service_url_label)) },
        placeholder = { Text("https://...") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Monospace(viewModel.restRequest(method, blob))
    Text(stringResource(R.string.rest_response_hint, responseKeyName))
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun Hint(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall)
}

/** Selectable (long-press to copy) monospaced text block for blobs and requests. */
@Composable
private fun Monospace(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 6,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
