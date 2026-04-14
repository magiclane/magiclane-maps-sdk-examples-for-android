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

class SecondFragment : Fragment() {

    private val maps = mutableMapOf<Long, MapView?>()
    private val maxSurfacesCount = 9

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

        setupFab(binding.addSurfaceButton, R.drawable.ic_minus_symbol, primaryTint, onPrimaryTint) {
            deleteLastSurface()
        }
        setupFab(binding.removeSurfaceButton, R.drawable.ic_plus_symbol, primaryTint, onPrimaryTint) {
            addSurface()
        }

        binding.previousButton.setOnClickListener {
            findNavController().popBackStack()
        }

        addSurface()
    }

    override fun onStop() {
        super.onStop()
        while (binding.scrolledLinearLayout.isNotEmpty()) {
            deleteLastSurface()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

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

    private fun addSurface() {
        val linearLayout = binding.scrolledLinearLayout
        if (linearLayout.childCount >= maxSurfacesCount) return

        val surfaceContainerHeight = resources.getDimensionPixelSize(R.dimen.surface_container_height)
        val surfaceContainerMargin = resources.getDimensionPixelSize(R.dimen.surface_container_margin)

        val surface = GemSurfaceView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )

            onSdkInitFailed = { error ->
                activity?.let { activity ->
                    val message = getString(R.string.sdk_initialization_failed, GemError.getMessage(error, activity))
                    Util.postOnMain {
                        showDialog(message) {
                            activity.finish()
                            exitProcess(0)
                        }
                    }
                }
            }

            onDefaultMapViewCreated = onDefaultMapViewCreated@{ mapView ->
                val screenAddress = gemScreen?.address ?: return@onDefaultMapViewCreated
                maps[screenAddress] = mapView
            }
        }

        SdkSettings.onApiTokenRejected = {
            Util.postOnMain {
                showDialog(getString(R.string.token_rejected_message))
            }
        }

        val frame = FrameLayout(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, surfaceContainerHeight).also {
                it.setMargins(surfaceContainerMargin, surfaceContainerMargin, surfaceContainerMargin, 0)
            }
            addView(surface)
        }

        linearLayout.addView(frame)
    }

    private fun deleteLastSurface() {
        val linearLayout = binding.scrolledLinearLayout
        if (linearLayout.isEmpty()) return

        val frame = linearLayout.getChildAt(linearLayout.childCount - 1) as FrameLayout
        val lastSurface = frame.getChildAt(0) as GemSurfaceView

        SdkCall.execute {
            val screenAddress = lastSurface.gemScreen?.address
            maps[screenAddress]?.release()
            maps.remove(screenAddress)
        }

        linearLayout.removeView(frame)
    }

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
}
