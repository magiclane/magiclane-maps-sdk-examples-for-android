/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
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
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), SoundUtils.ITTSPlayerInitializationListener {

    private lateinit var binding: ActivityMainBinding

    // UI Dimensions
    private var searchIconSize = 0
    private var turnImageSize: Int = 0
    private var topInset = 0
    private var leftInset = 0
    private var rightInset = 0
    private var inflate = 0
    private var appBarHeight = 0
    private var bottomDialogHeight = 0

    // Search state
    private var searchFilter = ""

    // Route and Navigation state
    private var routesList = ArrayList<Route>()
    private var navigationStatus = ENavigationStatus.Running
    private var lastTurnImageId: Long = Long.MAX_VALUE

    // Permission handling
    private var shouldCheckLocationPermissionOnResume = false

    private val checkAuthorizationListener = ProgressListener.create(
        onCompleted = { errorCode, _ ->
            if (errorCode != GemError.NoError) {
                showInvalidTokenDialog()
            }
        },
    )

    private val routingService = RoutingService(
        onStarted = { showRoutingProgress() },
        onCompleted = { routes, errorCode, _ -> handleRoutingCompleted(routes, errorCode) },
        onStatusChanged = { status -> handleRoutingStatusChanged(status) },
    )

    // Services and listeners
    private val viewModel: MainActivityViewModel by viewModels()
    private val searchAdapter = SearchAdapter()
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
    }

    /**
     * Navigation listener that receives notifications from the navigation service.
     */
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
            else -> showDialog(getString(R.string.routing_error, GemError.getMessage(errorCode)))
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
                    showDialog(getString(R.string.route_navigation_error, GemError.getMessage(error)))
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
                    showDialog(getString(R.string.route_simulation_error, GemError.getMessage(error)))
                }
            }

            clearRoutesData()
        }
    }

    private fun presentRoutesOnMap() = SdkCall.execute {
        binding.gemSurfaceView.mapView?.presentRoutes(
            routesList,
            displayBubble = true,
            displayTollIcon = false,
            displayFerryIcon = false,
            displayTrafficIcon = false,
            edgeAreaInsets = Rect(
                leftInset,
                getTopInset(),
                rightInset,
                bottomDialogHeight + inflate,
            ),
        )
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

    // Helper methods for navigation listener callbacks
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
            bikeSettingsButton.isVisible = false
            topPanel.isVisible = true
            bottomPanel.isVisible = true
        }
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

    private fun performSearch(filter: String) {
        searchJob = CoroutineScope(Dispatchers.IO).launch {
            delay(SEARCH_DEBOUNCE_MS)

            SdkCall.runSynced {
                searchService.searchByFilter(
                    textFilter = filter,
                    onCompleted = { results, errorCode, _ ->
                        when (errorCode) {
                            GemError.Cancel -> return@searchByFilter
                            GemError.NoError -> {
                                SdkCall.execute {
                                    val list = results.map { landmark ->
                                        SearchResultItem(
                                            landmark.image?.asBitmap(searchIconSize, searchIconSize),
                                            GemUtil.formatName(landmark),
                                            GemUtil.getLandmarkDescription(landmark, true),
                                            landmark,
                                        )
                                    }.toMutableList()
                                    viewModel.searchResultListLivedata.postValue(list)
                                }
                            }
                            else -> viewModel.searchResultListLivedata.postValue(mutableListOf())
                        }
                    },
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        SoundUtils.addTTSPlayerInitializationListener(this)

        turnImageSize = resources.getDimension(R.dimen.turn_image_size).toInt()

        SdkSettings.onConnectionStatusUpdated = { isConnected ->
            if (isConnected) {
                SdkSettings.appAuthorization?.let {
                    SdkCall.execute {
                        SdkSettings.verifyAppAuthorization(it, checkAuthorizationListener)
                    }
                } ?: run {
                    showInvalidTokenDialog()
                }

                SdkSettings.onConnectionStatusUpdated = {}
            }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EspressoIdlingResource.increment()

        // Measure app bar height after layout
        binding.appBarLayout.post {
            appBarHeight = binding.appBarLayout.height
        }

        // Set up window insets listener
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topInset = systemBars.top + inflate
            leftInset = systemBars.left + inflate
            rightInset = systemBars.right + inflate
            insets
        }

        searchIconSize = resources.getDimension(R.dimen.icon_size).toInt()
        inflate = resources.getDimension(R.dimen.padding_40).toInt()

        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_init_failed, GemError.getMessage(error, this))
            Util.postOnMain {
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

                            landmark?.let { landmark ->
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
                                                    Landmark("My position", position.latitude, position.longitude)
                                                calculateRoute(departure, landmark)
                                            } else {
                                                runOnUiThread {
                                                    showDialog(
                                                        getString(R.string.current_position_not_available),
                                                    )
                                                }
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
                        }
                    }

                    bikeSettingsButton.isVisible = true
                }
            }
        }

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

        SdkSettings.onApiTokenRejected = {
            showInvalidTokenDialog()
        }

        if (checkLocationStatus()) {
            requestPermissions()
        }

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }

        binding.mapSearchView.setupWithSearchBar(binding.mapSearchBar)

        binding.mapSearchView.editText.addTextChangedListener {
            val filter = it.toString().trim()
            if (filter == searchFilter) return@addTextChangedListener

            searchFilter = filter
            SdkCall.execute { searchService.cancelSearch() }

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

        viewModel.searchResultListLivedata.observe(this) {
            searchAdapter.submitList(it)
            binding.searchProgressBar.isInvisible = true
            binding.noResultsTextView.isVisible = it.isEmpty() && searchFilter.isNotBlank()
        }

        binding.bikeSettingsButton.setOnClickListener {
            supportFragmentManager.beginTransaction().replace<BikeSettingsFragment>(
                R.id.fragment_container,
            ).commit()
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
                binding.calculateRoutePanel.root.isVisible = false
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
                binding.startNavigationPanel.root.isVisible = false
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

    private fun highlightLandmarkOnMap(landmark: Landmark) = SdkCall.execute {
        binding.gemSurfaceView.mapView?.let { mapView ->
            val rect = getFreeScreenRect()

            mapView.deactivateAllHighlights()

            landmark.image = ImageDatabase().getImageById(SdkImages.Core.Search_Results_Pin.value)

            val contour = landmark.getContourGeographicArea()
            val highlightSettings: HighlightRenderSettings

            @Suppress("VerboseNullabilityAndEmptiness")
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
        return Rect(
            leftInset,
            getAppBarHeight() + inflate,
            binding.root.width - rightInset,
            binding.root.height - bottomDialogHeight - inflate,
        )
    }

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

    override fun onDestroy() {
        super.onDestroy()

        SoundUtils.removeTTSPlayerInitializationListener(this)

        // Release the SDK.
        GemSdk.release()
        exitProcess(0)
    }

    private fun enableGPSButton() {
        // Set actions for entering/ exiting following position mode.
        binding.apply {
            gemSurfaceView.mapView?.apply {
                onExitFollowingPosition = {
                    followGpsButton.visibility = View.VISIBLE
                    topPanel.visibility = View.GONE
                    bottomPanel.visibility = View.GONE
                }

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
                }

                // Set on click action for the GPS button.
                followGpsButton.setOnClickListener {
                    SdkCall.execute { followPosition() }
                }
            }
        }
    }

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

    private fun calculateRoute(departure: Landmark, destination: Landmark) = SdkCall.execute {
        val waypoints = arrayListOf(
            departure,
            destination,
        )

        routingService.preferences = viewModel.routePreferences
        routingService.calculateRoute(waypoints)
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

                // Set up close button
                closeButton.setOnClickListener {
                    root.visibility = View.GONE
                    bottomDialogHeight = 0
                    onViewClosed?.invoke()
                }

                // Set up calculate route button
                buttonCalculateRoute.setOnClickListener {
                    root.visibility = View.GONE
                    bottomDialogHeight = 0
                    onViewClosed?.invoke()
                    onCalculateRoute()
                }

                // Show the panel
                root.visibility = View.VISIBLE

                // Measure height after it's shown
                root.post {
                    bottomDialogHeight = root.height
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

                // Set up close button
                closeButton.setOnClickListener {
                    root.visibility = View.GONE
                    bottomDialogHeight = 0
                    onViewClosed?.invoke()
                }

                // Set up start navigation button
                buttonStartNavigation.setOnClickListener {
                    root.visibility = View.GONE
                    bottomDialogHeight = 0
                    onStartNavigation()
                }

                // Set up start navigation button
                buttonStartSimulation.setOnClickListener {
                    root.visibility = View.GONE
                    bottomDialogHeight = 0
                    onStartSimulation()
                }

                // Show the panel
                root.visibility = View.VISIBLE

                // Measure height after it's shown
                root.post {
                    val height = root.height
                    bottomDialogHeight = height
                    onViewCreated?.invoke()
                }
            }
        }
    }

    private fun showInvalidTokenDialog() {
        showDialog(
            getString(R.string.invalid_token),
        ) {
            finish()
        }
    }

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
                showDialog(GemError.getMessage(errorCode))
            }

            binding.apply {
                mapSearchBar.isVisible = true
                bikeSettingsButton.isVisible = true
                binding.topPanel.isVisible = false
                binding.bottomPanel.isVisible = false
            }
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

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitialized() {
        SoundPlayingService.setTTSLanguage("eng-USA")
    }

    // ITTSPlayerInitializationListener
    override fun onTTSPlayerInitializationFailed() {
        SoundPlayingService.setDefaultHumanVoice()
    }
}

//region TESTING
object EspressoIdlingResource {
    val espressoIdlingResource = CountingIdlingResource("BikeSimulationTestsIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
