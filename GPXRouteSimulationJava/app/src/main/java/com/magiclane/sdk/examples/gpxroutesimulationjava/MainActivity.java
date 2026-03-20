/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.gpxroutesimulationjava;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.magiclane.sdk.core.EOffboardListenerStatus;
import com.magiclane.sdk.core.EUnitSystem;
import com.magiclane.sdk.core.GemError;
import com.magiclane.sdk.core.GemSdk;
import com.magiclane.sdk.core.ISound;
import com.magiclane.sdk.d3scene.FollowPositionPreferences;
import com.magiclane.sdk.d3scene.MapViewPreferences;
import com.magiclane.sdk.places.Landmark;
import com.magiclane.sdk.core.Path;
import com.magiclane.sdk.core.ProgressListener;
import com.magiclane.sdk.core.Rgba;
import com.magiclane.sdk.core.SdkSettings;
import com.magiclane.sdk.core.SoundPlayingListener;
import com.magiclane.sdk.core.SoundPlayingPreferences;
import com.magiclane.sdk.core.SoundPlayingService;
import com.magiclane.sdk.core.Time;
import com.magiclane.sdk.core.TimeDistance;
import com.magiclane.sdk.d3scene.Animation;
import com.magiclane.sdk.d3scene.EAnimation;
import com.magiclane.sdk.d3scene.ERouteDisplayMode;
import com.magiclane.sdk.d3scene.MapView;
import com.magiclane.sdk.examples.gpxroutesimulationjava.databinding.ActivityMainBinding;
import com.magiclane.sdk.examples.gpxroutesimulationjava.databinding.DialogLayoutBinding;
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
    // Helper class to track whether the turn icon changed between updates
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

    private long lastTurnImageId = Long.MAX_VALUE;

    private ENavigationStatus navigationStatus = ENavigationStatus.Running;

    // Define a navigation service from which we will start the simulation.
    private final NavigationService navigationService = new NavigationService();

    // Define a listener that will let us know the progress of the routing process.
    private final ProgressListener routingProgressListener = new ProgressListener();

    // -----------------------------------------------------------------------
    // Navigation listener — override only the callbacks we need
    // -----------------------------------------------------------------------

    /**
     * Define a navigation listener that will receive notifications from the
     * navigation service.
     * We override the relevant methods; for all available methods see the SDK docs.
     */
    private final NavigationListener navigationListener = new NavigationListener() {

        @Override
        public void onNavigationStarted() {
            GemCall.INSTANCE.execute(() -> {
                MapView mapView = binding.gemSurfaceView.getMapView();
                if (mapView != null) {
                    Route route = navigationService.getNavigationRoute(this);
                    if (route != null) {
                        // presentRoute(route) — explicit Kotlin defaults for all optional params.
                        mapView.presentRoute(route,
                                false, true, true, true, true, true,
                                new Animation(EAnimation.Linear, 1000, null, null),
                                ERouteDisplayMode.Full,
                                null, null);
                    }

                    // customize the map view's follow position preferences (e.g. zoom level and view angle)
                    /*
                    MapViewPreferences mapPrefs = mapView.getPreferences();
                    if (mapPrefs != null) {
                        FollowPositionPreferences followPrefs = mapPrefs.getFollowPositionPreferences();
                        if (followPrefs != null) {
                            followPrefs.setZoomLevel(81, 0);
                            followPrefs.setViewAngle(60.0, false);
                        }
                    }
                    */

                    enableGPSButton();
                    // followPosition() — explicit Kotlin defaults for all optional params.
                    mapView.followPosition(true,
                            new Animation(EAnimation.Linear, 900, null, null),
                            -1, Double.MAX_VALUE,
                            null,
                            null,
                            false);
                }
                return Unit.INSTANCE;
            });

            runOnUiThread(() -> {
                binding.topPanel.setVisibility(View.VISIBLE);
                binding.bottomPanel.setVisibility(View.VISIBLE);
            });
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
                // Fetch data for the navigation top panel (instruction related info).
                instrText[0] = instr.getNextStreetName() != null ? instr.getNextStreetName() : "";

                if (instrText[0].isEmpty()) {
                    instrText[0] = instr.getNextTurnInstruction() != null
                            ? instr.getNextTurnInstruction() : "";
                }

                instrIcon[0] = getNextTurnImage(instr, turnImageSize, turnImageSize, sameTurnImage);
                instrDistance[0] = getDistance(instr);

                // Fetch data for the navigation bottom panel (route related info).
                Route route = navigationService.getNavigationRoute(this);
                if (route != null) {
                    etaText[0] = getEta(route);   // estimated time of arrival
                    rttText[0] = getRtt(route);   // remaining travel time
                    rtdText[0] = getRtd(route);   // remaining travel distance
                }
                return Unit.INSTANCE;
            });

            // Update the navigation panels info on the UI thread.
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
    // Routing service — receives route computation results
    // -----------------------------------------------------------------------

    private final RoutingService routingService = new RoutingService();

    // -----------------------------------------------------------------------
    // Activity lifecycle
    // -----------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Allow the navigation listener to play sounds.
        navigationListener.setCanPlayNavigationSound(true);

        // Configure routing service callbacks.
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
                                        GemError.INSTANCE.getMessage(error, this))));
                    }
                    return Unit.INSTANCE;
                });
            } else {
                // There was a problem computing the routing operation.
                showDialog(getString(R.string.routing_error,
                        GemError.INSTANCE.getMessage(errorCode, this)));
            }
            return Unit.INSTANCE;
        });

        turnImageSize = (int) getResources().getDimension(R.dimen.turn_image_size);
        SoundUtils.INSTANCE.addTTSPlayerInitializationListener(this);

        binding.gemSurfaceView.setOnSdkInitFailed(error -> {
            String errorMessage = getString(R.string.sdk_initialization_failed,
                    GemError.INSTANCE.getMessage(error, this));
            Util.INSTANCE.postOnMain(() -> showDialog(errorMessage, () -> {
                finish();
                System.exit(0);
            }));
            return Unit.INSTANCE;
        });

        SdkSettings.INSTANCE.setOnWorldwideRoadMapSupportStatus(status -> {
            if (status == EOffboardListenerStatus.UpToDate) {
                // Unregister by setting an empty no-op callback (matches Kotlin's = {}).
                SdkSettings.INSTANCE.setOnWorldwideRoadMapSupportStatus(s -> Unit.INSTANCE);
                calculateRouteFromGPX();
            }
            return Unit.INSTANCE;
        });

        SdkSettings.INSTANCE.setOnApiTokenRejected(() -> {
            showDialog(getString(R.string.token_rejected_message));
            return Unit.INSTANCE;
        });

        if (!Util.INSTANCE.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Deinitialize the SDK.
        GemSdk.INSTANCE.release();
        System.exit(0);
    }

    // -----------------------------------------------------------------------
    // GPS follow button
    // -----------------------------------------------------------------------

    private void enableGPSButton() {
        MapView mapView = binding.gemSurfaceView.getMapView();
        if (mapView == null) return;

        mapView.setOnExitFollowingPosition(() -> {
            binding.followGpsButton.setVisibility(View.VISIBLE);
            return Unit.INSTANCE;
        });

        mapView.setOnEnterFollowingPosition(() -> {
            binding.followGpsButton.setVisibility(View.GONE);
            return Unit.INSTANCE;
        });

        binding.followGpsButton.setOnClickListener(v ->
                GemCall.INSTANCE.execute(() -> {
                    MapView mv = binding.gemSurfaceView.getMapView();
                    if (mv != null) {
                        mv.followPosition(true,
                                new Animation(EAnimation.Linear, 900, null, null),
                                -1, Double.MAX_VALUE,
                                null,
                                null,
                                false);
                    }
                    return Unit.INSTANCE;
                }));
    }

    // -----------------------------------------------------------------------
    // Route calculation from GPX
    // -----------------------------------------------------------------------

    private void calculateRouteFromGPX() {
        GemCall.INSTANCE.execute(() -> {
            String gpxAssetsFilename = "gpx/test_route.gpx";

            try {
                // Open the GPX input stream from assets.
                java.io.InputStream input =
                        getApplicationContext().getResources().getAssets().open(gpxAssetsFilename);

                // Produce a Path based on the data in the stream.
                Path track = Path.Companion.produceWithGpx(input);
                if (track == null) return Unit.INSTANCE;

                // Set the transport mode to bike and calculate the route.
                int error = routingService.calculateRoute(
                        track, ERouteTransportMode.Bicycle, false, null, null, null, null);
                if (error != GemError.NoError) {
                    Util.INSTANCE.postOnMain(() -> showDialog(
                            getString(R.string.routing_error,
                                    GemError.INSTANCE.getMessage(error, this))));
                }
            } catch (java.io.IOException e) {
                Util.INSTANCE.postOnMain(() -> showDialog(
                        getString(R.string.routing_error, e.getMessage())));
            }
            return Unit.INSTANCE;
        });
    }

    // -----------------------------------------------------------------------
    // Dialog helper
    // -----------------------------------------------------------------------

    private void showDialog(String text) {
        showDialog(text, null);
    }

    private void showDialog(String text, Runnable onDismiss) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        DialogLayoutBinding dialogBinding =
                DialogLayoutBinding.inflate(getLayoutInflater());
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

    // -----------------------------------------------------------------------
    // Navigation helper methods
    // -----------------------------------------------------------------------

    private Bitmap getNextTurnImage(NavigationInstruction navInstr, int width, int height,
                                    TSameImage sameImage) {
        if (!navInstr.hasNextTurnInfo()) return null;

        long uid = navInstr.getNextTurnDetails() != null
                && navInstr.getNextTurnDetails().getAbstractGeometryImage() != null
                ? navInstr.getNextTurnDetails().getAbstractGeometryImage().getUid() : 0L;

        if (uid == lastTurnImageId) {
            sameImage.value = true;
            return null;
        }

        com.magiclane.sdk.routesandnavigation.AbstractGeometryImage image =
                navInstr.getNextTurnDetails() != null
                        ? navInstr.getNextTurnDetails().getAbstractGeometryImage() : null;

        if (image != null) {
            lastTurnImageId = image.getUid();
        }

        Rgba aInner = new Rgba(255, 255, 255, 255);
        Rgba aOuter = new Rgba(0, 0, 0, 255);
        Rgba iInner = new Rgba(128, 128, 128, 255);
        Rgba iOuter = new Rgba(128, 128, 128, 255);

        return GemUtilImages.INSTANCE.asBitmap(image, width, height, aInner, aOuter, iInner, iOuter);
    }

    /** Returns a formatted distance string for the next turn. */
    private String getDistance(NavigationInstruction instr) {
        int totalDistance = instr.getTimeDistanceToNextTurn() != null
                ? instr.getTimeDistanceToNextTurn().getTotalDistance() : 0;
        Pair<String, String> pair = GemUtil.INSTANCE.getDistText(
                totalDistance, EUnitSystem.Metric, false, false);
        return pair.getFirst() + " " + pair.getSecond();
    }

    /** Returns the estimated time of arrival (ETA) as a formatted string. */
    @SuppressLint("DefaultLocale")
    private String getEta(Route route) {
        TimeDistance timeDistance = route.getTimeDistance(true);
        int totalTime = (timeDistance != null) ? timeDistance.getTotalTime() : 0;
        Time time = new Time();
        time.setLocalTime();
        time.setLongValue(time.getLongValue() + (long) totalTime * 1000L);
        return String.format("%d:%02d", time.getHour(), time.getMinute());
    }

    /** Returns the remaining travel time as a formatted string. */
    private String getRtt(Route route) {
        TimeDistance timeDistance = route.getTimeDistance(true);
        int totalTime = (timeDistance != null) ? timeDistance.getTotalTime() : 0;
        Pair<String, String> pair = GemUtil.INSTANCE.getTimeText(totalTime, false, false);
        return pair.getFirst() + " " + pair.getSecond();
    }

    /** Returns the remaining travel distance as a formatted string. */
    private String getRtd(Route route) {
        TimeDistance timeDistance = route.getTimeDistance(true);
        int totalDistance = (timeDistance != null) ? timeDistance.getTotalDistance() : 0;
        Pair<String, String> pair = GemUtil.INSTANCE.getDistText(totalDistance, EUnitSystem.Metric, false, false);
        return pair.getFirst() + " " + pair.getSecond();
    }

    private void onNavigationEnded(int errorCode) {
        runOnUiThread(() -> {
            if (errorCode != GemError.NoError && errorCode != GemError.Cancel) {
                showDialog(GemError.INSTANCE.getMessage(errorCode, this));
            }
            binding.topPanel.setVisibility(View.GONE);
            binding.bottomPanel.setVisibility(View.GONE);
        });

        GemCall.INSTANCE.execute(() -> {
            if (binding.gemSurfaceView.getMapView() != null) {
                binding.gemSurfaceView.getMapView().hideRoutes();
            }
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
            GemCall.INSTANCE.execute(() -> {
                Route route = navigationService.getNavigationRoute(navigationListener);
                if (route != null && route.getStatus() == ERouteStatus.WaitingInternetConnection) {
                    return getString(R.string.waiting_for_internet_connection);
                }
                return "";
            });
        }
        return "";
    }
}

