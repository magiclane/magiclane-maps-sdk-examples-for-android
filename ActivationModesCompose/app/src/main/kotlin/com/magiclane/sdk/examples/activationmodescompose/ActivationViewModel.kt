/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.activationmodescompose

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.magiclane.sdk.activation.ActivationService
import com.magiclane.sdk.activation.EActivationStatus
import com.magiclane.sdk.activation.ProductID
import com.magiclane.sdk.compose.map.GemMapState
import com.magiclane.sdk.core.ESdkNotActivatedReason
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.EWatermarkPosition
import com.magiclane.sdk.util.SdkCall

/**
 * REST contract of the activation service, the same one the SDK uses internally:
 *   POST   {service url}/services/tokens/v1/activations   body {"request_blob": "..."}  -> {"offline_activation_key": "..."}
 *   DELETE {service url}/services/tokens/v1/activations   body {"request_blob": "..."}  -> {"offline_deactivation_key": "..."}
 */
const val ACTIVATIONS_PATH = "/services/tokens/v1/activations"

/**
 * Holds everything the panel shows and edits, and performs the activation calls on the SDK thread.
 *
 * The SDK notifications ([onSdkNotActivated] / [onSdkActivated]) arrive on the main thread through
 * `GemSdk.onSdkNotActivated` / `GemSdk.onSdkActivated`, assigned in MainActivity before the SDK is initialized.
 */
class ActivationViewModel(application: Application) : AndroidViewModel(application) {

    // -- status ---------------------------------------------------------------------------------------------------------------

    var isActivated by mutableStateOf(false)
        private set

    /** Whether the application allows the SDK to use the internet connection (the SDK starts with it disallowed here). */
    var allowOnline by mutableStateOf(false)
        private set

    var applicationId by mutableStateOf<String?>(null)
        private set

    var lastNotification by mutableStateOf("")
        private set

    var message by mutableStateOf("")
    var errorMessage by mutableStateOf("")

    // -- offline activation ---------------------------------------------------------------------------------------------------

    var licenseKeyInput by mutableStateOf("")
    var activationBlob by mutableStateOf("")
    var offlineActivationKeyInput by mutableStateOf("")

    // -- offline deactivation -------------------------------------------------------------------------------------------------

    var deactivationBlob by mutableStateOf("")
    var offlineDeactivationKeyInput by mutableStateOf("")

    /** Typed in by the developer for the REST request (the SDK only learns it once it has been online). */
    var activationServiceUrlInput by mutableStateOf("")

    private var mapState: GemMapState? = null

    private val res get() = getApplication<Application>().resources

    // -- SDK notifications ----------------------------------------------------------------------------------------------------

    fun onSdkNotActivated(reason: ESdkNotActivatedReason) {
        val reasonText = when (reason) {
            ESdkNotActivatedReason.NotYetActivated -> res.getString(R.string.reason_not_yet_activated)
            ESdkNotActivatedReason.ActivationExpired -> res.getString(R.string.reason_activation_expired)
        }
        lastNotification = res.getString(R.string.notification_not_activated, reasonText)
        isActivated = false
        applyWatermark()
    }

    fun onSdkActivated() {
        lastNotification = res.getString(R.string.notification_activated)
        isActivated = true
        applyWatermark()
    }

    // -- lifecycle ------------------------------------------------------------------------------------------------------------

    /** Called once the map is available: from now on the activation state is mirrored as a watermark text. */
    fun onMapReady(mapState: GemMapState) {
        this.mapState = mapState
        applyWatermark()
    }

    /**
     * Reads the current state from the SDK. A device that is already activated from a previous run gets no
     * notification at start, so the panel must not rely on the callbacks alone for its initial state.
     */
    fun refreshStatus() {
        SdkCall.execute {
            isActivated = ActivationService().isActive(ProductID.CORE)
            applicationId = SdkSettings.applicationId
        }
        applyWatermark()
    }

    // -- 1. connectivity ------------------------------------------------------------------------------------------------------

    fun setConnectionAllowed(allow: Boolean) {
        allowOnline = allow
        SdkCall.execute { SdkSettings.allowConnection = allow }
        message = res.getString(
            if (allow) R.string.message_connection_allowed else R.string.message_connection_disallowed,
        )
    }

    // -- 2. offline activation ------------------------------------------------------------------------------------------------

    fun getActivationRequestBlob() {
        val appId = applicationId ?: run {
            message = res.getString(R.string.message_no_token)
            return
        }
        val result = SdkCall.execute {
            ActivationService().getOfflineActivationRequestBlob(appId, licenseKeyInput.trim(), ProductID.CORE)
        } ?: return
        if (result.first == GemError.NoError) {
            activationBlob = result.second
            message = res.getString(R.string.message_activation_blob_ready)
        } else {
            activationBlob = ""
            message = failure("getOfflineActivationRequestBlob", result)
        }
        refreshStatus()
    }

    fun completeOfflineActivation() {
        val result = SdkCall.execute {
            ActivationService().completeOfflineActivation(offlineActivationKeyInput.trim())
        } ?: return
        if (result.first == GemError.NoError) {
            message = res.getString(R.string.message_offline_activation_done)
            activationBlob = ""
            offlineActivationKeyInput = ""
        } else {
            message = failure("completeOfflineActivation", result)
        }
        refreshStatus()
    }

    // -- 3. offline deactivation ----------------------------------------------------------------------------------------------

    fun getDeactivationRequestBlob() {
        val appId = applicationId ?: run {
            message = res.getString(R.string.message_no_token)
            return
        }
        val result = SdkCall.execute {
            // The deactivation request needs the license key of the active activation; with auto-activation the SDK
            // generated it, so read it back from the activation records.
            val licenseKey = ActivationService().getActivationsForProduct(ProductID.CORE)
                .firstOrNull { it.status == EActivationStatus.Activated }?.licenseKey
                ?: return@execute null
            ActivationService().getOfflineDeactivationRequestBlob(appId, licenseKey, ProductID.CORE)
        }
        if (result == null) {
            message = res.getString(R.string.message_nothing_to_deactivate)
            return
        }
        if (result.first == GemError.NoError) {
            deactivationBlob = result.second
            message = res.getString(R.string.message_deactivation_blob_ready)
        } else {
            deactivationBlob = ""
            message = failure("getOfflineDeactivationRequestBlob", result)
        }
        refreshStatus()
    }

    fun completeOfflineDeactivation() {
        val result = SdkCall.execute {
            ActivationService().completeOfflineDeactivation(offlineDeactivationKeyInput.trim())
        } ?: return
        if (result.first == GemError.NoError) {
            message = res.getString(R.string.message_offline_deactivation_done)
            deactivationBlob = ""
            offlineDeactivationKeyInput = ""
        } else {
            message = failure("completeOfflineDeactivation", result)
        }
        refreshStatus()
    }

    // -- 4. reset -------------------------------------------------------------------------------------------------------------

    /**
     * Testing aid: returns the device to the state the example starts in - offline and NOT ACTIVATED. Deleting the
     * activation that holds the Core gate open is a state transition like any other, so the SDK reports
     * `onSdkNotActivated` at once; allowing the connection afterwards runs auto-activation again. Nothing is released at
     * Magic Lane Services - a real device must use the offline deactivation above.
     */
    fun resetToFirstRun() {
        if (allowOnline) setConnectionAllowed(false)
        SdkCall.execute {
            val service = ActivationService()
            service.getActivationsForProduct(ProductID.CORE).forEach { service.deleteActivation(it.id) }
        }
        activationBlob = ""
        deactivationBlob = ""
        licenseKeyInput = ""
        offlineActivationKeyInput = ""
        offlineDeactivationKeyInput = ""
        message = res.getString(R.string.message_reset_done)
        refreshStatus()
    }

    // -- helpers --------------------------------------------------------------------------------------------------------------

    /** The REST request a developer can issue with any HTTP client to obtain the offline key for a blob. */
    fun restRequest(method: String, blob: String): String {
        val url = activationServiceUrlInput.trim().ifEmpty { "https://<activation-service>" }
        return "$method $url$ACTIVATIONS_PATH\nContent-Type: application/json\n\n{\"request_blob\": \"$blob\"}"
    }

    private fun failure(call: String, result: Pair<Int, String>) =
        res.getString(R.string.message_call_failed, call, result.first, result.second)

    /**
     * The application's reaction to the SDK's activation state: a watermark text while not activated, none otherwise.
     * This is the application's choice - the SDK itself draws nothing for this state. The text is placed at the top of
     * the map because the control panel covers the lower part of the screen in portrait.
     */
    private fun applyWatermark() {
        val activated = isActivated
        mapState?.postToMap { map ->
            if (activated) {
                map.setWatermarkText("", "")
            } else {
                map.setWatermarkText(
                    "SDK not activated",
                    "Limited offline functionality",
                    1.0f,
                    EWatermarkPosition.EWPTop,
                )
            }
            map.invalidate() // the watermark is drawn on the next frame; request one, the map is otherwise idle
        }
    }
}
