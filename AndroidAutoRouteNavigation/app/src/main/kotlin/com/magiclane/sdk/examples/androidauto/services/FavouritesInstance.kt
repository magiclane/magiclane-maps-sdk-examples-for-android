/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
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