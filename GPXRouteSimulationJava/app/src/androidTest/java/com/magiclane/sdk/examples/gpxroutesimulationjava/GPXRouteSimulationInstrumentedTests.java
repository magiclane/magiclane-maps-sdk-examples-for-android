/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.gpxroutesimulationjava;

import android.content.Context;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.LargeTest;

import com.magiclane.sdk.core.GemError;
import com.magiclane.sdk.core.Path;
import com.magiclane.sdk.core.ProgressListener;
import com.magiclane.sdk.examples.testing.GemSdkTestRule;
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode;
import com.magiclane.sdk.routesandnavigation.NavigationListener;
import com.magiclane.sdk.routesandnavigation.NavigationService;
import com.magiclane.sdk.routesandnavigation.RoutingService;
import com.magiclane.sdk.util.GemCall;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import kotlin.Unit;

@LargeTest
@RunWith(AndroidJUnit4ClassRunner.class)
public class GPXRouteSimulationInstrumentedTests {

    private static final Context appContext = ApplicationProvider.getApplicationContext();

    @ClassRule
    public static final GemSdkTestRule sdkRule = new GemSdkTestRule();

    @Test
    public void simulateRoute() throws InterruptedException {
        final String TAG = "GPXSimTest";
        final String[] gpxFiles = {
            "gpx/1.gpx",
            "gpx/2.gpx",
            "gpx/test_route.gpx",
            "gpx/3.gpx",
            "gpx/test_route_old.gpx",
            "gpx/4.gpx",
            "gpx/5.gpx",
            "gpx/test.gpx",
        };

        final NavigationService navigationService = new NavigationService();
        final CountDownLatch latch = new CountDownLatch(gpxFiles.length);
        final RoutingService[] routingServiceHolder = new RoutingService[1];

        final NavigationListener navigationListener = new NavigationListener() {
            @Override
            public void onNavigationInstructionUpdated(
                    com.magiclane.sdk.routesandnavigation.NavigationInstruction instr) {
                GemCall.INSTANCE.execute(() -> {
                    navigationService.cancelNavigation(this);
                    return Unit.INSTANCE;
                });
            }

            @Override
            public void onNavigationError(int error) {
                if (error == GemError.Cancel) {
                    Log.d(TAG, "Navigation cancelled");
                    latch.countDown();
                }
            }
        };

        routingServiceHolder[0] = new RoutingService();
        routingServiceHolder[0].setOnCompleted((routes, errorCode, s) -> {
            if (errorCode == GemError.NoError) {
                com.magiclane.sdk.routesandnavigation.Route route = routes.get(0);
                GemCall.INSTANCE.execute(() -> {
                    ProgressListener progressListener = ProgressListener.Companion.create(
                            null, null, null,
                            code -> {
                                Assert.assertFalse(
                                        GemError.getMessage(code),
                                        GemError.isError(code));
                                return Unit.INSTANCE;
                            },
                            false
                    );
                    int result = navigationService.startSimulationWithRoute(
                            route, navigationListener, progressListener, 1.0f);
                    Assert.assertFalse(GemError.getMessage(result), GemError.isError(result));
                    return Unit.INSTANCE;
                });
            } else {
                Assert.fail(GemError.getMessage(errorCode));
            }
            return Unit.INSTANCE;
        });

        // Trigger first route calculation; each latch.countDown() leads to the next.
        calculateRouteFromGPX(routingServiceHolder[0], gpxFiles[0]);

        // Override latch behaviour: after each navigation cancel, trigger next file.
        // We re-implement a sequential loop using a separate thread.
        Thread sequencer = new Thread(() -> {
            for (int i = 1; i < gpxFiles.length; i++) {
                try {
                    // Wait for previous navigation to finish (cancel received)
                    boolean ok = latch.await(60, TimeUnit.SECONDS);
                    if (!ok) {
                        Assert.fail("Timeout waiting for navigation to cancel for file: " + gpxFiles[i - 1]);
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                final String gpxFile = gpxFiles[i];
                Log.d(TAG, "Starting: " + gpxFile);
                calculateRouteFromGPX(routingServiceHolder[0], gpxFile);
            }
        });
        sequencer.start();

        // Wait for all navigations to complete (60 seconds total timeout).
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        Assert.assertTrue("Timed out before all simulations finished", completed);
    }

    private static void calculateRouteFromGPX(RoutingService routingService, String gpxAssetPath) {
        GemCall.INSTANCE.execute(() -> {
            try {
                InputStream input = appContext.getResources().getAssets().open(gpxAssetPath);
                Path track = Path.Companion.produceWithGpx(input);
                if (track == null) return Unit.INSTANCE;

                int result = routingService.calculateRoute(
                        track, ERouteTransportMode.Bicycle, false, null, null, null, null);
                Assert.assertFalse(GemError.getMessage(result), GemError.isError(result));
            } catch (IOException e) {
                Assert.fail("IOException opening " + gpxAssetPath + ": " + e.getMessage());
            }
            return Unit.INSTANCE;
        });
    }
}

