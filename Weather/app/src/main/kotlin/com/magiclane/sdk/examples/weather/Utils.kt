/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.weather

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.examples.weather.databinding.DialogLayoutBinding

enum class DialogType { ERROR, INFO }

object Utils {

    fun showDialog(
        text: String,
        activityRef: AppCompatActivity,
        type: DialogType = DialogType.ERROR,
        onDismiss: (() -> Unit)? = null,
    ) {
        activityRef.run {
            val dialog = BottomSheetDialog(this)
            val titleRes = if (type == DialogType.ERROR) R.string.error else R.string.info
            val binding = DialogLayoutBinding.inflate(layoutInflater).apply {
                title.text = getString(titleRes)
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
                setContentView(binding.root)
                show()
            }
        }
    }
}
