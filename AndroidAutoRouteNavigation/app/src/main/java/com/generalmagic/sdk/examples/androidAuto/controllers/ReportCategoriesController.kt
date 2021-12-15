/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("unused")

package com.generalmagic.sdk.examples.androidAuto.controllers

import androidx.car.app.CarContext
import com.generalmagic.sdk.core.MapDetails
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.core.SocialOverlay
import com.generalmagic.sdk.core.SocialReportsOverlayCategory
import com.generalmagic.sdk.d3scene.OverlayCategory
import com.generalmagic.sdk.examples.androidAuto.Service
import com.generalmagic.sdk.examples.androidAuto.model.GenericListItemModel
import com.generalmagic.sdk.examples.androidAuto.model.UIActionModel
import com.generalmagic.sdk.examples.androidAuto.screens.FreeNavigationScreen
import com.generalmagic.sdk.examples.androidAuto.screens.NavigationScreen
import com.generalmagic.sdk.examples.androidAuto.screens.QuickLeftListScreen
import com.generalmagic.sdk.examples.util.INVALID_ID
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util
import java.util.ArrayList

typealias ReportCategoriesScreen = QuickLeftListScreen

class ReportCategoriesController(context: CarContext) : ReportCategoriesScreen(context) {
    private val socialOverlay = SocialOverlay()

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

            val overlayInfo = socialOverlay.reportsOverlayInfo ?: return@execute

            val categories = overlayInfo.getCategories(countryISOCode) ?: return@execute

            for (category in categories) {
                val modelItem = GenericListItemModel()
                modelItem.title = category.name ?: continue
                modelItem.icon = category.image?.asBitmap(100, 100)
                modelItem.isBrowsable = true
                modelItem.onClicked = {
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

            val prepareIdOrError = socialOverlay.prepareReporting()
            if (prepareIdOrError <= 0) {
                Util.postOnMain {
                    Service.pop()
                    Service.show(ErrorDialogController(context, prepareIdOrError))
                }
                return@execute // error
            }

            Util.postOnMain {
                Service.show(
                    ReportSubCategoriesController(
                        context,
                        socialOverlay,
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
    private val socialOverlay: SocialOverlay,
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

        val overlayInfo = socialOverlay.reportsOverlayInfo ?: return@execute arrayListOf()

        val categories =
            overlayInfo.getCategory(mainCategoryId)?.subcategories ?: return@execute arrayListOf()

        for (category in categories) {
            val modelItem = GenericListItemModel()
            modelItem.title = category.name ?: continue
            modelItem.icon = category.image?.asBitmap(100, 100)
            modelItem.isBrowsable = true
            modelItem.onClicked = {
                val hasSubcategories = SdkCall.execute { category.hasSubcategories() } ?: false
                if (hasSubcategories) {
                    Service.show(
                        ReportSubCategoriesController(
                            context,
                            socialOverlay,
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
        val error = socialOverlay.report(
            prepareId, category.uid, socialReportListener
        )
//        if (GemError.isError(error)) {
//            Util.postOnMain {
//                Service.show(ErrorDialogController(context, error))
//            }
//        }
    }

}
