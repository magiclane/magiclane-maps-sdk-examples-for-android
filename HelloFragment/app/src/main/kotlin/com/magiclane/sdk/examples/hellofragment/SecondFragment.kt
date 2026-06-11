/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.hellofragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.hellofragment.databinding.DialogLayoutBinding
import com.magiclane.sdk.examples.hellofragment.databinding.FragmentSecondBinding
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class SecondFragment : Fragment() {

    companion object {
        private val SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    }

    private lateinit var binding: FragmentSecondBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_second, container, false)
        registerSdkListeners()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(requireActivity().window, requireActivity().window.decorView)
            .isAppearanceLightStatusBars = false

        binding.buttonSecond.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        clearSdkListeners()
    }

    // Registers all SDK surface and settings callbacks.
    private fun registerSdkListeners() {
        binding.gemSurfaceView.onSdkInitFailed = { error ->
            val errorMessage =
                getString(R.string.sdk_initialization_failed, GemError.getMessage(error, requireContext()))
            Util.postOnMain {
                if (isAdded) {
                    showDialog(errorMessage) {
                        activity?.finish()
                        exitProcess(0)
                    }
                }
            }
        }

        binding.gemSurfaceView.onDefaultMapViewCreated = { _ ->
            // Align the Magic Lane logo with system window insets on first map creation.
            updateFocusViewport()
        }

        // Re-align the logo whenever the surface is resized (e.g. rotation).
        binding.gemSurfaceView.onSurfaceChanged = { _, _ ->
            updateFocusViewport()
        }

        SdkSettings.onApiTokenRejected = {
            Util.postOnMain { if (isAdded) showDialog(getString(R.string.token_rejected_message)) }
        }
    }

    // Clears SDK-level listeners to avoid callbacks reaching a detached fragment.
    private fun clearSdkListeners() {
        SdkSettings.onApiTokenRejected = {}
        binding.gemSurfaceView.apply {
            onSdkInitFailed = null
            onDefaultMapViewCreated = null
            onSurfaceChanged = null
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

    /** Shows a non-dismissable bottom-sheet error dialog. */
    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        if (!isAdded) return
        val dialog = BottomSheetDialog(requireContext())
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
