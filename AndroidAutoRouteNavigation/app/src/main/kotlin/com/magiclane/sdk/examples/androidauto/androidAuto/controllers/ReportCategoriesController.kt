/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

@file:Suppress("unused")

package com.magiclane.sdk.examples.androidauto.androidAuto.controllers

import androidx.car.app.CarContext
import com.magiclane.sdk.core.MapDetails
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SocialOverlay
import com.magiclane.sdk.core.SocialReportsOverlayCategory
import com.magiclane.sdk.d3scene.OverlayCategory
import com.magiclane.sdk.examples.androidauto.androidAuto.Service
import com.magiclane.sdk.examples.androidauto.androidAuto.model.GenericListItemModel
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIActionModel
import com.magiclane.sdk.examples.androidauto.androidAuto.screens.FreeNavigationScreen
import com.magiclane.sdk.examples.androidauto.androidAuto.screens.NavigationScreen
import com.magiclane.sdk.examples.androidauto.androidAuto.screens.QuickLeftListScreen
import com.magiclane.sdk.examples.androidauto.util.INVALID_ID
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.util.ArrayList

typealias ReportCategoriesScreen = QuickLeftListScreen

class ReportCategoriesController(context: CarContext) : ReportCategoriesScreen(context) {

    override fun updateData() {
        title = "Report event"
        headerAction = UIActionModel.backModel()

        listItemModelList = getItems()
    }

    override fun updateMapView() {
        /* DON'T UPDATE */
    }

    private fun getItems(): ArrayList<GenericListItemModel> {
        val result = ArrayList<GenericListItemModel>()

        SdkCall.execute {
            val countryISOCode = MapDetails().isoCodeForCurrentPosition ?: return@execute

            val overlayInfo = SocialOverlay.reportsOverlayInfo ?: return@execute

            val categories = overlayInfo.getCategories(countryISOCode) ?: return@execute

            for (category in categories) {
                val modelItem = GenericListItemModel()
                modelItem.title = category.name ?: continue
                modelItem.icon = category.image?.asBitmap(100, 100)
                modelItem.isBrowsable = true
                modelItem.onClicked = onClicked@{
                    if (Service.topScreen != this)
                        return@onClicked

                    reportSubCategories(category)
                }

                result.add(modelItem)
            }
        }

        return result
    }

    private fun reportSubCategories(category: SocialReportsOverlayCategory) {
        SdkCall.execute {
            val mainCategoryId = category.uid
            if (mainCategoryId == INVALID_ID)
                return@execute

            val prepareIdOrError = SocialOverlay.prepareReporting()
            if (prepareIdOrError <= 0) {
                Util.postOnMain {
                    Service.pop()
                    Service.pushScreen(ErrorDialogController(context, prepareIdOrError))
                }
                return@execute // error
            }

            Util.postOnMain {
                Service.pushScreen(
                    ReportSubCategoriesController(
                        context,
                        prepareIdOrError,
                        mainCategoryId
                    )
                )
            }
        }
    }

}

typealias ReportSubCategoriesScreen = QuickLeftListScreen

class ReportSubCategoriesController(
    context: CarContext,
    private val prepareId: Int,
    private val mainCategoryId: Int
) : ReportSubCategoriesScreen(context) {
    private val socialReportListener = ProgressListener.create()

    override fun updateData() {
        title = "Report event"
        headerAction = UIActionModel.backModel()

        listItemModelList = getItems()
    }

    override fun updateMapView() {
        /* DON'T UPDATE */
    }

    private fun getItems(): ArrayList<GenericListItemModel> = SdkCall.execute {
        val result = ArrayList<GenericListItemModel>()

        val overlayInfo = SocialOverlay.reportsOverlayInfo ?: return@execute arrayListOf()

        val categories =
            overlayInfo.getCategory(mainCategoryId)?.subcategories ?: return@execute arrayListOf()

        for (category in categories) {
            val modelItem = GenericListItemModel()
            modelItem.title = category.name ?: continue
            modelItem.icon = category.image?.asBitmap(100, 100)
            modelItem.isBrowsable = true
            modelItem.onClicked = onClicked@{
                if (Service.topScreen != this)
                    return@onClicked

                val hasSubcategories = SdkCall.execute { category.hasSubcategories() } ?: false
                if (hasSubcategories) {
                    Service.pushScreen(
                        ReportSubCategoriesController(
                            context,
                            prepareId,
                            category.uid
                        )
                    )
                } else {
                    submitReport(category)

                    // return to valid screen (main or navigation)
                    var isBadScreen = true
                    while (isBadScreen) {
                        Service.pop()

                        val topScreen = Service.screenManager?.top

                        if ((topScreen as? NavigationScreen) != null)
                            isBadScreen = false

                        if ((topScreen as? MainScreen) != null)
                            isBadScreen = false

                        if ((topScreen as? FreeNavigationScreen) != null)
                            isBadScreen = false
                    }
                }
            }
            result.add(modelItem)
        }

        return@execute result
    } ?: arrayListOf()

    private fun submitReport(category: OverlayCategory) = SdkCall.execute {
        /*val error =*/ SocialOverlay.report(
            prepareId, category.uid, socialReportListener
        )
//        if (GemError.isError(error)) {
//            Util.postOnMain {
//                Service.pushScreen(ErrorDialogController(context, error))
//            }
//        }
    }

}
