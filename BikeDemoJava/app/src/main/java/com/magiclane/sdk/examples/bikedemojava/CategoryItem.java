/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemojava;

import android.graphics.Bitmap;

// Represents a POI category chip in the horizontal bar.
public class CategoryItem {
    private final String name;
    private final Bitmap icon;
    private final int landmarkStoreId;
    private final int categoryId;

    public CategoryItem(String name, Bitmap icon, int landmarkStoreId, int categoryId) {
        this.name = name;
        this.icon = icon;
        this.landmarkStoreId = landmarkStoreId;
        this.categoryId = categoryId;
    }

    public String getName() {
        return name;
    }

    public Bitmap getIcon() {
        return icon;
    }

    public int getLandmarkStoreId() {
        return landmarkStoreId;
    }

    public int getCategoryId() {
        return categoryId;
    }
}
