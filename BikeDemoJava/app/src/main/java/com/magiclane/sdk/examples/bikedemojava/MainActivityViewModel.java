/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemojava;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.magiclane.sdk.core.GenericCategories;
import com.magiclane.sdk.places.Landmark;
import com.magiclane.sdk.places.LandmarkCategory;
import com.magiclane.sdk.routesandnavigation.EBikeProfile;
import com.magiclane.sdk.routesandnavigation.EEBikeType;
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode;
import com.magiclane.sdk.routesandnavigation.ElectricBikeProfile;
import com.magiclane.sdk.routesandnavigation.RoutePreferences;
import com.magiclane.sdk.util.GemCall;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivityViewModel extends ViewModel {

    public final MutableLiveData<List<SearchResultItem>> searchResultListLivedata = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isElectricBikeProfile = new MutableLiveData<>(false);

    // POI categories shown in the horizontal bar, and the index of the selected chip.
    public final MutableLiveData<List<CategoryItem>> categoriesLivedata = new MutableLiveData<>(Collections.emptyList());
    public final MutableLiveData<Integer> selectedCategory = new MutableLiveData<>(CategoryAdapter.NO_CATEGORY);
    private boolean categoriesLoaded = false;

    // Loads the generic POI categories once the SDK map data is ready.
    public void loadCategories(int iconSize) {
        if (categoriesLoaded) return;
        categoriesLoaded = true;
        new Thread(() -> {
            List<CategoryItem> items = GemCall.INSTANCE.execute(() -> {
                List<CategoryItem> result = new ArrayList<>();
                ArrayList<LandmarkCategory> categories = new GenericCategories().getCategories();
                if (categories != null) {
                    for (LandmarkCategory cat : categories) {
                        String name = cat.getName();
                        if (name == null) continue;
                        result.add(new CategoryItem(
                            name,
                            cat.getImage() != null ? cat.getImage().asBitmap(iconSize, iconSize) : null,
                            cat.getLandmarkStoreId(),
                            cat.getId()
                        ));
                    }
                }
                return result;
            });
            categoriesLivedata.postValue(items != null ? items : Collections.emptyList());
        }).start();
    }

    public Landmark destination = null;
    public boolean isElectric = false;
    public EBikeProfile bikeProfile = EBikeProfile.City;
    public RoutePreferences routePreferences;
    private ElectricBikeProfile electricBikeProfile = null;
    private final List<SettingsItem> settingsList = new ArrayList<>();
    private float hillsFactor = 5f;
    private float bikeWeight = 12f;
    private float bikerWeight = 70f;
    private float auxConsumptionDay = 20f;
    private float auxConsumptionNight = 20f;
    private boolean avoidFerries = false;
    private boolean avoidUnpavedRoads = false;

    public void initPreferences() {
        electricBikeProfile = new ElectricBikeProfile(EEBikeType.Pedelec, null, null, null, null);
        routePreferences = new RoutePreferences();
        routePreferences.setTransportMode(ERouteTransportMode.Bicycle);
        routePreferences.setBikeProfile(EBikeProfile.City, isElectric ? electricBikeProfile : null);
    }

    public void setBikeProfile(EBikeProfile type) {
        GemCall.INSTANCE.execute(() -> {
            bikeProfile = type;
            routePreferences.setBikeProfile(type, isElectric ? electricBikeProfile : null);
            return null;
        });
    }

    private void setIsElectric(boolean isElectric) {
        GemCall.INSTANCE.execute(() -> {
            this.isElectric = isElectric;
            isElectricBikeProfile.postValue(isElectric);
            setBikeProfile(bikeProfile);
            return null;
        });
    }

    public List<SettingsItem> getSettingsList() {
        settingsList.clear();

        settingsList.add(new SettingsSwitchItem("E-Bike", isElectric, this::setIsElectric));

        settingsList.add(new SettingsSliderItem("Hills", 0f, hillsFactor, 10f, "", value -> {
            hillsFactor = value;
            GemCall.INSTANCE.execute(() -> {
                routePreferences.setAvoidBikingHillFactor(value);
                return null;
            });
        }));

        settingsList.add(new SettingsSwitchItem("Avoid Ferries", avoidFerries, value -> {
            avoidFerries = value;
            GemCall.INSTANCE.execute(() -> {
                routePreferences.setAvoidFerries(value);
                return null;
            });
        }));

        settingsList.add(new SettingsSwitchItem("Avoid Unpaved Roads", avoidUnpavedRoads, value -> {
            avoidUnpavedRoads = value;
            GemCall.INSTANCE.execute(() -> {
                routePreferences.setAvoidUnpavedRoads(value);
                return null;
            });
        }));

        settingsList.add(new SettingsSliderItem("Bike Weight", 9f, bikeWeight, 50f, "kg", value -> {
            bikeWeight = value;
            GemCall.INSTANCE.execute(() -> {
                if (electricBikeProfile != null) {
                    electricBikeProfile.setBikeMass(value);
                }
                return null;
            });
        }));

        settingsList.add(new SettingsSliderItem("Biker Weight", 10f, bikerWeight, 150f, "kg", value -> {
            bikerWeight = value;
            GemCall.INSTANCE.execute(() -> {
                if (electricBikeProfile != null) {
                    electricBikeProfile.setBikerMass(value);
                }
                return null;
            });
        }));

        settingsList.add(new SettingsSliderItem("Aux Consumption Day", 0f, auxConsumptionDay, 100f, "Wh/h", value -> {
            auxConsumptionDay = value;
            GemCall.INSTANCE.execute(() -> {
                if (electricBikeProfile != null) {
                    electricBikeProfile.setAuxConsumptionDay(value);
                }
                return null;
            });
        }));

        settingsList.add(new SettingsSliderItem("Aux Consumption Night", 0f, auxConsumptionNight, 100f, "Wh/h", value -> {
            auxConsumptionNight = value;
            GemCall.INSTANCE.execute(() -> {
                if (electricBikeProfile != null) {
                    electricBikeProfile.setAuxConsumptionNight(value);
                }
                return null;
            });
        }));

        return settingsList;
    }
}

