// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.rangefinder

// -------------------------------------------------------------------------------------------------------------------------------

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.databinding.Observable
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.ERouteDisplayMode
import com.magiclane.sdk.examples.rangefinder.databinding.ActivityMainBinding
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
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{

    // ---------------------------------------------------ACTIVITY MEMBERS--------------------------------------------------------
    // ---------------------------------------------------------------------------------------------------------------------------

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainActivityViewModel

    private val propertiesObserver = object : Observable.OnPropertyChangedCallback()
    {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int)
        {
            updateOptions()
        }
    }

    /**
     * [routingService] is a service that specialises in computing routes
     */
    private val routingService = RoutingService(
        onStarted = {
            enableButtons(false)
        },
        onCompleted = { routes, errorCode, _ ->
            enableButtons(true)

            when (errorCode)
            {
                GemError.NoError ->
                {
                    //if the process ended with no error add the new route to the route list
                    viewModel.listOfRoutes.add(routes[0])
                    //then display
                    addRangeOnMap(
                        viewModel.listOfRoutes.last(),
                        viewModel.listOfRangeProfiles.last().color
                    )
                    SdkCall.execute { centerRoutes() }
                    showScrollableRangesList()
                }

                GemError.Cancel ->
                { // The routing action was cancelled.
                    viewModel.listOfRangeProfiles.removeAt(viewModel.listOfRangeProfiles.size - 1)
                }

                else ->
                { // There was a problem at computing the routing operation.
                    viewModel.listOfRangeProfiles.removeAt(viewModel.listOfRangeProfiles.size - 1)
                    showErrorDialog(
                        resources.getString(
                            R.string.service_error,
                            GemError.getMessage(errorCode)
                        )
                    )
                }
            }

            EspressoIdlingResource.decrement()
        }
    )

    // ------------------------------------------------OVERRIDDEN METHODS---------------------------------------------------------
    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.rootView)
        EspressoIdlingResource.increment()
        viewModel = ViewModelProvider(this, ViewModelProvider.NewInstanceFactory())[MainActivityViewModel::class.java]
        SdkSettings.onMapDataReady = { isReady ->
            if (isReady)
            {
                viewModel.load()
                addPropertyCallback()

                // Defines an action that should be done when the world map is ready (Updated/ loaded).
                binding.apply {
                    progressBar.isVisible = false
                    optionsButton.setOnClickListener {
                        scroll.isVisible = !scroll.isVisible
                        optionsButton.icon = ResourcesCompat.getDrawable(
                            resources,
                            if (scroll.isVisible) R.drawable.ic_arrow_drop_up_24
                            else R.drawable.ic_arrow_drop_down_24,
                            theme
                        )
                    }

                    addButton.setOnClickListener {
                        EspressoIdlingResource.increment()
                        //check to see if more ranges can be generated on map
                        if (viewModel.listOfRangeProfiles.size >= MAX_ITEMS)
                        {
                            showErrorDialog(
                                resources.getString(
                                    R.string.maximum_items_warning,
                                    MAX_ITEMS
                                )
                            )
                            return@setOnClickListener
                        }

                        if (scroll.isVisible)
                            optionsButton.callOnClick()

                        if (binding.rangeValueEditText.text!!.isNotEmpty())
                        {
                            //get a copy of the current range settings profile
                            val newRange = viewModel.currentRangeSettingsProfile.copy()
                            if (checkIfNewRangeAlreadyExists(newRange) == null)
                            {
                                //if the same range does not already exist
                                //set a new color for it and update it's visibility status
                                SdkCall.execute { newRange.color = viewModel.getNewColor() }
                                newRange.isDisplayed = true
                                //add that copy to the view model's list of range profiles
                                viewModel.listOfRangeProfiles.add(newRange)
                                //command the service to begin generating a new route with
                                //your new range value
                                SdkCall.execute { calculateRanges() }
                                binding.rangeValueEditText.setText("")
                                hideKeyboard()
                            } else
                                showErrorDialog(resources.getString(R.string.same_range_detected_warning))
                        } else
                            showErrorDialog(resources.getString(R.string.empty_range_value_warning))
                    }

                    //set on click listeners on each selector in order to show a dialog with options
                    transportModeSelector.setOnClickListener {
                        showOptionsDialog(it)
                    }
                    bikeTypeSelector.setOnClickListener {
                        showOptionsDialog(it)
                    }
                    rangeTypeSelector.setOnClickListener {
                        showOptionsDialog(it)
                    }

                    //set text listeners in order to update the current range settings profile
                    bikeWeightEditText.doAfterTextChanged { txt ->
                        viewModel.currentRangeSettingsProfile.bikeWeight =
                            txt.toString().toIntOrNull() ?: 0
                    }
                    bikerWeightEditText.doAfterTextChanged { txt ->
                        viewModel.currentRangeSettingsProfile.bikerWeight =
                            txt.toString().toIntOrNull() ?: 0
                    }
                    rangeValueEditText.doAfterTextChanged { txt ->
                        viewModel.currentRangeSettingsProfile.rangeValue =
                            txt.toString().toIntOrNull() ?: 0
                    }
                    updateOptions()
                }
                EspressoIdlingResource.decrement()
            }
        }

        SdkSettings.onApiTokenRejected = {/*
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one. 
             */
            showErrorDialog(resources.getString(R.string.token_rejected))
        }

        if (!Util.isInternetConnected(this))
        {
            showErrorDialog(resources.getString(R.string.internet_connection_warning))
        }
        onBackPressedDispatcher.addCallback(this) {
            finish()
            exitProcess(0)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onConfigurationChanged(newConfig: Configuration)
    {
        super.onConfigurationChanged(newConfig)

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
            changeConstraintsForLandscape()

        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT)
            changeConstraintsForPortrait()

        if (binding.scroll.isVisible)
            binding.optionsButton.callOnClick()
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onPause()
    {
        super.onPause()
        if (isFinishing)
        {
            removePropertyCallback()
            GemSdk.release()
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
    }

    // --------------------------------------------HELPER METHODS-------------------------------------------------------------
    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Checks whether the range value exists or not already in the retained list of range
     * profiles from [viewModel]
     * @param newRange the new [RangeSettingsProfile] to be added
     */
    private fun checkIfNewRangeAlreadyExists(newRange: RangeSettingsProfile) =
        viewModel.listOfRangeProfiles.find {
            val itemMatcher = it.transportMode == newRange.transportMode &&
                it.rangeType == newRange.rangeType &&
                it.rangeValue == newRange.rangeValue
            if (it.transportMode == ERouteTransportMode.Bicycle && newRange.transportMode == ERouteTransportMode.Bicycle)
                itemMatcher && it.bikeType == newRange.bikeType
            else
                itemMatcher
        }

    // ---------------------------------------------------------------------------------------------------------------------------


    /**
     * Adds a listener for every [RangeSettingsProfile] cashed in the [viewModel]
     */
    private fun addPropertyCallback()
    {
        viewModel.cashList.forEach {
            it.addOnPropertyChangedCallback(
                propertiesObserver
            )
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Removes the listener for every [RangeSettingsProfile] cashed in the [viewModel]
     */
    private fun removePropertyCallback()
    {
        viewModel.cashList.forEach {
            it.removeOnPropertyChangedCallback(
                propertiesObserver
            )
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Updates the visibility and values of options for range generation seen on screen.
     */
    private fun updateOptions()
    {
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

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Returns the matching measuring unit based on the current range type
     * @param [rangeType]
     */
    private fun getMeasuringUnit(rangeType: ERouteType) = when (rangeType)
    {
        ERouteType.Fastest -> getString(R.string.seconds)
        ERouteType.Shortest -> getString(R.string.meters)
        ERouteType.Economic -> getString(R.string.watts_per_hour)
        else -> ""
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Shows a [BottomSheetDialog] with a short message
     */
    @SuppressLint("InflateParams")
    private fun showErrorDialog(text: String)
    {
        while (!EspressoIdlingResource.espressoIdlingResource.isIdleNow)
            EspressoIdlingResource.decrement()

        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_layout, null).apply {
            findViewById<TextView>(R.id.title).text = getString(R.string.error)
            findViewById<TextView>(R.id.title).setTextColor(
                ContextCompat.getColor(this@MainActivity, R.color.text_color)
            )
            findViewById<TextView>(R.id.message).text = text
            findViewById<TextView>(R.id.message).setTextColor(
                ContextCompat.getColor(this@MainActivity, R.color.text_color)
            )
            findViewById<Button>(R.id.button).setOnClickListener {
                dialog.dismiss()
            }
        }
        dialog.apply {
            setCancelable(false)
            setContentView(view)
            show()
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Shows a dialog with a list of options based on the picked selector view
     * @param view the selector that was clicked on
     */
    private fun showOptionsDialog(view: View)
    {
        val list = getOptionsArrayList(view).map { it.name }

        val dialogBuilder = MaterialAlertDialogBuilder(this)

        dialogBuilder.setTitle(getDialogTitle(view))
            .setItems(list.toTypedArray()) { dialog, pos ->
                onItemClicked(view, pos)
                dialog.cancel()
            }
            .show()
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * On item clicked callback. Will updated the current selected option from the
     * current [RangeSettingsProfile]
     * @param selector the selector that was clicked on
     * @param position option's positions in the dialog's options list
     */
    private fun onItemClicked(selector: View, position: Int)
    {
        if (selector.id == R.id.transport_mode_selector)
            viewModel.currentRangeSettingsProfile = viewModel.cashList[position]
        with(viewModel.currentRangeSettingsProfile) {
            when (selector.id)
            {
                R.id.transport_mode_selector ->
                    transportMode = viewModel.listOfTransportTypes[position]

                R.id.range_type_selector ->
                    rangeType =
                        if (isBicycleTransportType()) viewModel.listOfBicycleRangeTypes[position]
                        else viewModel.listOfRangeTypes[position]

                R.id.bike_type_selector ->
                    bikeType = viewModel.listOfBikeTypes[position]
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Returns the matching options list to be shown in the dialog view
     * based on the on the picked selector view
     * @param view the selector that was clicked on
     */
    private fun getOptionsArrayList(view: View) = when (view.id)
    {
        R.id.transport_mode_selector ->
            viewModel.listOfTransportTypes

        R.id.range_type_selector ->
            if (isBicycleTransportType())
                viewModel.listOfBicycleRangeTypes
            else
                viewModel.listOfRangeTypes

        R.id.bike_type_selector -> viewModel.listOfBikeTypes

        else -> ArrayList()
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Checks whether the current range type coincides with [ERouteTransportMode.Bicycle]
     */
    private fun isBicycleTransportType(): Boolean =
        viewModel.currentRangeSettingsProfile.transportMode == ERouteTransportMode.Bicycle

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Returns the matching title to be shown in the dialog view
     * based on the on the picked selector view
     * @param view the selector that was clicked on
     */
    private fun getDialogTitle(view: View) = ContextCompat.getString(
        this, when (view.id)
        {
            R.id.transport_mode_selector -> R.string.transport_mode
            R.id.range_type_selector -> R.string.range_type
            R.id.bike_type_selector -> R.string.bike_type
            else -> R.string.transport_mode
        }
    )

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Commands the [routingService] to calculate array of routes with
     * attached range values and routing preferences
     */
    private fun calculateRanges()
    {
        SdkCall.execute {
            with(routingService.preferences) {
                viewModel.currentRangeSettingsProfile.let {
                    //get an electric bike profile in case the Economic range type option is picked
                    val electricBikeProfile =
                        if (isBicycleTransportType() && it.rangeType == ERouteType.Economic)
                            ElectricBikeProfile(
                                EEBikeType.Pedelec,
                                it.bikeWeight.toFloat(),
                                it.bikerWeight.toFloat(),
                                2f,
                                4f
                            )
                        else null

                    //set your routing preferences according to your selected options
                    transportMode = it.transportMode
                    routeType = it.rangeType
                    setRouteRanges(
                        ArrayList(arrayListOf(viewModel.listOfRangeProfiles.last().rangeValue)),
                        100
                    )
                    if (isBicycleTransportType())
                        setBikeProfile(it.bikeType, electricBikeProfile)
                }
            }
            routingService.calculateRoute(arrayListOf(Landmark("London", 51.5073204, -0.1276475)))
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun hideKeyboard()
    {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * Creates and displays a set of chip-like views for each [RangeSettingsProfile]
     * retained in the view model list of range profiles
     */
    private fun showScrollableRangesList()
    {
        viewModel.listOfRangeProfiles.let { currentSelectedRanges ->
            binding.apply {
                currentRangesButtonsContainer.removeAllViews()
                if (currentSelectedRanges.size <= 0) return

                for (i in 0 until currentSelectedRanges.size)
                {
                    val rangeContainer = layoutInflater.inflate(
                        R.layout.button_text,
                        currentRangesButtonsContainer,
                        false
                    ) as ConstraintLayout
                    val textView = rangeContainer.findViewById<TextView>(R.id.text_button)
                    val clearButton = rangeContainer.findViewById<ImageView>(R.id.icon)

                    currentSelectedRanges[i].let {
                        textView.text = resources.getString(
                            R.string.range_item_text,
                            if (isBicycleTransportType()) it.bikeType.name else "",
                            it.transportMode.name,
                            it.rangeValue,
                            getMeasuringUnit(it.rangeType)
                        )
                    }

                    textView.setTextColor(
                        ContextCompat.getColor(
                            this@MainActivity,
                            R.color.text_color
                        )
                    )

                    //center on the route on long click
                    textView.setOnLongClickListener {
                        SdkCall.execute { centerRoutes(route = viewModel.listOfRoutes[i]) }
                        true
                    }

                    //display or hide the route on click
                    textView.setOnClickListener {
                        viewModel.listOfRangeProfiles[i].let {
                            it.isDisplayed = !it.isDisplayed
                            if (it.isDisplayed)
                                addRangeOnMap(
                                    viewModel.listOfRoutes[i],
                                    viewModel.listOfRangeProfiles[i].color
                                )
                            else
                                removeRangeFromMap(viewModel.listOfRoutes[i])
                            SdkCall.execute {
                                setStrokeColor(
                                    rangeContainer,
                                    getStrokeColor(index = i)
                                )
                            }
                        }
                    }

                    //set click event on delete button
                    clearButton.setOnClickListener {
                        //remove the associated range settings profile of this route
                        currentSelectedRanges.removeAt(i)
                        SdkCall.execute {
                            //mark the color as unused
                            viewModel.resetColor(i)
                            //remove the range from map
                            removeRangeFromMap(viewModel.listOfRoutes[i])
                            viewModel.listOfRoutes.removeAt(i)
                            centerRoutes()
                        }
                        showScrollableRangesList()
                    }
                    clearButton.setColorFilter(
                        ContextCompat.getColor(
                            this@MainActivity,
                            R.color.text_color
                        )
                    )

                    SdkCall.execute { setStrokeColor(rangeContainer, getStrokeColor(index = i)) }
                    currentRangesButtonsContainer.addView(rangeContainer)
                }

                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                currentRangesScrollContainer.post {
                    currentRangesScrollContainer.fullScroll(
                        View.FOCUS_RIGHT
                    )
                }

            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * An utility function that enables and disables views that may cause thread interruptions
     * while routes calculations are being made
     */
    private fun enableButtons(enable: Boolean)
    {
        binding.addButton.isEnabled = enable
        for (item in binding.currentRangesButtonsContainer.children)
        {
            val button = item.findViewById<ImageView>(R.id.icon)
            button.isEnabled = enable
        }
        binding.progressBar.isVisible = !enable
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun addRangeOnMap(route: Route, color: Rgba) = SdkCall.execute {
        binding.gemSurfaceView.mapView?.preferences?.routes?.addWithRenderSettings(route,
            RouteRenderSettings().also {
                it.innerSize = 0.3
                it.outerSize = 0.3

                it.outerColor = color
                it.innerColor = color

                it.options = ERouteRenderOptions.Main.value
            }
        )
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun removeRangeFromMap(route: Route) = SdkCall.execute {
        binding.gemSurfaceView.mapView?.preferences?.routes?.remove(route)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    /**
     * @param rgbaColor the [Rgba] of the respective range settings profile item
     * @return [Color]
     */
    private fun getAndroidColor(rgbaColor: Int): Int
    {
        val r = 0x000000ff and rgbaColor
        val g = 0x000000ff and (rgbaColor shr 8)
        val b = 0x000000ff and (rgbaColor shr 16)
        val a = 0x000000ff and (rgbaColor shr 24)

        return Color.argb(a, r, g, b)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun setStrokeColor(view: View, @ColorInt color: Int)
    {
        view.background.colorFilter = PorterDuffColorFilter(
            color,
            PorterDuff.Mode.SRC_IN
        )
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun getStrokeColor(index: Int) = viewModel.listOfRangeProfiles[index].let {
        if (it.isDisplayed) getAndroidColor(it.color.apply {
            alpha = 255
        }.value)
        else ContextCompat.getColor(this, R.color.outline)
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun changeConstraintsForLandscape()
    {
        binding.apply {
            ConstraintSet().apply {
                //move main content to left side and shorten it's width
                connect(
                    R.id.main_content,
                    ConstraintSet.TOP,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.TOP
                )
                connect(
                    R.id.main_content,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.BOTTOM
                )
                connect(
                    R.id.main_content,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START
                )
                connect(
                    R.id.main_content,
                    ConstraintSet.END,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.END
                )
                setHorizontalBias(R.id.main_content, 0f)
                setVerticalBias(R.id.main_content, 0f)

                //move gem surface to right side and shorten it's width
                connect(
                    R.id.gem_surface_view,
                    ConstraintSet.TOP,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.TOP
                )
                connect(
                    R.id.gem_surface_view,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.BOTTOM
                )
                connect(
                    R.id.gem_surface_view,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START
                )
                connect(
                    R.id.gem_surface_view,
                    ConstraintSet.END,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.END
                )

                //move scroll to right side and shorten it's width
                connect(
                    R.id.scroll,
                    ConstraintSet.TOP,
                    R.id.main_content,
                    ConstraintSet.BOTTOM
                )
                connect(
                    R.id.scroll,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.BOTTOM
                )
                connect(
                    R.id.scroll,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START
                )
                connect(
                    R.id.scroll,
                    ConstraintSet.END,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.END
                )
                setHorizontalBias(R.id.scroll, 0f)

                applyTo(rootView)
            }

            mainContent.updateLayoutParams {
                width = resources.displayMetrics.widthPixels / 2
                height = ConstraintSet.WRAP_CONTENT
            }

            gemSurfaceView.updateLayoutParams {
                width = ConstraintSet.MATCH_CONSTRAINT
                height = ConstraintSet.MATCH_CONSTRAINT
            }

            scroll.updateLayoutParams {
                width = resources.displayMetrics.widthPixels / 2
                height = ConstraintSet.MATCH_CONSTRAINT
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun changeConstraintsForPortrait()
    {
        binding.apply {
            ConstraintSet().apply {
                //move main content to top side and match it's width to it's parent
                connect(
                    R.id.main_content,
                    ConstraintSet.TOP,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.TOP
                )
                connect(
                    R.id.main_content,
                    ConstraintSet.BOTTOM,
                    R.id.gem_surface_view,
                    ConstraintSet.TOP
                )
                connect(
                    R.id.main_content,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START
                )
                connect(
                    R.id.main_content,
                    ConstraintSet.END,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.END
                )

                //move gem surface to bottom of main content and match it's layout params to the remaining space
                connect(
                    R.id.gem_surface_view,
                    ConstraintSet.TOP,
                    R.id.main_content,
                    ConstraintSet.BOTTOM
                )
                connect(
                    R.id.gem_surface_view,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.BOTTOM
                )
                connect(
                    R.id.gem_surface_view,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START
                )
                connect(
                    R.id.gem_surface_view,
                    ConstraintSet.END,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.END
                )

                //move scroll to bottom of main content and match it's layout params to the remaining space
                connect(R.id.scroll, ConstraintSet.TOP, R.id.main_content, ConstraintSet.BOTTOM)
                connect(
                    R.id.scroll,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.BOTTOM
                )
                connect(
                    R.id.scroll,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START
                )
                connect(R.id.scroll, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

                applyTo(rootView)
            }

            mainContent.updateLayoutParams {
                width = ConstraintSet.MATCH_CONSTRAINT
                height = ConstraintSet.WRAP_CONTENT
            }

            gemSurfaceView.updateLayoutParams {
                width = ConstraintSet.MATCH_CONSTRAINT
                height = ConstraintSet.MATCH_CONSTRAINT
            }

            scroll.updateLayoutParams {
                width = ConstraintSet.MATCH_CONSTRAINT
                height = ConstraintSet.MATCH_CONSTRAINT
            }

        }
    }

    /**
     * Utility function that centers the route on map in a predefined rectangle.
     * If no route is provided all routes will be centered.
     * Needs [SdkCall]
     */
    private fun centerRoutes(route: Route? = null)
    {
        val centeringPadding = resources.getDimensionPixelSize(R.dimen.big_padding)
        val centeringRectangle = Rect(
            left = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                binding.gemSurfaceView.measuredWidth - resources.displayMetrics.widthPixels / 2 - centeringPadding
            else centeringPadding,
            top = centeringPadding,
            right = binding.gemSurfaceView.measuredWidth - centeringPadding,
            bottom = binding.gemSurfaceView.measuredHeight - centeringPadding
        )
        route?.let {
            binding.gemSurfaceView.mapView?.centerOnRoute(
                route,
                centeringRectangle,
                Animation(EAnimation.Linear, 900)
            )
        } ?: binding.gemSurfaceView.mapView?.centerOnRoutes(
            viewModel.listOfRoutes,
            ERouteDisplayMode.Full,
            centeringRectangle,
            Animation(EAnimation.Linear, 900)
        )
    }

    //region --------------------------------------------------FOR TESTING--------------------------------------------------------------
    // ---------------------------------------------------------------------------------------------------------------------------
    public object EspressoIdlingResource
    {
        val espressoIdlingResource = CountingIdlingResource("ApplyMapStyleInstrumentedTestsIdlingResource")
        fun increment() = espressoIdlingResource.increment()
        fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
    }
    //endregion  -------------------------------------------------------------------------------------------------------------------------------
}
// -------------------------------------------------------------------------------------------------------------------------------
