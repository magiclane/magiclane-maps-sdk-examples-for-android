/*
 * SPDX-FileCopyrightText: 2022-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.rangefinder

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.databinding.Observable
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.ERouteDisplayMode
import com.magiclane.sdk.d3scene.EWatermarkPosition
import com.magiclane.sdk.examples.rangefinder.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.rangefinder.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.EEBikeType
import com.magiclane.sdk.routesandnavigation.ERouteRenderOptions
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.ERouteType
import com.magiclane.sdk.routesandnavigation.ElectricBikeProfile
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RouteRenderSettings
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.getValue
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    companion object {
        // System areas to keep clear of (status/navigation bars + any display cutout)
        // when computing the free map area and positioning the Magic Lane logo.
        private val SYSTEM_INSET_TYPES = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

        // Magic Lane watermark logo size (in millimeters) and opacity (0f..1f).
        private const val LOGO_SIZE_MM = 20.0f
        private const val LOGO_ALPHA = 1.0f
    }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainActivityViewModel by viewModels()

    private val propertiesObserver = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            updateOptions()
        }
    }

    private fun CharSequence?.toIntOrZero(): Int = this?.toString()?.toIntOrNull() ?: 0

    /**
     * [routingService] is a service that specialises in computing routes
     */
    private val routingService = RoutingService(
        onStarted = {
            enableButtons(false)
        },
        onCompleted = { routes, errorCode, _ ->
            enableButtons(true)

            when (errorCode) {
                GemError.NoError ->
                    {
                        // if the process ended with no error add the new route to the route list
                        viewModel.listOfRoutes.add(routes[0])
                        // then display
                        addRangeOnMap(
                            viewModel.listOfRoutes.last(),
                            viewModel.listOfRangeProfiles.last().color,
                        )
                        SdkCall.execute { centerRoutes() }
                        showScrollableRangesList()
                    }

                GemError.Cancel ->
                    { // The routing action was cancelled.
                        viewModel.listOfRangeProfiles.removeAt(
                            viewModel.listOfRangeProfiles.size - 1,
                        )
                    }

                else ->
                    { // There was a problem at computing the routing operation.
                        viewModel.listOfRangeProfiles.removeAt(
                            viewModel.listOfRangeProfiles.size - 1,
                        )
                        showDialog(
                            resources.getString(
                                R.string.service_error,
                                SdkCall.runSynced { GemError.getMessage(errorCode, this@MainActivity) } ?: "",
                            ),
                        )
                    }
            }

            EspressoIdlingResource.decrement()
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            adjustAddRangeFinderContainerSize(landscape = true)
        }

        EspressoIdlingResource.increment()

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(resources.getString(R.string.internet_required))
        }
    }

    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            // The SDK is not initialized yet here, so resolve the message directly
            // (without an enclosing SdkCall block).
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnUiThread {
                showDialog(errorMessage) { finish() }
            }
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = { mapView ->
            // Pin the Magic Lane watermark logo to the bottom-right corner of the map.
            mapView.setWatermarkLogoProperties(EWatermarkPosition.EWPBottomRight, LOGO_SIZE_MM, LOGO_ALPHA)

            // Once the map view exists, keep the camera viewport clear of the system bars/panel.
            updateFocusViewport()
        }

        // Re-position the logo whenever the surface is resized (e.g. on rotation).
        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = {}

                viewModel.load()
                addPropertyCallback()

                // Defines an action that should be done when the world map is ready (Updated/ loaded).
                binding.apply {
                    progressBar.isVisible = false
                    addRangeContainer.isVisible = true
                    optionsButton.setOnClickListener {
                        settingsContainer.isVisible = !settingsContainer.isVisible
                        optionsButton.icon = ResourcesCompat.getDrawable(
                            resources,
                            if (settingsContainer.isVisible) {
                                R.drawable.ic_arrow_drop_up_24
                            } else {
                                R.drawable.ic_arrow_drop_down_24
                            },
                            theme,
                        )
                    }

                    addButton.setOnClickListener {
                        EspressoIdlingResource.increment()
                        // check to see if more ranges can be generated on map
                        if (viewModel.listOfRangeProfiles.size >= MAX_ITEMS) {
                            showDialog(
                                resources.getString(
                                    R.string.maximum_items_warning,
                                    MAX_ITEMS,
                                ),
                            )
                            return@setOnClickListener
                        }

                        if (settingsContainer.isVisible) {
                            optionsButton.callOnClick()
                        }

                        if (binding.rangeValueEditText.text!!.isNotEmpty()) {
                            // get a copy of the current range settings profile
                            val newRange = viewModel.currentRangeSettingsProfile.copy()
                            if (findExistingRangeProfile(newRange) == null) {
                                // if the same range does not already exist
                                // set a new color for it and update its visibility status
                                SdkCall.execute { newRange.color = viewModel.getNewColor() }
                                newRange.isDisplayed = true
                                // add that copy to the view model's list of range profiles
                                viewModel.listOfRangeProfiles.add(newRange)
                                // command the service to begin generating a new route with
                                // your new range value
                                SdkCall.execute { calculateRanges() }
                                binding.rangeValueEditText.setText("")
                                hideKeyboard()
                            } else {
                                showDialog(
                                    resources.getString(R.string.same_range_detected_warning),
                                )
                            }
                        } else {
                            showDialog(resources.getString(R.string.empty_range_value_warning))
                        }
                    }

                    // set on click listeners on each selector in order to show a dialog with options
                    transportModeSelector.setOnClickListener {
                        showOptionsDialog(it)
                    }
                    bikeTypeSelector.setOnClickListener {
                        showOptionsDialog(it)
                    }
                    rangeTypeSelector.setOnClickListener {
                        showOptionsDialog(it)
                    }

                    // set text listeners in order to update the current range settings profile
                    bikeWeightEditText.doAfterTextChanged { txt ->
                        viewModel.currentRangeSettingsProfile.bikeWeight = txt.toIntOrZero()
                    }
                    bikerWeightEditText.doAfterTextChanged { txt ->
                        viewModel.currentRangeSettingsProfile.bikerWeight = txt.toIntOrZero()
                    }
                    rangeValueEditText.doAfterTextChanged { txt ->
                        viewModel.currentRangeSettingsProfile.rangeValue = txt.toIntOrZero()
                    }
                    updateOptions()
                }
                EspressoIdlingResource.decrement()
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnUiThread {
                showDialog(getString(R.string.token_rejected_message))
            }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = {}
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        adjustAddRangeFinderContainerSize(
            landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE,
        )
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            removePropertyCallback()
            GemSdk.release()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearSdkListeners()

        // Deinitialize the SDK.
        GemSdk.release()
        exitProcess(0)
    }

    /**
     * Checks whether the range value exists or not already in the retained list of range
     * profiles from [viewModel]
     * @param newRange the new [RangeSettingsProfile] to be added
     */
    private fun findExistingRangeProfile(newRange: RangeSettingsProfile) = viewModel.listOfRangeProfiles.find {
        val itemMatcher = it.transportMode == newRange.transportMode &&
            it.rangeType == newRange.rangeType &&
            it.rangeValue == newRange.rangeValue
        if (it.transportMode == ERouteTransportMode.Bicycle && newRange.transportMode == ERouteTransportMode.Bicycle) {
            itemMatcher && it.bikeType == newRange.bikeType
        } else {
            itemMatcher
        }
    }

    /**
     * Adds a listener for every [RangeSettingsProfile] cashed in the [viewModel]
     */
    private fun addPropertyCallback() {
        viewModel.cashList.forEach {
            it.addOnPropertyChangedCallback(
                propertiesObserver,
            )
        }
    }

    /**
     * Removes the listener for every [RangeSettingsProfile] cashed in the [viewModel]
     */
    private fun removePropertyCallback() {
        viewModel.cashList.forEach {
            it.removeOnPropertyChangedCallback(
                propertiesObserver,
            )
        }
    }

    /**
     * Updates the visibility and values of options for range generation seen on screen.
     */
    private fun updateOptions() {
        with(viewModel.currentRangeSettingsProfile) {
            binding.apply {
                transportModeSelector.text = transportMode.name
                bikeTypeSelector.text = bikeType.name
                rangeTypeSelector.text = rangeType.name

                val showBikeOptions = transportMode == ERouteTransportMode.Bicycle
                bikeTypeSelector.isVisible = showBikeOptions
                bikeTypeText.isVisible = showBikeOptions

                val showEconomicBikeOptions = rangeType == ERouteType.Economic
                bikeWeightEditTextLayout.isVisible = showEconomicBikeOptions
                bikerWeightEditTextLayout.isVisible = showEconomicBikeOptions

                rangeValueEditTextLayout.helperText = getMeasuringUnit(rangeType)
            }
        }
    }

    /**
     * Returns the matching measuring unit based on the current range type
     * @param [rangeType]
     */
    private fun getMeasuringUnit(rangeType: ERouteType) = when (rangeType) {
        ERouteType.Fastest -> getString(R.string.seconds)
        ERouteType.Shortest -> getString(R.string.meters)
        ERouteType.Economic -> getString(R.string.watts_per_hour)
        else -> ""
    }

    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        if (!isActivityAlive()) return

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

    private fun isActivityAlive(): Boolean {
        return !isFinishing && !isDestroyed
    }

    /**
     * Shows a dialog with a list of options based on the picked selector view
     * @param view the selector that was clicked on
     */
    private fun showOptionsDialog(view: View) {
        val list = getOptionsArrayList(view).map { it.name }
        val checkedItemPosition = getCheckedItemPosition(view)

        MaterialAlertDialogBuilder(this)
            .setTitle(getDialogTitle(view))
            .setSingleChoiceItems(list.toTypedArray(), checkedItemPosition) { dialog, pos ->
                onItemClicked(view, pos)
                dialog.dismiss()
            }
            .show()
    }

    private fun getCheckedItemPosition(view: View) = with(viewModel.currentRangeSettingsProfile) {
        when (view.id) {
            R.id.transport_mode_selector -> viewModel.listOfTransportTypes.indexOf(transportMode)
            R.id.range_type_selector ->
                if (isBicycleTransportType()) {
                    viewModel.listOfBicycleRangeTypes.indexOf(rangeType)
                } else {
                    viewModel.listOfRangeTypes.indexOf(rangeType)
                }
            R.id.bike_type_selector -> viewModel.listOfBikeTypes.indexOf(bikeType)
            else -> -1
        }
    }

    /**
     * On item clicked callback. Will updated the current selected option from the
     * current [RangeSettingsProfile]
     * @param selector the selector that was clicked on
     * @param position option's positions in the dialog's options list
     */
    private fun onItemClicked(selector: View, position: Int) {
        if (selector.id == R.id.transport_mode_selector) {
            viewModel.currentRangeSettingsProfile = viewModel.cashList[position]
        }
        with(viewModel.currentRangeSettingsProfile) {
            when (selector.id) {
                R.id.transport_mode_selector ->
                    transportMode = viewModel.listOfTransportTypes[position]

                R.id.range_type_selector ->
                    rangeType =
                        if (isBicycleTransportType()) {
                            viewModel.listOfBicycleRangeTypes[position]
                        } else {
                            viewModel.listOfRangeTypes[position]
                        }

                R.id.bike_type_selector ->
                    bikeType = viewModel.listOfBikeTypes[position]
            }
        }
    }

    /**
     * Returns the matching options list to be shown in the dialog view
     * based on the picked selector view
     * @param view the selector that was clicked on
     */
    private fun getOptionsArrayList(view: View): List<Enum<*>> = when (view.id) {
        R.id.transport_mode_selector ->
            viewModel.listOfTransportTypes

        R.id.range_type_selector ->
            if (isBicycleTransportType()) {
                viewModel.listOfBicycleRangeTypes
            } else {
                viewModel.listOfRangeTypes
            }

        R.id.bike_type_selector -> viewModel.listOfBikeTypes

        else -> emptyList()
    }

    /**
     * Checks whether the current range type coincides with [ERouteTransportMode.Bicycle]
     */
    private fun isBicycleTransportType(): Boolean =
        viewModel.currentRangeSettingsProfile.transportMode == ERouteTransportMode.Bicycle

    /**
     * Returns the matching title to be shown in the dialog view
     * based on the picked selector view
     * @param view the selector that was clicked on
     */
    private fun getDialogTitle(view: View) = ContextCompat.getString(
        this,
        when (view.id) {
            R.id.transport_mode_selector -> R.string.transport_mode
            R.id.range_type_selector -> R.string.range_type
            R.id.bike_type_selector -> R.string.bike_type
            else -> R.string.transport_mode
        },
    )

    /**
     * Commands the [routingService] to calculate array of routes with
     * attached range values and routing preferences
     */
    private fun calculateRanges() {
        SdkCall.execute {
            with(routingService.preferences) {
                viewModel.currentRangeSettingsProfile.let {
                    // get an electric bike profile in case the Economic range type option is picked
                    val electricBikeProfile =
                        if (isBicycleTransportType() && it.rangeType == ERouteType.Economic) {
                            ElectricBikeProfile(
                                EEBikeType.Pedelec,
                                it.bikeWeight.toFloat(),
                                it.bikerWeight.toFloat(),
                                2f,
                                4f,
                            )
                        } else {
                            null
                        }

                    // set your routing preferences according to your selected options
                    transportMode = it.transportMode
                    routeType = it.rangeType
                    setRouteRanges(
                        ArrayList(arrayListOf(viewModel.listOfRangeProfiles.last().rangeValue)),
                        100,
                    )
                    if (isBicycleTransportType()) {
                        setBikeProfile(it.bikeType, electricBikeProfile)
                    }
                }
            }
            routingService.calculateRoute(arrayListOf(Landmark("London", 51.5073204, -0.1276475)))
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
    }

    /**
     * Creates and displays a set of chip-like views for each [RangeSettingsProfile]
     * retained in the view model list of range profiles
     */
    private fun showScrollableRangesList() {
        viewModel.listOfRangeProfiles.let { currentSelectedRanges ->
            binding.apply {
                currentRangesButtonsContainer.removeAllViews()
                if (currentSelectedRanges.isEmpty()) return

                for (i in 0 until currentSelectedRanges.size) {
                    val rangeContainer = layoutInflater.inflate(
                        R.layout.button_text,
                        currentRangesButtonsContainer,
                        false,
                    ) as ConstraintLayout
                    val textView = rangeContainer.findViewById<TextView>(R.id.text_button)
                    val clearButton = rangeContainer.findViewById<ImageView>(R.id.icon)

                    currentSelectedRanges[i].let {
                        textView.text = resources.getString(
                            R.string.range_item_text,
                            if (isBicycleTransportType()) it.bikeType.name else "",
                            it.transportMode.name,
                            it.rangeValue,
                            getMeasuringUnit(it.rangeType),
                        )
                    }

                    textView.setTextColor(
                        ContextCompat.getColor(
                            this@MainActivity,
                            R.color.text_color,
                        ),
                    )

                    // center on the route on long click
                    textView.setOnLongClickListener {
                        SdkCall.execute {
                            if (viewModel.listOfRangeProfiles[i].isDisplayed) {
                                centerRoutes(
                                    route = viewModel.listOfRoutes[i],
                                )
                            }
                        }
                        true
                    }

                    // display or hide the route on click
                    textView.setOnClickListener {
                        viewModel.listOfRangeProfiles[i].let {
                            it.isDisplayed = !it.isDisplayed
                            if (it.isDisplayed) {
                                addRangeOnMap(
                                    viewModel.listOfRoutes[i],
                                    viewModel.listOfRangeProfiles[i].color,
                                )
                            } else {
                                removeRangeFromMap(viewModel.listOfRoutes[i])
                            }
                            SdkCall.execute {
                                setStrokeColor(
                                    rangeContainer,
                                    getStrokeColor(index = i),
                                )
                            }
                        }
                    }

                    // set click event on delete button
                    clearButton.setOnClickListener {
                        // remove the associated range settings profile of this route
                        currentSelectedRanges.removeAt(i)
                        SdkCall.execute {
                            // mark the color as unused
                            viewModel.resetColor(i)
                            // remove the range from map
                            removeRangeFromMap(viewModel.listOfRoutes[i])
                            viewModel.listOfRoutes.removeAt(i)
                            centerRoutes()
                        }
                        showScrollableRangesList()
                    }
                    clearButton.setColorFilter(
                        ContextCompat.getColor(
                            this@MainActivity,
                            R.color.text_color,
                        ),
                    )

                    SdkCall.execute { setStrokeColor(rangeContainer, getStrokeColor(index = i)) }
                    currentRangesButtonsContainer.addView(rangeContainer)
                }

                settingsContainer.post { settingsContainer.fullScroll(View.FOCUS_DOWN) }
                currentRangesScrollContainer.post {
                    currentRangesScrollContainer.fullScroll(
                        View.FOCUS_RIGHT,
                    )
                }
            }
        }
    }

    /**
     * A utility function that enables and disables views
     * while routes calculations are being made
     */
    private fun enableButtons(enable: Boolean) {
        binding.addButton.isEnabled = enable
        for (item in binding.currentRangesButtonsContainer.children) {
            val button = item.findViewById<ImageView>(R.id.icon)
            button.isEnabled = enable
        }
        binding.progressBar.isVisible = !enable
    }

    private fun addRangeOnMap(route: Route, color: Rgba) = SdkCall.execute {
        binding.gemSurfaceView.mapView?.preferences?.routes?.addWithRenderSettings(
            route,
            RouteRenderSettings().also {
                it.innerSize = 0.3
                it.outerSize = 0.3

                it.outerColor = color
                it.innerColor = color

                it.options = ERouteRenderOptions.Main.value
            },
        )
    }

    private fun removeRangeFromMap(route: Route) = SdkCall.execute {
        binding.gemSurfaceView.mapView?.preferences?.routes?.remove(route)
    }

    /**
     * @param rgbaColor the [Rgba] of the respective range settings profile item
     * @return [Color]
     */
    private fun getAndroidColor(rgbaColor: Int): Int {
        val r = 0x000000ff and rgbaColor
        val g = 0x000000ff and (rgbaColor shr 8)
        val b = 0x000000ff and (rgbaColor shr 16)
        val a = 0x000000ff and (rgbaColor shr 24)

        return Color.argb(a, r, g, b)
    }

    private fun setStrokeColor(view: View, @ColorInt color: Int) {
        view.background.colorFilter = PorterDuffColorFilter(
            color,
            PorterDuff.Mode.SRC_IN,
        )
    }

    private fun getStrokeColor(index: Int) = viewModel.listOfRangeProfiles[index].let {
        if (it.isDisplayed) {
            getAndroidColor(
                it.color.apply {
                    alpha = 255
                }.value,
            )
        } else {
            ContextCompat.getColor(this, R.color.outline)
        }
    }

    private fun adjustAddRangeFinderContainerSize(landscape: Boolean) {
        binding.apply {
            addRangeContainer.updateLayoutParams {
                width = if (landscape) {
                    resources.displayMetrics.widthPixels / 2
                } else {
                    ViewGroup.LayoutParams.MATCH_PARENT
                }
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }

            if (!landscape) {
                settingsContainer.updateLayoutParams {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            }
        }

        if (landscape) {
            updateLandscapeSettingsMaxHeight()
        }
    }

    private fun updateLandscapeSettingsMaxHeight() {
        // Wait for measured heights to compute the free vertical space accurately.
        binding.root.post {
            val screenHeight = binding.root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
            val settingsMaxHeight = (screenHeight - binding.addRangeContainer.height).coerceAtLeast(0)
            binding.settingsContainer.updateLayoutParams {
                height = settingsMaxHeight
            }
        }
    }

    /**
     * Computes the portion of the map surface that is not covered by the system bars,
     * the display cutout or the range options panel (in surface-local coordinates).
     *
     * @param padding amount (in pixels) to deflate the rectangle by on every side. Use a
     * positive value to leave breathing room when centering routes, and `0` to obtain the
     * full visible area (e.g. when positioning the Magic Lane logo on the viewport edge).
     */
    private fun getFreeSpaceRect(padding: Int = resources.getDimensionPixelSize(R.dimen.padding_40)): Rect {
        val systemInsets = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(SYSTEM_INSET_TYPES)

        val leftInset = systemInsets?.left ?: 0
        val topInset = systemInsets?.top ?: 0
        val rightInset = systemInsets?.right ?: 0
        val bottomInset = systemInsets?.bottom ?: 0

        val gemSurfaceTop = binding.gemSurfaceView.top
        val surfaceWidth = binding.gemSurfaceView.width
            .takeIf { it > 0 }
            ?: binding.root.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        val surfaceHeight = binding.gemSurfaceView.height
            .takeIf { it > 0 }
            ?: binding.root.height
                .takeIf { it > 0 }
                ?.minus(gemSurfaceTop)
            ?: (resources.displayMetrics.heightPixels - gemSurfaceTop)

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val left = if (isLandscape) {
            binding.addRangeContainer.width
                .takeIf { it > 0 }
                ?: (resources.displayMetrics.widthPixels / 2)
        } else {
            leftInset
        }
        val top = if (isLandscape) {
            topInset
        } else {
            val overlayBottom = if (binding.settingsContainer.isVisible) {
                binding.settingsContainer.bottom.takeIf { it > 0 } ?: binding.addRangeContainer.bottom
            } else {
                binding.addRangeContainer.bottom
            }

            (overlayBottom - gemSurfaceTop).coerceAtLeast(0)
        }
        val right = (surfaceWidth - rightInset).coerceAtLeast(left)
        val bottom = (surfaceHeight - bottomInset).coerceAtLeast(top)
        val paddedLeft = (left + padding).coerceAtMost(right)
        val paddedTop = (top + padding).coerceAtMost(bottom)

        return Rect(
            left = paddedLeft,
            top = paddedTop,
            right = (right - padding).coerceAtLeast(paddedLeft),
            bottom = (bottom - padding).coerceAtLeast(paddedTop),
        )
    }

    /**
     * Positions the Magic Lane logo within the free map area so it stays clear of the
     * system bars, the display cutout and the range options panel.
     * Needs [SdkCall].
     */
    private fun updateFocusViewport() = SdkCall.runSynced {
        val mapView = binding.gemSurfaceView.mapView ?: return@runSynced
        // No padding here: the logo should sit right at the edge of the visible area.
        mapView.preferences?.focusViewport = getFreeSpaceRect(padding = 0)
    }

    /**
     * Utility function that centers the route on map in a predefined rectangle.
     * If no route is provided all routes will be centered.
     * Needs [SdkCall]
     */
    private fun centerRoutes(route: Route? = null) {
        val centeringRectangle = getFreeSpaceRect()
        route?.let {
            binding.gemSurfaceView.mapView?.centerOnRoute(
                route,
                centeringRectangle,
                Animation(EAnimation.Linear, 900),
            )
        } ?: binding.gemSurfaceView.mapView?.centerOnRoutes(
            viewModel.listOfRoutes,
            ERouteDisplayMode.Full,
            centeringRectangle,
            Animation(EAnimation.Linear, 900),
        )
    }

    object EspressoIdlingResource {
        val espressoIdlingResource =
            CountingIdlingResource("RangeFinderInstrumentedTestsIdlingResource")
        fun increment() = espressoIdlingResource.increment()
        fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
    }
}
