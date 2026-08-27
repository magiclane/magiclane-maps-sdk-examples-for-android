/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapscatalog

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.magiclane.sdk.core.EOffboardListenerReason
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.EServiceGroupType
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.mapscatalog.databinding.ActivityMainBinding
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private val viewModel: MapsCatalogViewModel by viewModels()

    private lateinit var binding: ActivityMainBinding
    private lateinit var rowsBuilder: CatalogRowsBuilder
    private lateinit var adapter: CatalogAdapter

    private var selectedTab = OFFLINE_TAB

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        rowsBuilder = CatalogRowsBuilder(this, viewModel)
        restoreUiState(savedInstanceState)
        setupViews()
        observeViewModel()

        registerSdkListeners()

        // disable automatic map update
        SdkSettings.setAllowAutoMapUpdate(false)

        // This example has no map view, so the SDK must be initialized explicitly.
        val errorCode = GemSdk.initSdkWithDefaults(this)
        if (errorCode != GemError.NoError) {
            viewModel.errorMessage.value = getString(
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
            viewModel.errorMessage.value = getString(R.string.internet_required)
        }

        // A restored online tab must trigger the catalog request too.
        if (selectedTab != OFFLINE_TAB) viewModel.onOnlineTabShown()
        rebuildRows()
    }

    override fun onDestroy() {
        super.onDestroy()

        clearSdkListeners()
        GemSdk.release()
        exitProcess(0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB, selectedTab)
        outState.putStringArrayList(STATE_CONTINENTS, ArrayList(rowsBuilder.expandedContinents))
        outState.putStringArrayList(STATE_ONLINE_COUNTRIES, ArrayList(rowsBuilder.expandedOnlineCountries))
        outState.putStringArrayList(STATE_OFFLINE_COUNTRIES, ArrayList(rowsBuilder.expandedOfflineCountries))
    }

    private fun restoreUiState(savedInstanceState: Bundle?) {
        savedInstanceState ?: return
        selectedTab = savedInstanceState.getInt(STATE_TAB, OFFLINE_TAB)
        savedInstanceState.getStringArrayList(STATE_CONTINENTS)?.let { rowsBuilder.expandedContinents.addAll(it) }
        savedInstanceState.getStringArrayList(STATE_ONLINE_COUNTRIES)
            ?.let { rowsBuilder.expandedOnlineCountries.addAll(it) }
        savedInstanceState.getStringArrayList(STATE_OFFLINE_COUNTRIES)
            ?.let { rowsBuilder.expandedOfflineCountries.addAll(it) }
    }

    // region views

    private fun setupViews() {
        adapter = CatalogAdapter(rowCallbacks)
        binding.catalogList.layoutManager = LinearLayoutManager(this)
        binding.catalogList.adapter = adapter
        (binding.catalogList.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        ItemTouchHelper(SwipeToDeleteCallback(adapter)).attachToRecyclerView(binding.catalogList)

        binding.tabs.selectedIndex = selectedTab
        binding.tabs.onTabSelected = { index ->
            if (selectedTab != index) {
                selectedTab = index
                binding.tabs.selectedIndex = index
                // The search is tab-local, like on the Compose screen.
                viewModel.query = ""
                binding.searchInput.setText("")
                if (index != OFFLINE_TAB) viewModel.onOnlineTabShown()
                rebuildRows()
                binding.catalogList.scrollToPosition(0)
            }
        }

        binding.searchInput.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            if (viewModel.query != query) {
                viewModel.query = query
                rebuildRows()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.contentChanged.observe(this) { rebuildRows() }

        viewModel.errorMessage.observe(this) { message ->
            if (!message.isNullOrEmpty()) {
                showMessageDialog(getString(R.string.error), message) { viewModel.errorMessage.value = "" }
            }
        }
        viewModel.infoMessage.observe(this) { message ->
            if (!message.isNullOrEmpty()) {
                showMessageDialog(getString(R.string.info), message) { viewModel.infoMessage.value = "" }
            }
        }
    }

    private fun rebuildRows() {
        val rows = if (selectedTab == OFFLINE_TAB) rowsBuilder.buildOfflineRows() else rowsBuilder.buildOnlineRows()
        adapter.submitList(rows)
    }

    private fun showMessageDialog(title: String, message: String, onDismiss: () -> Unit) {
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
            .setOnDismissListener { onDismiss() }
            .show()
    }

    private fun showDeleteConfirmDialog(title: String, message: String? = null, onConfirm: () -> Unit) {
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_confirm_title, title))
            .apply { message?.let { setMessage(it) } }
            .setPositiveButton(R.string.delete) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // Resolves a country of the visible tab by its code (bulk actions carry the code).
    private fun findCountry(countryCode: String): CatalogCountryUi? {
        val countries = if (selectedTab == OFFLINE_TAB) viewModel.localCountries else viewModel.onlineCountries
        return countries.firstOrNull { it.countryCode == countryCode }
    }

    private val rowCallbacks = object : CatalogRowCallbacks {
        override fun onContinentToggle(name: String) {
            rowsBuilder.expandedContinents.toggle(name)
            rebuildRows()
        }

        override fun onCountryToggle(countryCode: String) {
            val expanded = if (selectedTab == OFFLINE_TAB) {
                rowsBuilder.expandedOfflineCountries
            } else {
                rowsBuilder.expandedOnlineCountries
            }
            expanded.toggle(countryCode)
            rebuildRows()
        }

        override fun onDownload(itemId: Long) = viewModel.download(itemId)

        override fun onPause(itemId: Long) = viewModel.pause(itemId)

        override fun onResume(itemId: Long) = viewModel.download(itemId)

        override fun onDeleteRequest(itemId: Long, title: String) {
            showDeleteConfirmDialog(title) { viewModel.delete(itemId) }
        }

        override fun onDownloadAll(countryCode: String) {
            findCountry(countryCode)?.let(viewModel::downloadAll)
        }

        override fun onPauseAll(countryCode: String) {
            findCountry(countryCode)?.let(viewModel::pauseAll)
        }

        override fun onResumeAll(countryCode: String) {
            findCountry(countryCode)?.let(viewModel::resumeAll)
        }

        override fun onDeleteAllRequest(countryCode: String, title: String) {
            showDeleteConfirmDialog(title) { findCountry(countryCode)?.let(viewModel::deleteAll) }
        }

        override fun onUpdateCardTapped() = viewModel.onUpdateCardTapped()

        override fun onCancelUpdate() = viewModel.cancelMapsUpdate()

        override fun onDeleteAllLocalRequest() {
            showDeleteConfirmDialog(
                getString(R.string.delete_all_maps_title),
                getString(R.string.delete_all_maps_message),
            ) { viewModel.deleteAllLocal() }
        }
    }

    private fun <T> MutableSet<T>.toggle(value: T) {
        if (!add(value)) remove(value)
    }

    // endregion

    // region SDK listeners

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
                    viewModel.infoMessage.value = getString(R.string.maps_up_to_date)
                }
            }
        }

        // Road map support can be revoked after startup (e.g. the disk filled up while
        // downloading, or the SDK version expired): report why the catalog stopped working.
        SdkSettings.onWorldwideRoadMapSupportDisabled = { reason ->
            Util.postOnMain {
                viewModel.errorMessage.value = getString(
                    when (reason) {
                        EOffboardListenerReason.NoDiskSpace -> R.string.road_map_support_disabled_no_disk_space
                        EOffboardListenerReason.ExpiredSdk -> R.string.road_map_support_disabled_expired_sdk
                    },
                )
            }
        }

        SdkSettings.onApiTokenRejected = {
            Util.postOnMain { viewModel.errorMessage.value = getString(R.string.token_rejected_message) }
        }
    }

    // Clears SDK-level callbacks to prevent them reaching a destroyed activity.
    private fun clearSdkListeners() {
        SdkSettings.onServiceStatusUpdated = { _, _ -> }
        SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
        SdkSettings.onWorldwideRoadMapSupportDisabled = {}
        SdkSettings.onApiTokenRejected = {}
    }

    // endregion

    private companion object {
        const val OFFLINE_TAB = 0
        const val STATE_TAB = "selected_tab"
        const val STATE_CONTINENTS = "expanded_continents"
        const val STATE_ONLINE_COUNTRIES = "expanded_online_countries"
        const val STATE_OFFLINE_COUNTRIES = "expanded_offline_countries"
    }
}
