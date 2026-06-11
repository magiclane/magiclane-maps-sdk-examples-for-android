/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routingonmapjava;

import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;

import com.magiclane.sdk.examples.testing.GemSdkTestRule;
import com.magiclane.sdk.core.GemError;
import com.magiclane.sdk.places.Landmark;
import com.magiclane.sdk.routesandnavigation.Route;
import com.magiclane.sdk.routesandnavigation.RoutingService;
import com.magiclane.sdk.util.GemCall;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4ClassRunner.class)
public class RoutingOnMapTest {

    @ClassRule
    public static GemSdkTestRule sdkRule = new GemSdkTestRule();

    /**
     * NOT TEST
     */
    private void notify(Object lock) {
        synchronized (lock) {
            lock.notify();
        }
    }

    private void wait(Object lock, Long timeout) throws InterruptedException {
        synchronized (lock) {
            lock.wait(timeout);
        }
    }

    @Test
    public void routingServiceShouldReturnRoutes() throws InterruptedException {
        AtomicReference<Boolean> onCompletedPassed = new AtomicReference<>(false);
        AtomicInteger error = new AtomicInteger(GemError.General);
        Object objSync = new Object();
        AtomicReference<ArrayList<Route>> routeList = new AtomicReference<>();

        RoutingService routingService = new RoutingService();
        routingService.setOnCompleted((routes, errorCode, message) -> {
            error.set(errorCode);
            onCompletedPassed.set(true);
            GemCall.INSTANCE.execute(() -> {
                routeList.set(routes);
                notify(objSync);
                return this;
            });
            return null;
        });

        GemCall.INSTANCE.execute(() -> {
                ArrayList<Landmark> waypoints = new ArrayList<Landmark>();
                waypoints.add(new Landmark("London", 51.5073204, -0.1276475));
                waypoints.add(new Landmark("Paris", 48.8566932, 2.3514616));
                routingService.calculateRoute(waypoints, null, false, (t) -> null, null, null);
                return null;
            }
        );
        wait(objSync, 12000L);
        assert (onCompletedPassed.get()) : "OnCompleted not passed : ${GemError.getMessage(error)}";
        assert (error.get() == GemError.NoError) : "Error code: " + error.get();
        assert (!routeList.get().isEmpty()) : "Routing service returned no results.";
    }
}
