/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemojava;

import android.graphics.Bitmap;
import com.magiclane.sdk.places.Landmark;

public class SearchResultItem {
    private final Bitmap bmp;
    private final String text;
    private final String subText;
    private final String distance;
    private final String unit;
    private final Landmark landmark;

    public SearchResultItem(Bitmap bmp, String text, String subText, String distance, String unit, Landmark landmark) {
        this.bmp = bmp;
        this.text = text;
        this.subText = subText;
        this.distance = distance;
        this.unit = unit;
        this.landmark = landmark;
    }

    public Bitmap getBmp() {
        return bmp;
    }

    public String getText() {
        return text;
    }

    public String getSubText() {
        return subText;
    }

    public String getDistance() {
        return distance;
    }

    public String getUnit() {
        return unit;
    }

    public Landmark getLandmark() {
        return landmark;
    }
}
