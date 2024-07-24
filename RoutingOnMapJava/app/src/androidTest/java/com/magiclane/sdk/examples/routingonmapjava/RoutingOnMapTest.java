// -------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------


package com.magiclane.sdk.examples.routingonmapjava;

import android.content.Context;
import android.net.ConnectivityManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;

import com.magiclane.sdk.core.ApiCallLogger;
import com.magiclane.sdk.core.GemError;
import com.magiclane.sdk.core.GemSdk;
import com.magiclane.sdk.core.SdkSettings;
import com.magiclane.sdk.places.Landmark;
import com.magiclane.sdk.routesandnavigation.Route;
import com.magiclane.sdk.routesandnavigation.RoutingService;
import com.magiclane.sdk.util.GemCall;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4ClassRunner.class)
public class RoutingOnMapTest {

    private static final Long TIMEOUT = 600000L;
    private static final Context appContext = ApplicationProvider.getApplicationContext();
    private static Boolean initResult = false;

    @BeforeClass
    public static void checkSdkInitStartActivity() {
        try {
            assert (initResult);
        } catch (AssertionError e) {
            throw new AssertionError("GEM SDK not initialized", e);
        }
    }

    @Before
    public void checkTokenAndNetwork() {
        //verify token and internet connection
        GemCall.INSTANCE.execute(() -> {
            assert (!Objects.requireNonNull(GemSdk.INSTANCE.getTokenFromManifest(appContext)).isEmpty());
            return null;
        });
        assert (appContext.getSystemService(ConnectivityManager.class).getActiveNetwork() != null);
    }
    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------

    static class SDKInitRule implements TestRule {
        static class SDKStatement extends Statement {
            private final Statement statement;
            private final Object lock = new Object();

            public SDKStatement(Statement statement) {
                this.statement = statement;
                SdkSettings.INSTANCE.setOnMapDataReady((isReady) -> {
                    if (isReady)
                        synchronized (lock) {
                            lock.notify();
                        }
                    return null;
                });
            }

            @Override
            public void evaluate() throws Throwable {
                if (!GemSdk.INSTANCE.isInitialized()) {
                    initResult = GemSdk.INSTANCE.initSdkWithDefaults(appContext, null, null, null, null, null, null, null, null, null, false, new ApiCallLogger(), null, null, true, false);

                    // must wait for map data ready
                    synchronized (lock) {
                        lock.wait(TIMEOUT);
                    }
                } else return;

                try {
                    statement.evaluate(); // This executes tests
                } finally {
                    GemSdk.INSTANCE.release();
                }

            }

        }

        @Override
        public Statement apply(Statement base, Description description) {
            return new SDKStatement(base);
        }
    }
    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------

    @ClassRule
    public static SDKInitRule sdkInitRule = new SDKInitRule();

    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------

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
