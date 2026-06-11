/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.multiplesurfacesinfragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isEmpty
import androidx.core.view.isNotEmpty
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.examples.multiplesurfacesinfragment.databinding.DialogLayoutBinding
import com.magiclane.sdk.examples.multiplesurfacesinfragment.databinding.FragmentSecondBinding
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

/**
 * Hosts a vertically scrolling list of independent map surfaces.
 *
 * Each [GemSurfaceView] owns its own GL surface and renders an independent
 * [MapView]; the user can add or remove surfaces at runtime (up to
 * [MAX_SURFACES_COUNT]). Every surface is released individually so its native
 * resources are freed as soon as it leaves the screen.
 */
class SecondFragment : Fragment() {

    companion object {
        /** Upper bound on how many map surfaces may be displayed at once. */
        private const val MAX_SURFACES_COUNT = 9
    }

    // Maps the native screen address of each surface to its MapView, so the
    // correct map can be released when its surface is removed.
    private val maps = mutableMapOf<Long, MapView?>()

    private var _binding: FragmentSecondBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DataBindingUtil.inflate(inflater, R.layout.fragment_second, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val primaryTint = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.primary))
        val onPrimaryTint = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.on_primary))

        // The "−" button removes the last surface, the "+" button adds a new one.
        setupFab(binding.addSurfaceButton, R.drawable.ic_minus_symbol, primaryTint, onPrimaryTint) {
            deleteLastSurface()
        }
        setupFab(binding.removeSurfaceButton, R.drawable.ic_plus_symbol, primaryTint, onPrimaryTint) {
            addSurface()
        }

        binding.previousButton.setOnClickListener {
            findNavController().popBackStack()
        }

        // Global SDK listeners are shared by every surface, so register them once.
        registerSdkListeners()

        // Start with a single surface on screen.
        addSurface()
    }

    override fun onStop() {
        super.onStop()
        // Release every surface while leaving the screen; they are recreated in
        // onViewCreated when the fragment becomes visible again.
        while (binding.scrolledLinearLayout.isNotEmpty()) {
            deleteLastSurface()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        clearSdkListeners()
        _binding = null
    }

    // Registers SDK-level listeners that are not tied to a specific surface.
    private fun registerSdkListeners() {
        SdkSettings.onApiTokenRejected = {
            runOnAliveUi { showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    // Clears SDK-level listeners so callbacks never reach a destroyed view.
    private fun clearSdkListeners() {
        SdkSettings.onApiTokenRejected = {}
    }

    // Applies the shared visual style and click behaviour to a bottom-bar FAB.
    private fun setupFab(
        fab: FloatingActionButton,
        iconRes: Int,
        backgroundTint: ColorStateList,
        iconTint: ColorStateList,
        onClick: () -> Unit,
    ) {
        fab.visibility = View.VISIBLE
        fab.backgroundTintList = backgroundTint
        fab.imageTintList = iconTint
        fab.setImageDrawable(ContextCompat.getDrawable(requireContext(), iconRes))
        fab.setOnClickListener { onClick() }
    }

    // Creates a new map surface and appends it to the scrolling list.
    private fun addSurface() {
        val linearLayout = binding.scrolledLinearLayout
        if (linearLayout.childCount >= MAX_SURFACES_COUNT) return

        val surfaceContainerHeight = resources.getDimensionPixelSize(R.dimen.surface_container_height)
        val surfaceContainerMargin = resources.getDimensionPixelSize(R.dimen.surface_container_margin)

        val surface = GemSurfaceView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )

            // SDK failed to initialize for this surface: this is fatal, so report
            // it and close the app. The SDK is not running here, so the error
            // message is resolved directly (no SdkCall wrapping).
            onSdkInitFailed = { error ->
                val message =
                    getString(R.string.sdk_initialization_failed, GemError.getMessage(error, requireContext()))
                runOnAliveUi {
                    showDialog(message) {
                        activity?.finish()
                        exitProcess(0)
                    }
                }
            }

            // The default MapView for this surface is ready: remember it by its
            // native screen address so it can be released individually later.
            onDefaultMapViewCreated = onDefaultMapViewCreated@{ mapView ->
                val screenAddress = gemScreen?.address ?: return@onDefaultMapViewCreated
                maps[screenAddress] = mapView
            }
        }

        // Wrap each surface in a fixed-height, margined frame so the surfaces
        // stack cleanly inside the vertical scroll container.
        val frame = FrameLayout(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, surfaceContainerHeight).also {
                it.setMargins(surfaceContainerMargin, surfaceContainerMargin, surfaceContainerMargin, 0)
            }
            addView(surface)
        }

        linearLayout.addView(frame)
    }

    // Releases and removes the most recently added surface.
    private fun deleteLastSurface() {
        val linearLayout = binding.scrolledLinearLayout
        if (linearLayout.isEmpty()) return

        val frame = linearLayout.getChildAt(linearLayout.childCount - 1) as FrameLayout
        val lastSurface = frame.getChildAt(0) as GemSurfaceView

        // Release the native MapView associated with this surface on the SDK thread.
        SdkCall.execute {
            val screenAddress = lastSurface.gemScreen?.address
            maps[screenAddress]?.release()
            maps.remove(screenAddress)
        }

        linearLayout.removeView(frame)
    }

    // Shows a non-dismissable bottom-sheet error dialog, optionally running
    // [onDismiss] when the user acknowledges it.
    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        val activity = activity?.takeUnless { it.isFinishing || it.isDestroyed } ?: return

        val dialog = BottomSheetDialog(activity)
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

    // Posts [block] to the main thread, skipping it if the fragment is no longer
    // attached to a live activity (guards against late SDK callbacks).
    private fun runOnAliveUi(block: () -> Unit) {
        Util.postOnMain {
            if (isAdded && activity?.takeUnless { it.isFinishing || it.isDestroyed } != null) {
                block()
            }
        }
    }
}
