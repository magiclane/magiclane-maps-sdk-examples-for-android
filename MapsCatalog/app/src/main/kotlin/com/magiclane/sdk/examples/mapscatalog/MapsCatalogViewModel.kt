/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapscatalog

import android.app.Application
import android.graphics.Bitmap
import android.text.format.Formatter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.ContentStoreItem
import com.magiclane.sdk.content.ContentUpdater
import com.magiclane.sdk.content.EContentStoreItemStatus
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.content.EContentUpdaterStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.MapDetails
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Version
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.text.Normalizer

/**
 * Drives the maps-catalog UI: loads the online road-map catalog, tracks per-item
 * download state/progress, mirrors the locally downloaded maps and runs the world-map
 * update flow.
 *
 * All `ContentStoreItem` access happens on the SDK thread (via [SdkCall]); the UI reads
 * immutable snapshots plus two main-thread maps keyed by item id. Every visible-state
 * mutation happens on the main thread and is announced through [contentChanged], which
 * the activity observes to rebuild the displayed list.
 */
class MapsCatalogViewModel(application: Application) : AndroidViewModel(application) {

    private val contentStore = ContentStore()
    private val mapDetails = MapDetails()

    // SDK item handles by id. Store items serve download/pause; local items serve delete
    // of content downloaded in previous sessions (same ids, distinct native objects).
    private val storeItemsById = HashMap<Long, ContentStoreItem>()
    private val localItemsById = HashMap<Long, ContentStoreItem>()

    // Country flag images by ISO code, decoded once. SDK-thread owned (see flagFor).
    private val flagCache = HashMap<String, Bitmap?>()

    private var catalogRequested = false

    // Whether the online tab has been opened: the catalog is only requested for it. A
    // request that could not run yet (no connection) is served once the content
    // service reports connected.
    private var catalogWanted = false

    private var autoResumeAttempted = false
    private var updater: ContentUpdater? = null

    // Whether the running update flow was started by a tap on the card: only then do
    // its outcomes ("up to date", no connection) deserve a dialog. SDK-thread owned.
    private var updateUserRequested = false

    // Whether the running update flow must stay invisible - started automatically
    // because no maps are on the device: no card changes, no progress, no dialogs.
    // SDK-thread owned.
    private var updateSilent = false

    // Whether the SDK reported a newer world map: restored on the update card when a
    // running update gets cancelled.
    private var updateAvailable = false

    // region UI state

    /**
     * Fired (on the main thread) whenever any state the catalog lists are built from
     * changed; the activity rebuilds the visible rows in response.
     */
    val contentChanged = MutableLiveData<Unit>()

    /** Message of the error dialog, empty while none is shown. */
    val errorMessage = MutableLiveData("")

    /** Message of the info dialog, empty while none is shown. */
    val infoMessage = MutableLiveData("")

    /** Live download state per item id. Rows fall back to [CatalogItemState.Idle]. */
    private val itemStates = HashMap<Long, CatalogItemState>()

    /** Live download progress (0..100) per item id. */
    private val itemProgress = HashMap<Long, Int>()

    var onlineStatus = CatalogStatus.Loading
        private set

    var onlineCountries: List<CatalogCountryUi> = emptyList()
        private set

    var onlineContinents: List<CatalogContinentUi> = emptyList()
        private set

    var localCountries: List<CatalogCountryUi> = emptyList()
        private set

    /**
     * Version of the map data in use ("major.minor"), read from [MapDetails.mapVersion]
     */
    var mapsVersion = ""
        private set

    var updateMode = MapUpdateCardMode.Check
        private set

    var updateProgress: Int? = null
        private set

    /** Version of the available world-map update ("major.minor"), empty while unknown. */
    var availableUpdateVersion = ""
        private set

    /**
     * Whether the SDK content service is connected, as reported through
     * `SdkSettings.onServiceStatusUpdated`. Gates the catalog request and the update
     * entry points; the online tab reports the missing connection while false.
     */
    var isOnline = false
        private set

    /** The current search query, set by the activity as the user types. */
    var query = ""

    /** True while a world-map update runs (or waits): new downloads and deletes are blocked. */
    val isUpdateActive: Boolean
        get() = updateMode == MapUpdateCardMode.Checking ||
            updateMode == MapUpdateCardMode.Updating ||
            updateMode == MapUpdateCardMode.Applying ||
            updateMode == MapUpdateCardMode.WaitingConnection

    // Must run on the main thread, after any visible-state mutation.
    private fun notifyContentChanged() {
        contentChanged.value = Unit
    }

    // endregion

    /** Current [CatalogItemState] of the item, [CatalogItemState.Idle] when untracked. */
    fun stateOf(itemId: Long): CatalogItemState = itemStates[itemId] ?: CatalogItemState.Idle

    /** Current download progress of the item (0..100). */
    fun progressOf(itemId: Long): Int = itemProgress[itemId] ?: 0

    /**
     * Entry point called when the SDK reports the content-service connection state
     * (`SdkSettings.onServiceStatusUpdated`). Beyond driving [isOnline], a connection
     * (re)gained serves a catalog request the online tab is still waiting for and
     * resumes downloads interrupted by a previous app shutdown (trying without the
     * connection would fail).
     */
    fun onContentServiceStatus(connected: Boolean) {
        isOnline = connected
        notifyContentChanged()
        if (connected) {
            resumeInterruptedTransfers()
            if (catalogWanted) loadCatalog()
        }
    }

    /**
     * Entry point called when the online tab is shown: the catalog is loaded on
     * demand, not at startup. Without a connection the request is parked and served
     * once the content service reports connected (see [onContentServiceStatus]).
     */
    fun onOnlineTabShown() {
        catalogWanted = true
        if (isOnline) loadCatalog()
    }

    /**
     * Entry point called when the SDK reports worldwide road-map support. An old-data
     * report starts the update card in [MapUpdateCardMode.UpdateAvailable] — unless no
     * maps are present on the device, in which case there is nothing the user could
     * weigh in on and the update runs and applies completely silently.
     */
    fun onRoadMapSupportStatus(upToDate: Boolean) {
        if (upToDate) return

        updateAvailable = true
        SdkCall.execute {
            val localMaps = contentStore.getLocalContentList(EContentType.RoadMap).orEmpty()
            if (localMaps.isEmpty()) {
                startMapsUpdate(silent = true)
                return@execute
            }
            // The update card names the target version; the store fills it in on the
            // local items once it knows the newer world map.
            val version = readAvailableUpdateVersion(localMaps)
            Util.postOnMain {
                if (updateMode == MapUpdateCardMode.Check) updateMode = MapUpdateCardMode.UpdateAvailable
                availableUpdateVersion = version
                notifyContentChanged()
            }
        }
    }

    // Formats a non-zero SDK version as "major.minor", empty when null or zero.
    private fun versionText(version: Version?): String =
        version?.takeIf { it.version != 0 }?.let { "${it.major}.${it.minor}" }.orEmpty()

    // Version of the newest store world map ("major.minor"), read from the local
    // items (the store stamps them once a newer version exists), empty while unknown.
    // Must run on the SDK thread.
    private fun readAvailableUpdateVersion(items: List<ContentStoreItem>): String =
        items.firstNotNullOfOrNull { item -> versionText(item.updateVersion).ifEmpty { null } }.orEmpty()

    // region catalog loading

    /** Requests the online road-map catalog (once) and builds the continent grouping. */
    private fun loadCatalog() {
        if (catalogRequested) return
        catalogRequested = true
        onlineStatus = CatalogStatus.Loading
        notifyContentChanged()

        SdkCall.execute {
            val listener = ProgressListener.create(onCompleted = { errorCode, _ ->
                if (errorCode == GemError.NoError) {
                    SdkCall.execute {
                        val items = contentStore.getStoreContentList(EContentType.RoadMap)?.first
                        buildOnlineModels(items.orEmpty())
                    }
                } else {
                    Util.postOnMain {
                        onlineStatus = CatalogStatus.Failed
                        catalogRequested = false
                        errorMessage.value = getApplication<Application>().getString(
                            R.string.catalog_download_error,
                            SdkCall.runSynced { GemError.getMessage(errorCode, getApplication()) },
                        )
                        notifyContentChanged()
                    }
                }
            })

            val errorCode = contentStore.asyncGetStoreContentList(EContentType.RoadMap, listener)
            if (errorCode != GemError.NoError) {
                Util.postOnMain {
                    onlineStatus = CatalogStatus.Failed
                    catalogRequested = false
                    notifyContentChanged()
                }
            }
        }
    }

    // Snapshots the SDK items into UI models and groups them by country and continent.
    // Must run on the SDK thread.
    private fun buildOnlineModels(items: List<ContentStoreItem>) {
        storeItemsById.clear()

        val groups = LinkedHashMap<String, MutableList<ContentStoreItem>>()
        for (item in items) {
            val code = item.countryCodes?.firstOrNull() ?: continue
            groups.getOrPut(code) { mutableListOf() }.add(item)
            storeItemsById[item.id] = item
        }

        val states = HashMap<Long, CatalogItemState>()
        val progress = HashMap<Long, Int>()
        val countries = groups.map { (code, groupItems) ->
            for (item in groupItems) {
                states[item.id] = item.toCatalogState()
                progress[item.id] = item.downloadProgress
            }
            toCountryUi(code, groupItems)
        }

        val continents = countries
            .groupBy { ContinentMapper.getContinent(it.countryCode) }
            .map { (continent, continentCountries) ->
                CatalogContinentUi(
                    name = continent,
                    countriesText = getApplication<Application>().resources.getQuantityString(
                        R.plurals.country_count,
                        continentCountries.size,
                        continentCountries.size,
                    ),
                    mapsText = getApplication<Application>().resources.getQuantityString(
                        R.plurals.map_count,
                        continentCountries.sumOf { it.items.size },
                        continentCountries.sumOf { it.items.size },
                    ),
                    countries = continentCountries,
                )
            }
            .sortedBy { ContinentMapper.getContinentOrder(it.name) }

        Util.postOnMain {
            for ((id, state) in states) {
                // Keep live transfers authoritative: a snapshot never downgrades a row
                // that is currently reporting progress through its own listener.
                if (itemStates[id] != CatalogItemState.Downloading) itemStates[id] = state
                if (state == CatalogItemState.Downloading || state == CatalogItemState.Paused) {
                    itemProgress[id] = progress[id] ?: 0
                }
            }
            onlineCountries = countries
            onlineContinents = continents
            onlineStatus = CatalogStatus.Ready
            notifyContentChanged()
        }
    }

    // Must run on the SDK thread.
    private fun toCountryUi(code: String, groupItems: List<ContentStoreItem>): CatalogCountryUi {
        val countryName = mapDetails.getCountryName(code)
            .orEmpty()
            .ifEmpty { groupItems.first().name.orEmpty() }
        val hasRegions = groupItems.size > 1
        val totalBytes = groupItems.sumOf { it.totalSize }

        val itemUis = groupItems.map { item ->
            val itemName = item.name.orEmpty()
            CatalogItemUi(
                id = item.id,
                // Region items are named "Country - Region"; show the bare region name.
                name = if (hasRegions) itemName.substringAfter(" - ", itemName) else itemName,
                sizeText = formatSize(item.totalSize),
                sizeBytes = item.totalSize,
                canDelete = item.canDeleteContent(),
            )
        }

        return CatalogCountryUi(
            countryCode = code,
            name = countryName,
            flag = flagFor(code),
            items = itemUis,
            detailText = if (hasRegions) {
                getApplication<Application>().getString(
                    R.string.region_count,
                    groupItems.size,
                    formatSize(totalBytes),
                )
            } else {
                formatSize(totalBytes)
            },
        )
    }

    // Must run on the SDK thread.
    private fun flagFor(code: String): Bitmap? = flagCache.getOrPut(code) {
        mapDetails.getCountryFlag(code)?.asBitmap(FLAG_PIXEL_SIZE, FLAG_PIXEL_SIZE)
    }

    /** Rebuilds the offline tab content from the fully downloaded local maps. */
    fun refreshLocalContent() {
        SdkCall.execute {
            val items = contentStore.getLocalContentList(EContentType.RoadMap).orEmpty()

            // Every local item stays addressable (resume/delete), but only completed
            // maps are listed offline: an interrupted download belongs to the catalog
            // tab until it finishes.
            localItemsById.clear()
            for (item in items) localItemsById[item.id] = item

            val groups = LinkedHashMap<String, MutableList<ContentStoreItem>>()
            for (item in items.filter { it.isCompleted() }) {
                val code = item.countryCodes?.firstOrNull() ?: continue
                groups.getOrPut(code) { mutableListOf() }.add(item)
            }

            val countries = groups.map { (code, groupItems) -> toCountryUi(code, groupItems) }
            val completedIds = items.filter { it.isCompleted() }.map { it.id }
            val version = versionText(mapDetails.mapVersion)
            val updateVersion = readAvailableUpdateVersion(items)

            Util.postOnMain {
                for (id in completedIds) {
                    if (itemStates[id] != CatalogItemState.Downloading) {
                        itemStates[id] = CatalogItemState.Completed
                    }
                }
                localCountries = countries
                mapsVersion = version
                availableUpdateVersion = updateVersion
                notifyContentChanged()
            }
        }
    }

    // endregion

    // region item actions

    /** Starts (or resumes) the download of an item. Ignored while a map update runs. */
    fun download(itemId: Long) {
        if (isUpdateActive) return
        val sdkItem = storeItemsById[itemId] ?: localItemsById[itemId] ?: return
        startDownload(itemId, sdkItem)
    }

    // Starts the transfer unconditionally (also used to resume interrupted downloads,
    // which may queue behind a resumed map update). An [autoResume] transfer was not
    // requested by the user right now, so a connection failure is not an error worth
    // a popup: the download stays paused and is retried on the next connected
    // notification.
    private fun startDownload(itemId: Long, sdkItem: ContentStoreItem, autoResume: Boolean = false) {
        SdkCall.execute {
            val listener = ProgressListener.create(
                onStarted = {
                    Util.postOnMain {
                        itemStates[itemId] = CatalogItemState.Downloading
                        notifyContentChanged()
                    }
                },
                onProgress = { percent ->
                    Util.postOnMain {
                        itemStates[itemId] = CatalogItemState.Downloading
                        itemProgress[itemId] = percent
                        notifyContentChanged()
                    }
                },
                onCompleted = { errorCode, _ -> onDownloadFinished(itemId, errorCode, autoResume) },
            )

            when (val errorCode = sdkItem.asyncDownload(listener, GemSdk.EDataSavePolicy.UseDefault, true)) {
                GemError.NoError -> Util.postOnMain {
                    itemStates[itemId] = CatalogItemState.Downloading
                    itemProgress[itemId] = sdkItem.downloadProgress
                    notifyContentChanged()
                }

                GemError.UpToDate -> Util.postOnMain {
                    itemStates[itemId] = CatalogItemState.Completed
                    notifyContentChanged()
                }

                else -> if (autoResume && isConnectionError(errorCode)) {
                    parkInterruptedDownload(itemId, sdkItem)
                } else {
                    reportItemError(itemId, errorCode)
                }
            }
        }
    }

    // Runs on the SDK thread. Parks the interrupted download as paused; a tap on the
    // item restarts it.
    private fun parkInterruptedDownload(itemId: Long, sdkItem: ContentStoreItem?) {
        val progress = sdkItem?.downloadProgress
        Util.postOnMain {
            itemStates[itemId] = CatalogItemState.Paused
            if (progress != null) itemProgress[itemId] = progress
            notifyContentChanged()
        }
    }

    private fun isConnectionError(errorCode: Int): Boolean = errorCode == GemError.NoConnection ||
        errorCode == GemError.ConnectionRequired ||
        errorCode == GemError.Connection ||
        errorCode == GemError.NetworkFailed

    /** Pauses a running download. */
    fun pause(itemId: Long) {
        val sdkItem = storeItemsById[itemId] ?: return
        SdkCall.execute {
            sdkItem.pauseDownload()
            syncItemState(itemId)
        }
    }

    /**
     * Deletes the local content of an item (downloaded, downloading or paused).
     * Ignored while a map update runs: the update works on the local maps, pulling
     * one out from under it would corrupt the flow.
     */
    fun delete(itemId: Long) {
        if (isUpdateActive) return
        val sdkItem = localItemsById[itemId] ?: storeItemsById[itemId] ?: return
        SdkCall.execute {
            sdkItem.cancelDownload()
            sdkItem.deleteContent()
            Util.postOnMain {
                itemStates[itemId] = CatalogItemState.Idle
                itemProgress.remove(itemId)
                notifyContentChanged()
            }
            refreshLocalContent()
        }
    }

    /** Starts downloading every item of [country] that is not downloaded or running. */
    fun downloadAll(country: CatalogCountryUi) {
        for (item in country.items) {
            val state = stateOf(item.id)
            if (state == CatalogItemState.Idle || state == CatalogItemState.Paused) download(item.id)
        }
    }

    /** Pauses every running download of [country]. */
    fun pauseAll(country: CatalogCountryUi) {
        for (item in country.items) {
            if (stateOf(item.id) == CatalogItemState.Downloading) pause(item.id)
        }
    }

    /** Resumes every paused download of [country]. */
    fun resumeAll(country: CatalogCountryUi) {
        for (item in country.items) {
            if (stateOf(item.id) == CatalogItemState.Paused) download(item.id)
        }
    }

    /** Deletes every deletable item of [country]. */
    fun deleteAll(country: CatalogCountryUi) {
        for (item in country.items) {
            if (stateOf(item.id) != CatalogItemState.Idle) delete(item.id)
        }
    }

    /** Deletes all locally downloaded maps. */
    fun deleteAllLocal() {
        for (country in localCountries) deleteAll(country)
    }

    private fun onDownloadFinished(itemId: Long, errorCode: Int, autoResume: Boolean = false) {
        SdkCall.execute {
            val state = syncItemState(itemId)
            if (state == CatalogItemState.Completed) {
                refreshLocalContent()
            } else if (state == CatalogItemState.Idle &&
                errorCode != GemError.NoError && errorCode != GemError.Cancel && errorCode != GemError.Suspended
            ) {
                if (autoResume && isConnectionError(errorCode)) {
                    parkInterruptedDownload(itemId, localItemsById[itemId] ?: storeItemsById[itemId])
                } else {
                    reportItemError(itemId, errorCode)
                }
            }
        }
    }

    // Re-reads the item's status on the SDK thread and mirrors it into the state maps.
    private fun syncItemState(itemId: Long): CatalogItemState {
        val sdkItem = storeItemsById[itemId] ?: localItemsById[itemId] ?: return CatalogItemState.Idle
        val state = sdkItem.toCatalogState()
        val progress = sdkItem.downloadProgress
        Util.postOnMain {
            itemStates[itemId] = state
            when (state) {
                CatalogItemState.Downloading, CatalogItemState.Paused -> itemProgress[itemId] = progress
                else -> itemProgress.remove(itemId)
            }
            notifyContentChanged()
        }
        return state
    }

    private fun reportItemError(itemId: Long, errorCode: Int) {
        val name = (onlineCountries.asSequence() + localCountries.asSequence())
            .flatMap { it.items }
            .firstOrNull { it.id == itemId }
            ?.name
            .orEmpty()
        Util.postOnMain {
            errorMessage.value = getApplication<Application>().getString(
                R.string.item_download_error,
                name,
                SdkCall.runSynced { GemError.getMessage(errorCode, getApplication()) },
            )
        }
    }

    // endregion

    // region world map update

    /**
     * Handles a tap on the map-update card: an idle card runs an on-demand update
     * check ([checkForMapsUpdate]), a card already reporting an available update
     * starts the update flow. Without internet neither can be performed at all:
     * report that right away instead of engaging the engine (which would accept
     * the update and park it).
     */
    fun onUpdateCardTapped() {
        when (updateMode) {
            MapUpdateCardMode.Check ->
                if (isOnline) {
                    checkForMapsUpdate()
                } else {
                    errorMessage.value = requiresInternetMessage()
                }

            MapUpdateCardMode.UpdateAvailable ->
                if (isOnline) {
                    startMapsUpdate(userRequested = true)
                } else {
                    errorMessage.value = requiresInternetMessage()
                }

            else -> Unit // Already checking/updating/applying; the card just reports progress.
        }
    }

    private fun requiresInternetMessage(): String =
        getApplication<Application>().getString(R.string.update_check_requires_internet)

    /**
     * Asks the store whether a newer world map exists. The verdict arrives through
     * `SdkSettings.onWorldwideRoadMapSupportStatus` with `checkOnDemand = true`: old
     * data flips the card to [MapUpdateCardMode.UpdateAvailable] (via
     * [onRoadMapSupportStatus]), up-to-date data is confirmed by the activity with an
     * info dialog. Only a rejected check request is reported here.
     */
    private fun checkForMapsUpdate() {
        SdkCall.execute {
            val errorCode = contentStore.checkForUpdate(EContentType.RoadMap)
            if (errorCode != GemError.NoError) {
                val details = GemError.getMessage(errorCode, getApplication())
                Util.postOnMain {
                    errorMessage.value = getApplication<Application>().getString(R.string.update_check_error, details)
                }
            }
        }
    }

    /**
     * Cancels the running world-map update and restores the update card. The engine
     * also reports the cancellation through [updateListener], which posts the same
     * final state.
     */
    fun cancelMapsUpdate() {
        SdkCall.execute {
            val contentUpdater = updater ?: return@execute
            updater = null
            updateUserRequested = false
            updateSilent = false
            contentUpdater.cancel()
            // A mid-flight update proves an update exists: the card returns to the
            // update-available entry, never to "check for update".
            updateAvailable = true
            Util.postOnMain {
                updateMode = MapUpdateCardMode.UpdateAvailable
                updateProgress = null
                notifyContentChanged()
            }
            refreshLocalContent()
        }
    }

    // Kept as a field: the update outlives startMapsUpdate, and a local listener could
    // be garbage collected before the SDK reports progress/completion.
    private val updateListener = ProgressListener.create(
        // From a plain check the engine always opens with a version verification:
        // showing "Updating" right away would flash a fake download (progress bar +
        // cancel) and collapse again when the maps turn out to be up to date. The
        // card switches to Updating once the engine reports an actual download
        // (status/progress notifications below). A flow started from the known
        // update-available hint already parked the card on Updating - keep it.
        onStarted = {
            if (!updateSilent) {
                Util.postOnMain {
                    if (updateMode != MapUpdateCardMode.Updating) {
                        updateMode = MapUpdateCardMode.Checking
                        updateProgress = null
                        notifyContentChanged()
                    }
                }
            }
        },
        onProgress = { percent ->
            if (!updateSilent) {
                Util.postOnMain {
                    // A progress tick means the transfer runs again after a connection loss.
                    if (updateMode == MapUpdateCardMode.WaitingConnection) {
                        updateMode = MapUpdateCardMode.Updating
                    }
                    updateProgress = percent
                    notifyContentChanged()
                }
            }
        },
        // With auto map update disabled, the updater downloads and then parks in a
        // ready state waiting for the app: apply explicitly or the flow never ends.
        // The connection statuses keep the card in sync while the engine waits out an
        // internet loss and resumes the transfer on its own.
        onStatusChanged = { status ->
            when (EContentUpdaterStatus.entries.firstOrNull { it.value == status }) {
                EContentUpdaterStatus.FullyReady,
                EContentUpdaterStatus.PartiallyReady,
                -> applyMapsUpdate()

                EContentUpdaterStatus.WaitConnection,
                EContentUpdaterStatus.WaitWIFIConnection,
                -> if (!updateSilent) {
                    // Offline the engine ACCEPTS update() and parks, reporting the
                    // missing connection only through this status. Park the card in
                    // WaitingConnection; the engine resumes the flow on its own once
                    // the connection is back.
                    Util.postOnMain {
                        if (updateMode == MapUpdateCardMode.Checking ||
                            updateMode == MapUpdateCardMode.Updating
                        ) {
                            updateMode = MapUpdateCardMode.WaitingConnection
                            notifyContentChanged()
                        }
                    }
                }

                EContentUpdaterStatus.CheckForUpdate,
                -> if (!updateSilent) {
                    Util.postOnMain {
                        if (updateMode == MapUpdateCardMode.WaitingConnection) {
                            updateMode = MapUpdateCardMode.Checking
                            notifyContentChanged()
                        }
                    }
                }

                EContentUpdaterStatus.Download,
                EContentUpdaterStatus.DownloadRemainingContent,
                EContentUpdaterStatus.DownloadPendingContent,
                -> if (!updateSilent) {
                    Util.postOnMain {
                        if (updateMode == MapUpdateCardMode.Checking ||
                            updateMode == MapUpdateCardMode.WaitingConnection
                        ) {
                            updateMode = MapUpdateCardMode.Updating
                            notifyContentChanged()
                        }
                    }
                }

                else -> Unit
            }
        },
        onCompleted = { errorCode, _ -> onUpdateFinished(errorCode) },
    )

    // Applies a fully/partially downloaded world-map update. Runs on the SDK thread.
    private fun applyMapsUpdate() {
        SdkCall.execute {
            val contentUpdater = updater ?: return@execute
            if (!contentUpdater.canApply()) return@execute
            if (!updateSilent) {
                Util.postOnMain {
                    updateMode = MapUpdateCardMode.Applying
                    notifyContentChanged()
                }
            }
            reportMapsUpdateApplied(contentUpdater.apply())
        }
    }

    // The "up to date" confirmation is tied to the apply outcome: a failed apply is
    // reported instead of being passed off as up to date. A silent flow reports
    // neither outcome. Runs on the SDK thread (GemError.getMessage requires it).
    private fun reportMapsUpdateApplied(applyError: Int) {
        if (updateSilent) return
        if (applyError == GemError.NoError) {
            Util.postOnMain {
                infoMessage.value = getApplication<Application>().getString(R.string.maps_up_to_date)
            }
        } else {
            val details = GemError.getMessage(applyError, getApplication())
            Util.postOnMain {
                errorMessage.value = getApplication<Application>().getString(R.string.maps_update_apply_error, details)
                // The engine does not always report a rejected apply through the
                // listener; without this reset the card would park on "Applying".
                updateMode = if (updateAvailable) MapUpdateCardMode.UpdateAvailable else MapUpdateCardMode.Check
                updateProgress = null
                notifyContentChanged()
            }
        }
    }

    // A silent flow (no maps on the device to go stale) runs the whole update without
    // any UI: the card stays on "check for update", progress and outcomes are muted.
    private fun startMapsUpdate(userRequested: Boolean = false, silent: Boolean = false) {
        // Immediate feedback on the card; a restart from the waiting-connection state
        // keeps its card until the engine reports how the resumed update continues.
        // From the update-available hint the flow opens directly as Updating: the
        // check already happened, announcing "Checking" again would contradict the
        // card the user just tapped.
        if (!silent) {
            Util.postOnMain {
                when (updateMode) {
                    MapUpdateCardMode.Check -> {
                        updateMode = MapUpdateCardMode.Checking
                        notifyContentChanged()
                    }

                    MapUpdateCardMode.UpdateAvailable -> {
                        updateMode = MapUpdateCardMode.Updating
                        updateProgress = null
                        notifyContentChanged()
                    }

                    else -> Unit
                }
            }
        }

        SdkCall.execute {
            val creation = contentStore.createContentUpdater(EContentType.RoadMap)
            val contentUpdater = creation?.first
            val createError = creation?.second ?: GemError.General
            if (contentUpdater == null || (createError != GemError.NoError && createError != GemError.Exist)) {
                if (!silent) {
                    Util.postOnMain {
                        updateMode = idleUpdateMode()
                        notifyContentChanged()
                    }
                }
                return@execute
            }
            updater = contentUpdater
            updateUserRequested = userRequested
            updateSilent = silent

            when (val updateError = contentUpdater.update(true, updateListener)) {
                GemError.NoError -> {
                    // A silent flow reports nothing; the engine drives it (and waits
                    // out a missing connection) on its own from here.
                    if (silent) return@execute
                    // Report where the accepted update actually stands: a fresh check,
                    // a resumed mid-flight download (seed its progress instead of
                    // showing 0% until the next tick), or - without internet - parked
                    // in a wait-connection state.
                    val progress = contentUpdater.progress
                    val mode = when (contentUpdater.status) {
                        EContentUpdaterStatus.WaitConnection,
                        EContentUpdaterStatus.WaitWIFIConnection,
                        -> MapUpdateCardMode.WaitingConnection

                        EContentUpdaterStatus.Download,
                        EContentUpdaterStatus.DownloadRemainingContent,
                        EContentUpdaterStatus.DownloadPendingContent,
                        -> MapUpdateCardMode.Updating

                        else -> MapUpdateCardMode.Checking
                    }
                    Util.postOnMain {
                        // Same rule as the WaitConnection status notification below:
                        // an explicit check the engine cannot even start offline is an
                        // error, not a parked update.
                        if (userRequested && mode == MapUpdateCardMode.WaitingConnection) {
                            errorMessage.value = requiresInternetMessage()
                            cancelMapsUpdate()
                        } else {
                            // A flow started from the update-available hint keeps its
                            // Updating card through the engine's opening version
                            // verification instead of falling back to "Checking".
                            val shown = if (mode == MapUpdateCardMode.Checking &&
                                updateMode == MapUpdateCardMode.Updating
                            ) {
                                MapUpdateCardMode.Updating
                            } else {
                                mode
                            }
                            updateMode = shown
                            updateProgress = if (shown == MapUpdateCardMode.Updating) progress else null
                            notifyContentChanged()
                        }
                    }
                }

                GemError.UpToDate -> {
                    updateAvailable = false
                    updateUserRequested = false
                    updateSilent = false
                    if (silent) return@execute
                    Util.postOnMain {
                        updateMode = MapUpdateCardMode.Check
                        notifyContentChanged()
                        // Confirmation only for an explicit check: the startup
                        // auto-resume must not greet every session with an
                        // "up to date" message.
                        if (userRequested) {
                            infoMessage.value = getApplication<Application>().getString(R.string.maps_up_to_date)
                        }
                    }
                }

                // An explicit check without internet is answered right away instead of
                // parking the card on "waiting for connection"; the quiet flows keep
                // waiting and restart once the engine is connected again.
                GemError.ConnectionRequired, GemError.NoConnection -> {
                    if (silent) return@execute
                    Util.postOnMain {
                        if (userRequested) {
                            updateMode = idleUpdateMode()
                            errorMessage.value = requiresInternetMessage()
                        } else {
                            updateMode = MapUpdateCardMode.WaitingConnection
                        }
                        notifyContentChanged()
                    }
                }

                else -> {
                    updateSilent = false
                    if (silent) return@execute
                    Util.postOnMain {
                        updateMode = idleUpdateMode()
                        errorMessage.value = getApplication<Application>().getString(
                            R.string.catalog_download_error,
                            SdkCall.runSynced { GemError.getMessage(updateError, getApplication()) },
                        )
                        notifyContentChanged()
                    }
                }
            }
        }
    }

    // The card mode when no update flow runs: the "update available" hint survives
    // a failed or cancelled attempt.
    private fun idleUpdateMode(): MapUpdateCardMode =
        if (updateAvailable) MapUpdateCardMode.UpdateAvailable else MapUpdateCardMode.Check

    private fun onUpdateFinished(errorCode: Int) {
        SdkCall.execute {
            if (errorCode == GemError.NoError || errorCode == GemError.UpToDate) updateAvailable = false
            // A canceled mid-flight update proves an update exists (even when the
            // engine never got to report road-map support, e.g. offline).
            if (errorCode == GemError.Cancel) updateAvailable = true
            val userRequested = updateUserRequested
            val silent = updateSilent
            updateUserRequested = false
            updateSilent = false
            updater = null
            if (!silent) {
                Util.postOnMain {
                    // The usual end of an explicit check on current maps: the engine
                    // accepts update() and only reports "up to date" through this
                    // completion, so the confirmation dialog belongs here too.
                    if (errorCode == GemError.UpToDate && userRequested) {
                        infoMessage.value = getApplication<Application>().getString(R.string.maps_up_to_date)
                    }
                    // A canceled update is still available; anything else resets the card.
                    updateMode = if (errorCode == GemError.Cancel) {
                        MapUpdateCardMode.UpdateAvailable
                    } else {
                        MapUpdateCardMode.Check
                    }
                    updateProgress = null
                    notifyContentChanged()
                }
            }
            refreshLocalContent()
        }
    }

    /**
     * Resumes a world-map update interrupted by a previous app shutdown. The engine
     * persists the update, so re-creating the content updater picks it up: any status
     * other than [EContentUpdaterStatus.Idle] means an update is mid-flight
     * (downloading, waiting for a connection or ready to apply). Called right after
     * the SDK initialized successfully — no connection is needed to resume: without
     * internet update() parks in a wait-connection state, the card reports it and
     * the engine restarts the transfer on its own once the connection is back.
     * With no maps on the device the resumed flow stays silent, like the one
     * [onRoadMapSupportStatus] starts.
     */
    fun resumeMapsUpdate() {
        SdkCall.execute {
            val contentUpdater = contentStore.createContentUpdater(EContentType.RoadMap)?.first
            if (contentUpdater != null && contentUpdater.status != EContentUpdaterStatus.Idle) {
                startMapsUpdate(silent = contentStore.getLocalContentList(EContentType.RoadMap).isNullOrEmpty())
            }
        }
    }

    /**
     * Resumes item downloads interrupted by a previous app shutdown, once per session
     * as soon as the SDK is ready and connected. Partially downloaded maps come back
     * in the local content list as incomplete items whose transfer just needs to be
     * restarted. While a resumed world-map update runs, the engine parks these
     * downloads and runs them once the update finished. A resume attempt that fails
     * for lack of connection parks the item as paused instead of being reported as
     * an error; a tap on the item restarts it.
     */
    private fun resumeInterruptedTransfers() {
        if (autoResumeAttempted) return
        autoResumeAttempted = true

        SdkCall.execute {
            for (item in contentStore.getLocalContentList(EContentType.RoadMap).orEmpty()) {
                if (item.isCompleted()) continue
                localItemsById[item.id] = item
                startDownload(item.id, item, autoResume = true)
            }
        }
    }

    // endregion

    // region search

    /**
     * Filters [countries] by the current [query] (diacritics- and case-insensitive,
     * matching only at word starts): a matching country keeps all its maps,
     * otherwise only its matching maps are kept.
     */
    fun filterCountries(countries: List<CatalogCountryUi>): List<CatalogCountryUi> {
        val needle = normalize(query.trim())
        if (needle.isEmpty()) return countries

        return countries.mapNotNull { country ->
            if (matchesWordStart(normalize(country.name), needle)) return@mapNotNull country

            val matchingItems = country.items.filter { matchesWordStart(normalize(it.name), needle) }
            when {
                matchingItems.isEmpty() -> null
                matchingItems.size == country.items.size -> country
                else -> country.copy(
                    items = matchingItems,
                    detailText = getApplication<Application>().getString(
                        R.string.region_count,
                        matchingItems.size,
                        formatSize(matchingItems.sumOf { it.sizeBytes }),
                    ),
                )
            }
        }
    }

    /** True if [needle] occurs in [text] starting at the beginning of a word. */
    private fun matchesWordStart(text: String, needle: String): Boolean {
        var index = text.indexOf(needle)
        while (index >= 0) {
            if (index == 0 || !text[index - 1].isLetterOrDigit()) return true
            index = text.indexOf(needle, index + 1)
        }
        return false
    }

    private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(DIACRITICS_REGEX, "")
        .lowercase()

    // endregion

    /** Formats a byte count for display ("154 MB"). */
    fun formatSize(bytes: Long): String = Formatter.formatShortFileSize(getApplication(), bytes)

    private fun ContentStoreItem.toCatalogState(): CatalogItemState = when {
        isCompleted() || status == EContentStoreItemStatus.Completed -> CatalogItemState.Completed
        status == EContentStoreItemStatus.Paused -> CatalogItemState.Paused
        status == EContentStoreItemStatus.Unavailable -> CatalogItemState.Idle
        else -> CatalogItemState.Downloading
    }

    private companion object {
        const val FLAG_PIXEL_SIZE = 96
        val DIACRITICS_REGEX = Regex("\\p{Mn}+")
    }
}
