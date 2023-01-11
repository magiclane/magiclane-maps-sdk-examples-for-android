/*
 * Copyright (C) 2019-2023, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

package com.magiclane.sdk.examples.androidauto.services

import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.LandmarkCategory
import com.magiclane.sdk.places.LandmarkStore
import com.magiclane.sdk.places.LandmarkStoreService

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