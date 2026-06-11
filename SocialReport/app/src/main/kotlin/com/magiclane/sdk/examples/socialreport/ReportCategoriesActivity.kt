/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.socialreport

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.MapDetails
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SocialOverlay
import com.magiclane.sdk.examples.socialreport.databinding.ActivityReportCategoriesBinding
import com.magiclane.sdk.examples.socialreport.databinding.DialogLayoutBinding
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.util.SdkCall

class ReportCategoriesActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CATEGORY_ID = "category_id"
        const val EXTRA_CATEGORY_NAME = "category_name"
        const val EXTRA_REPORT_LAT = "report_lat"
        const val EXTRA_REPORT_LON = "report_lon"
        private const val INVALID_ID = -1
        private const val GRID_SPAN_COUNT = 2
    }

    private lateinit var binding: ActivityReportCategoriesBinding
    private val socialReportListener = ProgressListener.create()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityReportCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        binding.toolbar.setNavigationOnClickListener { finish() }

        intent.getStringExtra(EXTRA_CATEGORY_NAME)?.let { binding.toolbar.title = it }

        val categoryId = intent.getIntExtra(EXTRA_CATEGORY_ID, INVALID_ID)
        loadCategories(categoryId)
    }

    private fun loadCategories(categoryId: Int) = SdkCall.execute {
        val overlayInfo = SocialOverlay.reportsOverlayInfo ?: return@execute
        val iconSize = resources.getDimension(R.dimen.event_image_size).toInt()
        val items = mutableListOf<CategoryItem>()

        if (categoryId == INVALID_ID) {
            val countryISOCode = MapDetails().isoCodeForCurrentPosition ?: return@execute
            val categories = overlayInfo.getCategories(countryISOCode) ?: return@execute
            for (category in categories) {
                val name = category.name ?: continue
                items.add(
                    CategoryItem(
                        uid = category.uid,
                        name = name,
                        icon = category.image?.asBitmap(iconSize, iconSize),
                        hasSubcategories = category.hasSubcategories(),
                    ),
                )
            }
        } else {
            val categories = overlayInfo.getCategory(categoryId)?.subcategories ?: return@execute
            for (category in categories) {
                val name = category.name ?: continue
                items.add(
                    CategoryItem(
                        uid = category.uid,
                        name = name,
                        icon = category.image?.asBitmap(iconSize, iconSize),
                        hasSubcategories = category.hasSubcategories(),
                    ),
                )
            }
        }

        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            binding.categoriesRecyclerView.layoutManager = GridLayoutManager(this, GRID_SPAN_COUNT)
            binding.categoriesRecyclerView.adapter = CategoryAdapter(items) { item -> onCategorySelected(item) }
        }
    }

    private fun onCategorySelected(item: CategoryItem) {
        if (item.hasSubcategories) {
            startActivity(
                Intent(this, ReportCategoriesActivity::class.java).apply {
                    putExtra(EXTRA_CATEGORY_ID, item.uid)
                    putExtra(EXTRA_CATEGORY_NAME, item.name)
                },
            )
        } else {
            submitReport(item.uid)
        }
    }

    private fun submitReport(categoryUid: Int) = SdkCall.execute {
        val prepareIdOrError = SocialOverlay.prepareReporting()
        if (prepareIdOrError <= 0) {
            // A negative/zero id means reporting could not be prepared (commonly a poor GPS fix).
            // GemError.getMessage runs here on the SDK thread, so no SdkCall.runSynced wrapper is needed.
            val errorMsg = if (prepareIdOrError == GemError.NotFound || prepareIdOrError == GemError.Required) {
                getString(R.string.gps_accuracy_not_good)
            } else {
                GemError.getMessage(prepareIdOrError, this)
            }
            runOnUiThread { showDialog(errorMsg) }
            return@execute
        }

        val position = PositionService.improvedPosition?.takeIf { it.isValid() }
        val error = SocialOverlay.report(prepareIdOrError, categoryUid, socialReportListener)

        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            if (GemError.isError(error)) {
                // Back on the UI thread, so fetch the localized message inside SdkCall.runSynced.
                showDialog(SdkCall.runSynced { GemError.getMessage(error, this) } ?: "")
            } else {
                val coords = position?.coordinates
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        if (coords != null) {
                            putExtra(EXTRA_REPORT_LAT, coords.latitude)
                            putExtra(EXTRA_REPORT_LON, coords.longitude)
                        }
                    },
                )
                finish()
            }
        }
    }

    private fun showDialog(text: String, title: String = getString(R.string.error), onDismiss: (() -> Unit)? = null) {
        if (isFinishing || isDestroyed) return

        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            this.title.text = title
            message.text = text
            button.setOnClickListener {
                dialog.dismiss()
                onDismiss?.invoke()
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
