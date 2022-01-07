/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.androidauto.services

import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.places.LandmarkCategory
import com.generalmagic.sdk.places.LandmarkStore
import com.generalmagic.sdk.places.LandmarkStoreService

object FavouritesInstance {
    lateinit var store: LandmarkStore
        private set

    lateinit var category: LandmarkCategory
        private set

    val favourites: ArrayList<Landmark>
        get() {
            val categId = category.id
            return store.getLandmarks(categId) ?: arrayListOf()
        }

    // ---------------------------------------------------------------------------------------------
    fun init() {
        store = LandmarkStoreService().createLandmarkStore("Favourites")?.first!!

        // prepare favourites category
        store.categories?.let { list ->
            for (item in list) {
                if (item.name == "Favourites") {
                    category = item
                    break
                }
            }
        }

        if (!this::category.isInitialized) {
            category = LandmarkCategory()
            category.name = "Favourites"
            store.addCategory(category)
        }
    }

    // ---------------------------------------------------------------------------------------------
}