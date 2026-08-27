/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.voicescatalogcompose

import android.app.Application
import android.content.Context
import android.text.format.Formatter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import com.magiclane.sdk.compose.components.contentstore.CatalogItemState
import com.magiclane.sdk.compose.components.contentstore.CatalogStatus
import com.magiclane.sdk.compose.components.contentstore.CatalogTtsLanguageUi
import com.magiclane.sdk.compose.components.contentstore.CatalogVoiceContinentUi
import com.magiclane.sdk.compose.components.contentstore.CatalogVoiceGroupUi
import com.magiclane.sdk.compose.components.contentstore.CatalogVoiceUi
import com.magiclane.sdk.compose.components.contentstore.ContinentMapper
import com.magiclane.sdk.compose.components.contentstore.VoiceGender
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.ContentStoreItem
import com.magiclane.sdk.content.EContentStoreItemStatus
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.core.EVoiceType
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.MapDetails
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.io.File
import java.text.Collator
import java.text.Normalizer

/**
 * Drives the voices-catalog UI: loads the online human-voices catalog, tracks per-item
 * download state/progress, mirrors the locally downloaded voices and applies the
 * navigation guidance voice (a downloaded human voice or the device Text-to-Speech).
 *
 * All `ContentStoreItem` access happens on the SDK thread (via [SdkCall]); the UI reads
 * immutable snapshots plus two [androidx.compose.runtime.snapshots.SnapshotStateMap]s
 * keyed by item id, so a progress tick only recomposes the affected row.
 */
class VoicesCatalogViewModel(application: Application) : AndroidViewModel(application) {

    private val contentStore = ContentStore()
    private val mapDetails = MapDetails()

    // The applied voice survives app sessions: each selection is persisted (with its
    // display strings, shown from the first frame — see seedPersistedSelection) and
    // reapplied at the next startup (a human voice as soon as the local content is
    // read, a Text-to-Speech language once the device engine is ready).
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // SDK item handles by id. Store items serve download/pause; local items serve
    // delete/apply of content downloaded in previous sessions (same ids, distinct
    // native objects).
    private val storeItemsById = HashMap<Long, ContentStoreItem>()
    private val localItemsById = HashMap<Long, ContentStoreItem>()

    // Country flag images by ISO code, decoded once. SDK-thread owned (see flagFor).
    private val flagCache = HashMap<String, ImageBitmap?>()

    private var catalogRequested = false

    // Whether the online tab has been opened: the catalog is only requested for it. A
    // request that could not run yet (no connection) is served once the content
    // service reports connected.
    private var catalogWanted = false

    private var autoResumeAttempted = false

    // region UI state

    /** Live download state per item id. Rows fall back to [CatalogItemState.Idle]. */
    val itemStates = mutableStateMapOf<Long, CatalogItemState>()

    /** Live download progress (0..100) per item id. */
    val itemProgress = mutableStateMapOf<Long, Int>()

    var onlineStatus by mutableStateOf(CatalogStatus.Loading)
        private set

    var onlineGroups by mutableStateOf<List<CatalogVoiceGroupUi>>(emptyList())
        private set

    var onlineContinents by mutableStateOf<List<CatalogVoiceContinentUi>>(emptyList())
        private set

    var localGroups by mutableStateOf<List<CatalogVoiceGroupUi>>(emptyList())
        private set

    /**
     * Whether the SDK content service is connected, as reported through
     * `SdkSettings.onServiceStatusUpdated`. Gates the catalog request; the online tab
     * reports the missing connection while false.
     */
    var isOnline by mutableStateOf(false)
        private set

    /** True while the device Text-to-Speech engine guides (no human voice applied). */
    var isTtsSelected by mutableStateOf(true)
        private set

    /** Item id of the applied human voice, null while Text-to-Speech guides. */
    var selectedVoiceId by mutableStateOf<Long?>(null)
        private set

    /** Languages offered by the device Text-to-Speech engine, the expanded TTS item. */
    var ttsLanguages by mutableStateOf<List<CatalogTtsLanguageUi>>(emptyList())
        private set

    /** Code of the applied engine language, null while a human voice guides. */
    var selectedTtsCode by mutableStateOf<String?>(null)
        private set

    /** Name line of the "Selected Voice" indicator ("Adam" or "Text-to-Speech"). */
    var selectedVoiceName by mutableStateOf(application.getString(R.string.text_to_speech))
        private set

    /** Language line of the "Selected Voice" indicator ("English (Ireland)"). */
    var selectedVoiceLanguageText by mutableStateOf("")
        private set

    // The engine's default language (system TTS settings), null when it reports none.
    private var defaultTtsCode: String? = null

    // The startup selection (saved voice, else engine default, else Michael) is
    // applied once. Set on the SDK thread (early human-voice restore) and on the main
    // thread (TTS-gated default selection).
    @Volatile
    private var defaultVoiceApplied = false

    var errorMessage by mutableStateOf("")

    var infoMessage by mutableStateOf("")

    var query by mutableStateOf("")

    init {
        seedPersistedSelection()
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

    // region catalog loading

    /** Requests the online voices catalog (once) and builds the continent grouping. */
    private fun loadCatalog() {
        if (catalogRequested) return
        catalogRequested = true
        onlineStatus = CatalogStatus.Loading

        SdkCall.execute {
            val listener = ProgressListener.create(
                onCompleted = { errorCode, _ ->
                    if (errorCode == GemError.NoError) {
                        SdkCall.execute {
                            val items = contentStore.getStoreContentList(EContentType.HumanVoice)?.first
                            buildOnlineModels(items.orEmpty())
                        }
                    } else {
                        Util.postOnMain {
                            onlineStatus = CatalogStatus.Failed
                            catalogRequested = false
                            errorMessage = getApplication<Application>().getString(
                                R.string.catalog_download_error,
                                SdkCall.runSynced { GemError.getMessage(errorCode, getApplication()) },
                            )
                        }
                    }
                },
            )

            val errorCode = contentStore.asyncGetStoreContentList(EContentType.HumanVoice, listener)
            if (errorCode != GemError.NoError) {
                Util.postOnMain {
                    onlineStatus = CatalogStatus.Failed
                    catalogRequested = false
                }
            }
        }
    }

    // Snapshots the SDK items into UI models and groups them by language/country and
    // continent. Must run on the SDK thread.
    private fun buildOnlineModels(items: List<ContentStoreItem>) {
        storeItemsById.clear()

        val states = HashMap<Long, CatalogItemState>()
        val progress = HashMap<Long, Int>()
        for (item in items) {
            storeItemsById[item.id] = item
            states[item.id] = item.toCatalogState()
            progress[item.id] = item.downloadProgress
        }

        val groups = buildVoiceGroups(items)

        val continents = groups
            .groupBy { ContinentMapper.getContinent(it.countryCode) }
            .map { (continent, continentGroups) ->
                val countryCount = continentGroups.map { it.countryCode }.toSet().size
                val voiceCount = continentGroups.sumOf { it.voices.size }
                CatalogVoiceContinentUi(
                    name = continent,
                    countriesText = getApplication<Application>().resources.getQuantityString(
                        R.plurals.country_count,
                        countryCount,
                        countryCount,
                    ),
                    voicesText = getApplication<Application>().resources.getQuantityString(
                        R.plurals.voice_count,
                        voiceCount,
                        voiceCount,
                    ),
                    groups = continentGroups,
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
            onlineGroups = groups
            onlineContinents = continents
            onlineStatus = CatalogStatus.Ready
        }
    }

    // Groups the voices by country + language, the sections of both tabs. Must run on
    // the SDK thread.
    private fun buildVoiceGroups(items: List<ContentStoreItem>): List<CatalogVoiceGroupUi> {
        val groups = LinkedHashMap<String, MutableList<ContentStoreItem>>()
        for (item in items) {
            val code = item.countryCodes?.firstOrNull().orEmpty()
            val languageCode = item.language?.languageCode.orEmpty()
            groups.getOrPut("$code|$languageCode") { mutableListOf() }.add(item)
        }

        return groups.map { (key, groupItems) ->
            val code = key.substringBefore('|')
            val voices = groupItems.map { toVoiceUi(it, code) }
            val countryName = mapDetails.getCountryName(code).orEmpty()
            val countText = getApplication<Application>().resources.getQuantityString(
                R.plurals.voice_count,
                voices.size,
                voices.size,
            )
            CatalogVoiceGroupUi(
                key = key,
                countryCode = code,
                // Prefer the language display name ("Deutsch"); a voice without
                // language metadata is titled by its country.
                languageName = voices.first().languageName.ifEmpty { countryName },
                detailText = if (countryName.isEmpty()) {
                    countText
                } else {
                    getApplication<Application>().getString(R.string.group_detail, countryName, countText)
                },
                sizeText = formatSize(groupItems.sumOf { it.totalSize }),
                voices = voices,
            )
        }
    }

    // Must run on the SDK thread.
    private fun toVoiceUi(item: ContentStoreItem, countryCode: String): CatalogVoiceUi = CatalogVoiceUi(
        id = item.id,
        name = item.name.orEmpty(),
        gender = when (item.voiceParameter(PARAM_GENDER)?.lowercase()) {
            "female" -> VoiceGender.Female
            "male" -> VoiceGender.Male
            else -> null
        },
        // Prefer the native/endonym spelling ("Deutsch" over "German") reported by the
        // voice parameters, falling back to the localized language name.
        languageName = item.nativeLanguageName() ?: item.language?.name.orEmpty(),
        flag = flagFor(countryCode),
        sizeText = formatSize(item.totalSize),
        sizeBytes = item.totalSize,
        canDelete = item.canDeleteContent() && !item.isProtectedVoice(),
        isProtected = item.isProtectedVoice(),
    )

    // Voice metadata ("gender", "native_language") travels in the item's content
    // parameters. Must run on the SDK thread.
    private fun ContentStoreItem.voiceParameter(key: String): String? =
        contentParameters?.firstOrNull { it.key == key }?.valueString?.takeIf { it.isNotEmpty() }

    // Some catalog entries (e.g. the Malaysian and Farsi voices) carry the voice type
    // ("human") in their "native_language" parameter instead of a language name; such
    // a corrupted value is discarded so the localized language name is used instead.
    private fun ContentStoreItem.nativeLanguageName(): String? =
        voiceParameter(PARAM_NATIVE_LANGUAGE)?.takeUnless { it.equals(voiceParameter(PARAM_TYPE), ignoreCase = true) }

    // Michael is the SDK's built-in fallback voice, a critical resource that must
    // never be deleted. `canDeleteContent()` does not report it as protected (yet),
    // so it is recognized by name. Must run on the SDK thread.
    private fun ContentStoreItem.isProtectedVoice(): Boolean = name?.contains(PROTECTED_VOICE_NAME) == true

    // Must run on the SDK thread.
    private fun flagFor(code: String): ImageBitmap? {
        if (code.isEmpty()) return null
        return flagCache.getOrPut(code) {
            mapDetails.getCountryFlag(code)?.asBitmap(FLAG_PIXEL_SIZE, FLAG_PIXEL_SIZE)?.asImageBitmap()
        }
    }

    /** Rebuilds the offline tab content from the fully downloaded local voices. */
    fun refreshLocalContent() {
        SdkCall.execute {
            val items = contentStore.getLocalContentList(EContentType.HumanVoice).orEmpty()

            localItemsById.clear()
            val completed = mutableListOf<ContentStoreItem>()
            for (item in items) {
                // Every local item stays addressable (resume/delete), but only
                // completed voices are listed offline: an interrupted download belongs
                // to the catalog tab until it finishes.
                localItemsById[item.id] = item
                if (item.isCompleted()) completed.add(item)
            }

            // The local list comes back in download order; the offline sections are
            // sorted by their displayed title (locale-aware, "Österreich" ≤ "Polski")
            // so a new download lands at its alphabetical place, not at the end.
            val collator = Collator.getInstance()
            val groups = buildVoiceGroups(completed)
                .sortedWith(
                    compareBy<CatalogVoiceGroupUi, String>(collator) { it.languageName }
                        // Same language in several countries ("English"): by country.
                        .thenBy(collator) { it.detailText },
                )
            val completedIds = completed.map { it.id }

            Util.postOnMain {
                for (id in completedIds) {
                    if (itemStates[id] != CatalogItemState.Downloading) {
                        itemStates[id] = CatalogItemState.Completed
                    }
                }
                localGroups = groups
            }
            restoreSavedHumanVoice()
            readAppliedVoice()
        }
    }

    // endregion

    // region voice selection

    // Shows the persisted selection on the very first frame: the indicator must not
    // flash "Text-to-Speech" while the saved human voice is being reapplied. The
    // display strings were saved at selection time; the actual voice is (re)applied
    // asynchronously and confirms (or corrects) this seed.
    private fun seedPersistedSelection() {
        when (prefs.getString(PREF_SELECTED_TYPE, null)) {
            PREF_TYPE_TTS -> {
                selectedTtsCode = prefs.getString(PREF_SELECTED_TTS_CODE, null)
                selectedVoiceLanguageText = prefs.getString(PREF_SELECTED_LANGUAGE_TEXT, null).orEmpty()
            }

            PREF_TYPE_HUMAN -> {
                prefs.getString(PREF_SELECTED_NAME, null)?.let { name ->
                    isTtsSelected = false
                    selectedVoiceName = name
                    selectedVoiceLanguageText = prefs.getString(PREF_SELECTED_LANGUAGE_TEXT, null).orEmpty()
                }
            }
        }
    }

    /**
     * Reapplies a human voice saved by a previous session as soon as the local content
     * is known — unlike the Text-to-Speech paths it does not depend on the device
     * engine, so it must not wait for [onTtsPlayerReady]. Must run on the SDK thread;
     * the caller reads the applied state back afterward.
     */
    private fun restoreSavedHumanVoice() {
        if (defaultVoiceApplied) return
        if (prefs.getString(PREF_SELECTED_TYPE, null) != PREF_TYPE_HUMAN) return
        val path = prefs.getString(PREF_SELECTED_VOICE_PATH, null) ?: return
        if (!File(path).exists()) return
        defaultVoiceApplied = true
        SdkSettings.setVoiceByPath(path)
    }

    /**
     * Entry point called once the device Text-to-Speech engine is initialized: loads
     * the engine's languages and applies the startup selection — the voice saved by a
     * previous session, else the engine's default language if any, the SDK's built-in
     * human voice (Michael) otherwise.
     */
    fun onTtsPlayerReady() {
        SdkCall.execute {
            val languages = SoundPlayingService.getTTSLanguages().map { tts ->
                val region = tts.code.substringAfter('-', "")
                CatalogTtsLanguageUi(
                    code = tts.code,
                    // The engine reports variant names ("English - British"); the
                    // country line already disambiguates, so keep the language alone.
                    languageName = tts.name.substringBefore(" - "),
                    countryName = if (region.isEmpty()) "" else mapDetails.getCountryName(region).orEmpty(),
                )
            }
            val defaultCode = SoundPlayingService.ttsPlayer?.getDefaultLanguage()?.let { language ->
                val lang = language.languageCode.orEmpty()
                val region = language.regionCode.orEmpty()
                "$lang-$region".takeIf { lang.isNotEmpty() && region.isNotEmpty() }
            }

            Util.postOnMain {
                ttsLanguages = languages
                defaultTtsCode = defaultCode?.takeIf { code -> languages.any { it.code == code } }
                applyDefaultVoiceSelection()
            }
        }
    }

    /** Entry point called when the Text-to-Speech engine failed to initialize. */
    fun onTtsPlayerUnavailable() {
        Util.postOnMain {
            ttsLanguages = emptyList()
            defaultTtsCode = null
            applyDefaultVoiceSelection()
        }
    }

    // Applies the startup selection once the engine state is known: the voice saved
    // by a previous session; on the first run (or when the saved voice is gone) the
    // OS default engine language, or the SDK's built-in human voice when there is none.
    private fun applyDefaultVoiceSelection() {
        if (defaultVoiceApplied) return
        defaultVoiceApplied = true

        if (restoreSavedVoiceSelection()) return

        val default = ttsLanguages.firstOrNull { it.code == defaultTtsCode }
        if (default != null) selectTtsLanguage(default) else selectBuiltInVoice()
    }

    // Reapplies the selection persisted by a previous session. False when there is
    // none, or it can no longer be served (engine language gone, voice file deleted).
    private fun restoreSavedVoiceSelection(): Boolean {
        when (prefs.getString(PREF_SELECTED_TYPE, null)) {
            PREF_TYPE_TTS -> {
                val code = prefs.getString(PREF_SELECTED_TTS_CODE, null)
                val language = ttsLanguages.firstOrNull { it.code == code } ?: return false
                selectTtsLanguage(language)
                return true
            }

            PREF_TYPE_HUMAN -> {
                val path = prefs.getString(PREF_SELECTED_VOICE_PATH, null) ?: return false
                if (!File(path).exists()) return false
                SdkCall.execute {
                    SdkSettings.setVoiceByPath(path)
                    readAppliedVoice()
                }
                return true
            }
        }
        return false
    }

    // Applies the SDK's built-in human voice (Michael, the engine's non-deletable
    // fallback resource); the applied state is read back to select its local item.
    // Like any other selection it is persisted, so the next session restores it
    // directly instead of re-running the default-selection logic.
    private fun selectBuiltInVoice() {
        SdkCall.execute {
            val voice = SdkSettings.getBestVoiceMatch("eng", "USA")
                ?.firstOrNull { it.type == EVoiceType.Human }
            voice?.filename?.let { path ->
                SdkSettings.setVoiceByPath(path)
                // The display strings come from the voice's local item (the same
                // basename match as readAppliedVoice), the voice itself as fallback.
                val fileName = File(path).name
                val item = localItemsById.values.firstOrNull { candidate ->
                    candidate.isCompleted() && candidate.fileName?.let { File(it).name } == fileName
                }
                persistVoiceSelection(
                    PREF_TYPE_HUMAN,
                    PREF_SELECTED_VOICE_PATH,
                    path,
                    name = item?.name ?: voice.name.orEmpty(),
                    languageText = item?.let { humanVoiceLanguageText(it) }.orEmpty(),
                )
                readAppliedVoice()
            }
        }
    }

    // Mirrors the engine's applied voice into the UI state: a computer voice (or none)
    // means Text-to-Speech, a human voice is matched to a local item by file name.
    // Must run on the SDK thread.
    private fun readAppliedVoice() {
        val voice = SdkSettings.voice
        if (voice == null || voice.type == EVoiceType.Computer) {
            val code = voice?.language?.let { language ->
                val lang = language.languageCode.orEmpty()
                val region = language.regionCode.orEmpty()
                "$lang-$region".takeIf { lang.isNotEmpty() && region.isNotEmpty() }
            }
            Util.postOnMain {
                // Adopt the reported language only when the engine list offers it: an
                // applied computer voice reports a placeholder region ("ron-ZZZ" for
                // the "ron-ROU" selection), and blindly taking that over would drop
                // the list selection; null keeps the current one.
                applyTtsSelectionState(code?.takeIf { c -> ttsLanguages.any { it.code == c } })
            }
            return
        }

        val appliedFile = voice.filename?.let { File(it).name }
        val applied = localItemsById.values.firstOrNull { item ->
            item.isCompleted() && item.fileName?.let { File(it).name } == appliedFile
        }
        if (applied == null) {
            Util.postOnMain { applyTtsSelectionState(null) }
            return
        }

        val ui = toVoiceUi(applied, applied.countryCodes?.firstOrNull().orEmpty())
        val languageText = humanVoiceLanguageText(applied)
        Util.postOnMain {
            isTtsSelected = false
            selectedVoiceId = applied.id
            selectedTtsCode = null
            selectedVoiceName = ui.name
            selectedVoiceLanguageText = languageText
        }
    }

    /** Applies the downloaded human voice [voice] as the navigation guidance voice. */
    fun selectVoice(voice: CatalogVoiceUi) {
        SdkCall.execute {
            val item = localItemsById[voice.id] ?: storeItemsById[voice.id] ?: return@execute
            val path = item.fileName ?: return@execute
            SdkSettings.setVoiceByPath(path)
            val languageText = humanVoiceLanguageText(item)
            persistVoiceSelection(PREF_TYPE_HUMAN, PREF_SELECTED_VOICE_PATH, path, voice.name, languageText)
            Util.postOnMain {
                isTtsSelected = false
                selectedVoiceId = voice.id
                selectedTtsCode = null
                selectedVoiceName = voice.name
                selectedVoiceLanguageText = languageText
            }
        }
    }

    /** Applies the device Text-to-Speech engine [language] as the guidance voice. */
    fun selectTtsLanguage(language: CatalogTtsLanguageUi) {
        SdkCall.execute {
            SoundPlayingService.setTTSLanguage(language.code)
            persistVoiceSelection(
                PREF_TYPE_TTS,
                PREF_SELECTED_TTS_CODE,
                language.code,
                name = null,
                languageText = languageWithCountry(language.languageName, language.countryName),
            )
            Util.postOnMain {
                isTtsSelected = true
                selectedVoiceId = null
                selectedTtsCode = language.code
                selectedVoiceName = getApplication<Application>().getString(R.string.text_to_speech)
                selectedVoiceLanguageText = languageWithCountry(language.languageName, language.countryName)
            }
        }
    }

    // Persists the applied selection for the next session: its type plus the value
    // needed to reapply it (engine language code or voice file path), and the display
    // strings shown while it is being reapplied (see seedPersistedSelection).
    private fun persistVoiceSelection(
        type: String,
        valueKey: String,
        value: String,
        name: String?,
        languageText: String,
    ) {
        prefs.edit {
            putString(PREF_SELECTED_TYPE, type)
                .putString(valueKey, value)
                .putString(PREF_SELECTED_NAME, name)
                .putString(PREF_SELECTED_LANGUAGE_TEXT, languageText)
        }
    }

    private fun applyTtsSelectionState(code: String?) {
        isTtsSelected = true
        selectedVoiceId = null
        if (code != null) selectedTtsCode = code
        selectedVoiceName = getApplication<Application>().getString(R.string.text_to_speech)
        val applied = code ?: selectedTtsCode
        selectedVoiceLanguageText = when {
            applied == null -> ""
            else -> ttsLanguages.firstOrNull { it.code == applied }
                ?.let { languageWithCountry(it.languageName, it.countryName) }
                // The engine languages may not be listed yet (startup); the persisted
                // text seeded for this selection stays until they are.
                ?: selectedVoiceLanguageText
        }
    }

    // "English (Ireland)" — the voice's language plus its country. Must run on the SDK
    // thread (country lookup).
    private fun humanVoiceLanguageText(item: ContentStoreItem): String {
        val languageName = item.nativeLanguageName() ?: item.language?.name.orEmpty()
        val countryCode = item.countryCodes?.firstOrNull().orEmpty()
        val countryName = if (countryCode.isEmpty()) "" else mapDetails.getCountryName(countryCode).orEmpty()
        return languageWithCountry(languageName, countryName)
    }

    private fun languageWithCountry(language: String, country: String): String = when {
        language.isEmpty() -> country
        country.isEmpty() -> language
        else -> getApplication<Application>().getString(R.string.language_with_country, language, country)
    }

    // endregion

    // region item actions

    /** Starts (or resumes) the download of a voice. */
    fun download(itemId: Long) {
        val sdkItem = storeItemsById[itemId] ?: localItemsById[itemId] ?: return
        startDownload(itemId, sdkItem)
    }

    // Starts the transfer unconditionally (also used to resume interrupted downloads).
    // An [autoResume] transfer was not requested by the user right now, so a
    // connection failure is not an error worth a popup: the download stays paused and
    // is retried on the next connected notification.
    private fun startDownload(itemId: Long, sdkItem: ContentStoreItem, autoResume: Boolean = false) {
        SdkCall.execute {
            val listener = ProgressListener.create(
                onStarted = {
                    Util.postOnMain { itemStates[itemId] = CatalogItemState.Downloading }
                },
                onProgress = { percent ->
                    Util.postOnMain {
                        itemStates[itemId] = CatalogItemState.Downloading
                        itemProgress[itemId] = percent
                    }
                },
                onCompleted = { errorCode, _ -> onDownloadFinished(itemId, errorCode, autoResume) },
            )

            when (val errorCode = sdkItem.asyncDownload(listener, GemSdk.EDataSavePolicy.UseDefault, true)) {
                GemError.NoError -> Util.postOnMain {
                    itemStates[itemId] = CatalogItemState.Downloading
                    itemProgress[itemId] = sdkItem.downloadProgress
                }

                GemError.UpToDate -> Util.postOnMain { itemStates[itemId] = CatalogItemState.Completed }

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
     * Deletes the local content of a voice (downloaded, downloading or paused). The
     * applied voice is never deleted — guidance must not point at a file that is gone —
     * and neither is Michael, the SDK's critical built-in fallback voice. Both are
     * (re)checked here, at delete time, not through the UI's snapshotted flags.
     */
    fun delete(itemId: Long) {
        if (selectedVoiceId == itemId) return
        val sdkItem = localItemsById[itemId] ?: storeItemsById[itemId] ?: return
        SdkCall.execute {
            if (sdkItem.isProtectedVoice()) return@execute
            sdkItem.cancelDownload()
            sdkItem.deleteContent()
            Util.postOnMain {
                itemStates[itemId] = CatalogItemState.Idle
                itemProgress.remove(itemId)
            }
            refreshLocalContent()
        }
    }

    /** Starts downloading every voice of [group] that is not downloaded or running. */
    fun downloadAll(group: CatalogVoiceGroupUi) {
        for (voice in group.voices) {
            val state = stateOf(voice.id)
            if (state == CatalogItemState.Idle || state == CatalogItemState.Paused) download(voice.id)
        }
    }

    /** Pauses every running download of [group]. */
    fun pauseAll(group: CatalogVoiceGroupUi) {
        for (voice in group.voices) {
            if (stateOf(voice.id) == CatalogItemState.Downloading) pause(voice.id)
        }
    }

    /** Resumes every paused download of [group]. */
    fun resumeAll(group: CatalogVoiceGroupUi) {
        for (voice in group.voices) {
            if (stateOf(voice.id) == CatalogItemState.Paused) download(voice.id)
        }
    }

    /**
     * Deletes every voice of [group] that has local content. [delete] itself skips
     * the applied voice and the SDK's protected resources, so a "Delete All" can
     * never remove them.
     */
    fun deleteAll(group: CatalogVoiceGroupUi) {
        for (voice in group.voices) {
            if (stateOf(voice.id) != CatalogItemState.Idle) delete(voice.id)
        }
    }

    /**
     * Deletes all locally downloaded human voices — and nothing else: the
     * Text-to-Speech languages are not content, and the applied voice and the SDK's
     * built-in fallback survive (see [delete]).
     */
    fun deleteAllLocal() {
        for (group in localGroups) deleteAll(group)
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
        }
        return state
    }

    private fun reportItemError(itemId: Long, errorCode: Int) {
        val name = (onlineGroups.asSequence() + localGroups.asSequence())
            .flatMap { it.voices }
            .firstOrNull { it.id == itemId }
            ?.name
            .orEmpty()
        Util.postOnMain {
            errorMessage = getApplication<Application>().getString(
                R.string.item_download_error,
                name,
                SdkCall.runSynced { GemError.getMessage(errorCode, getApplication()) },
            )
        }
    }

    /**
     * Resumes voice downloads interrupted by a previous app shutdown, once per session
     * as soon as the SDK is ready and connected. Partially downloaded voices come back
     * in the local content list as incomplete items whose transfer just needs to be
     * restarted. A resume attempt that fails for lack of connection parks the item as
     * paused instead of being reported as an error; a tap on the item restarts it.
     */
    private fun resumeInterruptedTransfers() {
        if (autoResumeAttempted) return
        autoResumeAttempted = true

        SdkCall.execute {
            for (item in contentStore.getLocalContentList(EContentType.HumanVoice).orEmpty()) {
                if (item.isCompleted()) continue
                localItemsById[item.id] = item
                startDownload(item.id, item, autoResume = true)
            }
        }
    }

    // endregion

    // region search

    /**
     * Filters the voice groups by the current [query] (diacritics- and
     * case-insensitive, matching at word starts only): a group matching by language or
     * country keeps all its voices, otherwise only its matching voices (by speaker or
     * language name) are kept.
     */
    fun filterGroups(groups: List<CatalogVoiceGroupUi>): List<CatalogVoiceGroupUi> {
        val needle = normalize(query.trim())
        if (needle.isEmpty()) return groups

        return groups.mapNotNull { group ->
            if (matchesWordStart(group.languageName, needle) || matchesWordStart(group.detailText, needle)) {
                return@mapNotNull group
            }

            val matching = group.voices.filter {
                matchesWordStart(it.name, needle) || matchesWordStart(it.languageName, needle)
            }
            when {
                matching.isEmpty() -> null
                matching.size == group.voices.size -> group
                else -> group.copy(voices = matching)
            }
        }
    }

    /**
     * Filters the device engine languages by the current [query] (diacritics- and
     * case-insensitive, matching language or country name), the Text-to-Speech section
     * of the offline search results. Empty while no search is active.
     */
    fun filterTtsLanguages(): List<CatalogTtsLanguageUi> {
        val needle = normalize(query.trim())
        if (needle.isEmpty()) return emptyList()

        return ttsLanguages.filter {
            matchesWordStart(it.languageName, needle) || matchesWordStart(it.countryName, needle)
        }
    }

    /** True if any word of [text] starts with the already-normalized [needle]. */
    private fun matchesWordStart(text: String, needle: String): Boolean {
        val haystack = normalize(text)
        var index = haystack.indexOf(needle)
        while (index >= 0) {
            if (index == 0 || !haystack[index - 1].isLetterOrDigit()) return true
            index = haystack.indexOf(needle, index + 1)
        }
        return false
    }

    private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(DIACRITICS_REGEX, "")
        .lowercase()

    // endregion

    /** Formats a byte count for display ("12 MB"). */
    fun formatSize(bytes: Long): String = Formatter.formatShortFileSize(getApplication(), bytes)

    private fun ContentStoreItem.toCatalogState(): CatalogItemState = when {
        isCompleted() || status == EContentStoreItemStatus.Completed -> CatalogItemState.Completed
        status == EContentStoreItemStatus.Paused -> CatalogItemState.Paused
        status == EContentStoreItemStatus.Unavailable -> CatalogItemState.Idle
        else -> CatalogItemState.Downloading
    }

    private companion object {
        const val FLAG_PIXEL_SIZE = 96
        const val PARAM_GENDER = "gender"
        const val PROTECTED_VOICE_NAME = "Michael"
        const val PREFS_NAME = "voice_selection"
        const val PREF_SELECTED_TYPE = "selected_voice_type"
        const val PREF_SELECTED_TTS_CODE = "selected_tts_code"
        const val PREF_SELECTED_VOICE_PATH = "selected_voice_path"
        const val PREF_SELECTED_NAME = "selected_voice_name"
        const val PREF_SELECTED_LANGUAGE_TEXT = "selected_language_text"
        const val PREF_TYPE_TTS = "tts"
        const val PREF_TYPE_HUMAN = "human"
        const val PARAM_NATIVE_LANGUAGE = "native_language"
        const val PARAM_TYPE = "type"
        val DIACRITICS_REGEX = Regex("\\p{Mn}+")
    }
}
