/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemojava;

public class SettingsSliderItem extends SettingsItem {
    private final float valueFrom;
    private final float value;
    private final float valueTo;
    private final String unit;
    private final SliderCallback callback;

    public interface SliderCallback {
        void onChanged(float value);
    }

    public SettingsSliderItem(String title, float valueFrom, float value, float valueTo, String unit, SliderCallback callback) {
        super(title);
        this.valueFrom = valueFrom;
        this.value = value;
        this.valueTo = valueTo;
        this.unit = unit;
        this.callback = callback;
    }

    public float getValueFrom() {
        return valueFrom;
    }

    public float getValue() {
        return value;
    }

    public float getValueTo() {
        return valueTo;
    }

    public String getUnit() {
        return unit;
    }

    public SliderCallback getCallback() {
        return callback;
    }
}

