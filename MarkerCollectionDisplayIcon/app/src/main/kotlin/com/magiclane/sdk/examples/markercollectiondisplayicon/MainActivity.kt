/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.markercollectiondisplayicon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.view.ViewCompat
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
import com.magiclane.sdk.d3scene.Marker
import com.magiclane.sdk.d3scene.MarkerCollection
import com.magiclane.sdk.d3scene.MarkerCollectionRenderSettings
import com.magiclane.sdk.examples.markercollectiondisplayicon.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.markercollectiondisplayicon.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.routesandnavigation.EImageFileFormat
import java.io.ByteArrayOutputStream
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupSdkErrorHandlers()
        setupMapContent()
    }

    private fun setupSdkErrorHandlers() {
        binding.gemSurface.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnUiThread {
                showDialog(errorMessage) { finish() }
            }
        }

        SdkSettings.onApiTokenRejected = {
            runOnUiThread {
                showDialog(getString(R.string.token_rejected_message))
            }
        }
    }

    private fun setupMapContent() {
        binding.gemSurface.onDefaultMapViewCreated = { mapView ->
            val predefinedPlaces = listOf(
                Place("Subway", Coordinates(45.75242654325917, 4.828547972110576)),
                Place("McDonald's", Coordinates(45.75291679094701, 4.828855627148713)),
                Place("Two Amigos", Coordinates(45.75295718457783, 4.828377481057234)),
                Place("Le Jardin de Chine", Coordinates(45.75272771410631, 4.828376649181688)),
            )

            val focusPlace = predefinedPlaces.last()

            val (pointCollection, pointSettings) = createPointMarkers(predefinedPlaces)
            mapView.preferences?.markers?.add(pointCollection, pointSettings)

            val (polylineCollection, polylineSettings) = createPolyline(predefinedPlaces)
            mapView.preferences?.markers?.add(polylineCollection, polylineSettings)

            val (polygonCollection, polygonSettings) = createPolygon(focusPlace.coordinates)
            mapView.preferences?.markers?.add(polygonCollection, polygonSettings)

            mapView.centerOnCoordinates(focusPlace.coordinates, initialZoomLevel, xy = getFreeScreenRect().center, animation = Animation(EAnimation.Linear, animationDurationMs))
        }
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
            labelTextSize = pointLabelTextSizeMm
            labelingMode = EMarkerLabelingMode.Item
            imageSize = pointImageSizeMm
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
            polylineInnerSize = polylineSizeMm
        }
        return collection to settings
    }

    private fun createPolygon(center: Coordinates): Pair<MarkerCollection, MarkerCollectionRenderSettings> {
        val settings = MarkerCollectionRenderSettings(
            polylineInnerColor = Rgba.magenta(),
            polygonFillColor = Rgba(255, 0, 0, 128),
        ).apply {
            polylineInnerSize = polygonOutlineSizeMm
        }

        val collection = MarkerCollection(EMarkerType.Polygon, "Polygon")
        collection.add(Marker(center, polygonRadiusMeters))
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

    /**
     * Calculates the free space rectangle on screen.
     * This represents the full screen minus the toolbar on top and system bar insets.
     *
     * @return A Rect representing the available space (left, top, right, bottom)
     */
    private fun getFreeScreenRect(): Rect {
        val root = binding.root
        val insets = ViewCompat.getRootWindowInsets(root)?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

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

    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        if (isFinishing || isDestroyed) return

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

    override fun onDestroy() {
        super.onDestroy()

        // Release the SDK.
        GemSdk.release()
        exitProcess(0)
    }

    private data class Place(val name: String, val coordinates: Coordinates)

    companion object {
        private const val pointLabelTextSizeMm = 2.0
        private const val pointImageSizeMm = 8.0
        private const val polylineSizeMm = 1.5
        private const val polygonOutlineSizeMm = 1.0
        private const val polygonRadiusMeters = 50
        private const val initialZoomLevel = 80
        private const val animationDurationMs = 900
    }
}
