/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.markercollectiondisplayicon

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.DataBuffer
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Image
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EMarkerLabelingMode
import com.magiclane.sdk.d3scene.EMarkerType
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.d3scene.Marker
import com.magiclane.sdk.d3scene.MarkerCollection
import com.magiclane.sdk.d3scene.MarkerCollectionRenderSettings
import com.magiclane.sdk.examples.markercollectiondisplayicon.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.markercollectiondisplayicon.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.routesandnavigation.EImageFileFormat
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.io.ByteArrayOutputStream
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        registerSdkListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearSdkListeners()
        GemSdk.release()
        exitProcess(0)
    }

    // Registers all SDK surface and settings callbacks.
    private fun registerSdkListeners() {
        binding.gemSurface.onSdkInitFailed = { error ->
            // The SDK is not initialized here, so resolve the message directly (no SdkCall needed).
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi { showDialog(errorMessage) { finish() } }
        }

        binding.gemSurface.onDefaultMapViewCreated = { mapView ->
            // Align the Magic Lane logo with the visible map area on first map creation.
            updateFocusViewport()
            populateMap(mapView)
        }

        // Re-align the logo whenever the surface is resized (e.g. rotation).
        binding.gemSurface.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    // Clears SDK-level listeners to avoid callbacks reaching a destroyed activity.
    private fun clearSdkListeners() {
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurface.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    // Adds the point, polyline and polygon marker collections and frames them on screen.
    private fun populateMap(mapView: MapView) {
        val predefinedPlaces = listOf(
            Place("Subway", Coordinates(45.75242654325917, 4.828547972110576)),
            Place("McDonald's", Coordinates(45.75291679094701, 4.828855627148713)),
            Place("Two Amigos", Coordinates(45.75295718457783, 4.828377481057234)),
            Place("Le Jardin de Chine", Coordinates(45.75272771410631, 4.828376649181688)),
        )

        val focusPlace = predefinedPlaces.last()
        val markers = mapView.preferences?.markers

        val (pointCollection, pointSettings) = createPointMarkers(predefinedPlaces)
        markers?.add(pointCollection, pointSettings)

        val (polylineCollection, polylineSettings) = createPolyline(predefinedPlaces)
        markers?.add(polylineCollection, polylineSettings)

        val (polygonCollection, polygonSettings) = createPolygon(focusPlace.coordinates)
        markers?.add(polygonCollection, polygonSettings)

        mapView.centerOnCoordinates(
            focusPlace.coordinates,
            INITIAL_ZOOM_LEVEL,
            xy = getFreeScreenRect().center,
            animation = Animation(EAnimation.Linear, ANIMATION_DURATION_MS),
        )
    }

    private fun createPointMarkers(places: List<Place>): Pair<MarkerCollection, MarkerCollectionRenderSettings> {
        val collection = MarkerCollection(EMarkerType.Point, "Restaurants Nearby")
        places.forEach { place ->
            Marker().apply {
                setCoordinates(arrayListOf(place.coordinates))
                name = place.name
                collection.add(this)
            }
        }

        val image = getBitmap(R.drawable.restaurant)?.let {
            Image.produceWithDataBuffer(DataBuffer(toPngByteArray(it)), EImageFileFormat.Png)
        }

        val settings = MarkerCollectionRenderSettings(image).apply {
            labelTextSize = POINT_LABEL_TEXT_SIZE_MM
            labelingMode = EMarkerLabelingMode.Item.value
            imageSize = POINT_IMAGE_SIZE_MM
        }

        return collection to settings
    }

    private fun createPolyline(places: List<Place>): Pair<MarkerCollection, MarkerCollectionRenderSettings> {
        val collection = MarkerCollection(EMarkerType.Polyline, "Polyline")
        Marker().apply {
            places.forEach { add(it.coordinates) }
            collection.add(this)
        }

        val settings = MarkerCollectionRenderSettings(polylineInnerColor = Rgba.blue()).apply {
            polylineInnerSize = POLYLINE_SIZE_MM
        }
        return collection to settings
    }

    private fun createPolygon(center: Coordinates): Pair<MarkerCollection, MarkerCollectionRenderSettings> {
        val settings = MarkerCollectionRenderSettings(
            polylineInnerColor = Rgba.magenta(),
            polygonFillColor = Rgba(255, 0, 0, 128),
        ).apply {
            polylineInnerSize = POLYGON_OUTLINE_SIZE_MM
        }

        val collection = MarkerCollection(EMarkerType.Polygon, "Polygon")
        collection.add(Marker(center, POLYGON_RADIUS_METERS))
        return collection to settings
    }

    private fun toPngByteArray(bmp: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val byteArray: ByteArray = stream.toByteArray()
        bmp.recycle()

        return byteArray
    }

    private fun getBitmap(drawableRes: Int): Bitmap? {
        val drawable = ResourcesCompat.getDrawable(resources, drawableRes, theme)

        drawable ?: return null

        val canvas = Canvas()
        val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        canvas.setBitmap(bitmap)
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        drawable.draw(canvas)
        return bitmap
    }

    // Positions the Magic Lane logo within the visible map area, clear of the toolbar and system bars.
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            val mapView = binding.gemSurface.mapView ?: return@runSynced
            mapView.preferences?.focusViewport = getFreeScreenRect()
        }
    }

    /**
     * Calculates the free space rectangle on screen.
     * This represents the full screen minus the toolbar on top and system bar insets.
     *
     * @return A Rect representing the available space (left, top, right, bottom)
     */
    private fun getFreeScreenRect(): Rect {
        val root = binding.root
        val insets = ViewCompat.getRootWindowInsets(root)?.getInsets(SYSTEM_INSET_TYPES)

        val width = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        val left = insets?.left ?: 0
        val right = (width - (insets?.right ?: 0)).coerceAtLeast(left)

        val topInset = insets?.top ?: 0
        val toolbarBottom = binding.toolbar.bottom.takeIf { it > 0 } ?: 0
        val top = maxOf(topInset, toolbarBottom)
        val bottom = (height - (insets?.bottom ?: 0)).coerceAtLeast(top)

        return Rect(left, top, right, bottom)
    }

    /** Posts [block] to the main thread, running it only while the activity is still alive. */
    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

    /** Shows a non-dismissable bottom-sheet error dialog. */
    @SuppressLint("InflateParams")
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

    private data class Place(val name: String, val coordinates: Coordinates)

    companion object {
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

        private const val POINT_LABEL_TEXT_SIZE_MM = 2.0
        private const val POINT_IMAGE_SIZE_MM = 8.0
        private const val POLYLINE_SIZE_MM = 1.5
        private const val POLYGON_OUTLINE_SIZE_MM = 1.0
        private const val POLYGON_RADIUS_METERS = 50
        private const val INITIAL_ZOOM_LEVEL = 80
        private const val ANIMATION_DURATION_MS = 900
    }
}
