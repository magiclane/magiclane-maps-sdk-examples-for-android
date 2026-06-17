/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routingonmapjava;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.magiclane.sdk.core.EOffboardListenerStatus;
import com.magiclane.sdk.core.GemError;
import com.magiclane.sdk.core.GemSdk;
import com.magiclane.sdk.core.Rect;
import com.magiclane.sdk.core.SdkSettings;
import com.magiclane.sdk.d3scene.Animation;
import com.magiclane.sdk.d3scene.EAnimation;
import com.magiclane.sdk.d3scene.ERouteDisplayMode;
import com.magiclane.sdk.d3scene.MapView;
import com.magiclane.sdk.d3scene.MapViewPreferences;
import com.magiclane.sdk.examples.routingonmapjava.databinding.ActivityMainJavaBinding;
import com.magiclane.sdk.examples.routingonmapjava.databinding.DialogLayoutBinding;
import com.magiclane.sdk.places.Landmark;
import com.magiclane.sdk.routesandnavigation.MapViewRoutesCollection;
import com.magiclane.sdk.routesandnavigation.Route;
import com.magiclane.sdk.routesandnavigation.RoutingService;
import com.magiclane.sdk.util.GemCall;
import com.magiclane.sdk.util.Util;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    // System insets (status/navigation bars plus display cutout) used to keep map content
    // and the Magic Lane logo clear of system UI.
    private static final int SYSTEM_INSET_TYPES =
            WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();

    // Shared animation duration used for both presenting and re-centering routes.
    private static final int ROUTE_ANIMATION_DURATION_MS = 900;

    private ActivityMainJavaBinding binding;

    private ArrayList<Route> routesList = new ArrayList<>();

    private final RoutingService routingService = new RoutingService();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainJavaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Keep status bar icons light so they are visible over the map.
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);

        registerSdkListeners();

        if (!Util.INSTANCE.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required), null);
        }
    }

    @Override
    protected void onDestroy() {
        clearSdkListeners();

        // Release the SDK before the activity is fully destroyed.
        GemSdk.INSTANCE.release();

        super.onDestroy();
        System.exit(0);
    }

    private void calculateRoute() {
        GemCall.INSTANCE.execute(() -> {
            ArrayList<Landmark> waypoints = new ArrayList<>();
            waypoints.add(new Landmark("London", 51.5073204, -0.1276475));
            waypoints.add(new Landmark("Paris", 48.8566932, 2.3514616));

            // calculateRoute returns synchronously whether the calculation could be started. On
            // failure onCompleted never fires, so report the error and hide the progress bar here.
            int errorCode = routingService.calculateRoute(waypoints, null, false, null, null, null);
            if (errorCode != GemError.NoError) {
                String errorMessage = GemError.INSTANCE.getMessage(errorCode, this);
                runOnAliveUi(() -> {
                    showDialog(getString(R.string.routing_failed_to_start, errorMessage), null);
                });
            }
            return null;
        });
    }

    // Called when routing succeeds. Stores the result and displays all routes on the map.
    private void onRoutesReady(ArrayList<Route> routes) {
        routesList = routes;
        GemCall.INSTANCE.execute(() -> {
            MapView mapView = binding.gemSurfaceView.getMapView();
            // Present routes centred within the free screen area, avoiding toolbar and system bars.
            if (mapView != null) {
                mapView.presentRoutes(
                        routes, null, true, true, true, true, true, true,
                        new Animation(EAnimation.Linear, ROUTE_ANIMATION_DURATION_MS, null, null),
                        ERouteDisplayMode.Full, getEdgeAreaInsets()
                );
            }
            return null;
        });
    }

    // Registers the map touch listener. Tapping a route makes it the main route and re-centres
    // the map on the full route list.
    private void setupTouchHandler() {
        MapView mapView = binding.gemSurfaceView.getMapView();
        if (mapView == null) return;

        mapView.setOnTouch(xy -> {
            GemCall.INSTANCE.execute(() -> {
                mapView.setCursorScreenPosition(xy);

                ArrayList<Route> routes = mapView.getCursorSelectionRoutes();
                if (routes != null && !routes.isEmpty()) {
                    MapViewPreferences mapViewPreferences = mapView.getPreferences();
                    MapViewRoutesCollection routePrefs = mapViewPreferences != null ? mapViewPreferences.getRoutes() : null;

                    if (routePrefs != null) {
                        routePrefs.setMainRoute(routes.get(0));
                    }

                    mapView.centerOnRoutes(
                            routesList, ERouteDisplayMode.Full, getRouteViewRect(),
                            new Animation(EAnimation.Linear, ROUTE_ANIMATION_DURATION_MS, null, null)
                    );
                }

                return null;
            });

            return null;
        });
    }

    private void showDialog(String text, Runnable onDismiss) {
        if (!isActivityAlive()) return;

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        DialogLayoutBinding dialogBinding = DialogLayoutBinding.inflate(getLayoutInflater());
        dialogBinding.title.setText(getString(R.string.error));
        dialogBinding.message.setText(text);
        dialogBinding.button.setOnClickListener(v -> {
            if (onDismiss != null) onDismiss.run();
            dialog.dismiss();
        });

        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setDraggable(false);
        dialog.setCancelable(false);
        dialog.setContentView(dialogBinding.getRoot());
        dialog.show();
    }

    private void registerSdkListeners() {
        binding.gemSurfaceView.setOnSdkInitFailed(error -> {
            // No GemCall.runSynced wrapper is needed here: the error code is already available and
            // this callback does not run inside an SDK-synced context.
            String errorMessage = getString(R.string.sdk_initialization_failed, GemError.INSTANCE.getMessage(error, this));
            runOnAliveUi(() -> showDialog(errorMessage, this::finish));
            return null;
        });

        // Align the Magic Lane logo with the system window insets as soon as the map is created.
        binding.gemSurfaceView.setOnDefaultMapViewCreated(mapView -> {
            updateFocusViewport();
            return null;
        });

        // Re-align the logo whenever the surface is resized (e.g. on rotation).
        binding.gemSurfaceView.setOnSurfaceChanged((width, height) -> {
            updateFocusViewport();
            return null;
        });

        // Triggered when the worldwide road map changes readiness state. Route calculation and
        // touch handling are set up only once the map data is fully available (UpToDate). The
        // listener clears itself after the first successful fire to avoid re-triggering.
        SdkSettings.INSTANCE.setOnWorldwideRoadMapSupportStatus(status -> {
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.INSTANCE.setOnWorldwideRoadMapSupportStatus(s -> null);
                calculateRoute();
                setupTouchHandler();
            }
            return null;
        });

        SdkSettings.INSTANCE.setOnApiTokenRejected(() -> {
            runOnAliveUi(() -> showDialog(getString(R.string.token_rejected_message), null));
            return null;
        });

        routingService.setOnStarted(hasProgress -> {
            runOnAliveUi(() -> binding.progressBar.setVisibility(View.VISIBLE));
            return null;
        });

        routingService.setOnCompleted((routes, errorCode, hint) -> {
            runOnAliveUi(() -> {
                binding.progressBar.setVisibility(View.GONE);

                if (errorCode == GemError.NoError) {
                    onRoutesReady(routes);
                } else if (errorCode != GemError.Cancel) {
                    // Resolve the human-readable error message on the SDK thread via runSynced.
                    String errorMessage = GemCall.INSTANCE.runSynced(() -> GemError.INSTANCE.getMessage(errorCode, this));
                    showDialog(getString(R.string.routing_completed_with_error, errorMessage), null);
                }
            });
            return null;
        });
    }

    private void clearSdkListeners() {
        SdkSettings.INSTANCE.setOnWorldwideRoadMapSupportStatus(s -> null);
        SdkSettings.INSTANCE.setOnApiTokenRejected(() -> null);
        routingService.setOnStarted(hasProgress -> null);
        routingService.setOnCompleted((routes, errorCode, hint) -> null);
        binding.gemSurfaceView.setOnSdkInitFailed(error -> null);
        binding.gemSurfaceView.setOnDefaultMapViewCreated(mapView -> null);
        binding.gemSurfaceView.setOnSurfaceChanged((width, height) -> null);
    }

    private void runOnAliveUi(Runnable block) {
        Util.INSTANCE.postOnMain(() -> {
            if (isActivityAlive()) block.run();
        });
    }

    // Positions the Magic Lane logo (and other map UI) inside the area left free by the system
    // bars and display cutout, so it is never hidden behind system UI. Called when the map view
    // is first created and whenever the surface is resized.
    private void updateFocusViewport() {
        GemCall.INSTANCE.runSynced(() -> {
            MapView mapView = binding.gemSurfaceView.getMapView();
            if (mapView == null) return null;

            Rect viewport = mapView.getViewport();
            MapViewPreferences preferences = mapView.getPreferences();
            if (viewport == null || preferences == null) return null;

            WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(binding.getRoot());
            Insets insets = windowInsets != null ? windowInsets.getInsets(SYSTEM_INSET_TYPES) : Insets.NONE;

            int left = insets.left;
            int top = insets.top;
            int right = Math.max(viewport.getWidth() - insets.right, left);
            int bottom = Math.max(viewport.getHeight() - insets.bottom, top);
            preferences.setFocusViewport(new Rect(left, top, right, bottom));
            return null;
        });
    }

    // Returns edge insets (px) for presentRoutes: the SDK uses these to keep routes inside the
    // visible area, clear of the toolbar, system bars and display cutouts.
    private Rect getEdgeAreaInsets() {
        int[] p = resolveMapPadding();
        return new Rect(p[0], p[1], p[2], p[3]);
    }

    // Returns the free-screen rectangle (absolute px coordinates) for centerOnRoutes: the SDK
    // fits the route collection inside this rect when re-centering after a touch.
    private Rect getRouteViewRect() {
        int mapWidth = binding.gemSurfaceView.getWidth() > 0 ? binding.gemSurfaceView.getWidth() : binding.gemSurfaceView.getMeasuredWidth();
        int mapHeight = binding.gemSurfaceView.getHeight() > 0 ? binding.gemSurfaceView.getHeight() : binding.gemSurfaceView.getMeasuredHeight();
        int[] p = resolveMapPadding();
        return new Rect(
                p[0],
                p[1],
                Math.max(mapWidth - p[2], p[0]),
                Math.max(mapHeight - p[3], p[1])
        );
    }

    // Resolves the four padding values (px) shared by getEdgeAreaInsets and getRouteViewRect.
    // Top uses toolbar.bottom instead of the status-bar inset because the toolbar absorbs the
    // status-bar height via paddingTopWithSystemWindowInsets; Math.max guards against pre-layout
    // (bottom == 0).
    private int[] resolveMapPadding() {
        WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(binding.getRoot());
        Insets sysInsets = windowInsets != null
                ? windowInsets.getInsets(SYSTEM_INSET_TYPES)
                : Insets.NONE;
        int padding = getResources().getDimensionPixelSize(R.dimen.big_padding);
        int toolbarBottom = Math.max(binding.toolbar.getBottom(), 0);
        return new int[]{
                sysInsets.left + padding,    // left
                toolbarBottom + padding,      // top
                sysInsets.right + padding,   // right
                sysInsets.bottom + padding   // bottom
        };
    }

    private boolean isActivityAlive() {
        return !isFinishing() && !isDestroyed();
    }
}
