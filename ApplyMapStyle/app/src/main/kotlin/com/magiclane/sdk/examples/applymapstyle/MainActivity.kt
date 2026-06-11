/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.applymapstyle

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.ContentStoreItem
import com.magiclane.sdk.content.EContentStoreItemStatus
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.applymapstyle.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.applymapstyle.databinding.DialogLayoutBinding
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    companion object {
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    private lateinit var binding: ActivityMainBinding

    // Listener used to verify the app authorization token after connection is established.
    private val listener = ProgressListener.create(onCompleted = { errorCode, _ ->
        if (errorCode != GemError.NoError) {
            showInvalidTokenDialog()
        } else {
            fetchAvailableStyles()
        }
    })

    // Content store used to request the list of available map styles.
    private val contentStore = ContentStore()

    override fun onCreate(savedInstanceState: Bundle?) {
        EspressoIdlingResource.increment()

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        registerSdkListeners()

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearSdkListeners()
        GemSdk.release()
        exitProcess(0)
    }

    private fun registerSdkListeners() {
        binding.gemSurface.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            runOnAliveUi {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        // Adjust the Magic Lane logo position once the map view is ready.
        binding.gemSurface.onDefaultMapViewCreated = {
            updateFocusViewport()
        }

        // Re-adjust after rotation or other surface size changes.
        binding.gemSurface.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onConnectionStatusUpdated = { isConnected ->
            if (isConnected) {
                showStatusMessage(getString(R.string.check_application_token), true)
                SdkSettings.appAuthorization?.let {
                    SdkCall.execute {
                        SdkSettings.verifyAppAuthorization(it, listener)
                    }
                } ?: run {
                    showInvalidTokenDialog()
                }
                // Self-clear: only the first connection event matters.
                SdkSettings.onConnectionStatusUpdated = {}
            }
        }
    }

    private fun clearSdkListeners() {
        SdkSettings.onConnectionStatusUpdated = {}
        binding.gemSurface.apply {
            onSdkInitFailed = {}
            onDefaultMapViewCreated = {}
            onSurfaceChanged = { _, _ -> }
        }
    }

    // Adjusts the Magic Lane logo position to respect system window insets and the status panel.
    private fun updateFocusViewport() {
        SdkCall.runSynced {
            val mapView = binding.gemSurface.mapView ?: return@runSynced
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

    private fun showStatusMessage(text: String, withProgress: Boolean = false) {
        Util.postOnMain {
            binding.apply {
                if (!statusText.isVisible) {
                    statusText.visibility = View.VISIBLE
                }
                statusText.text = text

                statusProgressBar.visibility = if (withProgress) View.VISIBLE else View.GONE

                // Re-run after layout so the logo clears the panel's new height.
                statusText.post { updateFocusViewport() }
            }
        }
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

    private fun showInvalidTokenDialog() {
        runOnAliveUi {
            showDialog(getString(R.string.token_rejected_message)) {
                finish()
                exitProcess(0)
            }
        }
    }

    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain { if (isActivityAlive()) block() }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

    private fun fetchAvailableStyles() = SdkCall.execute {
        // Request the list of available map styles from the content store.
        contentStore.asyncGetStoreContentList(
            EContentType.ViewStyleHighRes,
            onStarted = {
                showStatusMessage(getString(R.string.download_map_styles_list), true)
            },

            onCompleted = onCompleted@{ styles, errorCode, _ ->
                if (errorCode != GemError.NoError) {
                    EspressoIdlingResource.decrement()
                    showDialog(
                        getString(
                            R.string.map_style_list_download_failed,
                            SdkCall.runSynced { GemError.getMessage(errorCode, this) },
                        ),
                    ) {
                        finish()
                        exitProcess(0)
                    }
                } else {
                    if (styles.isEmpty()) {
                        showDialog(getString(R.string.map_style_list_empty)) {
                            finish()
                            exitProcess(0)
                        }
                    } else {
                        // Pick the style at the midpoint of the list to showcase a non-default style.
                        val style = if (styles.size > 1) {
                            styles[(styles.size / 2) - 1]
                        } else {
                            styles[0]
                        }

                        startDownloadingStyle(style)
                    }
                }
            },
        )
    }

    private fun applyStyle(style: ContentStoreItem) = SdkCall.execute {
        // Apply the selected style to the main map view.
        binding.gemSurface.mapView?.preferences?.setMapStyleById(style.id)
    }

    private fun startDownloadingStyle(style: ContentStoreItem) = SdkCall.execute {
        if (style.status == EContentStoreItemStatus.Completed) {
            // Style already downloaded; apply it immediately.
            applyStyle(style)
            showStatusMessage(getString(R.string.style_applied, style.name))
            EspressoIdlingResource.decrement()
            return@execute
        }

        // Style not yet local — kick off the download.
        val errorCode = style.asyncDownload(
            onStarted = {
                showStatusMessage(getString(R.string.download_map_style, style.name), true)
            },

            onCompleted = { error, _ ->
                if (error != GemError.NoError) {
                    showDialog(
                        getString(
                            R.string.map_style_download_failed,
                            SdkCall.runSynced { GemError.getMessage(error, this) },
                        ),
                    ) {
                        finish()
                        exitProcess(0)
                    }
                } else {
                    applyStyle(style)
                    showStatusMessage(getString(R.string.style_applied, style.name))
                    EspressoIdlingResource.decrement()
                }
            },
        )

        if (errorCode != GemError.NoError) {
            runOnAliveUi {
                showDialog(
                    getString(
                        R.string.error_starting_download,
                        SdkCall.runSynced { GemError.getMessage(errorCode, this) },
                    ),
                ) {
                    finish()
                    exitProcess(0)
                }
            }
        }
    }
}

//region TESTING
object EspressoIdlingResource {
    val espressoIdlingResource = CountingIdlingResource("ApplyMapStyleIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
