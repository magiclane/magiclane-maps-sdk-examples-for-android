/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemojava;

public class SettingsSwitchItem extends SettingsItem {
    private final boolean itIs;
    private final SwitchCallback callback;

    public interface SwitchCallback {
        void onChanged(boolean value);
    }

    public SettingsSwitchItem(String title, boolean itIs, SwitchCallback callback) {
        super(title);
        this.itIs = itIs;
        this.callback = callback;
    }

    public boolean isItIs() {
        return itIs;
    }

    public SwitchCallback getCallback() {
        return callback;
    }
}

