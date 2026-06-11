/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.basicshapedrawer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.DataBuffer
import com.magiclane.sdk.core.EMapSpeedLimitCoverage
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Image
import com.magiclane.sdk.core.MapDetails
import com.magiclane.sdk.core.MapSpeedLimit
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.RectF
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.BasicShapeDrawer
import com.magiclane.sdk.d3scene.Canvas
import com.magiclane.sdk.d3scene.CanvasListener
import com.magiclane.sdk.d3scene.ETextAlignment
import com.magiclane.sdk.d3scene.ETextStyle
import com.magiclane.sdk.d3scene.TextState
import com.magiclane.sdk.examples.basicshapedrawer.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.basicshapedrawer.databinding.DialogLayoutBinding
import com.magiclane.sdk.routesandnavigation.EImageFileFormat
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.lang.Float.max
import java.util.Locale.getDefault
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    companion object {
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    private lateinit var binding: ActivityMainBinding

    private var canvas: Canvas? = null
    private var shapeDrawer: BasicShapeDrawer? = null
    private val canvasListener = CanvasListener()
    private val mapDetails = MapDetails()
    private var toolbarHeight = 0f
    private var speedSignSize = 0
    private var fontSize = 0
    private var padding = 0f
    private var countryIsoCode = ""
    private var countryName: String? = null
    private var speedLimits: ArrayList<MapSpeedLimit>? = null
    private var insideUrbanAreasTextureId = -1
    private var outsideUrbanAreasTextureId = -1
    private var expressRoadsTextureId = -1
    private var motorwaysTextureId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        speedSignSize = resources.getDimensionPixelSize(R.dimen.speed_sign_size)
        fontSize = resources.getDimensionPixelSize(R.dimen.speed_panel_font_size)
        padding = resources.getDimensionPixelSize(R.dimen.padding).toFloat()

        // Capture toolbar height after layout to use as the canvas origin offset.
        binding.toolbar.post {
            toolbarHeight = binding.toolbar.height.toFloat()
        }

        registerSdkListeners()
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
            Util.postOnMain {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        // Create the canvas and shape drawer once the GL surface is ready.
        binding.gemSurfaceView.onScreenCreated = { screen ->
            val rectF = RectF(0.0f, 0.0f, 1.0f, 1.0f) // full-screen canvas
            canvas = Canvas.produce(screen, rectF, canvasListener)
            shapeDrawer = BasicShapeDrawer.produce(canvas)
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = {
            Util.postOnMain { binding.progressBarView.isVisible = false }
            updateFocusViewport()

            // Load speed-sign textures once the map view is available.
            insideUrbanAreasTextureId = createFlagTexture("inside_urban_areas.png", speedSignSize, speedSignSize)
            outsideUrbanAreasTextureId = createFlagTexture("outside_urban_areas.png", speedSignSize, speedSignSize)
            expressRoadsTextureId = createFlagTexture("express_roads.png", speedSignSize, speedSignSize)
            motorwaysTextureId = createFlagTexture("motorways.png", speedSignSize, speedSignSize)

            binding.gemSurfaceView.onDrawFrameCustom = { _ ->
                shapeDrawer?.apply {
                    binding.gemSurfaceView.mapView?.let { mapView ->
                        mapView.viewport?.center?.let { center ->
                            val coordinates = mapView.transformScreenToWgs(center)
                            coordinates?.let { coord ->
                                // Update country data only when the map centre crosses into a new country.
                                val isoCode = mapDetails.getCountryCode(coord)
                                if (!isoCode.isNullOrEmpty() && (isoCode != countryIsoCode)) {
                                    countryIsoCode = isoCode
                                    countryName = mapDetails.getCountryName(countryIsoCode)?.uppercase(getDefault())
                                    speedLimits = mapDetails.getCountrySpeedLimits(countryIsoCode)
                                }
                            }
                        }
                    }

                    if (!countryName.isNullOrEmpty() && !speedLimits.isNullOrEmpty()) {
                        drawSpeedLimitsPanel(countryName!!, speedLimits!!)
                        renderShapes()
                    }
                }
            }
        }

        // Re-adjust the Magic Lane logo position after rotation or surface size changes.
        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        // Show error dialog if the app token is rejected by the server.
        SdkSettings.onApiTokenRejected = {
            Util.postOnMain {
                showDialog(getString(R.string.token_rejected_message))
            }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurfaceView.apply {
            onSdkInitFailed = {}
            onScreenCreated = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    // Adjusts the Magic Lane logo position to respect system window insets and the status panel.
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
            // Use the status panel height when visible, system bar inset otherwise.
            val bottom = if (binding.statusText.isVisible) {
                val panelHeight = binding.statusText.height.takeIf { it > 0 } ?: binding.statusText.measuredHeight
                (h - panelHeight).coerceAtLeast(top)
            } else {
                (h - (insets?.bottom ?: 0)).coerceAtLeast(top)
            }
            mapView.preferences?.focusViewport = Rect(left, top, right, bottom)
        }
    }

    private fun drawSpeedLimitRow(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        textureId: Int,
        speedLimit: String,
    ) {
        shapeDrawer?.apply {
            // Red ring outline.
            drawCircle(
                (right + left) / 2,
                (bottom + top) / 2,
                3.5f * padding,
                Rgba(255, 0, 0, 255).value,
                true,
            )
            // White fill inside the ring.
            drawCircle(
                (right + left) / 2,
                (bottom + top) / 2,
                2.5f * padding,
                Rgba(255, 255, 255, 255).value,
                true,
            )

            drawText(
                speedLimit,
                (left + right) / 2,
                (bottom + top) / 2,
                TextState(
                    Rgba(0, 0, 0, 255),
                    fontSize = fontSize,
                    alignment = ETextAlignment.Center,
                    style = ETextStyle.BoldStyle,
                ),
            )

            // Road-type icon drawn to the right of the speed sign.
            val w = right - left
            val x = w + padding
            drawTextureRectangle(textureId, x, top, x + w, bottom)
        }
    }

    private fun drawSpeedLimitsPanel(country: String, speedLimits: ArrayList<MapSpeedLimit>) {
        shapeDrawer?.apply {
            val x = 0f
            val y = toolbarHeight

            val countryTextState = TextState(
                Rgba(0, 0, 0, 255),
                fontSize = fontSize,
                alignment = ETextAlignment.LeftCenter,
                style = ETextStyle.BoldStyle,
            )

            val countryTextWidth = getTextWidth(country, countryTextState).toFloat()
            val countryTextHeight = getTextAscent(countryTextState).toFloat()
            val panelRight = x + max(countryTextWidth + 3 * padding, 2 * speedSignSize + 2 * padding)
            val panelBottom = y + countryTextHeight + 2 * padding + speedLimits.size * (speedSignSize + padding)

            // Semi-transparent white background panel.
            drawRectangle(x, y, panelRight, panelBottom, Rgba(255, 255, 255, 155).value, true, 2f)

            drawText(country, x + 2 * padding, y + 2.5f * padding, countryTextState)

            val left = padding
            var top = y + countryTextHeight + 2f * padding
            val right = left + speedSignSize
            var bottom = top + speedSignSize

            for (speedLimit in speedLimits) {
                when (speedLimit.coverage) {
                    EMapSpeedLimitCoverage.WithinTownLimits -> drawSpeedLimitRow(
                        left,
                        top,
                        right,
                        bottom,
                        insideUrbanAreasTextureId,
                        speedLimit.speedLimit.toString(),
                    )
                    EMapSpeedLimitCoverage.OutsideTownLimits -> drawSpeedLimitRow(
                        left,
                        top,
                        right,
                        bottom,
                        outsideUrbanAreasTextureId,
                        speedLimit.speedLimit.toString(),
                    )
                    EMapSpeedLimitCoverage.Freeway -> drawSpeedLimitRow(
                        left,
                        top,
                        right,
                        bottom,
                        expressRoadsTextureId,
                        speedLimit.speedLimit.toString(),
                    )
                    EMapSpeedLimitCoverage.Highway -> drawSpeedLimitRow(
                        left,
                        top,
                        right,
                        bottom,
                        motorwaysTextureId,
                        speedLimit.speedLimit.toString(),
                    )
                }

                top = bottom + padding
                bottom = top + speedSignSize
            }
        }
    }

    private fun createFlagTexture(fileName: String, width: Int, height: Int): Int {
        val imgDataBuffer = getImageDataBuffer(fileName)
        var id = -1

        Image.produceWithDataBuffer(imgDataBuffer, EImageFileFormat.Png)?.apply {
            size?.width = width
            size?.height = height
        }?.let { texture ->
            shapeDrawer?.createTexture(texture, width, height)?.let { id = it }
        }

        return id
    }

    private fun getImageDataBuffer(fileName: String): DataBuffer {
        try {
            val stream = ByteArrayOutputStream()
            val bitmap = assets.open(fileName).use { BitmapFactory.decodeStream(it) }
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            return DataBuffer(byteArray = stream.toByteArray())
        } catch (e: FileNotFoundException) {
            showDialog(e.message.toString())
            return DataBuffer()
        }
    }

    private fun drawTextureRectangle(textureId: Int, left: Float, top: Float, right: Float, bottom: Float) {
        shapeDrawer?.drawTexturedRectangle(
            textureId,
            left,
            top,
            right,
            bottom,
            Rgba(255, 255, 255, 255).value,
            true,
        )
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
}
