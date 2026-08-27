/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.voicescatalog

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
import com.magiclane.sdk.core.EServiceGroupType
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.examples.voicescatalog.databinding.ActivityMainBinding
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sound.SoundUtils
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener {

    private val viewModel: VoicesCatalogViewModel by viewModels()

    private lateinit var binding: ActivityMainBinding
    private lateinit var rowsBuilder: VoiceRowsBuilder
    private lateinit var adapter: VoicesAdapter

    private var selectedTab = OFFLINE_TAB

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        rowsBuilder = VoiceRowsBuilder(this, viewModel)
        restoreUiState(savedInstanceState)
        setupViews()
        observeViewModel()

        registerSdkListeners()

        // This example has no map view, so the SDK must be initialized explicitly.
        val errorCode = GemSdk.initSdkWithDefaults(this)
        if (errorCode != GemError.NoError) {
            viewModel.errorMessage.value = getString(
                R.string.sdk_initialization_failed,
                SdkCall.runSynced { GemError.getMessage(errorCode, this) },
            )
        } else {
            // Voices downloaded in previous sessions (and the applied guidance voice)
            // are available right away; only the online catalog needs to wait for the
            // content service availability.
            viewModel.refreshLocalContent()

            // The device engine may have finished initializing before the listener
            // registration; the languages are loaded now, or from the listener.
            if (SdkCall.runSynced { SoundPlayingService.ttsPlayerIsInitialized } == true) {
                viewModel.onTtsPlayerReady()
            }
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

    // The device Text-to-Speech engine initializes asynchronously; its languages can
    // only be listed (and the default TTS selection applied) once it reports ready.
    override fun onTTSPlayerInitialized() = viewModel.onTtsPlayerReady()

    override fun onTTSPlayerInitializationFailed() = viewModel.onTtsPlayerUnavailable()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB, selectedTab)
        outState.putBoolean(STATE_TTS_EXPANDED, rowsBuilder.ttsExpanded)
        outState.putStringArrayList(STATE_CONTINENTS, ArrayList(rowsBuilder.expandedContinents))
        outState.putStringArrayList(STATE_ONLINE_GROUPS, ArrayList(rowsBuilder.expandedOnlineGroups))
        outState.putStringArrayList(STATE_OFFLINE_GROUPS, ArrayList(rowsBuilder.expandedOfflineGroups))
    }

    private fun restoreUiState(savedInstanceState: Bundle?) {
        savedInstanceState ?: return
        selectedTab = savedInstanceState.getInt(STATE_TAB, OFFLINE_TAB)
        rowsBuilder.ttsExpanded = savedInstanceState.getBoolean(STATE_TTS_EXPANDED, false)
        savedInstanceState.getStringArrayList(STATE_CONTINENTS)?.let { rowsBuilder.expandedContinents.addAll(it) }
        savedInstanceState.getStringArrayList(STATE_ONLINE_GROUPS)?.let { rowsBuilder.expandedOnlineGroups.addAll(it) }
        savedInstanceState.getStringArrayList(STATE_OFFLINE_GROUPS)
            ?.let { rowsBuilder.expandedOfflineGroups.addAll(it) }
    }

    // region views

    private fun setupViews() {
        adapter = VoicesAdapter(rowCallbacks)
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

    // Resolves a voice group of the visible tab by its key (bulk actions carry the key).
    private fun findGroup(groupKey: String): CatalogVoiceGroupUi? {
        val groups = if (selectedTab == OFFLINE_TAB) viewModel.localGroups else viewModel.onlineGroups
        return groups.firstOrNull { it.key == groupKey }
    }

    // Resolves a voice by id across both tabs (the offline row of a voice applies it
    // no matter where its UI model came from).
    private fun findVoice(voiceId: Long): CatalogVoiceUi? =
        (viewModel.localGroups.asSequence() + viewModel.onlineGroups.asSequence())
            .flatMap { it.voices }
            .firstOrNull { it.id == voiceId }

    private val rowCallbacks = object : VoiceRowCallbacks {
        override fun onContinentToggle(name: String) {
            rowsBuilder.expandedContinents.toggle(name)
            rebuildRows()
        }

        override fun onGroupToggle(groupKey: String) {
            val expanded = if (selectedTab == OFFLINE_TAB) {
                rowsBuilder.expandedOfflineGroups
            } else {
                rowsBuilder.expandedOnlineGroups
            }
            expanded.toggle(groupKey)
            rebuildRows()
        }

        override fun onTtsToggle() {
            rowsBuilder.ttsExpanded = !rowsBuilder.ttsExpanded
            rebuildRows()
        }

        override fun onSelectTtsLanguage(code: String) {
            viewModel.ttsLanguages.firstOrNull { it.code == code }?.let(viewModel::selectTtsLanguage)
        }

        override fun onSelectVoice(voiceId: Long) {
            findVoice(voiceId)?.let(viewModel::selectVoice)
        }

        override fun onDownload(voiceId: Long) = viewModel.download(voiceId)

        override fun onPause(voiceId: Long) = viewModel.pause(voiceId)

        override fun onDeleteRequest(voiceId: Long, title: String) {
            showDeleteConfirmDialog(title) { viewModel.delete(voiceId) }
        }

        override fun onDownloadAll(groupKey: String) {
            findGroup(groupKey)?.let(viewModel::downloadAll)
        }

        override fun onPauseAll(groupKey: String) {
            findGroup(groupKey)?.let(viewModel::pauseAll)
        }

        override fun onResumeAll(groupKey: String) {
            findGroup(groupKey)?.let(viewModel::resumeAll)
        }

        override fun onDeleteAllRequest(groupKey: String, title: String) {
            showDeleteConfirmDialog(title) { findGroup(groupKey)?.let(viewModel::deleteAll) }
        }

        override fun onDeleteAllLocalRequest() {
            showDeleteConfirmDialog(
                getString(R.string.delete_all_voices_title),
                getString(R.string.delete_all_voices_message),
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
        // Must be registered before initSdkWithDefaults so an engine that initializes
        // during SDK init is not missed.
        SoundUtils.addTTSPlayerInitializationListener(this)
        // The content-download service connection drives the online state: gained, it
        // serves the catalog request the online tab may be waiting for; lost, it parks
        // the online tab on its "no internet" report.
        SdkSettings.onServiceStatusUpdated = { service, connected ->
            if (service == EServiceGroupType.ContentService) {
                Util.postOnMain { viewModel.onContentServiceStatus(connected) }
            }
        }

        SdkSettings.onApiTokenRejected = {
            Util.postOnMain { viewModel.errorMessage.value = getString(R.string.token_rejected_message) }
        }
    }

    // Clears SDK-level callbacks to prevent them reaching a destroyed activity.
    private fun clearSdkListeners() {
        SoundUtils.removeTTSPlayerInitializationListener(this)
        SdkSettings.onServiceStatusUpdated = { _, _ -> }
        SdkSettings.onApiTokenRejected = {}
    }

    // endregion

    private companion object {
        const val OFFLINE_TAB = 0
        const val STATE_TAB = "selected_tab"
        const val STATE_TTS_EXPANDED = "tts_expanded"
        const val STATE_CONTINENTS = "expanded_continents"
        const val STATE_ONLINE_GROUPS = "expanded_online_groups"
        const val STATE_OFFLINE_GROUPS = "expanded_offline_groups"
    }
}
