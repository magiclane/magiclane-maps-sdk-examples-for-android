/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapscatalogcompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.magiclane.sdk.compose.theme.MagicLaneTheme
import com.magiclane.sdk.core.EOffboardListenerReason
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.EServiceGroupType
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {

    private val viewModel: MapsCatalogViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MagicLaneTheme {
                MapsCatalogApp(Modifier.fillMaxSize())
            }
        }

        registerSdkListeners()

        // disable automatic map update
        SdkSettings.setAllowAutoMapUpdate(false)

        // This example has no map view, so the SDK must be initialized explicitly.
        val errorCode = GemSdk.initSdkWithDefaults(this)
        if (errorCode != GemError.NoError) {
            viewModel.errorMessage = getString(
                R.string.sdk_initialization_failed,
                SdkCall.runSynced { GemError.getMessage(errorCode, this) },
            )
        } else {
            // Maps downloaded in previous sessions are available right away; only the
            // online catalog needs to wait for the content service availability.
            viewModel.refreshLocalContent()
            // A world-map update interrupted by a previous shutdown is resumed as soon
            // as the SDK is up, based on the persisted content updater status.
            viewModel.resumeMapsUpdate()
        }

        if (!Util.isInternetConnected(this)) {
            viewModel.errorMessage = getString(R.string.internet_required)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        clearSdkListeners()
        GemSdk.release()
        exitProcess(0)
    }

    // Registers all SDK-level callbacks.
    private fun registerSdkListeners() {
        // The content-download service connection drives the online state: gained, it
        // serves the catalog request the online tab may be waiting for; lost, it parks
        // the online tab and the update entry points on their "no internet" reports.
        SdkSettings.onServiceStatusUpdated = { service, connected ->
            if (service == EServiceGroupType.ContentService) {
                Util.postOnMain { viewModel.onContentServiceStatus(connected) }
            }
        }

        // Fires once the SDK connection is up: up-to-date world map data opens the
        // online catalog, old data surfaces the "map update available" card on the
        // offline tab instead.
        SdkSettings.onWorldwideRoadMapSupportStatus = { status, checkOnDemand ->
            Util.postOnMain { viewModel.onRoadMapSupportStatus(upToDate = status == EOffboardListenerStatus.UpToDate) }

            if (checkOnDemand && status == EOffboardListenerStatus.UpToDate) {
                Util.postOnMain {
                    viewModel.infoMessage = getString(R.string.maps_up_to_date)
                }
            }
        }

        // Road map support can be revoked after startup (e.g. the disk filled up while
        // downloading, or the SDK version expired): report why the catalog stopped working.
        SdkSettings.onWorldwideRoadMapSupportDisabled = { reason ->
            Util.postOnMain {
                viewModel.errorMessage = getString(
                    when (reason) {
                        EOffboardListenerReason.NoDiskSpace -> R.string.road_map_support_disabled_no_disk_space
                        EOffboardListenerReason.ExpiredSdk -> R.string.road_map_support_disabled_expired_sdk
                    },
                )
            }
        }

        SdkSettings.onApiTokenRejected = {
            Util.postOnMain { viewModel.errorMessage = getString(R.string.token_rejected_message) }
        }
    }

    // Clears SDK-level callbacks to prevent them reaching a destroyed activity.
    private fun clearSdkListeners() {
        SdkSettings.onServiceStatusUpdated = { _, _ -> }
        SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
        SdkSettings.onWorldwideRoadMapSupportDisabled = {}
        SdkSettings.onApiTokenRejected = {}
    }
}
