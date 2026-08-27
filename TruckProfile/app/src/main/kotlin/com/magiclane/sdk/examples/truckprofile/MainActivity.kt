/*
 * SPDX-FileCopyrightText: 2022-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.truckprofile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.Slider
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.examples.truckprofile.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.truckprofile.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.routesandnavigation.TruckProfile
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.util.Locale
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    companion object {
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        private const val FREE_SPACE_INFLATE_DP = 30
        private const val ROUTE_ANIMATION_MS = 900
    }

    private lateinit var binding: ActivityMainBinding

    enum class ESeekBarValuesType {
        DoubleType,
        IntType,
    }

    /** Multiplier to convert from the display unit to the SDK native unit (e.g. tonnes → kg = 1000). */
    enum class ETruckProfileUnitConverters(val unit: Float) {
        Weight(1000f), // tonnes → kilograms
        Height(100f), // metres  → centimetres
        Length(100f), // metres  → centimetres
        Width(100f), // metres  → centimetres
        AxleWeight(1000f), // tonnes → kilograms
        MaxSpeed(0.27778f), // km/h   → m/s
    }

    enum class ETruckProfileSettings(
        /** Multiplier used to convert between display units and SDK native units. */
        val converter: ETruckProfileUnitConverters,
        /** Reads the corresponding raw value from a TruckProfile (in SDK native units). */
        val getValue: (TruckProfile) -> Number,
    ) {
        Weight(ETruckProfileUnitConverters.Weight, { it.mass }),
        Height(ETruckProfileUnitConverters.Height, { it.height }),
        Length(ETruckProfileUnitConverters.Length, { it.length }),
        Width(ETruckProfileUnitConverters.Width, { it.width }),
        AxleWeight(ETruckProfileUnitConverters.AxleWeight, { it.axleLoad }),
        MaxSpeed(ETruckProfileUnitConverters.MaxSpeed, { it.maxSpeed }),
    }

    data class TruckProfileSettingsModel(
        var title: String = "",
        var type: ESeekBarValuesType,
        var minValueText: String = "",
        var currentValueText: String = "",
        var maxValueText: String = "",
        var minIntValue: Int = 0,
        var currentIntValue: Int = 0,
        var maxIntValue: Int = 0,
        var minDoubleValue: Float = 0f,
        var currentDoubleValue: Float = 0f,
        var maxDoubleValue: Float = 0f,
        var unit: String = "",
    )

    private lateinit var preferencesTruckProfile: TruckProfile

    private var routesList = ArrayList<Route>()

    // Lazy so getString() is safe — adapter is first accessed after the Activity context is attached.
    private val adapter by lazy { TruckProfileSettingsAdapter(getInitialDataSet()) }

    private var waypoints = arrayListOf<Landmark>()

    private val routingService = RoutingService(
        onStarted = {
            binding.progressBar.isVisible = true
        },

        onCompleted = { routes, errorCode, _ ->
            binding.progressBar.isVisible = false
            when (errorCode) {
                GemError.NoError -> {
                    routesList = routes
                    adapter.notifyItemRangeChanged(0, routesList.size)
                    SdkCall.execute {
                        binding.gemSurfaceView.mapView?.presentRoutes(
                            routes = routes,
                            displayBubble = true,
                            animation = Animation(EAnimation.Linear, ROUTE_ANIMATION_MS),
                            edgeAreaInsets = getEdgeAreaInsets(),
                        )
                    }

                    binding.settingsButton.isVisible = true
                    EspressoIdlingResource.decrement()
                }

                else -> {
                    if (errorCode != GemError.Cancel) {
                        showDialog(
                            getString(
                                R.string.routing_service_error,
                                SdkCall.runSynced { GemError.getMessage(errorCode, this) },
                            ),
                        )
                        EspressoIdlingResource.decrement()
                    }
                }
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        EspressoIdlingResource.increment()
        binding.settingsButton.setOnClickListener {
            onSettingsButtonClicked()
        }

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            },
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        clearSdkListeners()
        GemSdk.release()
        exitProcess(0)
    }

    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi { showDialog(errorMessage) { finish() } }
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = {
            updateFocusViewport()
            binding.gemSurfaceView.mapView?.onTouch = { xy ->
                SdkCall.execute {
                    binding.gemSurfaceView.mapView?.cursorScreenPosition = xy
                    val routes = binding.gemSurfaceView.mapView?.cursorSelectionRoutes
                    if (!routes.isNullOrEmpty()) {
                        val route = routes[0]
                        binding.gemSurfaceView.mapView?.apply {
                            preferences?.routes?.mainRoute = route
                            centerOnRoutes(
                                routesList,
                                animation = Animation(EAnimation.Linear, ROUTE_ANIMATION_MS),
                                viewRc = getRouteViewRect(),
                            )
                        }
                    }
                }
            }
        }

        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onWorldwideRoadMapSupportStatus = { status, _ ->
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
                SdkCall.execute {
                    waypoints = arrayListOf(
                        Landmark("London", 51.5073204, -0.1276475),
                        Landmark("Paris", 48.8566932, 2.3514616),
                    )

                    // Initialize with a default truck profile; user can change it via the settings dialog.
                    preferencesTruckProfile = TruckProfile(
                        massKg = (3 * ETruckProfileUnitConverters.Weight.unit).toInt(),
                        heightCm = (1.8 * ETruckProfileUnitConverters.Height.unit).toInt(),
                        lengthCm = (5 * ETruckProfileUnitConverters.Length.unit).toInt(),
                        widthCm = (2 * ETruckProfileUnitConverters.Width.unit).toInt(),
                        axleLoadKg = (1.5 * ETruckProfileUnitConverters.AxleWeight.unit).toInt(),
                        maxSpeedMs = 60 * ETruckProfileUnitConverters.MaxSpeed.unit.toDouble(),
                    )

                    routingService.preferences.transportMode = ERouteTransportMode.Lorry
                    routingService.preferences.truckProfile = preferencesTruckProfile

                    val errorCode = routingService.calculateRoute(waypoints)
                    if (errorCode != GemError.NoError) {
                        val errorMessage =
                            getString(R.string.routing_calculation_error, GemError.getMessage(errorCode, this))
                        runOnAliveUi { showDialog(errorMessage) { finish() } }
                    }
                }
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onWorldwideRoadMapSupportStatus = { _, _ -> }
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    // Adjusts the Magic Lane logo position to respect system window insets.
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            val mapView = binding.gemSurfaceView.mapView ?: return@runSynced
            val viewport = mapView.viewport ?: return@runSynced
            val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(SYSTEM_INSET_TYPES)

            val w = viewport.width
            val h = viewport.height
            val left = insets?.left ?: 0
            val top = insets?.top ?: 0
            val right = (w - (insets?.right ?: 0)).coerceAtLeast(left)
            val bottom = (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
            mapView.preferences?.focusViewport = Rect(left, top, right, bottom)
        }
    }

    // Returns edge insets (px) for presentRoutes: top uses the toolbar bottom, all other sides
    // use system bar / cutout insets, with FREE_SPACE_INFLATE_DP added to every edge.
    private fun getEdgeAreaInsets(): Rect {
        val (left, top, right, bottom) = resolveMapPadding()
        return Rect(left, top, right, bottom)
    }

    // Returns the free-screen rectangle (absolute px coordinates) for centerOnRoutes.
    private fun getRouteViewRect(): Rect {
        val mapWidth = binding.gemSurfaceView.width.takeIf { it > 0 } ?: binding.gemSurfaceView.measuredWidth
        val mapHeight = binding.gemSurfaceView.height.takeIf { it > 0 } ?: binding.gemSurfaceView.measuredHeight
        val (left, top, right, bottom) = resolveMapPadding()
        return Rect(
            left,
            top,
            (mapWidth - right).coerceAtLeast(left),
            (mapHeight - bottom).coerceAtLeast(top),
        )
    }

    // Shared padding values (px) used by getEdgeAreaInsets and getRouteViewRect.
    // Top is toolbar.bottom (which already absorbs the status-bar height); the other three sides
    // are the raw system-bar / cutout insets. FREE_SPACE_INFLATE_DP is added to every edge.
    private fun resolveMapPadding(): Array<Int> {
        val sysInsets = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(SYSTEM_INSET_TYPES)
        val inflate = (FREE_SPACE_INFLATE_DP * resources.displayMetrics.density).toInt()
        val toolbarBottom = binding.toolbar.bottom.takeIf { it > 0 } ?: 0
        return arrayOf(
            (sysInsets?.left ?: 0) + inflate,
            toolbarBottom + inflate,
            (sysInsets?.right ?: 0) + inflate,
            (sysInsets?.bottom ?: 0) + inflate,
        )
    }

    /** Shows a non-dismissable bottom-sheet error dialog. */
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

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

    private fun onSettingsButtonClicked() {
        val convertView = layoutInflater.inflate(R.layout.truck_profile_settings_view, null)
        convertView.findViewById<RecyclerView>(R.id.truck_profile_settings_list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            addItemDecoration(
                DividerItemDecoration(
                    applicationContext,
                    (layoutManager as LinearLayoutManager).orientation,
                ),
            )
            adapter = this@MainActivity.adapter
        }

        adapter.notifyItemRangeChanged(0, ETruckProfileSettings.entries.size)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_name))
            .setView(convertView)
            .setNeutralButton(getString(R.string.save)) { dialog, _ ->
                onSaveButtonClicked()
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun onSaveButtonClicked() {
        EspressoIdlingResource.increment()
        val dataSet = adapter.dataSet

        // Read display values and convert to SDK native units using each setting's converter.
        val weight = (dataSet[ETruckProfileSettings.Weight.ordinal].currentDoubleValue * ETruckProfileSettings.Weight.converter.unit).toInt()
        val height = (dataSet[ETruckProfileSettings.Height.ordinal].currentDoubleValue * ETruckProfileSettings.Height.converter.unit).toInt()
        val length = (dataSet[ETruckProfileSettings.Length.ordinal].currentDoubleValue * ETruckProfileSettings.Length.converter.unit).toInt()
        val width = (dataSet[ETruckProfileSettings.Width.ordinal].currentDoubleValue * ETruckProfileSettings.Width.converter.unit).toInt()
        val axleWeight = (dataSet[ETruckProfileSettings.AxleWeight.ordinal].currentDoubleValue * ETruckProfileSettings.AxleWeight.converter.unit).toInt()
        // MaxSpeed uses an int slider (km/h), convert to m/s.
        val maxSpeed = dataSet[ETruckProfileSettings.MaxSpeed.ordinal].currentIntValue * ETruckProfileSettings.MaxSpeed.converter.unit.toDouble()

        SdkCall.execute {
            routingService.apply {
                preferences.transportMode = ERouteTransportMode.Lorry
                preferencesTruckProfile = TruckProfile(
                    massKg = weight,
                    heightCm = height,
                    lengthCm = length,
                    widthCm = width,
                    axleLoadKg = axleWeight,
                    maxSpeedMs = maxSpeed,
                )
                preferences.truckProfile = preferencesTruckProfile
                binding.gemSurfaceView.mapView?.preferences?.routes?.clear()

                val errorCode = calculateRoute(waypoints)
                if (errorCode != GemError.NoError) {
                    val errorMessage =
                        getString(R.string.routing_calculation_error, GemError.getMessage(errorCode, this@MainActivity))
                    runOnAliveUi { showDialog(errorMessage) { finish() } }
                }
            }
        }
    }

    private fun getInitialDataSet(): List<TruckProfileSettingsModel> = buildList {
        add(
            TruckProfileSettingsModel(
                title = getString(R.string.weight), type = ESeekBarValuesType.DoubleType,
                minValueText = "3 t", currentValueText = "3.0 t", maxValueText = "50 t",
                minDoubleValue = 3.0f, currentDoubleValue = 3.0f, maxDoubleValue = 50.0f,
                unit = "t",
            ),
        )
        add(
            TruckProfileSettingsModel(
                title = getString(R.string.height), type = ESeekBarValuesType.DoubleType,
                minValueText = "1.8 m", currentValueText = "1.8 m", maxValueText = "5 m",
                minDoubleValue = 1.8f, currentDoubleValue = 1.8f, maxDoubleValue = 5.0f,
                unit = "m",
            ),
        )
        add(
            TruckProfileSettingsModel(
                title = getString(R.string.length), type = ESeekBarValuesType.DoubleType,
                minValueText = "5 m", currentValueText = "5.0 m", maxValueText = "20 m",
                minDoubleValue = 5.0f, currentDoubleValue = 5.0f, maxDoubleValue = 20.0f,
                unit = "m",
            ),
        )
        add(
            TruckProfileSettingsModel(
                title = getString(R.string.width), type = ESeekBarValuesType.DoubleType,
                minValueText = "2 m", currentValueText = "2.0 m", maxValueText = "4 m",
                minDoubleValue = 2f, currentDoubleValue = 2f, maxDoubleValue = 4f,
                unit = "m",
            ),
        )
        add(
            TruckProfileSettingsModel(
                title = getString(R.string.axle_weight), type = ESeekBarValuesType.DoubleType,
                minValueText = "1.5 t", currentValueText = "1.5 t", maxValueText = "10 t",
                minDoubleValue = 1.5f, currentDoubleValue = 1.5f, maxDoubleValue = 10.0f,
                unit = "t",
            ),
        )
        add(
            TruckProfileSettingsModel(
                title = getString(R.string.max_speed), type = ESeekBarValuesType.IntType,
                minValueText = "60 km/h", currentValueText = "130 km/h", maxValueText = "250 km/h",
                minIntValue = 60, currentIntValue = 130, maxIntValue = 250,
                unit = "km/h",
            ),
        )
    }

    inner class TruckProfileSettingsAdapter(val dataSet: List<TruckProfileSettingsModel>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            TruckProfileSettingsItemViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.settings_list_item_seekbar, parent, false),
            )

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            (holder as TruckProfileSettingsItemViewHolder).bind(position)
        }

        override fun getItemViewType(position: Int): Int = dataSet[position].type.ordinal

        override fun getItemCount(): Int = dataSet.size

        inner class TruckProfileSettingsItemViewHolder(view: View) :
            RecyclerView.ViewHolder(view) {
            private val text: TextView = view.findViewById(R.id.text)
            private val minValueText: TextView = view.findViewById(R.id.min_value_text)
            private val currentValueText: TextView = view.findViewById(R.id.current_value_text)
            private val maxValueText: TextView = view.findViewById(R.id.max_value_text)
            private val seekBar: Slider = view.findViewById(R.id.seek_bar)

            fun bind(position: Int) {
                val item = dataSet[position]
                val isDoubleItem = item.type == ESeekBarValuesType.DoubleType

                text.text = item.title
                // Min/max labels are common to both item types.
                minValueText.text = item.minValueText
                maxValueText.text = item.maxValueText

                // Clear any previous listener before re-binding to avoid accumulation on ViewHolder reuse.
                seekBar.clearOnChangeListeners()

                if (isDoubleItem) {
                    seekBar.apply {
                        valueTo = item.maxDoubleValue
                        valueFrom = item.minDoubleValue
                        addOnChangeListener { _, value, _ ->
                            item.currentDoubleValue = value
                            item.currentValueText = String.format(Locale.getDefault(), "%.1f %s", value, item.unit)
                            currentValueText.text = item.currentValueText
                        }
                    }
                } else {
                    seekBar.apply {
                        valueTo = item.maxIntValue.toFloat()
                        valueFrom = item.minIntValue.toFloat()
                        stepSize = 1f
                        addOnChangeListener { _, value, _ ->
                            item.currentIntValue = value.toInt()
                            item.currentValueText = String.format(
                                Locale.getDefault(),
                                "%d %s",
                                value.toInt(),
                                item.unit,
                            )
                            currentValueText.text = item.currentValueText
                        }
                    }
                }

                val setting = ETruckProfileSettings.entries[position]
                SdkCall.execute {
                    // Convert from SDK native unit to display unit (e.g. kg → t, cm → m, m/s → km/h).
                    val actualVal = setting.getValue(preferencesTruckProfile).toFloat() / setting.converter.unit
                    seekBar.value = actualVal

                    val valueText = if (isDoubleItem) {
                        String.format(Locale.getDefault(), "%.1f %s", actualVal, item.unit)
                    } else {
                        String.format(Locale.getDefault(), "%d %s", actualVal.toInt(), item.unit)
                    }
                    item.currentValueText = valueText
                    currentValueText.text = valueText

                    if (isDoubleItem) {
                        item.currentDoubleValue = actualVal
                    } else {
                        item.currentIntValue = actualVal.toInt()
                    }
                }

                seekBar.contentDescription = item.title
            }
        }
    }
}

//region TESTING
object EspressoIdlingResource {
    val espressoIdlingResource = CountingIdlingResource("TruckProfileIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
