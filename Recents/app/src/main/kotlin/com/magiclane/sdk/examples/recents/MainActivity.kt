/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.recents

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.RectangleGeographicArea
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.Time
import com.magiclane.sdk.examples.recents.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.recents.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.LandmarkStore
import com.magiclane.sdk.places.LandmarkStoreService
import com.magiclane.sdk.util.SdkCall
import kotlin.math.abs
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    companion object {
        const val RECENTS_STORE_NAME = "Recents"
    }

    private lateinit var binding: ActivityMainBinding

    // Landmark store used as the "Recents" history.
    private var recentsStore: LandmarkStore? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        applyOrientationLayout()
        applyWindowInsets()

        registerSdkCallbacks()
        initializeSdk()

        binding.addLondonButton.setOnClickListener { addRecent("London", 51.5074, -0.1278) }
        binding.addParisButton.setOnClickListener { addRecent("Paris", 48.8566, 2.3522) }
        binding.addRomeButton.setOnClickListener { addRecent("Rome", 41.9028, 12.4964) }
        binding.showRecentsButton.setOnClickListener {
            startActivity(Intent(this, RecentsActivity::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        clearSdkCallbacks()

        // Release the SDK.
        GemSdk.release()
        exitProcess(0)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationLayout()
    }

    private fun initializeSdk() {
        val error = GemSdk.initSdkWithDefaults(this)
        if (error != GemError.NoError) {
            showDialog(
                getString(R.string.sdk_initialization_failed, SdkCall.runSynced { GemError.getMessage(error, this) }),
            ) {
                // The SDK initialization failed, so we exit the app.
                finish()
            }
        } else {
            SdkCall.execute {
                recentsStore = LandmarkStoreService().createLandmarkStore(RECENTS_STORE_NAME)?.first
            }
        }
    }

    private fun registerSdkCallbacks() {
        SdkSettings.onApiTokenRejected = {
            showDialog(getString(R.string.token_rejected_message))
        }
    }

    private fun clearSdkCallbacks() {
        SdkSettings.onApiTokenRejected = {}
    }

    // Stacks the "Add" buttons vertically in portrait and lays them out
    // horizontally, with equal widths, in landscape.
    private fun applyOrientationLayout() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        binding.addButtonsContainer.orientation = if (isLandscape) {
            LinearLayout.HORIZONTAL
        } else {
            LinearLayout.VERTICAL
        }

        listOf(binding.addLondonButton, binding.addParisButton, binding.addRomeButton).forEach { button ->
            button.updateLayoutParams<LinearLayout.LayoutParams> {
                width = if (isLandscape) 0 else ViewGroup.LayoutParams.MATCH_PARENT
                weight = if (isLandscape) 1f else 0f
            }
        }
    }

    // Keeps the buttons clear of system bars and display cutouts.
    // The toolbar handles the top inset itself, through its binding adapters.
    private fun applyWindowInsets() {
        val bigPadding = resources.getDimensionPixelSize(R.dimen.big_padding)

        ViewCompat.setOnApplyWindowInsetsListener(binding.addButtonsContainer) { view, insets ->
            val systemInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.updatePadding(
                left = bigPadding + systemInsets.left,
                right = bigPadding + systemInsets.right,
            )
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.showRecentsButton) { view, insets ->
            val systemInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = bigPadding + systemInsets.left
                rightMargin = bigPadding + systemInsets.right
                bottomMargin = bigPadding + systemInsets.bottom
            }
            insets
        }
    }

    private fun addRecent(name: String, latitude: Double, longitude: Double) {
        val added = SdkCall.execute {
            addLandmarkToHistory(Landmark(name, Coordinates(latitude, longitude)))
        } ?: false

        val message = if (added) R.string.landmark_added else R.string.landmark_add_failed
        Toast.makeText(this, getString(message, name), Toast.LENGTH_SHORT).show()
    }

    // Returns the store ID of the landmark matching the given one by coordinates,
    // or 0 if the landmark is not in the store. Must be called on the SDK thread.
    private fun getLandmarkId(landmarkStore: LandmarkStore?, landmark: Landmark, categoryId: Int = -2): Int {
        if (landmarkStore != null && categoryId != 0) {
            val coordinates = landmark.coordinates ?: return 0
            val area = RectangleGeographicArea(coordinates, 10.0, 10.0)
            val landmarks = landmarkStore.getLandmarksByArea(area, categoryId).orEmpty()

            if (landmarks.isNotEmpty()) {
                val threshold = 0.00001
                val lat = coordinates.latitude
                val lon = coordinates.longitude

                for (item in landmarks) {
                    val itemCoordinates = item.coordinates ?: continue
                    if ((abs(itemCoordinates.latitude - lat) < threshold) &&
                        (abs(itemCoordinates.longitude - lon) < threshold)
                    ) {
                        return item.id
                    }
                }
            }
        }
        return 0
    }

    // Adds the landmark to the "Recents" store, stamped with the current time.
    // If the landmark is already in the store, it is removed first, so it becomes
    // the most recent entry. Must be called on the SDK thread.
    private fun addLandmarkToHistory(landmark: Landmark): Boolean {
        recentsStore?.let { store ->
            val id = getLandmarkId(store, landmark)

            val lmk = Landmark()
            lmk.assign(landmark)

            if (id != 0) {
                store.removeLandmark(id)
            }

            if (store.addLandmark(lmk) == GemError.NoError) {
                return true
            }
        }
        return false
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
}
