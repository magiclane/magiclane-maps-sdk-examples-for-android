/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemojava;

public class SettingsTextItem extends SettingsItem {
    private final String value;
    private final Runnable callback;

    public SettingsTextItem(String title, String value, Runnable callback) {
        super(title);
        this.value = value;
        this.callback = callback;
    }

    public String getValue() {
        return value;
    }

    public Runnable getCallback() {
        return callback;
    }
}
