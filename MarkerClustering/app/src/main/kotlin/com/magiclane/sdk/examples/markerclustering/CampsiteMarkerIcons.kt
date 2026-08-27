/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// Marker artwork:
//   • pin_bookable.png      — bookable campsite  (red  #DD3137, tent glyph)
//   • pin_non_bookable.png  — not bookable       (green #007228, tent glyph)
// (Note the eurocampings scheme: BOOKABLE is RED, NOT-BOOKABLE is GREEN.)
//
// The cluster capsule (#0C4B22) is drawn in code (makePill) so it can widen for
// 3-digit (and larger) counts instead of being a fixed-width asset.

package com.magiclane.sdk.examples.markerclustering

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.res.ResourcesCompat
import com.magiclane.sdk.core.DataBuffer
import com.magiclane.sdk.core.Image
import com.magiclane.sdk.routesandnavigation.EImageFileFormat
import java.io.ByteArrayOutputStream

/**
 * Builds the [Image] objects the renderer feeds to the SDK. Must be constructed
 * with an application [Context] to reach the bundled drawables.
 */
class CampsiteMarkerIcons(private val context: Context) {

    private val cache = HashMap<String, Image?>()

    /** Pin for a campsite. `selected` is ignored — the renderer emphasises the
     *  selected pin via a larger `imageSize`. */
    fun makePin(bookable: Boolean, selected: Boolean = false): Image? {
        val resName = if (bookable) "pin_bookable" else "pin_non_bookable"
        return cache.getOrPut(resName) {
            val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
            val drawable = ResourcesCompat.getDrawable(context.resources, resId, context.theme)
                ?: return@getOrPut null
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            imageFromBitmap(bitmap)
        }
    }

    /**
     * Cluster capsule background — flat #0C4B22 capsule with a white 40% border.
     * The count itself is drawn over it by the SDK's group-labeling engine.
     *
     * The BITMAP is power-of-two (64×64, or 128×64 for 3+ digit counts): the SDK
     * rasterises group icons into a texture atlas, and on some GPUs a
     * non-power-of-two icon fails to rasterise and the SDK falls back to its
     * default "missing icon". POT bitmap sizes keep that path happy.
     *
     * The VISIBLE capsule is drawn only as wide as the digit count needs and
     * centred in that bitmap — the leftover side space is transparent. The SDK
     * centres the count label on the image, landing it dead-centre of the
     * visible capsule.
     */
    fun makePill(digits: Int = 1): Image? = cache.getOrPut("pill-$digits") {
        val height = 64f // power-of-two
        val width = if (digits >= 3) 128f else 64f // 128 holds 3–4 digits
        val capsuleWidth = when {
            digits >= 4 -> 91f // snug 4-digit
            digits == 3 -> 76f // snug 3-digit
            else -> 64f // 1–2 digits → circle
        }
        val margin = (width - capsuleWidth) / 2f
        val stroke = height / 24f

        val bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(margin + stroke, stroke, width - margin - stroke, height - stroke)
        val radius = rect.height() / 2f

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(0x0C, 0x4B, 0x22)
        }
        canvas.drawRoundRect(rect, radius, radius, fill)

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = Color.argb(102, 255, 255, 255) // white @ 40%
        }
        canvas.drawRoundRect(rect, radius, radius, border)

        imageFromBitmap(bitmap)
    }

    /** A fully transparent 64×64 (power-of-two) image, used where a layer should
     *  draw nothing — the clustered layer's loose singles and the detail layer's
     *  grouped state. */
    fun transparent(): Image? = cache.getOrPut("transparent") {
        imageFromBitmap(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888))
    }

    private fun imageFromBitmap(bitmap: Bitmap): Image? {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        return Image.produceWithDataBuffer(DataBuffer(stream.toByteArray()), EImageFileFormat.Png)
    }
}
