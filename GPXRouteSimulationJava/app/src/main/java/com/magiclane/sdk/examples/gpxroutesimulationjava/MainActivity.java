/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.gpxroutesimulationjava;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.magiclane.sdk.core.EOffboardListenerStatus;
import com.magiclane.sdk.core.EUnitSystem;
import com.magiclane.sdk.core.GemError;
import com.magiclane.sdk.core.GemSdk;
import com.magiclane.sdk.core.ISound;
import com.magiclane.sdk.core.Path;
import com.magiclane.sdk.core.ProgressListener;
import com.magiclane.sdk.core.Rect;
import com.magiclane.sdk.core.Rgba;
import com.magiclane.sdk.core.SdkSettings;
import com.magiclane.sdk.core.SoundPlayingListener;
import com.magiclane.sdk.core.SoundPlayingPreferences;
import com.magiclane.sdk.core.SoundPlayingService;
import com.magiclane.sdk.core.Time;
import com.magiclane.sdk.core.TimeDistance;
import com.magiclane.sdk.core.XyF;
import com.magiclane.sdk.d3scene.Animation;
import com.magiclane.sdk.d3scene.EAnimation;
import com.magiclane.sdk.d3scene.ERouteDisplayMode;
import com.magiclane.sdk.d3scene.MapView;
import com.magiclane.sdk.examples.gpxroutesimulationjava.databinding.ActivityMainBinding;
import com.magiclane.sdk.examples.gpxroutesimulationjava.databinding.DialogLayoutBinding;
import com.magiclane.sdk.places.Landmark;
import com.magiclane.sdk.routesandnavigation.ENavigationStatus;
import com.magiclane.sdk.routesandnavigation.ERouteStatus;
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode;
import com.magiclane.sdk.routesandnavigation.NavigationInstruction;
import com.magiclane.sdk.routesandnavigation.NavigationListener;
import com.magiclane.sdk.routesandnavigation.NavigationService;
import com.magiclane.sdk.routesandnavigation.Route;
import com.magiclane.sdk.routesandnavigation.RoutingService;
import com.magiclane.sdk.util.GemCall;
import com.magiclane.sdk.util.GemUtil;
import com.magiclane.sdk.util.GemUtilImages;
import com.magiclane.sdk.util.Util;
import com.magiclane.sound.SoundUtils;

import kotlin.Pair;
import kotlin.Unit;

public class MainActivity extends AppCompatActivity
        implements SoundUtils.ITTSPlayerInitializationListener {

    // -----------------------------------------------------------------------
    // Helper: tracks whether the turn icon changed between instruction updates
    // -----------------------------------------------------------------------
    private static class TSameImage {
        boolean value = false;
    }

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private ActivityMainBinding binding;

    private final SoundPlayingListener playingListener = new SoundPlayingListener() {};
    private final SoundPlayingPreferences soundPreference = new SoundPlayingPreferences();

    private int turnImageSize = 0;

    // Long.MAX_VALUE ensures the first real image UID never matches, so it is always rendered.
    private long lastTurnImageId = Long.MAX_VALUE;

    private ENavigationStatus navigationStatus = ENavigationStatus.Running;

    // Captured once at portrait orientation; reused as the baseline for all subsequent
    // orientation constraint adjustments so the portrait layout is never recomputed from scratch.
    private ConstraintSet portraitConstraintSet;

    // -----------------------------------------------------------------------
    // SDK services
    // -----------------------------------------------------------------------

    private final NavigationService navigationService = new NavigationService();
    private final RoutingService routingService = new RoutingService();
    private final ProgressListener routingProgressListener = new ProgressListener();

    // -----------------------------------------------------------------------
    // Navigation listener
    // -----------------------------------------------------------------------

    /**
     * Receives navigation events from the SDK. We override only the callbacks
     * we need; see the SDK docs for the full list.
     */
    private final NavigationListener navigationListener = new NavigationListener() {

        @Override
        public void onNavigationStarted() {
            GemCall.INSTANCE.execute(() -> {
                MapView mapView = binding.gemSurfaceView.getMapView();
                if (mapView != null) {
                    Route route = navigationService.getNavigationRoute(this);
                    if (route != null) {
                        mapView.presentRoute(route,
                                false, true, true, true, true, true,
                                new Animation(EAnimation.Linear, 1000, null, null),
                                ERouteDisplayMode.Full,
                                null, null);
                    }
                    enableGPSButton();
                    mapView.followPosition(true,
                            new Animation(EAnimation.Linear, 900, null, null),
                            -1, Double.MAX_VALUE, null, null, false);
                }
                return Unit.INSTANCE;
            });

            applyCameraFocus();
            setNavigationPanelsVisible(true);
        }

        @Override
        public void onNavigationInstructionUpdated(@NonNull NavigationInstruction instr) {
            final String[] instrText = {""};
            final Bitmap[] instrIcon = {null};
            final String[] instrDistance = {""};
            final String[] etaText = {""};
            final String[] rttText = {""};
            final String[] rtdText = {""};
            final TSameImage sameTurnImage = new TSameImage();

            GemCall.INSTANCE.execute(() -> {
                instrText[0] = instr.getNextStreetName() != null ? instr.getNextStreetName() : "";
                if (instrText[0].isEmpty()) {
                    instrText[0] = instr.getNextTurnInstruction() != null
                            ? instr.getNextTurnInstruction() : "";
                }

                instrIcon[0] = getNextTurnImage(instr, turnImageSize, turnImageSize, sameTurnImage);
                instrDistance[0] = getDistance(instr);

                Route route = navigationService.getNavigationRoute(this);
                if (route != null) {
                    etaText[0] = getEta(route);
                    rttText[0] = getRtt(route);
                    rtdText[0] = getRtd(route);
                }
                return Unit.INSTANCE;
            });

            runOnUiThread(() -> {
                if (!sameTurnImage.value) {
                    binding.navIcon.setImageBitmap(instrIcon[0]);
                }
                binding.instructionDistance.setText(instrDistance[0]);
                binding.navInstruction.setText(instrText[0]);
                binding.eta.setText(etaText[0]);
                binding.rtt.setText(rttText[0]);
                binding.rtd.setText(rtdText[0]);
            });
        }

        @Override
        public void onDestinationReached(@NonNull Landmark landmark) {
            onNavigationEnded(GemError.NoError);
        }

        @Override
        public void onNotifyStatusChange(@NonNull ENavigationStatus status) {
            navigationStatus = status;
            runOnUiThread(() -> refreshStatusMessage());
        }

        @Override
        public void onNavigationError(int error) {
            onNavigationEnded(error);
        }

        @Override
        public void onNavigationSound(@NonNull ISound sound) {
            GemCall.INSTANCE.execute(() -> {
                SoundPlayingService.INSTANCE.play(sound, playingListener, soundPreference);
                return Unit.INSTANCE;
            });
        }
    };

    // -----------------------------------------------------------------------
    // Activity lifecycle
    // -----------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);

        navigationListener.setCanPlayNavigationSound(true);

        turnImageSize = (int) getResources().getDimension(R.dimen.turn_image_size);
        SoundUtils.INSTANCE.addTTSPlayerInitializationListener(this);

        // Snapshot portrait constraints before any orientation changes occur.
        portraitConstraintSet = new ConstraintSet();
        portraitConstraintSet.clone((ConstraintLayout) binding.getRoot());
        applyOrientationLayout();

        // Mirror RouteSimulation: when the app launches already in landscape, the binding
        // adapters have not yet had a chance to push the correct marginStart/marginEnd to the
        // panels before the first ConstraintSet apply. Set them explicitly so the panels start
        // with the right relative margins regardless of which orientation the app opened in.
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            int pm = getResources().getDimensionPixelSize(R.dimen.nav_panel_margin);
            for (View panel : new View[]{binding.topPanel, binding.bottomPanel}) {
                if (panel.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
                    ConstraintLayout.LayoutParams lp =
                            (ConstraintLayout.LayoutParams) panel.getLayoutParams();
                    lp.setMarginStart(pm);
                    lp.setMarginEnd(pm);
                    panel.setLayoutParams(lp);
                }
            }
        }

        registerSdkListeners();
        setupRoutingService();

        if (!Util.INSTANCE.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required));
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyOrientationLayout();
        applyCameraFocus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clearSdkListeners();
        GemSdk.INSTANCE.release();
        // exitProcess is required because the SDK holds native threads that do not stop on their
        // own when the Activity is destroyed, which would leave the process alive indefinitely.
        System.exit(0);
    }

    // -----------------------------------------------------------------------
    // SDK listeners
    // -----------------------------------------------------------------------

    private void registerSdkListeners() {
        binding.gemSurfaceView.setOnSdkInitFailed(error -> {
            // No SdkCall.runSynced wrapper needed here — the error code is already available.
            String errorMessage = getString(R.string.sdk_initialization_failed,
                    GemError.INSTANCE.getMessage(error, this));
            Util.INSTANCE.postOnMain(() -> showDialog(errorMessage, () -> {
                finish();
                System.exit(0);
            }));
            return Unit.INSTANCE;
        });

        binding.gemSurfaceView.setOnDefaultMapViewCreated(mapView -> {
            // Update the Magic Lane logo position once the map view is ready.
            updateFocusViewport();
            return Unit.INSTANCE;
        });

        binding.gemSurfaceView.setOnSurfaceChanged((w, h) -> {
            // Re-centre the logo after every surface resize (e.g. keyboard, rotation).
            Util.INSTANCE.postOnMain(this::updateFocusViewport);
            return Unit.INSTANCE;
        });

        // Delay route calculation until the road map is fully up to date.
        // The callback is cleared immediately after firing to prevent repeat invocations.
        SdkSettings.INSTANCE.setOnWorldwideRoadMapSupportStatus(status -> {
            if (status == EOffboardListenerStatus.UpToDate) {
                SdkSettings.INSTANCE.setOnWorldwideRoadMapSupportStatus(s -> Unit.INSTANCE);
                calculateRouteFromGPX();
            }
            return Unit.INSTANCE;
        });

        SdkSettings.INSTANCE.setOnApiTokenRejected(() -> {
            Util.INSTANCE.postOnMain(() ->
                    showDialog(getString(R.string.token_rejected_message)));
            return Unit.INSTANCE;
        });
    }

    private void clearSdkListeners() {
        SdkSettings.INSTANCE.setOnWorldwideRoadMapSupportStatus(s -> Unit.INSTANCE);
        SdkSettings.INSTANCE.setOnApiTokenRejected(() -> Unit.INSTANCE);
        binding.gemSurfaceView.setOnSdkInitFailed(error -> Unit.INSTANCE);
        binding.gemSurfaceView.setOnDefaultMapViewCreated(mapView -> Unit.INSTANCE);
        binding.gemSurfaceView.setOnSurfaceChanged((w, h) -> Unit.INSTANCE);
    }

    // -----------------------------------------------------------------------
    // Routing service setup
    // -----------------------------------------------------------------------

    private void setupRoutingService() {
        routingService.setOnStarted(started -> {
            binding.progressBar.setVisibility(View.VISIBLE);
            return Unit.INSTANCE;
        });

        routingService.setOnCompleted((routes, errorCode, s) -> {
            binding.progressBar.setVisibility(View.GONE);

            if (errorCode == GemError.NoError) {
                Route route = routes.get(0);
                GemCall.INSTANCE.execute(() -> {
                    int error = navigationService.startSimulationWithRoute(
                            route, navigationListener, routingProgressListener, 1.0f);
                    if (error != GemError.NoError) {
                        Util.INSTANCE.postOnMain(() -> showDialog(
                                getString(R.string.route_simulation_error,
                                        GemCall.INSTANCE.runSynced(() ->
                                                GemError.INSTANCE.getMessage(error, this)))));
                    }
                    return Unit.INSTANCE;
                });
            } else {
                showDialog(getString(R.string.routing_error,
                        GemCall.INSTANCE.runSynced(() ->
                                GemError.INSTANCE.getMessage(errorCode, this))));
            }
            return Unit.INSTANCE;
        });
    }

    // -----------------------------------------------------------------------
    // Route calculation from GPX asset
    // -----------------------------------------------------------------------

    private void calculateRouteFromGPX() {
        GemCall.INSTANCE.execute(() -> {
            String gpxAssetsFilename = "gpx/test_route.gpx";
            try {
                java.io.InputStream input =
                        getApplicationContext().getResources().getAssets().open(gpxAssetsFilename);

                Path track = Path.Companion.produceWithGpx(input);
                if (track == null) return Unit.INSTANCE;

                int error = routingService.calculateRoute(
                        track, ERouteTransportMode.Bicycle, false, null, null, null, null);
                if (error != GemError.NoError) {
                    Util.INSTANCE.postOnMain(() -> showDialog(
                            getString(R.string.routing_error,
                                    GemCall.INSTANCE.runSynced(() ->
                                            GemError.INSTANCE.getMessage(error, this)))));
                }
            } catch (java.io.IOException e) {
                Util.INSTANCE.postOnMain(() ->
                        showDialog(getString(R.string.routing_error, e.getMessage())));
            }
            return Unit.INSTANCE;
        });
    }

    // -----------------------------------------------------------------------
    // GPS follow button
    // -----------------------------------------------------------------------

    private void enableGPSButton() {
        MapView mapView = binding.gemSurfaceView.getMapView();
        if (mapView == null) return;

        mapView.setOnExitFollowingPosition(() -> {
            // Show the re-centre button and hide navigation panels while the map is free.
            binding.followGpsButton.setVisibility(View.VISIBLE);
            setNavigationPanelsVisible(false);
            return Unit.INSTANCE;
        });

        mapView.setOnEnterFollowingPosition(() -> {
            binding.followGpsButton.setVisibility(View.GONE);
            // Restore navigation panels only when a simulation is still running.
            Boolean simActive = GemCall.INSTANCE.execute(
                    () -> navigationService.isSimulationActive(navigationListener));
            if (Boolean.TRUE.equals(simActive)) {
                setNavigationPanelsVisible(true);
            }
            return Unit.INSTANCE;
        });

        binding.followGpsButton.setOnClickListener(v ->
                GemCall.INSTANCE.execute(() -> {
                    MapView mv = binding.gemSurfaceView.getMapView();
                    if (mv != null) {
                        mv.followPosition(true,
                                new Animation(EAnimation.Linear, 900, null, null),
                                -1, Double.MAX_VALUE, null, null, false);
                    }
                    return Unit.INSTANCE;
                }));
    }

    private void disableGPSButton() {
        MapView mapView = binding.gemSurfaceView.getMapView();
        if (mapView == null) return;
        mapView.setOnExitFollowingPosition(null);
        mapView.setOnEnterFollowingPosition(null);
        binding.followGpsButton.setOnClickListener(null);
        binding.followGpsButton.setVisibility(View.GONE);
    }

    // -----------------------------------------------------------------------
    // Navigation helpers
    // -----------------------------------------------------------------------

    private void onNavigationEnded(int errorCode) {
        runOnUiThread(() -> {
            if (errorCode != GemError.NoError && errorCode != GemError.Cancel) {
                showDialog(GemCall.INSTANCE.runSynced(() ->
                        GemError.INSTANCE.getMessage(errorCode, this)));
            }
            setNavigationPanelsVisible(false);
            disableGPSButton();
        });

        GemCall.INSTANCE.execute(() -> {
            MapView mv = binding.gemSurfaceView.getMapView();
            if (mv != null) mv.hideRoutes();
            return Unit.INSTANCE;
        });
    }

    private void refreshStatusMessage() {
        String statusMessage = getStatusMessage();
        if (statusMessage.isEmpty()) {
            binding.turnContainer.setVisibility(View.VISIBLE);
        } else {
            binding.turnContainer.setVisibility(View.GONE);
            binding.navInstruction.setText(statusMessage);
        }
    }

    private String getStatusMessage() {
        if (navigationStatus == ENavigationStatus.WaitingRoute) {
            Route route = navigationService.getNavigationRoute(navigationListener);
            if (route != null) {
                ERouteStatus routeStatus = route.getStatus();
                if (routeStatus == ERouteStatus.WaitingInternetConnection) {
                    return getString(R.string.waiting_for_internet_connection);
                } else if (routeStatus == ERouteStatus.Calculating) {
                    return getString(R.string.calculating);
                } else if (routeStatus == ERouteStatus.Ready) {
                    return getString(R.string.gps_accuracy_not_good_enough);
                }
            }
            return getString(R.string.calculating);
        }
        if (navigationStatus == ENavigationStatus.WaitingGPS) {
            // In simulation mode the GPS signal is synthetic; show "Calculating" instead.
            Boolean simActive = GemCall.INSTANCE.execute(
                    () -> navigationService.isSimulationActive(navigationListener));
            if (Boolean.TRUE.equals(simActive)) {
                return getString(R.string.calculating);
            }
            return getString(R.string.getting_position);
        }
        return "";
    }

    private void setNavigationPanelsVisible(boolean isVisible) {
        runOnUiThread(() -> {
            binding.topPanel.setVisibility(isVisible ? View.VISIBLE : View.GONE);
            binding.bottomPanel.setVisibility(isVisible ? View.VISIBLE : View.GONE);
            if (!isVisible) {
                updateFocusViewport();
            } else {
                // Post so the panels have laid out before we read their dimensions.
                binding.getRoot().post(this::updateFocusViewport);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Orientation & layout
    // -----------------------------------------------------------------------

    /**
     * Adjusts panel widths and constraints for the current orientation.
     * In landscape the panels occupy the left 40 % of the screen; in portrait
     * they span the full width with equal side margins.
     */
    private void applyOrientationLayout() {
        boolean isLandscape =
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        ConstraintLayout rootLayout = (ConstraintLayout) binding.getRoot();

        // ConstraintSet.applyTo() restores visibility from the clone (all panels were GONE at
        // clone time), so snapshot and restore live visibility state around the apply call.
        int topVis = binding.topPanel.getVisibility();
        int bottomVis = binding.bottomPanel.getVisibility();
        int fabVis = binding.followGpsButton.getVisibility();
        int progressVis = binding.progressBar.getVisibility();

        int panelMargin = getResources().getDimensionPixelSize(R.dimen.nav_panel_margin);
        ConstraintSet cs = new ConstraintSet();
        cs.clone(portraitConstraintSet);

        if (isLandscape) {
            // Fix each panel to 40 % of the screen width and pin it to the left edge.
            int panelWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.4f);
            for (int id : new int[]{R.id.top_panel, R.id.bottom_panel}) {
                cs.constrainWidth(id, panelWidth);
                cs.connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID,
                        ConstraintSet.START, 0);
                cs.clear(id, ConstraintSet.END);
            }
        } else {
            // Portrait: stretch panels across the full width with equal side margins.
            for (int id : new int[]{R.id.top_panel, R.id.bottom_panel}) {
                cs.connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID,
                        ConstraintSet.START, panelMargin);
                cs.connect(id, ConstraintSet.END, ConstraintSet.PARENT_ID,
                        ConstraintSet.END, panelMargin);
            }
        }

        cs.applyTo(rootLayout);

        // Restore visibility after applyTo() resets it.
        binding.topPanel.setVisibility(topVis);
        binding.bottomPanel.setVisibility(bottomVis);
        binding.followGpsButton.setVisibility(fabVis);
        binding.progressBar.setVisibility(progressVis);
    }

    // -----------------------------------------------------------------------
    // Camera & Magic Lane logo focus
    // -----------------------------------------------------------------------

    /**
     * Shifts the GPS arrow position on the map to stay in the visible (unobstructed) area.
     * In landscape the navigation panel covers the left side, so the focus is shifted right.
     */
    private void applyCameraFocus() {
        boolean isLandscape =
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        GemCall.INSTANCE.execute(() -> {
            MapView mapView = binding.gemSurfaceView.getMapView();
            if (mapView != null
                    && mapView.getPreferences() != null
                    && mapView.getPreferences().getFollowPositionPreferences() != null) {
                XyF focus = isLandscape ? new XyF(0.7f, 0.75f) : new XyF(0.5f, 0.75f);
                mapView.getPreferences().getFollowPositionPreferences().setCameraFocus(focus);
            }
            return Unit.INSTANCE;
        });
    }

    /**
     * Positions the Magic Lane logo inside the visible (unobstructed) map area by telling the
     * SDK which rectangle of the surface is free of overlaying UI panels.
     */
    private void updateFocusViewport() {
        GemCall.INSTANCE.runSynced(() -> {
            MapView mapView = binding.gemSurfaceView.getMapView();
            if (mapView != null && mapView.getPreferences() != null) {
                mapView.getPreferences().setFocusViewport(getFocusViewport());
            }
            return null;
        });
    }

    private Rect getFocusViewport() {
        View root = binding.getRoot();
        WindowInsetsCompat windowInsetsCompat = ViewCompat.getRootWindowInsets(root);
        androidx.core.graphics.Insets insets = windowInsetsCompat != null
                ? windowInsetsCompat.getInsets(
                        WindowInsetsCompat.Type.systemBars()
                                | WindowInsetsCompat.Type.displayCutout())
                : null;

        int rootWidth = root.getWidth();
        int rootHeight = root.getHeight();
        int width = rootWidth > 0 ? rootWidth : getResources().getDisplayMetrics().widthPixels;
        int height = rootHeight > 0 ? rootHeight : getResources().getDisplayMetrics().heightPixels;

        boolean isLandscape =
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int insetsLeft = insets != null ? insets.left : 0;
        int insetsTop = insets != null ? insets.top : 0;
        int insetsRight = insets != null ? insets.right : 0;
        int insetsBottom = insets != null ? insets.bottom : 0;

        if (isLandscape) {
            int w = Math.max(width, height);
            int h = Math.min(width, height);
            // The nav panel is on the left; start the logo-safe area at the panel's right edge.
            int left = binding.topPanel.getVisibility() == View.VISIBLE
                    ? binding.topPanel.getRight() : insetsLeft;
            int right = Math.max(w - insetsRight, left);
            int bottom = Math.max(h - insetsBottom, insetsTop);
            return new Rect(left, insetsTop, right, bottom);
        } else {
            int w = Math.min(width, height);
            int h = Math.max(width, height);
            int right = Math.max(w - insetsRight, insetsLeft);
            // The toolbar and top panel sit above the map; exclude them from the safe area.
            int top = binding.topPanel.getVisibility() == View.VISIBLE
                    ? binding.topPanel.getBottom()
                    : insetsTop;
            int bottom = binding.bottomPanel.getVisibility() == View.VISIBLE
                    ? Math.max(binding.bottomPanel.getTop(), top)
                    : Math.max(h - insetsBottom, top);
            return new Rect(insetsLeft, top, right, bottom);
        }
    }

    // -----------------------------------------------------------------------
    // Navigation data helpers (called on SDK thread)
    // -----------------------------------------------------------------------

    private Bitmap getNextTurnImage(NavigationInstruction navInstr, int width, int height,
                                    TSameImage sameImage) {
        if (!navInstr.hasNextTurnInfo()) return null;

        long uid = (navInstr.getNextTurnDetails() != null
                && navInstr.getNextTurnDetails().getAbstractGeometryImage() != null)
                ? navInstr.getNextTurnDetails().getAbstractGeometryImage().getUid() : 0L;

        if (uid == lastTurnImageId) {
            sameImage.value = true;
            return null;
        }

        com.magiclane.sdk.routesandnavigation.AbstractGeometryImage image =
                navInstr.getNextTurnDetails() != null
                        ? navInstr.getNextTurnDetails().getAbstractGeometryImage() : null;

        if (image != null) lastTurnImageId = image.getUid();

        // Active turn icon: white fill with black outline; inactive: grey fill and outline.
        Rgba aInner = new Rgba(255, 255, 255, 255);
        Rgba aOuter = new Rgba(0, 0, 0, 255);
        Rgba iInner = new Rgba(128, 128, 128, 255);
        Rgba iOuter = new Rgba(128, 128, 128, 255);

        return GemUtilImages.INSTANCE.asBitmap(image, width, height, aInner, aOuter, iInner, iOuter);
    }

    /** Returns a formatted distance string for the next turn (e.g. "500 m"). */
    private String getDistance(NavigationInstruction instr) {
        int totalDistance = instr.getTimeDistanceToNextTurn() != null
                ? instr.getTimeDistanceToNextTurn().getTotalDistance() : 0;
        Pair<String, String> pair = GemUtil.INSTANCE.getDistText(
                totalDistance, EUnitSystem.Metric, false, false);
        return pair.getFirst() + " " + pair.getSecond();
    }

    /** Returns the estimated time of arrival formatted as "HH:MM". */
    @SuppressLint("DefaultLocale")
    private String getEta(Route route) {
        TimeDistance td = route.getTimeDistance(true);
        int totalTime = td != null ? td.getTotalTime() : 0;
        Time time = new Time();
        time.setLocalTime();
        time.setLongValue(time.getLongValue() + (long) totalTime * 1000L);
        return String.format("%d:%02d", time.getHour(), time.getMinute());
    }

    /** Returns the remaining travel time formatted as "X min" or "X h Y min". */
    private String getRtt(Route route) {
        TimeDistance td = route.getTimeDistance(true);
        int totalTime = td != null ? td.getTotalTime() : 0;
        Pair<String, String> pair = GemUtil.INSTANCE.getTimeText(totalTime, false, false);
        return pair.getFirst() + " " + pair.getSecond();
    }

    /** Returns the remaining travel distance formatted as "X km" or "X m". */
    private String getRtd(Route route) {
        TimeDistance td = route.getTimeDistance(true);
        int totalDistance = td != null ? td.getTotalDistance() : 0;
        Pair<String, String> pair = GemUtil.INSTANCE.getDistText(
                totalDistance, EUnitSystem.Metric, false, false);
        return pair.getFirst() + " " + pair.getSecond();
    }

    // -----------------------------------------------------------------------
    // Dialog helpers
    // -----------------------------------------------------------------------

    private void showDialog(String text) {
        showDialog(text, null);
    }

    private void showDialog(String text, Runnable onDismiss) {
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

    // -----------------------------------------------------------------------
    // ITTSPlayerInitializationListener
    // -----------------------------------------------------------------------

    @Override
    public void onTTSPlayerInitialized() {
        SoundPlayingService.INSTANCE.setTTSLanguage("eng-USA");
    }

    @Override
    public void onTTSPlayerInitializationFailed() {
        SoundPlayingService.INSTANCE.setDefaultHumanVoice();
    }
}
