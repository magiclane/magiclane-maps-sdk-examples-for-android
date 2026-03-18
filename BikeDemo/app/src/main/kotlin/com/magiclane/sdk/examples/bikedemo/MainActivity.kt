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
import com.magiclane.sdk.examples.bikedemo.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.bikedemo.databinding.DialogLayoutBinding
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

    class TSameImage(var value: Boolean = false)

    private lateinit var binding: ActivityMainBinding

    private var searchIconSize = 0

    private var searchFilter = ""

    private var routesList = ArrayList<Route>()

    private var topInset = 0
    private var leftInset = 0
    private var rightInset = 0
    private var bottomInset = 0

    private var inflate = 0
    private var appBarHeight = 0
    private var bottomDialogHeight = 0

    private var navigationStatus = ENavigationStatus.Running

    private var lastTurnImageId: Long = Long.MAX_VALUE
    private var turnImageSize: Int = 0
    private var padding: Int = 0
    private var shouldCheckLocationPermissionOnResume = false

    private val checkAuthorizationListener = ProgressListener.create(onCompleted = { errorCode, _ ->
        if (errorCode != GemError.NoError) {
            showInvalidTokenDialog()
        }
    })

    private val routingService = RoutingService(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
            binding.cancelButton.visibility = View.VISIBLE
        },

        onCompleted = { routes, errorCode, _ ->
            binding.progressBar.visibility = View.GONE
            binding.cancelButton.visibility = View.GONE

            when (errorCode) {
                GemError.NoError -> {
                    SdkCall.execute {
                        routesList = routes

                        if (routesList.isNotEmpty()) {
                            var title = ""
                            viewModel.destination?.let {
                                title = getString(R.string.route_to, GemUtil.formatName(it))
                            }

                            val message = formatRouteName(routesList[0])

                            binding.mapSearchBar.isVisible = false

                            showStartNavigationDialog(title, message,
                                onStartNavigation = {
                                    SdkCall.execute {
                                        binding.gemSurfaceView.mapView?.preferences?.routes?.let { routes ->
                                            routes.mainRoute?.let { mainRoute ->

                                                val error = navigationService.startNavigationWithRoute(
                                                    mainRoute,
                                                    navigationListener,
                                                    navigationProgressListener
                                                )

                                                if (error != GemError.NoError) {
                                                    Util.postOnMain {
                                                        showDialog(getString(R.string.route_navigation_error, GemError.getMessage(error)))
                                                    }
                                                }

                                                routes.clear()
                                                routesList.clear()
                                            }
                                        }
                                    }
                                },
                                onStartSimulation = {
                                    SdkCall.execute {
                                        binding.gemSurfaceView.mapView?.preferences?.routes?.let { routes ->
                                            routes.mainRoute?.let { mainRoute ->

                                                val error = navigationService.startSimulationWithRoute(
                                                    mainRoute,
                                                    navigationListener,
                                                    navigationProgressListener
                                                )

                                                if (error != GemError.NoError) {
                                                    Util.postOnMain {
                                                        showDialog(getString(R.string.route_simulation_error, GemError.getMessage(error)))
                                                    }
                                                }

                                                routes.clear()
                                                routesList.clear()
                                            }
                                        }
                                    }
                                },
                                onViewCreated = {
                                    SdkCall.execute {
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
                                                bottomDialogHeight + inflate
                                            )
                                        )
                                    }
                                },
                                onViewClosed = {
                                    binding.mapSearchBar.isVisible = true
                                    SdkCall.execute {
                                        binding.gemSurfaceView.mapView?.preferences?.routes?.clear()
                                        routesList.clear()
                                    }
                                }
                            )
                        }
                    }
                }

                GemError.Cancel -> {
                    // The routing action was cancelled.
                }

                else -> {
                    // There was a problem at computing the routing operation.
                    showDialog(getString(R.string.routing_error, GemError.getMessage(errorCode)))
                }
            }
        },
        onStatusChanged = { status ->
            if (status == ERouteStatus.WaitingInternetConnection.value) {
                showDialog(getString(R.string.internet_required))
            }
        }
    )

    companion object {
        private const val REQUEST_PERMISSIONS = 110
    }

    val searchAdapter = SearchAdapter()

    private val viewModel: MainActivityViewModel by viewModels()

    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)

    private val playingListener = object : SoundPlayingListener() {}

    private val soundPreference = SoundPlayingPreferences()

    private var job: Job? = null

    private lateinit var positionListener: PositionListener

    /**
     * Define a navigation listener that will receive notifications from the
     * navigation service.
     * We will use just the onNavigationStarted method, but for more available
     * methods you should check the documentation.
     */
    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                binding.gemSurfaceView.mapView?.let { mapView ->
                    mapView.preferences?.enableCursor = false
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
        },
        onNavigationInstructionUpdated = { instr ->
            var instrText = ""
            var instrIcon: Bitmap? = null
            val sameTurnImage = TSameImage()
            var instructionDistance = ""

            var etaText = ""
            var rttText = ""
            var rtdText = ""

            SdkCall.execute {
                // Fetch data for the navigation top panel (instruction related info).
                instrText = instr.nextStreetName ?: ""

                if (instrText.isEmpty()) {
                    instrText = instr.nextTurnInstruction ?: ""
                }

                instrIcon = getNextTurnImage(instr, turnImageSize, turnImageSize, sameTurnImage)

                instructionDistance = instr.getDistanceInMeters()

                // Fetch data for the navigation bottom panel (route related info).
                navRoute?.apply {
                    etaText = getEta() // estimated time of arrival
                    rttText = getRtt() // remaining travel time
                    rtdText = getRtd() // remaining travel distance
                }
            }

            // Update the navigation panels info.
            binding.apply {
                if (!sameTurnImage.value) {
                    navIcon.setImageBitmap(instrIcon)
                }

                navInstruction.text = instrText
                instrDistance.text = instructionDistance

                eta.text = etaText
                rtt.text = rttText
                rtd.text = rtdText
            }
        },
        onDestinationReached = {
            onNavigationEnded()
        },
        onNotifyStatusChange = { status ->
            navigationStatus = status
            refreshStatusMessage()
        },
        onNavigationError = { error ->
            onNavigationEnded(error)
        },
        onNavigationSound = { sound ->
            SdkCall.execute {
                SoundPlayingService.play(sound, playingListener, soundPreference)
            }
        },
        canPlayNavigationSound = true
    )

    private val navigationProgressListener = ProgressListener.create(
        onStatusChanged = {
            refreshStatusMessage()
        }
    )

    private val searchService = SearchService()

    private fun refreshStatusMessage() {
        val statusMessage = getStatusMessage()
        if (statusMessage.isEmpty()) {
            binding.turnContainer.isVisible = true
        } else {
            binding.turnContainer.isVisible = false
            binding.navInstruction.text = statusMessage
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        SoundUtils.addTTSPlayerInitializationListener(this)

        turnImageSize = resources.getDimension(R.dimen.turn_image_size).toInt()
        padding = resources.getDimension(R.dimen.big_padding).toInt()

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
            bottomInset = systemBars.bottom + inflate
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

            positionListener = PositionListener {
                if (!it.isValid()) return@PositionListener

                PositionService.removeListener(positionListener)
                Util.postOnMain { enableGPSButton() }
            }
            PositionService.addListener(positionListener, EDataType.Position)

            viewModel.initPreferences()

            val parametersList = arrayListOf(Parameter(ESConfigKeys.Position.ImprovedPosPreferRouteSnap, "1"), Parameter(ESConfigKeys.Position.ImprovedPositionDefTransportMode, "bike"))
            PositionService.dataSource?.setPreferences(EDataType.Position, parametersList)

            Util.postOnMain {
                binding.apply {
                    gemSurfaceView.mapView?.onTouch = { xy ->
                        SdkCall.execute {
                            if (navigationService.isNavigationActive() || navigationService.isSimulationActive()) {
                                return@execute
                            }

                            binding.gemSurfaceView.mapView?.cursorScreenPosition = xy

                            val routes = gemSurfaceView.mapView?.cursorSelectionRoutes
                            if (!routes.isNullOrEmpty()) {
                                // set the touched route as the main route and center on it
                                binding.gemSurfaceView.mapView?.preferences?.routes?.mainRoute = routes[0]
                                binding.gemSurfaceView.mapView?.centerOnRoutes(routesList, viewRc = getFreeScreenRect(), animation = Animation(EAnimation.Linear, 900))

                                return@execute
                            }

                            val landmarks = binding.gemSurfaceView.mapView?.cursorSelectionLandmarks
                            if (!landmarks.isNullOrEmpty()) {
                                val landmark = landmarks[0]
                                landmark.coordinates?.let {
                                    viewModel.destination = landmark
                                    showCalculateRouteDialog(GemUtil.formatName(landmark), GemUtil.getLandmarkDescription(landmark, true),
                                        onCalculateRoute = {
                                            PositionService.position?.let { position ->
                                                val departure = Landmark("My position", position.latitude, position.longitude)
                                                calculateRoute(departure, landmark)
                                            } ?: run {
                                                showDialog(getString(R.string.current_position_not_available))
                                            }
                                        },
                                        onViewCreated = {
                                            highlightLandmarkOnMap(landmark)
                                        },
                                        onViewClosed = {
                                            deactivateHighlights()
                                        })
                                }
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
                showCalculateRouteDialog(item.text ?: "", item.subText ?: "",
                onCalculateRoute = {
                    PositionService.position?.let {
                        val departure = Landmark(getString(R.string.my_position), it.latitude, it.longitude)
                        val destination = item.landmark
                        calculateRoute(departure, destination)
                    } ?: run {
                        showDialog(getString(R.string.current_position_not_available))
                    }
                },
                onViewCreated = {
                    highlightLandmarkOnMap(viewModel.destination!!)
                },
                onViewClosed = {
                    deactivateHighlights()
                })
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

            if (filter != searchFilter) {
                searchFilter = filter

                SdkCall.execute {
                    searchService.cancelSearch()
                }

                binding.searchProgressBar.isInvisible = searchFilter.isBlank()
                job?.run { if (isActive) cancel() }

                if (searchFilter.isNotBlank())
                {
                    job = CoroutineScope(Dispatchers.IO).launch {
                        delay(300)

                        SdkCall.runSynced {
                            searchService.searchByFilter(
                                textFilter = binding.mapSearchView.text.trim().toString(),
                                onCompleted = { results, errorCode, _ ->
                                    if (errorCode == GemError.Cancel) return@searchByFilter
                                    if (errorCode == GemError.NoError) {
                                        SdkCall.execute {
                                            val list = results.map { landmark ->
                                                SearchResultItem(
                                                    landmark.image?.asBitmap(
                                                        searchIconSize,
                                                        searchIconSize,
                                                    ),
                                                    GemUtil.formatName(landmark),
                                                    GemUtil.getLandmarkDescription(landmark, true),
                                                    landmark
                                                )
                                            }.toMutableList()
                                            viewModel.searchResultListLivedata.postValue(list)
                                        }
                                    }
                                    else {
                                        viewModel.searchResultListLivedata.postValue(mutableListOf())
                                    }
                                })
                        }
                    }
                }
                else {
                    viewModel.searchResultListLivedata.postValue(mutableListOf())
                }
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

            if (binding.cancelButton.isVisible)
            {
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
            exitProcess(0)
        }
    }

    override fun onResume() {
        super.onResume()
        if (shouldCheckLocationPermissionOnResume) {
            shouldCheckLocationPermissionOnResume = false
            if (isLocationEnabled()) {
                requestPermissions()
            }
            else {
                showDialog(getString(R.string.location_services_required)) {
                    finish()
                    exitProcess(0)
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
            var highlightSettings: HighlightRenderSettings

            if ((contour != null) && !contour.isEmpty()) {
                binding.gemSurfaceView.mapView?.centerOnRectArea(
                    contour,
                    zoomLevel = -1,
                    viewRc = rect,
                    Animation(EAnimation.Linear, 900)
                )

                highlightSettings = HighlightRenderSettings(
                    EHighlightOptions.ShowContour.value or EHighlightOptions.ShowLandmark.value,
                    Rgba(255, 98, 0, 255),
                    Rgba(255, 98, 0, 255),
                    0.75,
                ).also {
                    it.imageSize = 6.0
                }
            } else {
                highlightSettings = HighlightRenderSettings(
                    EHighlightOptions.ShowLandmark,
                ).also {
                    it.imageSize = 6.0
                }

                landmark.coordinates?.let {
                    binding.gemSurfaceView.mapView?.centerOnCoordinates(
                        it,
                        -1,
                        rect.center,
                        Animation(EAnimation.Linear, 900),
                        0.0,
                        0.0,
                    )
                }
            }

            mapView.activateHighlightLandmarks(
                landmark,
                highlightSettings
            )
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
                    exitProcess(0)
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
            destination
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
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
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
        onViewClosed: (() -> Unit)? = null
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

        return@execute String.format("${distTextPair.first} ${distTextPair.second}, " + "${timeTextPair.first} ${timeTextPair.second}")
    } ?: ""

    private fun showStartNavigationDialog(
        title: String,
        message: String,
        onStartNavigation: () -> Unit,
        onStartSimulation: () -> Unit,
        onViewCreated: (() -> Unit)? = null,
        onViewClosed: (() -> Unit)? = null
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

                // Set up start navigationn button
                buttonStartNavigation.setOnClickListener {
                    root.visibility = View.GONE
                    bottomDialogHeight = 0
                    onStartNavigation()
                }

                // Set up start navigationn button
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
            exitProcess(0)
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
                settingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
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
        sameImage: TSameImage
    ): Bitmap? {
        if (!navInstr.hasNextTurnInfo()) return null
        if ((navInstr.nextTurnDetails?.abstractGeometryImage?.uid ?: 0) == lastTurnImageId) {
            sameImage.value = true
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
