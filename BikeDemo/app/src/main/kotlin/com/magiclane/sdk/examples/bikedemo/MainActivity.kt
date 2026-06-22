/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.replace
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ImageDatabase
import com.magiclane.sdk.core.Parameter
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.core.XyF
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.examples.bikedemo.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.bikedemo.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.EAddressField
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.routesandnavigation.EBikeProfile
import com.magiclane.sdk.routesandnavigation.ENavigationStatus
import com.magiclane.sdk.routesandnavigation.ERouteStatus
import com.magiclane.sdk.routesandnavigation.NavigationInstruction
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.sensordatasource.ESConfigKeys
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.SdkImages
import com.magiclane.sdk.util.Util
import com.magiclane.sound.SoundUtils
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener {

    private lateinit var binding: ActivityMainBinding

    // Snapshot of portrait constraints — cloned once at inflation so landscape tweaks always
    // start from a clean baseline regardless of the current orientation.
    private lateinit var portraitConstraintSet: ConstraintSet

    // UI dimensions derived from resources / insets
    private var searchIconSize = 0
    private var turnImageSize: Int = 0
    private var topInset = 0
    private var leftInset = 0
    private var rightInset = 0
    private var inflate = 0
    private var appBarHeight = 0
    private var bottomDialogHeight = 0 // height of the currently-visible bottom panel, px

    private var searchFilter = ""

    // Index of the selected category chip (NO_CATEGORY when none), the reference point used for
    // around-position searches and distance display, and a guard to ignore programmatic edits
    // to the search field (e.g. when a category name is written into it).
    private var activeCategoryIndex = CategoryAdapter.NO_CATEGORY
    private var isProgrammaticQuery = false
    private var reference: Coordinates? = null
    private var categoryIconSize = 0

    private var routesList = ArrayList<Route>()
    private var navigationStatus = ENavigationStatus.Running
    private var lastTurnImageId: Long = Long.MAX_VALUE
    private var shouldCheckLocationPermissionOnResume = false
    val isSearching = AtomicBoolean(false)

    private val checkAuthorizationListener = ProgressListener.create(
        onCompleted = { errorCode, _ ->
            if (errorCode != GemError.NoError) showInvalidTokenDialog()
        },
    )

    private val routingService = RoutingService(
        onStarted = { showRoutingProgress() },
        onCompleted = { routes, errorCode, _ -> handleRoutingCompleted(routes, errorCode) },
        onStatusChanged = { status -> handleRoutingStatusChanged(status) },
    )

    private val viewModel: MainActivityViewModel by viewModels()
    private val searchAdapter = SearchAdapter()
    private val categoryAdapter = CategoryAdapter()
    private val navigationService = NavigationService()
    private val searchService = SearchService()
    private val playingListener = object : SoundPlayingListener() {}
    private val soundPreference = SoundPlayingPreferences()

    private lateinit var positionListener: PositionListener
    private var searchJob: Job? = null

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    companion object {
        private const val REQUEST_PERMISSIONS = 110
        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val ANIMATION_DURATION_MS = 900
        private const val HIGHLIGHT_ALPHA = 0.75
        private const val HIGHLIGHT_IMAGE_SIZE = 6.0

        // Fraction of total screen width occupied by left-side panels in landscape.
        private const val ROUTE_PANEL_LANDSCAPE_RATIO = 0.45f
        private const val NAV_PANEL_LANDSCAPE_RATIO = 0.40f

        // Camera-focus x: 0.5 centres the GPS arrow on screen; 0.7 shifts it right so it sits
        // in the free map area when navigation panels occupy the left side in landscape.
        private const val CAMERA_FOCUS_X_CENTER = 0.5f
        private const val CAMERA_FOCUS_X_SHIFTED = 0.7f
        private const val CAMERA_FOCUS_Y = 0.75f
    }

    //region Navigation

    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = { handleNavigationStarted() },
        onNavigationInstructionUpdated = { instr -> updateNavigationInstruction(instr) },
        onDestinationReached = { onNavigationEnded() },
        onNotifyStatusChange = { status ->
            navigationStatus = status
            refreshStatusMessage()
        },
        onNavigationError = { error -> onNavigationEnded(error) },
        onNavigationSound = { sound ->
            SdkCall.execute {
                SoundPlayingService.play(sound, playingListener, soundPreference)
            }
        },
        canPlayNavigationSound = true,
    )

    private val navigationProgressListener = ProgressListener.create(
        onStatusChanged = { refreshStatusMessage() },
    )

    //endregion

    //region Routing

    private fun refreshStatusMessage() {
        val statusMessage = getStatusMessage()
        binding.turnContainer.isVisible = statusMessage.isEmpty()

        if (statusMessage.isNotEmpty()) {
            binding.navInstruction.text = statusMessage
        }
    }

    // Helper methods for routing service callbacks
    private fun showRoutingProgress() {
        binding.progressBar.visibility = View.VISIBLE
        binding.cancelButton.visibility = View.VISIBLE
    }

    private fun handleRoutingCompleted(routes: ArrayList<Route>, errorCode: ErrorCode) {
        binding.progressBar.visibility = View.GONE
        binding.cancelButton.visibility = View.GONE

        when (errorCode) {
            GemError.NoError -> processSuccessfulRoute(routes)
            GemError.Cancel -> { /* Routing action was cancelled */ }
            else -> showDialog(
                getString(R.string.routing_error, SdkCall.runSynced { GemError.getMessage(errorCode, this) }),
            )
        }
    }

    private fun handleRoutingStatusChanged(status: Int) {
        if (status == ERouteStatus.WaitingInternetConnection.value) {
            showDialog(getString(R.string.internet_required))
        }
    }

    private fun processSuccessfulRoute(routes: ArrayList<Route>) = SdkCall.execute {
        routesList = routes

        if (routesList.isNotEmpty()) {
            val title = viewModel.destination?.let {
                getString(R.string.route_to, GemUtil.formatName(it))
            } ?: ""
            val message = formatRouteName(routesList[0])

            showStartNavigationDialog(
                title,
                message,
                onStartNavigation = { startNavigationWithRoute() },
                onStartSimulation = { startSimulationWithRoute() },
                onViewCreated = { presentRoutesOnMap() },
                onViewClosed = { clearRoutesAndHideDialog() },
            )
        }
    }

    private fun startNavigationWithRoute() = SdkCall.execute {
        binding.gemSurfaceView.mapView?.preferences?.routes?.mainRoute?.let { mainRoute ->
            val error = navigationService.startNavigationWithRoute(
                mainRoute,
                navigationListener,
                navigationProgressListener,
            )

            if (error != GemError.NoError) {
                Util.postOnMain {
                    showDialog(
                        getString(
                            R.string.route_navigation_error,
                            SdkCall.runSynced { GemError.getMessage(error, this) },
                        ),
                    )
                }
            }

            clearRoutesData()
        }
    }

    private fun startSimulationWithRoute() = SdkCall.execute {
        binding.gemSurfaceView.mapView?.preferences?.routes?.mainRoute?.let { mainRoute ->
            val error = navigationService.startSimulationWithRoute(
                mainRoute,
                navigationListener,
                navigationProgressListener,
            )

            if (error != GemError.NoError) {
                Util.postOnMain {
                    showDialog(
                        getString(
                            R.string.route_simulation_error,
                            SdkCall.runSynced { GemError.getMessage(error, this) },
                        ),
                    )
                }
            }

            clearRoutesData()
        }
    }

    private fun presentRoutesOnMap() {
        binding.mapSearchBar.isVisible = false

        // Compute UI-dependent values on the main thread before the SDK call.
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val panelWidth = if (isLandscape) (resources.displayMetrics.widthPixels * 0.45f).toInt() else 0
        val leftEdge = if (isLandscape) panelWidth else leftInset
        val bottomEdge = if (!isLandscape) bottomDialogHeight + inflate else inflate
        val topEdge = getTopInset()
        val rightEdge = rightInset

        SdkCall.execute {
            binding.gemSurfaceView.mapView?.presentRoutes(
                routesList,
                displayBubble = true,
                displayTollIcon = false,
                displayFerryIcon = false,
                displayTrafficIcon = false,
                edgeAreaInsets = Rect(leftEdge, topEdge, rightEdge, bottomEdge),
            )
        }
    }

    private fun clearRoutesAndHideDialog() {
        binding.mapSearchBar.isVisible = true
        SdkCall.execute {
            binding.gemSurfaceView.mapView?.preferences?.routes?.clear()
            routesList.clear()
        }
    }

    private fun clearRoutesData() {
        binding.gemSurfaceView.mapView?.preferences?.routes?.clear()
        routesList.clear()
    }

    //endregion

    //region Navigation callbacks

    private fun handleNavigationStarted() {
        SdkCall.execute {
            binding.gemSurfaceView.mapView?.let { mapView ->
                navRoute?.let { route ->
                    mapView.presentRoute(route)
                }
                mapView.followPosition()
            }
        }
        binding.apply {
            mapSearchBar.isVisible = false
            topPanel.isVisible = true
            bottomPanel.isVisible = true
        }
        applyCameraFocus()
        binding.mapRoot.post { updateFocusViewport() }
    }

    private fun updateNavigationInstruction(instr: NavigationInstruction) {
        data class NavigationData(
            val instrText: String = "",
            val instrIcon: Bitmap? = null,
            val sameTurnImage: Boolean = false,
            val instructionDistance: String = "",
            val etaText: String = "",
            val rttText: String = "",
            val rtdText: String = "",
        )

        val navData = SdkCall.execute {
            val instrText = instr.nextStreetName?.takeIf { it.isNotEmpty() }
                ?: instr.nextTurnInstruction ?: ""

            var sameTurnImage = false
            val instrIcon = getNextTurnImage(instr, turnImageSize, turnImageSize) { isSame ->
                sameTurnImage = isSame
            }

            val instructionDistance = instr.getDistanceInMeters()

            val (etaText, rttText, rtdText) = navRoute?.let {
                Triple(it.getEta(), it.getRtt(), it.getRtd())
            } ?: Triple("", "", "")

            NavigationData(instrText, instrIcon, sameTurnImage, instructionDistance, etaText, rttText, rtdText)
        } ?: return

        binding.apply {
            if (!navData.sameTurnImage) {
                navIcon.setImageBitmap(navData.instrIcon)
            }
            navInstruction.text = navData.instrText
            instrDistance.text = navData.instructionDistance
            eta.text = navData.etaText
            rtt.text = navData.rttText
            rtd.text = navData.rtdText
        }
    }

    //endregion

    //region Search

    private fun performSearch(filter: String) {
        searchJob = CoroutineScope(Dispatchers.IO).launch {
            delay(SEARCH_DEBOUNCE_MS)

            SdkCall.postAsync {
                setSearchReferencePoint()
                isSearching.set(true)

                // Re-enable free-text address search after a category-only search disabled it.
                if (!searchService.preferences.searchAddressesEnabled) {
                    searchService.preferences.removeAllCategoryFilters()
                    searchService.preferences.searchAddressesEnabled = true
                }

                searchService.searchByFilter(
                    textFilter = filter,
                    reference = reference,
                    onCompleted = { results, errorCode, _ ->
                        isSearching.set(false)
                        when (errorCode) {
                            GemError.Cancel -> return@searchByFilter
                            GemError.NoError -> {
                                SdkCall.execute {
                                    viewModel.searchResultListLivedata.postValue(buildSearchItems(results))
                                }
                            }
                            else -> viewModel.searchResultListLivedata.postValue(mutableListOf())
                        }
                    },
                )
            }
        }
    }

    // Selects a category chip and runs an around-position search restricted to it. Tapping the
    // already-selected chip is a no-op.
    private fun selectCategory(index: Int) {
        if (activeCategoryIndex == index) return
        activeCategoryIndex = index
        viewModel.selectedCategory.value = index

        if (isSearching.compareAndSet(true, false)) {
            SdkCall.postAsync { searchService.cancelSearch() }
        }
        searchJob?.cancel()

        if (index == CategoryAdapter.NO_CATEGORY) return

        binding.searchProgressBar.isInvisible = false

        SdkCall.postAsync {
            searchService.preferences.removeAllCategoryFilters()
            searchService.preferences.searchAddressesEnabled = false
            setSearchReferencePoint()

            viewModel.categoriesLivedata.value?.getOrNull(index)?.let { cat ->
                searchService.preferences.landmarkStores?.addStoreCategoryId(cat.landmarkStoreId, cat.categoryId)

                isSearching.set(true)
                searchService.searchAroundPosition(
                    reference = reference,
                    onCompleted = { results, errorCode, _ ->
                        isSearching.set(false)
                        when (errorCode) {
                            GemError.Cancel -> return@searchAroundPosition
                            GemError.NoError -> {
                                SdkCall.execute {
                                    viewModel.searchResultListLivedata.postValue(buildSearchItems(results))
                                }
                            }
                            else -> viewModel.searchResultListLivedata.postValue(mutableListOf())
                        }
                    },
                )
            }
        }
    }

    // Uses the current GPS position as the reference for searches and distance display when valid.
    private fun setSearchReferencePoint() {
        val position = PositionService.position
        if (position?.isValid() == true) {
            reference = position.coordinates
        }
    }

    // Maps SDK landmarks to list items, computing the distance from the reference point.
    private fun buildSearchItems(landmarks: ArrayList<Landmark>): MutableList<SearchResultItem> =
        landmarks.map { landmark ->
            val meters = reference?.let { landmark.coordinates?.getDistance(it)?.toInt() } ?: 0
            val dist = GemUtil.getDistText(meters, EUnitSystem.Metric, true)
            SearchResultItem(
                bmp = landmark.image?.asBitmap(searchIconSize, searchIconSize),
                text = GemUtil.formatName(landmark),
                subText = GemUtil.getLandmarkDescription(landmark, true),
                distance = dist.first,
                unit = dist.second,
                landmark = landmark,
            )
        }.toMutableList()

    //endregion

    //region Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        SoundUtils.addTTSPlayerInitializationListener(this)

        turnImageSize = resources.getDimension(R.dimen.turn_image_size).toInt()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EspressoIdlingResource.increment()

        portraitConstraintSet = ConstraintSet().also { it.clone(binding.mapRoot) }

        searchIconSize = resources.getDimension(R.dimen.icon_size).toInt()
        categoryIconSize = resources.getDimension(R.dimen.category_icon_size).toInt()
        inflate = resources.getDimension(R.dimen.padding_40).toInt()

        // Measure app bar height after layout
        binding.appBarLayout.post {
            appBarHeight = binding.appBarLayout.height
        }

        // Apply initial orientation layout and manage GPS button margins after first layout pass
        binding.mapRoot.post {
            applyOrientationLayout()
            updateFollowGpsButtonMargins(isAnyPanelVisible())
        }

        // Set up window insets listener
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topInset = systemBars.top + inflate
            leftInset = systemBars.left + inflate
            rightInset = systemBars.right + inflate
            updateFollowGpsButtonMargins(isAnyPanelVisible())
            insets
        }

        registerSdkListeners()

        // Defines an action that should be done when the world map is ready (Updated/ loaded).
        searchAdapter.setOnViewHolderClickListener { item ->
            val itemHasValidCoordinates = SdkCall.execute { item.landmark.coordinates != null } ?: false
            if (!itemHasValidCoordinates) {
                return@setOnViewHolderClickListener
            }

            binding.mapSearchView.hide()
            viewModel.destination = item.landmark

            SdkCall.execute {
                showCalculateRouteDialog(
                    item.text ?: "",
                    item.subText ?: "",
                    onCalculateRoute = {
                        SdkCall.execute {
                            val position = PositionService.position
                            if ((position != null) && position.isValid()) {
                                val departure =
                                    Landmark(getString(R.string.my_position), position.latitude, position.longitude)
                                val destination = item.landmark
                                calculateRoute(departure, destination)
                            } else {
                                runOnUiThread { showDialog(getString(R.string.current_position_not_available)) }
                            }
                        }
                    },
                    onViewCreated = {
                        highlightLandmarkOnMap(viewModel.destination!!)
                    },
                    onViewClosed = {
                        deactivateHighlights()
                    },
                )
            }
        }

        EspressoIdlingResource.decrement()

        if (checkLocationStatus()) {
            requestPermissions()
        }

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }

        binding.mapSearchView.setupWithSearchBar(binding.mapSearchBar)

        binding.mapSearchView.editText.addTextChangedListener {
            // Ignore edits we make programmatically (e.g. writing a category name into the field).
            if (isProgrammaticQuery) return@addTextChangedListener

            val filter = it.toString().trim()
            if (filter == searchFilter && activeCategoryIndex == CategoryAdapter.NO_CATEGORY) {
                return@addTextChangedListener
            }

            searchFilter = filter

            // Typing clears any active category selection.
            if (activeCategoryIndex != CategoryAdapter.NO_CATEGORY) {
                activeCategoryIndex = CategoryAdapter.NO_CATEGORY
                viewModel.selectedCategory.value = CategoryAdapter.NO_CATEGORY
            }

            if (isSearching.compareAndSet(true, false)) {
                SdkCall.postAsync { searchService.cancelSearch() }
            }

            binding.searchProgressBar.isInvisible = searchFilter.isBlank()
            searchJob?.cancel()

            if (searchFilter.isNotBlank()) {
                performSearch(searchFilter)
            } else {
                viewModel.searchResultListLivedata.postValue(mutableListOf())
            }
        }

        viewModel.isElectricBikeProfile.observe(this) {
            invalidateOptionsMenu()
        }

        setSupportActionBar(binding.mapSearchBar)

        binding.searchResultsList.apply {
            adapter = searchAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        binding.categoriesView.apply {
            adapter = categoryAdapter
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            itemAnimator = null
        }
        categoryAdapter.setOnCategoryClickListener { index -> selectCategory(index) }

        viewModel.searchResultListLivedata.observe(this) {
            searchAdapter.submitList(it)
            binding.searchProgressBar.isInvisible = true
            binding.noResultsTextView.isVisible =
                it.isEmpty() && (searchFilter.isNotBlank() || activeCategoryIndex != CategoryAdapter.NO_CATEGORY)
        }

        viewModel.categoriesLivedata.observe(this) { items ->
            categoryAdapter.submitList(items)
        }

        viewModel.selectedCategory.observe(this) { selectedIndex ->
            categoryAdapter.setSelectedIndex(selectedIndex)
            if (selectedIndex != CategoryAdapter.NO_CATEGORY) {
                val name = viewModel.categoriesLivedata.value?.getOrNull(selectedIndex)?.name ?: ""
                isProgrammaticQuery = true
                binding.mapSearchView.editText.setText(name)
                binding.mapSearchView.editText.setSelection(name.length)
                searchFilter = name
                isProgrammaticQuery = false
            }
        }

        binding.cancelButton.setOnClickListener {
            SdkCall.execute {
                routingService.cancelRoute()
            }
            binding.progressBar.visibility = View.GONE
        }

        onBackPressedDispatcher.addCallback(this) {
            if (binding.fragmentContainer.getFragment<BikeSettingsFragment?>() != null) {
                supportFragmentManager.beginTransaction().remove(binding.fragmentContainer.getFragment()).commit()
                return@addCallback
            }

            if (binding.calculateRoutePanel.root.isVisible) {
                dismissPanel(binding.calculateRoutePanel.root)
                deactivateHighlights()
                return@addCallback
            }

            if (binding.cancelButton.isVisible) {
                SdkCall.execute {
                    routingService.cancelRoute()
                }
                binding.cancelButton.visibility = View.GONE
                binding.progressBar.visibility = View.GONE
                return@addCallback
            }

            if (binding.startNavigationPanel.root.isVisible) {
                dismissPanel(binding.startNavigationPanel.root)
                binding.followGpsButton.visibility = View.VISIBLE
                binding.mapSearchBar.isVisible = true
                SdkCall.execute { binding.gemSurfaceView.mapView?.preferences?.routes?.clear() }
                return@addCallback
            }

            var navigationIsActive = false
            var simulationIsActive = false

            SdkCall.execute {
                navigationIsActive = navigationService.isNavigationActive()
                simulationIsActive = navigationService.isSimulationActive()
            }

            if (navigationIsActive || simulationIsActive) {
                SdkCall.execute { navigationService.cancelNavigation() }
                return@addCallback
            }

            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (shouldCheckLocationPermissionOnResume) {
            shouldCheckLocationPermissionOnResume = false
            if (isLocationEnabled()) {
                requestPermissions()
            } else {
                showDialog(getString(R.string.location_services_required)) {
                    finish()
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.mapRoot.post {
            applyOrientationLayout()
            updateFollowGpsButtonMargins(isAnyPanelVisible())
            applyCameraFocus()
            updateFocusViewport()
        }
    }

    //region Orientation & layout

    private fun applyOrientationLayout() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // applyTo() resets every child of mapRoot to its XML-declared visibility and to the
        // margins that were live at clone time (i.e. before window insets were dispatched — all
        // zeros). Save visibilities and inset-adjusted margins now and restore them afterwards.
        val calcVis = binding.calculateRoutePanel.root.visibility
        val navVis = binding.startNavigationPanel.root.visibility
        val fabVis = binding.followGpsButton.visibility
        val topPanelVis = binding.topPanel.visibility
        val bottomPanelVis = binding.bottomPanel.visibility
        val cancelVis = binding.cancelButton.visibility
        val progressVis = binding.progressBar.visibility

        val topLp = binding.topPanel.layoutParams as? ConstraintLayout.LayoutParams
        val savedTopTopMargin = topLp?.topMargin ?: 0
        val savedTopLeftMargin = topLp?.leftMargin ?: 0
        val savedTopRightMargin = topLp?.rightMargin ?: 0

        val bottomLp = binding.bottomPanel.layoutParams as? ConstraintLayout.LayoutParams
        val savedBottomBottomMargin = bottomLp?.bottomMargin ?: 0
        val savedBottomLeftMargin = bottomLp?.leftMargin ?: 0
        val savedBottomRightMargin = bottomLp?.rightMargin ?: 0

        ConstraintSet().apply {
            clone(portraitConstraintSet)
            if (isLandscape) {
                val screenWidth = resources.displayMetrics.widthPixels
                val routePanelWidth = (screenWidth * ROUTE_PANEL_LANDSCAPE_RATIO).toInt()
                val navPanelWidth = (screenWidth * NAV_PANEL_LANDSCAPE_RATIO).toInt()
                val appBar = getAppBarHeight()

                // Route panels: pin to full left-side height below the app bar.
                // constrainHeight(0) = MATCH_CONSTRAINT, which requires both TOP and BOTTOM edges.
                for (panelId in listOf(R.id.calculate_route_panel, R.id.start_navigation_panel)) {
                    constrainWidth(panelId, routePanelWidth)
                    constrainHeight(panelId, 0)
                    connect(panelId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                    connect(panelId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, appBar)
                    connect(panelId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0)
                    clear(panelId, ConstraintSet.END)
                }
                // Navigation panels: keep their fixed portrait heights, left-aligned at 40% width.
                for (panelId in listOf(R.id.top_panel, R.id.bottom_panel)) {
                    constrainWidth(panelId, navPanelWidth)
                    connect(panelId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                    clear(panelId, ConstraintSet.END)
                }
                // GPS button: portrait layout already anchors END; only BOTTOM needs re-pinning.
                clear(R.id.follow_gps_button, ConstraintSet.BOTTOM)
                connect(R.id.follow_gps_button, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0)
            }
        }.applyTo(binding.mapRoot)

        binding.calculateRoutePanel.root.visibility = calcVis
        binding.startNavigationPanel.root.visibility = navVis
        binding.followGpsButton.visibility = fabVis
        binding.topPanel.visibility = topPanelVis
        binding.bottomPanel.visibility = bottomPanelVis
        binding.cancelButton.visibility = cancelVis
        binding.progressBar.visibility = progressVis

        // Restore only margins — widths are already correct from applyTo().
        (binding.topPanel.layoutParams as? ConstraintLayout.LayoutParams)?.let {
            it.topMargin = savedTopTopMargin
            it.leftMargin = savedTopLeftMargin
            it.rightMargin = savedTopRightMargin
            binding.topPanel.layoutParams = it
        }
        (binding.bottomPanel.layoutParams as? ConstraintLayout.LayoutParams)?.let {
            it.bottomMargin = savedBottomBottomMargin
            it.leftMargin = savedBottomLeftMargin
            it.rightMargin = savedBottomRightMargin
            binding.bottomPanel.layoutParams = it
        }
    }

    private fun updateFollowGpsButtonMargins(panelVisible: Boolean) {
        val params = binding.followGpsButton.layoutParams as? ConstraintLayout.LayoutParams ?: return
        val padding = resources.getDimensionPixelSize(R.dimen.padding_10)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val insets = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val bottomInset = insets?.bottom ?: 0
        val rightInset = insets?.right ?: 0
        val targetBottomMargin = if (!isLandscape && panelVisible) bottomDialogHeight + padding else bottomInset + padding
        val targetEndMargin = rightInset + padding
        if (params.bottomMargin != targetBottomMargin || params.marginEnd != targetEndMargin) {
            params.bottomMargin = targetBottomMargin
            params.marginEnd = targetEndMargin
            binding.followGpsButton.layoutParams = params
        }
    }

    //endregion

    //region Map focus & camera

    private fun isAnyPanelVisible(): Boolean =
        binding.calculateRoutePanel.root.isVisible || binding.startNavigationPanel.root.isVisible

    /**
     * Hides [panel], resets the stored dialog height, and refreshes the GPS-button margins and
     * the SDK focus viewport. Call whenever a bottom-sheet panel is dismissed.
     */
    private fun dismissPanel(panel: View) {
        panel.visibility = View.GONE
        bottomDialogHeight = 0
        updateFollowGpsButtonMargins(isAnyPanelVisible())
        updateFocusViewport()
    }

    private fun applyCameraFocus() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val navPanelVisible = binding.topPanel.isVisible
        SdkCall.execute {
            binding.gemSurfaceView.mapView?.preferences?.followPositionPreferences?.cameraFocus =
                if (isLandscape && navPanelVisible) {
                    XyF(CAMERA_FOCUS_X_SHIFTED, CAMERA_FOCUS_Y)
                } else {
                    XyF(CAMERA_FOCUS_X_CENTER, CAMERA_FOCUS_Y)
                }
        }
    }

    private fun updateFocusViewport() {
        SdkCall.runSynced {
            binding.gemSurfaceView.mapView?.preferences?.focusViewport = getFocusViewport()
        }
    }

    private fun getFocusViewport(): Rect {
        val root = binding.root
        val insets = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

        val width = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        return if (isLandscape) {
            // Landscape: panels are left-aligned; the free map area starts at the rightmost
            // visible panel's right edge.
            val w = maxOf(width, height)
            val h = minOf(width, height)
            val left = maxOf(
                if (binding.topPanel.isVisible) binding.topPanel.right else 0,
                if (binding.calculateRoutePanel.root.isVisible) binding.calculateRoutePanel.root.right else 0,
                if (binding.startNavigationPanel.root.isVisible) binding.startNavigationPanel.root.right else 0,
                insets?.left ?: 0,
            )
            val top = insets?.top ?: 0
            val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            val bottom = (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
            Rect(left, top, right, bottom)
        } else {
            // Portrait: panels are at the top and bottom; the free map area is the strip
            // between the lowest top-panel edge and the highest bottom-panel edge.
            val w = minOf(width, height)
            val h = maxOf(width, height)
            val left = insets?.left ?: 0
            val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            val top = if (binding.topPanel.isVisible) binding.topPanel.bottom else insets?.top ?: 0
            val bottom = minOf(
                if (binding.bottomPanel.isVisible) binding.bottomPanel.top else h,
                if (binding.calculateRoutePanel.root.isVisible) binding.calculateRoutePanel.root.top else h,
                if (binding.startNavigationPanel.root.isVisible) binding.startNavigationPanel.root.top else h,
                h - (insets?.bottom ?: 0),
            ).coerceAtLeast(top)
            Rect(left, top, right, bottom)
        }
    }

    //endregion

    private fun highlightLandmarkOnMap(landmark: Landmark) = SdkCall.execute {
        binding.gemSurfaceView.mapView?.let { mapView ->
            val rect = getFreeScreenRect()

            mapView.deactivateAllHighlights()

            landmark.image = ImageDatabase().getImageById(SdkImages.Core.Search_Results_Pin.value)

            val contour = landmark.getContourGeographicArea()
            val highlightSettings: HighlightRenderSettings

            if ((contour != null) && !contour.isEmpty()) {
                mapView.centerOnRectArea(
                    contour,
                    zoomLevel = -1,
                    viewRc = rect,
                    Animation(EAnimation.Linear, ANIMATION_DURATION_MS),
                )

                highlightSettings = HighlightRenderSettings(
                    EHighlightOptions.ShowContour.value or EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value,
                    Rgba(255, 98, 0, 255),
                    Rgba(255, 98, 0, 255),
                    HIGHLIGHT_ALPHA,
                ).apply {
                    imageSize = HIGHLIGHT_IMAGE_SIZE
                }
            } else {
                highlightSettings = HighlightRenderSettings(
                    EHighlightOptions.ShowLandmark.value or EHighlightOptions.Overlap.value,
                ).apply {
                    imageSize = HIGHLIGHT_IMAGE_SIZE
                }

                landmark.coordinates?.let {
                    mapView.centerOnCoordinates(
                        it,
                        -1,
                        rect.center,
                        Animation(EAnimation.Linear, ANIMATION_DURATION_MS),
                        0.0,
                        0.0,
                    )
                }
            }

            mapView.activateHighlightLandmarks(landmark, highlightSettings)
        }
    }

    private fun deactivateHighlights() = SdkCall.execute {
        binding.gemSurfaceView.mapView?.deactivateAllHighlights()
    }

    private fun getFreeScreenRect(): Rect {
        val mapWidth = binding.gemSurfaceView.width
        val mapHeight = binding.gemSurfaceView.height

        if (mapWidth <= 0 || mapHeight <= 0) {
            val fallbackWidth = binding.gemSurfaceView.measuredWidth
            val fallbackHeight = binding.gemSurfaceView.measuredHeight
            return Rect(0, 0, fallbackWidth, fallbackHeight)
        }

        val insets = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val leftSysInset = insets?.left ?: 0
        val rightSysInset = insets?.right ?: 0
        val bottomSysInset = insets?.bottom ?: 0

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val padding = resources.getDimensionPixelSize(R.dimen.map_free_space_padding)
        val topLimit = getAppBarHeight()

        val left: Int
        val right: Int
        val bottom: Int

        if (isLandscape) {
            left = when {
                binding.calculateRoutePanel.root.isVisible ->
                    (binding.calculateRoutePanel.root.right - binding.gemSurfaceView.left).coerceAtLeast(leftSysInset)
                binding.startNavigationPanel.root.isVisible ->
                    (binding.startNavigationPanel.root.right - binding.gemSurfaceView.left).coerceAtLeast(leftSysInset)
                else -> leftSysInset
            }
            right = (mapWidth - rightSysInset).coerceAtLeast(left + 1)
            bottom = (mapHeight - bottomSysInset).coerceAtLeast(topLimit + 1)
        } else {
            left = leftSysInset.coerceIn(0, (mapWidth - 1).coerceAtLeast(0))
            right = (mapWidth - rightSysInset).coerceIn(left + 1, mapWidth)
            val bottomLimitRaw = when {
                binding.calculateRoutePanel.root.isVisible ->
                    binding.calculateRoutePanel.root.top - binding.gemSurfaceView.top
                binding.startNavigationPanel.root.isVisible ->
                    binding.startNavigationPanel.root.top - binding.gemSurfaceView.top
                else -> mapHeight
            }
            bottom = bottomLimitRaw.coerceIn(topLimit + 1, mapHeight)
        }

        val paddedLeft = (left + padding).coerceAtMost(right - 1)
        val paddedRight = (right - padding).coerceAtLeast(paddedLeft + 1)
        val paddedTop = (topLimit + padding).coerceAtMost(bottom - 1)
        val paddedBottom = (bottom - padding).coerceAtLeast(paddedTop + 1)

        return Rect(paddedLeft, paddedTop, paddedRight, paddedBottom)
    }

    //endregion

    //region Options menu

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.run {
            val electric = findItem(R.id.e_bike_type)
            electric.isVisible = viewModel.isElectric
            val notElectric = findItem(R.id.bike_type)
            notElectric.isVisible = !viewModel.isElectric
            val icon = if (viewModel.isElectric) electric else notElectric
            icon.icon = ContextCompat.getDrawable(
                this@MainActivity,
                when (viewModel.bikeProfile) {
                    EBikeProfile.City -> if (viewModel.isElectric) R.drawable.ebikecity else R.drawable.bikecity
                    EBikeProfile.Cross -> if (viewModel.isElectric) R.drawable.ebikecross else R.drawable.bikecross
                    EBikeProfile.Mountain -> if (viewModel.isElectric) R.drawable.ebikemountain else R.drawable.bikemountain
                    EBikeProfile.Road -> if (viewModel.isElectric) R.drawable.ebikeroad else R.drawable.bikeroad
                },
            )
        }
        binding.mapSearchBar.setOnMenuItemClickListener { menuItem: MenuItem? ->
            val bikeTypeButton = binding.mapSearchBar.menu.findItem(
                if (viewModel.isElectric) R.id.e_bike_type else R.id.bike_type,
            )
            when (menuItem?.itemId) {
                R.id.bike_city, R.id.e_bike_city -> {
                    bikeTypeButton.icon = ContextCompat.getDrawable(
                        this,
                        if (viewModel.isElectric) R.drawable.ebikecity else R.drawable.bikecity,
                    )
                    viewModel.setBikeProfile(EBikeProfile.City)
                }

                R.id.bike_cross, R.id.e_bike_cross -> {
                    bikeTypeButton.icon = ContextCompat.getDrawable(
                        this,
                        if (viewModel.isElectric) R.drawable.ebikecross else R.drawable.bikecross,
                    )
                    viewModel.setBikeProfile(EBikeProfile.Cross)
                }

                R.id.bike_mountain, R.id.e_bike_mountain -> {
                    bikeTypeButton.icon = ContextCompat.getDrawable(
                        this,
                        if (viewModel.isElectric) R.drawable.ebikemountain else R.drawable.bikemountain,
                    )
                    viewModel.setBikeProfile(EBikeProfile.Mountain)
                }

                R.id.bike_road, R.id.e_bike_road -> {
                    bikeTypeButton.icon = ContextCompat.getDrawable(
                        this,
                        if (viewModel.isElectric) R.drawable.ebikeroad else R.drawable.bikeroad,
                    )
                    viewModel.setBikeProfile(EBikeProfile.Road)
                }

                R.id.settings -> {
                    supportFragmentManager.beginTransaction().replace<BikeSettingsFragment>(
                        R.id.fragment_container,
                    ).commit()
                }

                else -> {}
            }
            true
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.search_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    //endregion

    override fun onDestroy() {
        clearSdkListeners()
        super.onDestroy()
        SoundUtils.removeTTSPlayerInitializationListener(this)
        GemSdk.release()
        exitProcess(0)
    }

    //endregion

    //region SDK listeners

    private fun registerSdkListeners() {
        // Clear the callback after the first successful connection so reconnects don't
        // re-run the authorization check.
        SdkSettings.onConnectionStatusUpdated = { isConnected ->
            if (isConnected) {
                SdkSettings.appAuthorization?.let {
                    SdkCall.execute { SdkSettings.verifyAppAuthorization(it, checkAuthorizationListener) }
                } ?: run {
                    runOnAliveUi { showInvalidTokenDialog() }
                }
                SdkSettings.onConnectionStatusUpdated = {}
            }
        }

        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_init_failed, GemError.getMessage(error, this))
            runOnAliveUi {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = { mapView ->
            mapView.followPosition()

            if (PositionService.position?.isValid() == true) {
                Util.postOnMain { enableGPSButton() }
            } else {
                positionListener = PositionListener {
                    if (!it.isValid()) return@PositionListener

                    PositionService.removeListener(positionListener)
                    Util.postOnMain { enableGPSButton() }
                }
                PositionService.addListener(positionListener, EDataType.Position)
            }

            viewModel.initPreferences()
            viewModel.loadCategories(categoryIconSize)

            val parametersList =
                arrayListOf(
                    Parameter(ESConfigKeys.Position.ImprovedPosPreferRouteSnap, "1"),
                    Parameter(ESConfigKeys.Position.ImprovedPositionDefTransportMode, "bike"),
                )
            PositionService.dataSource?.setPreferences(EDataType.Position, parametersList)

            Util.postOnMain {
                binding.apply {
                    mapView.onTouch = { xy ->
                        SdkCall.execute {
                            if (navigationService.isNavigationActive() || navigationService.isSimulationActive()) {
                                return@execute
                            }

                            mapView.cursorScreenPosition = xy

                            val routes = gemSurfaceView.mapView?.cursorSelectionRoutes
                            if (!routes.isNullOrEmpty()) {
                                // set the touched route as the main route and center on it
                                mapView.preferences?.routes?.mainRoute = routes[0]
                                mapView.centerOnRoutes(
                                    routesList,
                                    viewRc = getFreeScreenRect(),
                                    animation = Animation(EAnimation.Linear, 900),
                                )

                                return@execute
                            }

                            var landmark: Landmark? = null

                            val landmarks = mapView.cursorSelectionLandmarks
                            if (!landmarks.isNullOrEmpty()) {
                                landmark = landmarks[0]
                            } else {
                                val overlays = mapView.cursorSelectionOverlayItems
                                if (!overlays.isNullOrEmpty()) {
                                    val overlay = overlays[0]
                                    overlay.coordinates?.let {
                                        val name = when {
                                            !overlay.name.isNullOrEmpty() -> overlay.name!!
                                            !overlay.overlayInfo?.name.isNullOrEmpty() -> overlay.overlayInfo?.name!!
                                            else -> "Unknown"
                                        }

                                        landmark = Landmark(
                                            name = name,
                                            latitude = it.latitude,
                                            longitude = it.longitude,
                                        ).apply {
                                            image = overlay.image
                                            description = getLandmarkDescription(mapView, it)
                                        }
                                    }
                                }
                            }

                            landmark?.let { presentLandmarkForRouting(it) }
                        }
                    }

                    // Long press: route to the closest street under the cursor.
                    mapView.onLongDown = { xy ->
                        SdkCall.execute {
                            if (navigationService.isNavigationActive() || navigationService.isSimulationActive()) {
                                return@execute
                            }

                            mapView.cursorScreenPosition = xy

                            val streets = mapView.cursorSelectionStreets
                            if (!streets.isNullOrEmpty()) {
                                presentLandmarkForRouting(streets[0])
                            }
                        }
                    }

                    invalidateOptionsMenu()
                    updateFocusViewport()
                }
            }
        }

        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            Util.postOnMain { updateFocusViewport() }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showInvalidTokenDialog() }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onConnectionStatusUpdated = {}
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

    /** Posts [block] to the main thread only if the activity hasn't been destroyed. */
    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    //endregion

    //region GPS button

    private fun enableGPSButton() {
        binding.apply {
            gemSurfaceView.mapView?.apply {
                // User panned away: show the re-center button and hide navigation panels.
                // They reappear via onEnterFollowingPosition when the user taps the button.
                onExitFollowingPosition = {
                    followGpsButton.visibility = View.VISIBLE
                    updateFollowGpsButtonMargins(isAnyPanelVisible())
                    topPanel.visibility = View.GONE
                    bottomPanel.visibility = View.GONE
                    applyCameraFocus()
                    updateFocusViewport()
                }

                // User tapped the GPS button: hide it and restore navigation panels if a
                // route is active. Also dismiss any open calculate-route panel.
                onEnterFollowingPosition = {
                    followGpsButton.visibility = View.GONE
                    var navigationIsActive = false
                    var simulationIsActive = false

                    SdkCall.execute {
                        navigationIsActive = navigationService.isNavigationActive()
                        simulationIsActive = navigationService.isSimulationActive()
                    }

                    if (navigationIsActive || simulationIsActive) {
                        topPanel.visibility = View.VISIBLE
                        bottomPanel.visibility = View.VISIBLE
                    }

                    applyCameraFocus()

                    if (calculateRoutePanel.root.isVisible) {
                        calculateRoutePanel.root.visibility = View.GONE
                        deactivateHighlights()
                    }
                    mapRoot.post { updateFocusViewport() }
                }

                followGpsButton.setOnClickListener {
                    SdkCall.execute { followPosition() }
                }
            }
        }
    }

    //endregion

    //region Permissions

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return

        for (item in grantResults) {
            if (item != PackageManager.PERMISSION_GRANTED) {
                showDialog(getString(R.string.location_permission_required)) {
                    finish()
                }
                return
            }
        }

        SdkCall.execute {
            // Notice permission status had changed
            PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)
        }
    }

    private fun requestPermissions(): Boolean {
        val permissions = arrayListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        return PermissionsHelper.requestPermissions(
            REQUEST_PERMISSIONS,
            this,
            permissions.toTypedArray(),
        )
    }

    //endregion

    //region Map interaction & highlighting

    private fun calculateRoute(departure: Landmark, destination: Landmark) = SdkCall.execute {
        val waypoints = arrayListOf(
            departure,
            destination,
        )

        routingService.preferences = viewModel.routePreferences
        val error = routingService.calculateRoute(waypoints)
        if (error != GemError.NoError) {
            // The computation never started, so onStarted/onCompleted won't fire: clear any
            // progress UI and report the failure.
            val message = GemError.getMessage(error, this)
            runOnAliveUi {
                binding.progressBar.visibility = View.GONE
                binding.cancelButton.visibility = View.GONE
                showDialog(getString(R.string.routing_error, message))
            }
        }
    }

    // Presents [landmark] as a potential destination: opens the calculate-route panel, highlights
    // it on the map, and routes to it from the current position on confirmation. Must be called
    // from an SDK thread (e.g. inside an onTouch / onLongDown handler).
    private fun presentLandmarkForRouting(landmark: Landmark) {
        viewModel.destination = landmark
        val details = GemUtil.pairFormatLandmarkDetails(landmark, true)
        showCalculateRouteDialog(
            details.first,
            details.second,
            onCalculateRoute = {
                SdkCall.execute {
                    val position = PositionService.position
                    if ((position != null) && position.isValid()) {
                        val departure =
                            Landmark(getString(R.string.my_position), position.latitude, position.longitude)
                        calculateRoute(departure, landmark)
                    } else {
                        runOnUiThread { showDialog(getString(R.string.current_position_not_available)) }
                    }
                }
            },
            onViewCreated = {
                highlightLandmarkOnMap(landmark)
            },
            onViewClosed = {
                deactivateHighlights()
            },
        )
    }

    fun getAppBarHeight(): Int {
        if (!binding.mapSearchBar.isVisible) {
            return 0
        }

        // Return stored value if available
        if (appBarHeight > 0) {
            return appBarHeight
        }

        // Try to get current height
        val currentHeight = binding.appBarLayout.height
        if (currentHeight > 0) {
            appBarHeight = currentHeight
            return appBarHeight
        }

        // Fallback: measure the app bar layout
        binding.appBarLayout.measure(
            View.MeasureSpec.makeMeasureSpec(binding.appBarLayout.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return binding.appBarLayout.measuredHeight
    }

    //region Dialogs

    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            title.text = getString(R.string.error)
            message.text = text
            button.setOnClickListener {
                onDismiss?.invoke()
                dialog.dismiss()
            }
        }
        dialog.apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            setCancelable(false)
            setContentView(dialogBinding.root)
            show()
        }
    }

    private fun showCalculateRouteDialog(
        title: String,
        message: String,
        onCalculateRoute: () -> Unit,
        onViewCreated: (() -> Unit)? = null,
        onViewClosed: (() -> Unit)? = null,
    ) {
        Util.postOnMain {
            binding.calculateRoutePanel.apply {
                // Set the content
                this.title.text = title
                this.message.text = message

                closeButton.setOnClickListener {
                    dismissPanel(root)
                    onViewClosed?.invoke()
                }

                buttonCalculateRoute.setOnClickListener {
                    dismissPanel(root)
                    onViewClosed?.invoke()
                    onCalculateRoute()
                }

                // Show the panel
                root.visibility = View.VISIBLE

                // Measure height after it's shown
                root.post {
                    bottomDialogHeight = root.height
                    updateFollowGpsButtonMargins(isAnyPanelVisible())
                    updateFocusViewport()
                    onViewCreated?.invoke()
                }
            }
        }
    }

    private fun formatRouteName(route: Route): String = SdkCall.execute {
        val timeDistance = route.timeDistance ?: return@execute ""
        val distInMeters = timeDistance.totalDistance
        val timeInSeconds = timeDistance.totalTime
        val distTextPair = GemUtil.getDistText(distInMeters, SdkSettings.unitSystem, bHighResolution = true)
        val timeTextPair = GemUtil.getTimeText(timeInSeconds)

        return@execute String.format(
            "${distTextPair.first} ${distTextPair.second}, " + "${timeTextPair.first} ${timeTextPair.second}",
        )
    } ?: ""

    private fun showStartNavigationDialog(
        title: String,
        message: String,
        onStartNavigation: () -> Unit,
        onStartSimulation: () -> Unit,
        onViewCreated: (() -> Unit)? = null,
        onViewClosed: (() -> Unit)? = null,
    ) {
        Util.postOnMain {
            binding.startNavigationPanel.apply {
                // Set the content
                this.title.text = title
                this.message.text = message

                closeButton.setOnClickListener {
                    dismissPanel(root)
                    this@MainActivity.binding.followGpsButton.visibility = View.VISIBLE
                    onViewClosed?.invoke()
                }

                buttonStartNavigation.setOnClickListener {
                    dismissPanel(root)
                    onStartNavigation()
                }

                buttonStartSimulation.setOnClickListener {
                    dismissPanel(root)
                    onStartSimulation()
                }

                // Hide the GPS button while the user reviews the route summary; restore it
                // when the panel is dismissed so the button is never orphaned on screen.
                root.visibility = View.VISIBLE
                this@MainActivity.binding.followGpsButton.visibility = View.GONE

                // Measure height after it's shown
                root.post {
                    val height = root.height
                    bottomDialogHeight = height
                    updateFollowGpsButtonMargins(isAnyPanelVisible())
                    updateFocusViewport()
                    onViewCreated?.invoke()
                }
            }
        }
    }

    private fun showInvalidTokenDialog() {
        showDialog(getString(R.string.invalid_token)) { finish() }
    }

    //endregion

    //region Helpers

    private fun getTopInset(): Int {
        val appBarHeight = getAppBarHeight()
        return if (appBarHeight > 0) appBarHeight + inflate else topInset
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    }

    private fun checkLocationStatus(): Boolean {
        if (!isLocationEnabled()) {
            showLocationDialog(
                message = getString(R.string.location_disabled),
                settingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            )
            return false
        }

        return true
    }

    private fun showLocationDialog(message: String, settingsIntent: Intent) {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            title.text = getString(R.string.location_status)
            this.message.text = message
            button.text = getString(R.string.open_settings)
            button.setOnClickListener {
                dialog.dismiss()
                startActivity(settingsIntent)
                shouldCheckLocationPermissionOnResume = true
            }
        }
        dialog.apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            setCancelable(false)
            setContentView(dialogBinding.root)
            show()
        }
    }

    private fun NavigationInstruction.getDistanceInMeters(): String {
        return GemUtil.getDistText(
            this.timeDistanceToNextTurn?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    @SuppressLint("DefaultLocale")
    private fun Route.getEta(): String {
        val etaNumber = this.getTimeDistance(true)?.totalTime ?: 0

        val time = Time()
        time.setLocalTime()
        time.longValue += etaNumber * 1000
        return String.format("%d:%02d", time.hour, time.minute)
    }

    private fun Route.getRtt(): String {
        return GemUtil.getTimeText(
            this.getTimeDistance(true)?.totalTime ?: 0,
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    private fun Route.getRtd(): String {
        return GemUtil.getDistText(
            this.getTimeDistance(true)?.totalDistance ?: 0,
            EUnitSystem.Metric,
        ).let { pair ->
            pair.first + " " + pair.second
        }
    }

    private fun getNextTurnImage(
        navInstr: NavigationInstruction,
        width: Int,
        height: Int,
        onSameImage: (Boolean) -> Unit = {},
    ): Bitmap? {
        if (!navInstr.hasNextTurnInfo()) return null

        if ((navInstr.nextTurnDetails?.abstractGeometryImage?.uid ?: 0) == lastTurnImageId) {
            onSameImage(true)
            return null
        }

        val image = navInstr.nextTurnDetails?.abstractGeometryImage
        if (image != null) {
            lastTurnImageId = image.uid
        }

        val aInner = Rgba(255, 255, 255, 255)
        val aOuter = Rgba(0, 0, 0, 255)
        val iInner = Rgba(128, 128, 128, 255)
        val iOuter = Rgba(128, 128, 128, 255)

        return GemUtilImages.asBitmap(
            image,
            width,
            height,
            aInner,
            aOuter,
            iInner,
            iOuter,
        )
    }

    private fun getStatusMessage(): String {
        when (navigationStatus) {
            ENavigationStatus.WaitingRoute -> {
                val routeStatus = navRoute?.status

                return when (routeStatus) {
                    ERouteStatus.WaitingInternetConnection -> {
                        getString(R.string.waiting_for_internet_connection)
                    }

                    ERouteStatus.Calculating -> {
                        getString(R.string.calculating)
                    }

                    ERouteStatus.Ready -> {
                        getString(R.string.gps_accuracy_not_good_enough)
                    }

                    else -> {
                        getString(R.string.calculating)
                    }
                }
            }
            ENavigationStatus.WaitingGPS -> {
                if (navigationService.isSimulationActive()) {
                    return getString(R.string.calculating)
                }
                return getString(R.string.getting_position)
            }
            else -> {
                // Do nothing for other statuses
            }
        }

        return ""
    }

    private fun onNavigationEnded(errorCode: ErrorCode = GemError.NoError) {
        runOnUiThread {
            if ((errorCode != GemError.NoError) && (errorCode != GemError.Cancel)) {
                showDialog(SdkCall.runSynced { GemError.getMessage(errorCode, this) }.orEmpty())
            }

            binding.apply {
                mapSearchBar.isVisible = true
                binding.topPanel.isVisible = false
                binding.bottomPanel.isVisible = false
            }
            applyCameraFocus()
            updateFocusViewport()
        }

        SdkCall.execute {
            binding.gemSurfaceView.mapView?.hideRoutes()
        }
    }

    @SuppressLint("DefaultLocale")
    private fun getLandmarkDescription(
        mapView: MapView,
        coordinates: Coordinates,
        isMyPosition: Boolean = false,
    ): String {
        var description = ""
        var descriptionContainsLatLon = false

        var address = mapView.getClosestAddress(coordinates, 50, false)
        if (address != null) {
            description = GemUtil.formatLandmarkDetails(address, true)
        }

        if (description.isEmpty()) {
            address = mapView.getClosestAddress(coordinates, 300, false)
            if (address != null) {
                description = address.addressInfo?.getField(EAddressField.City) ?: ""
            }

            if (description.isEmpty()) {
                address = mapView.getClosestAddress(coordinates, 2500, true)
                if (address != null) {
                    val city = address.addressInfo?.getField(EAddressField.City) ?: ""
                    if (city.isNotEmpty()) {
                        description = "Near $city"
                    }
                }

                if (description.isEmpty()) {
                    description = String.format("%.5f, %.5f", coordinates.latitude, coordinates.longitude)
                    descriptionContainsLatLon = true
                }
            }
        }

        if (isMyPosition) {
            if (!descriptionContainsLatLon) {
                description += "\nLatitude: ${String.format("%.5f", coordinates.latitude)}"
                description += "\nLongitude: ${String.format("%.5f", coordinates.longitude)}"
            }

            description += "\nAltitude: ${coordinates.altitude.toInt()}m"
        }

        return description
    }

    //endregion

    //region ITTSPlayerInitializationListener

    override fun onTTSPlayerInitialized() {
        SoundPlayingService.setTTSLanguage("eng-USA")
    }

    override fun onTTSPlayerInitializationFailed() {
        SoundPlayingService.setDefaultHumanVoice()
    }

    //endregion
}

//region TESTING
object EspressoIdlingResource {
    val espressoIdlingResource = CountingIdlingResource("BikeSimulationTestsIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
