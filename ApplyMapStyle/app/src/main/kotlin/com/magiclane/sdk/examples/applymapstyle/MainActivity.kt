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
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.applymapstyle.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.applymapstyle.databinding.DialogLayoutBinding
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val listener = ProgressListener.create(onCompleted = { errorCode, _ ->
        if (errorCode != GemError.NoError) {
            showInvalidTokenDialog()
        } else {
            fetchAvailableStyles()
        }
    })

    // Define a content store item so we can request the map styles from it.
    private val contentStore = ContentStore()

    override fun onCreate(savedInstanceState: Bundle?) {
        EspressoIdlingResource.increment()

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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

                SdkSettings.onConnectionStatusUpdated = {}
            }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.gemSurface.onSdkInitFailed = { error ->
            val errorMessage = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, this))
            Util.postOnMain {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        if (!Util.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required))
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
        exitProcess(0)
    }

    private fun showStatusMessage(text: String, withProgress: Boolean = false) {
        Util.postOnMain {
            binding.apply {
                if (!statusText.isVisible) {
                    statusText.visibility = View.VISIBLE
                }
                statusText.text = text

                if (withProgress) {
                    statusProgressBar.visibility = View.VISIBLE
                } else {
                    statusProgressBar.visibility = View.GONE
                }
            }
        }
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

    private fun showInvalidTokenDialog() {
        showDialog(getString(R.string.token_rejected_message)) {
            finish()
            exitProcess(0)
        }
    }

    private fun fetchAvailableStyles() = SdkCall.execute {
        // Call to the content store to asynchronously retrieve the list of map styles.
        contentStore.asyncGetStoreContentList(
            EContentType.ViewStyleHighRes,
            onStarted = {
                showStatusMessage(getString(R.string.download_map_styles_list), true)
            },

            onCompleted = onCompleted@{ styles, errorCode, _ ->
                if (errorCode != GemError.NoError) {
                    EspressoIdlingResource.decrement()
                    showDialog(
                        getString(R.string.map_style_list_download_failed, GemError.getMessage(errorCode, this)),
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
        // Apply the style to the main map view.
        binding.gemSurface.mapView?.preferences?.setMapStyleById(style.id)
    }

    private fun startDownloadingStyle(style: ContentStoreItem) = SdkCall.execute {
        if (style.status == EContentStoreItemStatus.Completed) {
            applyStyle(style)
            showStatusMessage(getString(R.string.style_applied, style.name))
            EspressoIdlingResource.decrement()
            return@execute
        } else {
            // Start downloading a map style item.
            val errorCode = style.asyncDownload(
                onStarted = {
                    showStatusMessage(getString(R.string.download_map_style, style.name), true)
                },

                onCompleted = { error, _ ->
                    if (error != GemError.NoError) {
                        showDialog(getString(R.string.map_style_download_failed, GemError.getMessage(error, this))) {
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
                showDialog(getString(R.string.error_starting_download, GemError.getMessage(errorCode, this))) {
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
