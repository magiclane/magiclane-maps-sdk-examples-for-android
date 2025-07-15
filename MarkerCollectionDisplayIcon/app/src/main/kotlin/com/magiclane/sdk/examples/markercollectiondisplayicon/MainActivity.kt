// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.markercollectiondisplayicon

// -------------------------------------------------------------------------------------------------------------------------------

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.magiclane.sdk.core.CircleGeographicArea
import com.magiclane.sdk.core.DataBuffer
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Image
import com.magiclane.sdk.core.PolygonGeographicArea
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.d3scene.EMarkerLabelingMode
import com.magiclane.sdk.d3scene.EMarkerType
import com.magiclane.sdk.d3scene.Marker
import com.magiclane.sdk.d3scene.MarkerCollection
import com.magiclane.sdk.d3scene.MarkerCollectionRenderSettings
import com.magiclane.sdk.examples.markercollectiondisplayicon.databinding.ActivityMainBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.routesandnavigation.EImageFileFormat
import java.io.ByteArrayOutputStream
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------
    
    private lateinit var binding: ActivityMainBinding

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapSurface = binding.gemSurface

        mapSurface.onDefaultMapViewCreated = { mapView ->
            val predefinedPlaces = arrayListOf(
                Pair("Subway", Coordinates(45.75242654325917, 4.828547972110576)),
                Pair("McDonald's", Coordinates(45.75291679094701, 4.828855627148713)),
                Pair("Two Amigos", Coordinates(45.75295718457783, 4.828377481057234)),
                Pair("Le Jardin de Chine", Coordinates(45.75272771410631, 4.828376649181688)),
            )

            /* Image and text */
            val imageTextsCollection = MarkerCollection(EMarkerType.Point, "Restaurants Nearby")

            for (place in predefinedPlaces)
            {
                Marker().apply {
                    setCoordinates(arrayListOf(place.second))
                    name = place.first
                    imageTextsCollection.add(this)
                }
            }

            val image = getBitmap(R.drawable.ic_restaurant_foreground)?.let {
                Image.produceWithDataBuffer(DataBuffer(toPngByteArray(it)), EImageFileFormat.Png)
            }

            val imageTextsSettings = MarkerCollectionRenderSettings(image)
            imageTextsSettings.labelTextSize = 2.0 //mm
            imageTextsSettings.labelingMode = EMarkerLabelingMode.Item

            mapView.preferences?.markers?.add(imageTextsCollection, imageTextsSettings)

            /* Polyline */
            val polylineCollection = MarkerCollection(EMarkerType.Polyline, "Polyline")

            Marker().apply {
                for (place in predefinedPlaces)
                    add(place.second)

                polylineCollection.add(this)
            }

            val polylineSettings = MarkerCollectionRenderSettings(polylineInnerColor = Rgba.blue())
            polylineSettings.polylineInnerSize = 1.5 //mm

            mapView.preferences?.markers?.add(polylineCollection, polylineSettings)
            
            /* Polygon */
            val polygonSettings = MarkerCollectionRenderSettings(polylineInnerColor = Rgba.magenta(), polygonFillColor = Rgba(255, 0, 0, 128))
            polygonSettings.polylineInnerSize = 1.0//mm
            
            val polygonCollection = MarkerCollection(EMarkerType.Polygon, "Polyline")
            val marker = Marker(Coordinates(45.75242654325917, 4.828547972110576), 200);
            polygonCollection.add(marker)
            mapView.preferences?.markers?.add(polygonCollection, polygonSettings)

            /* Center map on result */
            mapView.centerOnCoordinates(predefinedPlaces[0].second, 80)
        }
        onBackPressedDispatcher.addCallback(this){
            finish()
            exitProcess(0)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun toPngByteArray(bmp: Bitmap): ByteArray
    {
        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val byteArray: ByteArray = stream.toByteArray()
        bmp.recycle()

        return byteArray
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    private fun getBitmap(drawableRes: Int): Bitmap?
    {
        val drawable =
            ResourcesCompat.getDrawable(resources, drawableRes, theme)

        drawable ?: return null

        val canvas = Canvas()
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        canvas.setBitmap(bitmap)
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        drawable.draw(canvas)
        return bitmap
    }

    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        // Release the SDK.
        GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------
