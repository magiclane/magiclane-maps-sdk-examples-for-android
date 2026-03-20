/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemojava;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.test.espresso.idling.CountingIdlingResource;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.magiclane.sdk.core.EUnitSystem;
import com.magiclane.sdk.core.GemError;
import com.magiclane.sdk.core.GemSdk;
import com.magiclane.sdk.core.ImageDatabase;
import com.magiclane.sdk.core.Parameter;
import com.magiclane.sdk.core.ProgressListener;
import com.magiclane.sdk.core.Rect;
import com.magiclane.sdk.core.Rgba;
import com.magiclane.sdk.core.SdkSettings;
import com.magiclane.sdk.core.SoundPlayingListener;
import com.magiclane.sdk.core.SoundPlayingPreferences;
import com.magiclane.sdk.core.SoundPlayingService;
import com.magiclane.sdk.core.Time;
import com.magiclane.sdk.core.TimeDistance;
import com.magiclane.sdk.d3scene.Animation;
import com.magiclane.sdk.d3scene.EAnimation;
import com.magiclane.sdk.d3scene.EHighlightOptions;
import com.magiclane.sdk.d3scene.ERouteDisplayMode;
import com.magiclane.sdk.d3scene.HighlightRenderSettings;
import com.magiclane.sdk.d3scene.MapView;
import com.magiclane.sdk.d3scene.MapViewPreferences;
import com.magiclane.sdk.examples.bikedemojava.databinding.ActivityMainBinding;
import com.magiclane.sdk.examples.bikedemojava.databinding.DialogLayoutBinding;
import com.magiclane.sdk.places.Landmark;
import com.magiclane.sdk.places.SearchService;
import com.magiclane.sdk.routesandnavigation.EBikeProfile;
import com.magiclane.sdk.routesandnavigation.ENavigationStatus;
import com.magiclane.sdk.routesandnavigation.ERouteStatus;
import com.magiclane.sdk.routesandnavigation.MapViewRoutesCollection;
import com.magiclane.sdk.routesandnavigation.NavigationInstruction;
import com.magiclane.sdk.routesandnavigation.NavigationListener;
import com.magiclane.sdk.routesandnavigation.NavigationService;
import com.magiclane.sdk.routesandnavigation.Route;
import com.magiclane.sdk.routesandnavigation.RoutingService;
import com.magiclane.sdk.sensordatasource.ESConfigKeys;
import com.magiclane.sdk.sensordatasource.PositionListener;
import com.magiclane.sdk.sensordatasource.PositionService;
import com.magiclane.sdk.sensordatasource.enums.EDataType;
import com.magiclane.sdk.util.GemCall;
import com.magiclane.sdk.util.GemUtil;
import com.magiclane.sdk.util.GemUtilImages;
import com.magiclane.sdk.util.PermissionsHelper;
import com.magiclane.sdk.util.SdkImages;
import com.magiclane.sdk.util.Util;
import com.magiclane.sound.SoundUtils;
import java.util.ArrayList;
import java.util.List;
import kotlin.Pair;
import kotlin.Unit;

public class MainActivity extends AppCompatActivity implements SoundUtils.ITTSPlayerInitializationListener {

    public static class TSameImage {
        public boolean value = false;
    }

    private ActivityMainBinding binding;
    private int searchIconSize = 0;
    private static final long SEARCH_DEBOUNCE_MS = 400L;
    private HandlerThread searchDebounceThread;
    private Handler searchDebounceHandler;
    private Runnable pendingSearchTask;
    private volatile String searchFilter = "";
    private ArrayList<Route> routesList = new ArrayList<>();
    private int topInset = 0;
    private int leftInset = 0;
    private int rightInset = 0;
    private int inflate = 0;
    private int appBarHeight = 0;
    private int bottomDialogHeight = 0;
    private ENavigationStatus navigationStatus = ENavigationStatus.Running;
    private long lastTurnImageId = Long.MAX_VALUE;
    private int turnImageSize;
    private boolean shouldCheckLocationPermissionOnResume = false;
    private static final int REQUEST_PERMISSIONS = 110;

    private final SearchAdapter searchAdapter = new SearchAdapter();
    private MainActivityViewModel viewModel;
    private final NavigationService navigationService = new NavigationService();
    private final SoundPlayingListener playingListener = new SoundPlayingListener() {};
    private final SoundPlayingPreferences soundPreference = new SoundPlayingPreferences();
    private PositionListener positionListener;

    private final ProgressListener checkAuthorizationListener = new ProgressListener() {
        @Override
        public void notifyComplete(int errorCode, @NonNull String message) {
            if (errorCode != GemError.NoError) {
                showInvalidTokenDialog();
            }
        }
    };

    private final RoutingService routingService = new RoutingService();

    private Route getNavRoute() {
        return navigationService.getNavigationRoute(navigationListener);
    }

    private final NavigationListener navigationListener = new NavigationListener() {
        @Override
        public void onNavigationStarted() {
            MapView mapView = binding.gemSurfaceView.getMapView();
            if (mapView != null) {
                mapView.followPosition(
                    true,
                    new Animation(EAnimation.Linear, 900, null, null),
                    -1,
                     Double.MAX_VALUE,
                    null,
                    null,
                    true
                );
            }

            runOnUiThread(() -> {
                binding.mapSearchBar.setVisibility(View.GONE);
                binding.bikeSettingsButton.setVisibility(View.GONE);
                binding.topPanel.setVisibility(View.VISIBLE);
                binding.bottomPanel.setVisibility(View.VISIBLE);
            });
        }

        @Override
        public void onNavigationInstructionUpdated(NavigationInstruction instr) {
            TSameImage sameTurnImage = new TSameImage();

            String etaText = "";
            String rttText = "";
            String rtdText = "";

            String nextStreetName = instr.getNextStreetName();
            String instrText = nextStreetName != null ? nextStreetName : "";
            if (instrText.isEmpty()) {
                String nextTurnInstr = instr.getNextTurnInstruction();
                instrText = nextTurnInstr != null ? nextTurnInstr : "";
            }

            final Bitmap instrIcon = getNextTurnImage(instr, turnImageSize, turnImageSize, sameTurnImage);
            final String instructionDistance = getInstructionDistanceInMeters(instr);

            Route navRoute = getNavRoute();
            if (navRoute != null) {
                etaText = getEta(navRoute);
                rttText = getRtt(navRoute);
                rtdText = getRtd(navRoute);
            }

            final String finalInstrText = instrText;
            final String finalEtaText = etaText;
            final String finalRttText = rttText;
            final String finalRtdText = rtdText;

            runOnUiThread(() -> {
                if (!sameTurnImage.value) {
                    binding.navIcon.setImageBitmap(instrIcon);
                }

                binding.navInstruction.setText(finalInstrText);
                binding.instrDistance.setText(instructionDistance);
                binding.eta.setText(finalEtaText);
                binding.rtt.setText(finalRttText);
                binding.rtd.setText(finalRtdText);
            });
        }

        @Override
        public void onDestinationReached(@NonNull Landmark landmark) {
            onNavigationEnded(GemError.NoError);
        }

        @Override
        public void onNotifyStatusChange(@NonNull ENavigationStatus status) {
            navigationStatus = status;
            refreshStatusMessage();
        }

        @Override
        public void onNavigationError(int error) {
            onNavigationEnded(error);
        }

        @Override
        public void onNavigationSound(@NonNull com.magiclane.sdk.core.ISound sound) {
            SoundPlayingService.INSTANCE.play(sound, playingListener, soundPreference);
        }
    };

    private final ProgressListener navigationProgressListener = new ProgressListener() {
        @Override
        public void notifyStatusChanged(int status) {
            refreshStatusMessage();
        }
    };

    private final SearchService searchService = new SearchService();

    private void setupRoutingServiceCallbacks() {
        routingService.setOnStarted(started -> {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.cancelButton.setVisibility(View.VISIBLE);
            return Unit.INSTANCE;
        });

        routingService.setOnCompleted((routes, errorCode, message) -> {
            binding.progressBar.setVisibility(View.GONE);
            binding.cancelButton.setVisibility(View.GONE);

            if (errorCode != null && errorCode == GemError.NoError) {
                routesList = routes;

                if (!routesList.isEmpty()) {
                    String title = "";
                    if (viewModel.destination != null) {
                        title = getString(R.string.route_to, GemUtil.INSTANCE.formatName(viewModel.destination));
                    }

                    String routeMessage = formatRouteName(routesList.get(0));
                    binding.mapSearchBar.setVisibility(View.GONE);

                    showStartNavigationDialog(
                        title,
                        routeMessage,
                        () -> GemCall.INSTANCE.execute(() -> {
                            MapView mapView = binding.gemSurfaceView.getMapView();
                            if (mapView != null &&
                                mapView.getPreferences() != null &&
                                mapView.getPreferences().getRoutes() != null) {

                                Route mainRoute = mapView.getPreferences().getRoutes().getMainRoute();
                                if (mainRoute != null) {
                                    int error = navigationService.startNavigationWithRoute(
                                        mainRoute,
                                        navigationListener,
                                        navigationProgressListener,
                                        null
                                    );

                                    if (error != GemError.NoError) {
                                        runOnUiThread(() -> showDialog(
                                            getString(R.string.route_navigation_error, GemError.INSTANCE.getMessage(error, MainActivity.this))
                                        ));
                                    }

                                    removeAllRoutesFromMapExceptMainRoute(mapView.getPreferences().getRoutes(), mainRoute);
                                }
                            }

                            return null;
                        }),
                        () -> GemCall.INSTANCE.execute(() -> {
                            MapView mapView = binding.gemSurfaceView.getMapView();
                            if (mapView != null &&
                                mapView.getPreferences() != null &&
                                mapView.getPreferences().getRoutes() != null) {

                                Route mainRoute = mapView.getPreferences().getRoutes().getMainRoute();
                                if (mainRoute != null) {
                                    int error = navigationService.startSimulationWithRoute(
                                            mainRoute,
                                            navigationListener,
                                            navigationProgressListener,
                                            1.0f
                                    );

                                    if (error != GemError.NoError) {
                                        runOnUiThread(() -> showDialog(
                                                getString(R.string.route_simulation_error, GemError.INSTANCE.getMessage(error, MainActivity.this))
                                        ));
                                    }

                                    removeAllRoutesFromMapExceptMainRoute(mapView.getPreferences().getRoutes(), mainRoute);
                                }
                            }

                            return null;
                        }),
                        () -> GemCall.INSTANCE.execute(() -> {
                            MapView mapView = binding.gemSurfaceView.getMapView();
                            if (mapView != null) {
                                mapView.presentRoutes(
                                        routesList,
                                        null,
                                        true,
                                        true,
                                        false,
                                        false,
                                        false,
                                        true,
                                        new Animation(EAnimation.Linear, 900, null, null),
                                        ERouteDisplayMode.Full,
                                        new Rect(leftInset, getTopInset(), rightInset, bottomDialogHeight + inflate)
                                );
                            }
                            return null;
                        }),
                        () -> {
                            binding.mapSearchBar.setVisibility(View.VISIBLE);
                            GemCall.INSTANCE.execute(() -> {
                                MapView mapView = binding.gemSurfaceView.getMapView();
                                if (mapView != null &&
                                    mapView.getPreferences() != null &&
                                    mapView.getPreferences().getRoutes() != null) {
                                    mapView.getPreferences().getRoutes().clear();
                                }
                                routesList.clear();

                                return null;
                            });
                        }
                    );
                }
            } else {
                if ((errorCode != null) && (errorCode != GemError.Cancel)) {
                    showDialog(getString(
                            R.string.routing_error,
                            GemError.INSTANCE.getMessage(errorCode, MainActivity.this)
                    ));
                }
            }

            return Unit.INSTANCE;
        });

        routingService.setOnStatusChanged(status -> {
            if (status != null && status == ERouteStatus.WaitingInternetConnection.getValue()) {
                showDialog(getString(R.string.internet_required));
            }
            return Unit.INSTANCE;
        });
    }

    private void refreshStatusMessage() {
        String statusMessage = getStatusMessage();
        runOnUiThread(() -> {
            if (statusMessage.isEmpty()) {
                binding.turnContainer.setVisibility(View.VISIBLE);
            } else {
                binding.turnContainer.setVisibility(View.GONE);
                binding.navInstruction.setText(statusMessage);
            }
        });
    }

    private void removeAllRoutesFromMapExceptMainRoute(MapViewRoutesCollection routesCollection, Route mainRoute) {
        int n = routesCollection.getSize();
        for (int i = n - 1; i >= 0; i--) {
            Route route = routesCollection.getRoute(i);
            if (route != null && !routesCollection.isMainRoute(route)) {
                routesCollection.remove(route);
            }
        }

        routesCollection.hideLabel(mainRoute);
        routesList.clear();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        searchDebounceThread = new HandlerThread("SearchDebounceThread");
        searchDebounceThread.start();
        searchDebounceHandler = new Handler(searchDebounceThread.getLooper());

        SoundUtils.INSTANCE.addTTSPlayerInitializationListener(this);

        // Allow the navigation listener to play sounds.
        navigationListener.setCanPlayNavigationSound(true);

        turnImageSize = (int) getResources().getDimension(R.dimen.turn_image_size);

        SdkSettings.INSTANCE.setOnConnectionStatusUpdated(isConnected -> {
            if (isConnected) {
                String appAuth = SdkSettings.INSTANCE.getAppAuthorization();
                if (appAuth != null) {
                    GemCall.INSTANCE.execute(() -> {
                        SdkSettings.INSTANCE.verifyAppAuthorization(appAuth, checkAuthorizationListener);
                        return null;
                    });
                } else {
                    showInvalidTokenDialog();
                }

                SdkSettings.INSTANCE.setOnConnectionStatusUpdated(status -> Unit.INSTANCE);
            }
            return Unit.INSTANCE;
        });

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupRoutingServiceCallbacks();
        EspressoIdlingResource.increment();

        // Measure app bar height after layout
        binding.appBarLayout.post(() -> appBarHeight = binding.appBarLayout.getHeight());

        // Set up window insets listener
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            topInset = systemBars.top + inflate;
            leftInset = systemBars.left + inflate;
            rightInset = systemBars.right + inflate;
            return insets;
        });

        searchIconSize = (int) getResources().getDimension(R.dimen.icon_size);
        inflate = (int) getResources().getDimension(R.dimen.padding_40);

        binding.gemSurfaceView.setOnSdkInitFailed(error -> {
            String errorMessage = getString(R.string.sdk_init_failed, GemError.INSTANCE.getMessage(error, this));
            runOnUiThread(() -> showDialog(errorMessage, () -> {
                finish();
                System.exit(0);
            }));
            return Unit.INSTANCE;
        });

        binding.gemSurfaceView.setOnDefaultMapViewCreated(mapView -> {
            mapView.followPosition(true, new Animation(EAnimation.Linear, 900, null, null), -1, Double.MAX_VALUE, null, null, false);

            positionListener = new PositionListener() {
                @Override
                public void onNewPosition(@NonNull com.magiclane.sdk.sensordatasource.PositionData position) {
                    if (!position.isValid()) {
                        return;
                    }
                    PositionService.INSTANCE.removeListener(positionListener);
                    runOnUiThread(MainActivity.this::enableGPSButton);
                }
            };
            PositionService.INSTANCE.addListener(positionListener, EDataType.Position);

            viewModel.initPreferences();

            ArrayList<Parameter> parametersList = new ArrayList<>();
            parametersList.add(new Parameter(ESConfigKeys.Position.ImprovedPosPreferRouteSnap, "", "1"));
            parametersList.add(new Parameter(ESConfigKeys.Position.ImprovedPositionDefTransportMode, "", "bike"));
            if (PositionService.INSTANCE.getDataSource() != null) {
                PositionService.INSTANCE.getDataSource().setPreferences(EDataType.Position, parametersList);
            }

            runOnUiThread(() -> {
                mapView.setOnTouch(xy -> {
                    GemCall.INSTANCE.execute(() -> {
                        if (navigationService.isNavigationActive(navigationListener) ||
                            navigationService.isSimulationActive(navigationListener)) {
                            return null;
                        }

                        mapView.setCursorScreenPosition(xy);

                        List<Route> routes = mapView.getCursorSelectionRoutes();
                        MapViewPreferences preferences = mapView.getPreferences();
                        if (routes != null && !routes.isEmpty() && preferences != null) {
                            // set the touched route as the main route and center on it
                            MapViewRoutesCollection routesCollection =  mapView.getPreferences().getRoutes();
                            if (routesCollection != null) {
                                routesCollection.setMainRoute(routes.get(0));
                                mapView.centerOnRoutes(
                                        routesList,
                                        ERouteDisplayMode.Full,
                                        getFreeScreenRect(),
                                        new Animation(EAnimation.Linear, 900, null, null)
                                );
                            }

                            return null;
                        }

                        if (!routesList.isEmpty()) {
                            return null;
                        }

                        Landmark selectedLandmark = null;
                        List<Landmark> landmarks = mapView.getCursorSelectionLandmarks();
                        if (landmarks != null && !landmarks.isEmpty()) {
                            selectedLandmark = landmarks.get(0);
                        } else {
                            var overlayItems = mapView.getCursorSelectionOverlayItems();
                            if (overlayItems != null && !overlayItems.isEmpty()) {
                                var overlay = overlayItems.get(0);
                                var coordinates = overlay.getCoordinates();
                                if (coordinates != null) {
                                    selectedLandmark = new Landmark(
                                        overlay.getName() != null ? overlay.getName() : "Unknown",
                                        coordinates.getLatitude(),
                                        coordinates.getLongitude()
                                    );
                                }
                            }
                        }

                        if ((selectedLandmark != null) && (selectedLandmark.getCoordinates() != null)) {
                            Landmark landmark = selectedLandmark;
                            viewModel.destination = landmark;
                            showCalculateRouteDialog(
                                GemUtil.INSTANCE.formatName(landmark),
                                GemUtil.INSTANCE.getLandmarkDescription(landmark, true),
                                () -> {
                                    if (PositionService.INSTANCE.getPosition() != null) {
                                        Landmark departure = new Landmark(
                                            "My position",
                                            PositionService.INSTANCE.getPosition().getLatitude(),
                                            PositionService.INSTANCE.getPosition().getLongitude()
                                        );
                                        calculateRoute(departure, landmark);
                                    } else {
                                        showDialog(getString(R.string.current_position_not_available));
                                    }
                                },
                                () -> highlightLandmarkOnMap(landmark),
                                this::deactivateHighlights
                            );
                        }

                        return null;
                    });
                    return Unit.INSTANCE;
                });

                binding.bikeSettingsButton.setVisibility(View.VISIBLE);
            });
            return Unit.INSTANCE;
        });

        searchAdapter.setOnViewHolderClickListener(item -> {
            Boolean itemHasValidCoordinates = GemCall.INSTANCE.execute(() -> item.getLandmark().getCoordinates() != null);
            if (itemHasValidCoordinates == null || !itemHasValidCoordinates) {
                return;
            }

            binding.mapSearchView.hide();
            viewModel.destination = item.getLandmark();

            GemCall.INSTANCE.execute(() -> {
                showCalculateRouteDialog(
                    item.getText() != null ? item.getText() : "",
                    item.getSubText() != null ? item.getSubText() : "",
                    () -> {
                        if (PositionService.INSTANCE.getPosition() != null) {
                            Landmark departure = new Landmark(
                                getString(R.string.my_position),
                                PositionService.INSTANCE.getPosition().getLatitude(),
                                PositionService.INSTANCE.getPosition().getLongitude()
                            );
                            Landmark destination = item.getLandmark();
                            calculateRoute(departure, destination);
                        } else {
                            showDialog(getString(R.string.current_position_not_available));
                        }
                    },
                    () -> highlightLandmarkOnMap(viewModel.destination),
                    this::deactivateHighlights
                );
                return null;
            });
        });

        EspressoIdlingResource.decrement();

        SdkSettings.INSTANCE.setOnApiTokenRejected(() -> {
            showInvalidTokenDialog();
            return Unit.INSTANCE;
        });

        if (checkLocationStatus()) {
            requestPermissions();
        }

        if (!Util.INSTANCE.isInternetConnected(this)) {
            showDialog(getString(R.string.internet_required));
        }

        binding.mapSearchView.setupWithSearchBar(binding.mapSearchBar);

        binding.mapSearchView.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String filter = s.toString().trim();

                if (!filter.equals(searchFilter)) {
                    searchFilter = filter;

                    cancelPendingSearchTask();

                    GemCall.INSTANCE.postAsync(() -> {
                        searchService.cancelSearch();
                        return null;
                    });

                    if (searchFilter.isEmpty()) {
                        binding.searchProgressBar.setVisibility(View.INVISIBLE);
                        viewModel.searchResultListLivedata.postValue(new ArrayList<>());
                        return;
                    }

                    binding.searchProgressBar.setVisibility(View.VISIBLE);
                    String currentFilter = searchFilter;
                    pendingSearchTask = () -> {
                        if (!currentFilter.equals(searchFilter)) {
                            return;
                        }

                        GemCall.INSTANCE.postAsync(() -> {
                            searchService.searchByFilter(
                                currentFilter,
                                null,
                                (ArrayList<com.magiclane.sdk.core.EGenericCategoriesIDs>) null,
                                (results, errorCode, errorMessage) -> {
                                    if (errorCode == GemError.Cancel) return Unit.INSTANCE;
                                    if (errorCode == GemError.NoError) {
                                        GemCall.INSTANCE.execute(() -> {
                                            List<SearchResultItem> list = new ArrayList<>();
                                            for (Landmark landmark : results) {
                                                list.add(new SearchResultItem(
                                                    landmark.getImage() != null ?
                                                        landmark.getImage().asBitmap(searchIconSize, searchIconSize) : null,
                                                    GemUtil.INSTANCE.formatName(landmark),
                                                    GemUtil.INSTANCE.getLandmarkDescription(landmark, true),
                                                    landmark
                                                ));
                                            }

                                            viewModel.searchResultListLivedata.postValue(list);
                                            return null;
                                        });
                                    } else {
                                        viewModel.searchResultListLivedata.postValue(new ArrayList<>());
                                    }
                                    return Unit.INSTANCE;
                                },
                                started -> Unit.INSTANCE
                            );

                            return null;
                        });
                    };
                    if (searchDebounceHandler != null) {
                        searchDebounceHandler.postDelayed(pendingSearchTask, SEARCH_DEBOUNCE_MS);
                    }
                }
            }
        });

        viewModel = new ViewModelProvider(this).get(MainActivityViewModel.class);

        viewModel.isElectricBikeProfile.observe(this, isElectric -> invalidateOptionsMenu());

        setSupportActionBar(binding.mapSearchBar);

        binding.searchResultsList.setAdapter(searchAdapter);
        binding.searchResultsList.setLayoutManager(new LinearLayoutManager(this));

        viewModel.searchResultListLivedata.observe(this, list -> {
            searchAdapter.submitList(list);
            binding.searchProgressBar.setVisibility(View.INVISIBLE);
            binding.noResultsTextView.setVisibility(
                list.isEmpty() && !searchFilter.isEmpty() ? View.VISIBLE : View.GONE
            );
        });

        binding.bikeSettingsButton.setOnClickListener(v -> getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, new BikeSettingsFragment())
            .commit());

        binding.cancelButton.setOnClickListener(v -> {
            GemCall.INSTANCE.execute(() -> {
                routingService.cancelRoute();
                return null;
            });
            binding.progressBar.setVisibility(View.GONE);
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                if (fragment instanceof BikeSettingsFragment) {
                    getSupportFragmentManager().beginTransaction().remove(fragment).commit();
                    return;
                }

                if (binding.calculateRoutePanel.getRoot().getVisibility() == View.VISIBLE) {
                    binding.calculateRoutePanel.getRoot().setVisibility(View.GONE);
                    deactivateHighlights();
                    return;
                }

                if (binding.cancelButton.getVisibility() == View.VISIBLE) {
                    GemCall.INSTANCE.execute(() -> {
                        routingService.cancelRoute();
                        return null;
                    });
                    binding.cancelButton.setVisibility(View.GONE);
                    binding.progressBar.setVisibility(View.GONE);
                    return;
                }

                if (binding.startNavigationPanel.getRoot().getVisibility() == View.VISIBLE) {
                    binding.startNavigationPanel.getRoot().setVisibility(View.GONE);
                    binding.mapSearchBar.setVisibility(View.VISIBLE);
                    GemCall.INSTANCE.execute(() -> {
                        if (binding.gemSurfaceView.getMapView() != null &&
                            binding.gemSurfaceView.getMapView().getPreferences() != null &&
                            binding.gemSurfaceView.getMapView().getPreferences().getRoutes() != null) {
                            binding.gemSurfaceView.getMapView().getPreferences().getRoutes().clear();
                        }
                        return null;
                    });
                    return;
                }

                boolean navigationIsActive = Boolean.TRUE.equals(GemCall.INSTANCE.execute(() -> navigationService.isNavigationActive(navigationListener)));
                boolean simulationIsActive = Boolean.TRUE.equals(GemCall.INSTANCE.execute(() -> navigationService.isSimulationActive(navigationListener)));

                if (navigationIsActive || simulationIsActive) {
                    GemCall.INSTANCE.execute(() -> {
                        navigationService.cancelNavigation(navigationListener);
                        return null;
                    });
                    return;
                }

                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (shouldCheckLocationPermissionOnResume) {
            shouldCheckLocationPermissionOnResume = false;
            if (isLocationEnabled()) {
                requestPermissions();
            } else {
                showDialog(getString(R.string.location_services_required), this::finish);
            }
        }
    }

    private void highlightLandmarkOnMap(Landmark landmark) {
        GemCall.INSTANCE.execute(() -> {
            if (binding.gemSurfaceView.getMapView() != null) {
                Rect rect = getFreeScreenRect();

                binding.gemSurfaceView.getMapView().deactivateAllHighlights();

                landmark.setImage(new ImageDatabase().getImageById(SdkImages.Core.Search_Results_Pin.getValue()));

                com.magiclane.sdk.core.RectangleGeographicArea contour = landmark.getContourGeographicArea(false);
                HighlightRenderSettings highlightSettings;

                if (contour != null && !contour.isEmpty()) {
                    binding.gemSurfaceView.getMapView().centerOnRectArea(
                        contour,
                        -1,
                        rect,
                        new Animation(EAnimation.Linear, 900, null, null)
                    );

                    highlightSettings = new HighlightRenderSettings(
                        EHighlightOptions.ShowContour.getValue() | EHighlightOptions.ShowLandmark.getValue(),
                        new Rgba(255, 98, 0, 255),
                        new Rgba(255, 98, 0, 255),
                        0.75,
                        1.0
                    );
                    highlightSettings.setImageSize(6.0);
                } else {
                    highlightSettings = new HighlightRenderSettings();
                    highlightSettings.setOptions(EHighlightOptions.ShowLandmark.getValue());
                    highlightSettings.setImageSize(6.0);

                    if (landmark.getCoordinates() != null) {
                        binding.gemSurfaceView.getMapView().centerOnCoordinates(
                            landmark.getCoordinates(),
                            -1,
                            rect.getCenter(),
                            new Animation(EAnimation.Linear, 900, null, null),
                            0.0,
                            0.0
                        );
                    }
                }

                binding.gemSurfaceView.getMapView().activateHighlightLandmarks(
                    landmark,
                    highlightSettings,
                    -1
                );
            }
            return null;
        });
    }

    private void deactivateHighlights() {
        GemCall.INSTANCE.execute(() -> {
            if (binding.gemSurfaceView.getMapView() != null) {
                binding.gemSurfaceView.getMapView().deactivateAllHighlights();
            }
            return null;
        });
    }

    private Rect getFreeScreenRect() {
        return new Rect(
            leftInset,
            getAppBarHeight() + inflate,
            binding.getRoot().getWidth() - rightInset,
            binding.getRoot().getHeight() - bottomDialogHeight - inflate
        );
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem electric = menu.findItem(R.id.e_bike_type);
        electric.setVisible(viewModel.isElectric);
        MenuItem notElectric = menu.findItem(R.id.bike_type);
        notElectric.setVisible(!viewModel.isElectric);
        MenuItem icon = viewModel.isElectric ? electric : notElectric;

        int iconRes = R.drawable.bikecity;
        if (viewModel.isElectric) {
            switch (viewModel.bikeProfile) {
                case City: iconRes = R.drawable.ebikecity; break;
                case Cross: iconRes = R.drawable.ebikecross; break;
                case Mountain: iconRes = R.drawable.ebikemountain; break;
                case Road: iconRes = R.drawable.ebikeroad; break;
            }
        } else {
            switch (viewModel.bikeProfile) {
                case City: iconRes = R.drawable.bikecity; break;
                case Cross: iconRes = R.drawable.bikecross; break;
                case Mountain: iconRes = R.drawable.bikemountain; break;
                case Road: iconRes = R.drawable.bikeroad; break;
            }
        }
        icon.setIcon(ContextCompat.getDrawable(this, iconRes));

        binding.mapSearchBar.setOnMenuItemClickListener(menuItem -> {
            MenuItem bikeTypeButton = binding.mapSearchBar.getMenu().findItem(
                viewModel.isElectric ? R.id.e_bike_type : R.id.bike_type
            );

            int selectedIcon = R.drawable.bikecity;
            EBikeProfile selectedProfile = EBikeProfile.City;

            int itemId = menuItem.getItemId();
            if (itemId == R.id.bike_city || itemId == R.id.e_bike_city) {
                selectedIcon = viewModel.isElectric ? R.drawable.ebikecity : R.drawable.bikecity;
            } else if (itemId == R.id.bike_cross || itemId == R.id.e_bike_cross) {
                selectedIcon = viewModel.isElectric ? R.drawable.ebikecross : R.drawable.bikecross;
                selectedProfile = EBikeProfile.Cross;
            } else if (itemId == R.id.bike_mountain || itemId == R.id.e_bike_mountain) {
                selectedIcon = viewModel.isElectric ? R.drawable.ebikemountain : R.drawable.bikemountain;
                selectedProfile = EBikeProfile.Mountain;
            } else if (itemId == R.id.bike_road || itemId == R.id.e_bike_road) {
                selectedIcon = viewModel.isElectric ? R.drawable.ebikeroad : R.drawable.bikeroad;
                selectedProfile = EBikeProfile.Road;
            }

            bikeTypeButton.setIcon(ContextCompat.getDrawable(this, selectedIcon));
            viewModel.setBikeProfile(selectedProfile);
            return true;
        });
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.search_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        cancelPendingSearchTask();
        if (searchDebounceThread != null) {
            searchDebounceThread.quitSafely();
            searchDebounceThread = null;
        }
        searchDebounceHandler = null;

        GemCall.INSTANCE.runSynced(() -> {
            searchService.cancelSearch();
            return null;
        });

        SoundUtils.INSTANCE.removeTTSPlayerInitializationListener(this);
        GemSdk.INSTANCE.release();
        System.exit(0);
    }

    private void cancelPendingSearchTask() {
        Handler handler = searchDebounceHandler;
        if (pendingSearchTask != null && handler != null) {
            handler.removeCallbacks(pendingSearchTask);
            pendingSearchTask = null;
        }
    }

    private void enableGPSButton() {
        MapView mapView = binding.gemSurfaceView.getMapView();
        if (mapView != null) {
            mapView.setOnExitFollowingPosition(() -> {
                binding.followGpsButton.setVisibility(View.VISIBLE);
                binding.topPanel.setVisibility(View.GONE);
                binding.bottomPanel.setVisibility(View.GONE);
                return Unit.INSTANCE;
            });

            mapView.setOnEnterFollowingPosition(() -> {
                binding.followGpsButton.setVisibility(View.GONE);
                boolean navigationIsActive = Boolean.TRUE.equals(GemCall.INSTANCE.execute(() -> navigationService.isNavigationActive(navigationListener)));
                boolean simulationIsActive = Boolean.TRUE.equals(GemCall.INSTANCE.execute(() -> navigationService.isSimulationActive(navigationListener)));

                if (navigationIsActive || simulationIsActive) {
                    binding.topPanel.setVisibility(View.VISIBLE);
                    binding.bottomPanel.setVisibility(View.VISIBLE);
                }
                return Unit.INSTANCE;
            });

            binding.followGpsButton.setOnClickListener(v -> GemCall.INSTANCE.execute(() -> {
                if (binding.gemSurfaceView.getMapView() != null) {
                    binding.gemSurfaceView.getMapView().followPosition(
                            true,
                            new Animation(EAnimation.Linear, 900, null, null),
                            -1,
                            Double.MAX_VALUE,
                            null,
                            null,
                            false
                    );
                }
                return null;
            }));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_PERMISSIONS) return;

        for (int item : grantResults) {
            if (item != PackageManager.PERMISSION_GRANTED) {
                showDialog(getString(R.string.location_permission_required), this::finish);
                return;
            }
        }

        GemCall.INSTANCE.execute(() -> {
            PermissionsHelper.Companion.onRequestPermissionsResult(this, requestCode, grantResults);
            return null;
        });
    }

    private void requestPermissions() {
        String[] permissions = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        };

        PermissionsHelper.Companion.requestPermissions(
            REQUEST_PERMISSIONS,
            this,
            permissions
        );
    }

    private void calculateRoute(Landmark departure, Landmark destination) {
        GemCall.INSTANCE.execute(() -> {
            ArrayList<Landmark> waypoints = new ArrayList<>();
            waypoints.add(departure);
            waypoints.add(destination);

            routingService.setPreferences(viewModel.routePreferences);
            routingService.calculateRoute(
                waypoints,
                viewModel.routePreferences.getTransportMode(),
                false,
                null,
                null,
                null
            );
            return null;
        });
    }

    public int getAppBarHeight() {
        if (binding.mapSearchBar.getVisibility() != View.VISIBLE) {
            return 0;
        }

        if (appBarHeight > 0) {
            return appBarHeight;
        }

        int currentHeight = binding.appBarLayout.getHeight();
        if (currentHeight > 0) {
            appBarHeight = currentHeight;
            return appBarHeight;
        }

        binding.appBarLayout.measure(
            View.MeasureSpec.makeMeasureSpec(binding.appBarLayout.getWidth(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        return binding.appBarLayout.getMeasuredHeight();
    }

    private void showDialog(String text) {
        showDialog(text, null);
    }

    private void showDialog(String text, Runnable onDismiss) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        DialogLayoutBinding dialogBinding = DialogLayoutBinding.inflate(getLayoutInflater());
        dialogBinding.title.setText(getString(R.string.error));
        dialogBinding.message.setText(text);
        dialogBinding.button.setOnClickListener(v -> {
            if (onDismiss != null) {
                onDismiss.run();
            }
            dialog.dismiss();
        });

        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setDraggable(false);
        dialog.setCancelable(false);
        dialog.setContentView(dialogBinding.getRoot());
        dialog.show();
    }

    private void showCalculateRouteDialog(
        String title,
        String message,
        Runnable onCalculateRoute,
        Runnable onViewCreated,
        Runnable onViewClosed
    ) {
        runOnUiThread(() -> {
            binding.calculateRoutePanel.title.setText(title);
            binding.calculateRoutePanel.message.setText(message);

            binding.calculateRoutePanel.closeButton.setOnClickListener(v -> {
                binding.calculateRoutePanel.getRoot().setVisibility(View.GONE);
                bottomDialogHeight = 0;
                if (onViewClosed != null) {
                    onViewClosed.run();
                }
            });

            binding.calculateRoutePanel.buttonCalculateRoute.setOnClickListener(v -> {
                binding.calculateRoutePanel.getRoot().setVisibility(View.GONE);
                bottomDialogHeight = 0;
                if (onViewClosed != null) {
                    onViewClosed.run();
                }
                onCalculateRoute.run();
            });

            binding.calculateRoutePanel.getRoot().setVisibility(View.VISIBLE);

            binding.calculateRoutePanel.getRoot().post(() -> {
                bottomDialogHeight = binding.calculateRoutePanel.getRoot().getHeight();
                if (onViewCreated != null) {
                    onViewCreated.run();
                }
            });
        });
    }

    private String formatRouteName(Route route) {
        return GemCall.INSTANCE.execute(() -> {
            if (route.getTimeDistance() == null) return "";
            int distInMeters = route.getTimeDistance().getTotalDistance();
            int timeInSeconds = route.getTimeDistance().getTotalTime();
            Pair<String, String> distTextPair = GemUtil.INSTANCE.getDistText(
                distInMeters,
                SdkSettings.INSTANCE.getUnitSystem(),
                true,
                false
            );
            Pair<String, String> timeTextPair = GemUtil.INSTANCE.getTimeText(timeInSeconds, false, false);

            return String.format("%s %s, %s %s",
                distTextPair.getFirst(), distTextPair.getSecond(),
                timeTextPair.getFirst(), timeTextPair.getSecond());
        });
    }

    private void showStartNavigationDialog(
        String title,
        String message,
        Runnable onStartNavigation,
        Runnable onStartSimulation,
        Runnable onViewCreated,
        Runnable onViewClosed
    ) {
        runOnUiThread(() -> {
            binding.startNavigationPanel.title.setText(title);
            binding.startNavigationPanel.message.setText(message);

            binding.startNavigationPanel.closeButton.setOnClickListener(v -> {
                binding.startNavigationPanel.getRoot().setVisibility(View.GONE);
                bottomDialogHeight = 0;
                if (onViewClosed != null) {
                    onViewClosed.run();
                }
            });

            binding.startNavigationPanel.buttonStartNavigation.setOnClickListener(v -> {
                binding.startNavigationPanel.getRoot().setVisibility(View.GONE);
                bottomDialogHeight = 0;
                onStartNavigation.run();
            });

            binding.startNavigationPanel.buttonStartSimulation.setOnClickListener(v -> {
                binding.startNavigationPanel.getRoot().setVisibility(View.GONE);
                bottomDialogHeight = 0;
                onStartSimulation.run();
            });

            binding.startNavigationPanel.getRoot().setVisibility(View.VISIBLE);

            binding.startNavigationPanel.getRoot().post(() -> {
                bottomDialogHeight = binding.startNavigationPanel.getRoot().getHeight();
                if (onViewCreated != null) {
                    onViewCreated.run();
                }
            });
        });
    }

    private void showInvalidTokenDialog() {
        showDialog(getString(R.string.invalid_token), this::finish);
    }

    private int getTopInset() {
        int appBarHeight = getAppBarHeight();
        return appBarHeight > 0 ? appBarHeight + inflate : topInset;
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        return locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    private boolean checkLocationStatus() {
        if (!isLocationEnabled()) {
            showLocationDialog(
                getString(R.string.location_disabled),
                new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            );
            return false;
        }
        return true;
    }

    private void showLocationDialog(String message, Intent settingsIntent) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        DialogLayoutBinding dialogBinding = DialogLayoutBinding.inflate(getLayoutInflater());
        dialogBinding.title.setText(getString(R.string.location_status));
        dialogBinding.message.setText(message);
        dialogBinding.button.setText(getString(R.string.open_settings));
        dialogBinding.button.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(settingsIntent);
            shouldCheckLocationPermissionOnResume = true;
        });

        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setDraggable(false);
        dialog.setCancelable(false);
        dialog.setContentView(dialogBinding.getRoot());
        dialog.show();
    }

    private String getInstructionDistanceInMeters(NavigationInstruction instr) {
        return GemCall.INSTANCE.execute(() -> {
            int distance = instr.getTimeDistanceToNextTurn() != null ?
                instr.getTimeDistanceToNextTurn().getTotalDistance() : 0;
            Pair<String, String> pair = GemUtil.INSTANCE.getDistText(distance, EUnitSystem.Metric, false, false);
            return pair.getFirst() + " " + pair.getSecond();
        });
    }

    @SuppressLint("DefaultLocale")
    private String getEta(Route route) {
        return GemCall.INSTANCE.execute(() -> {
            TimeDistance timeDistance = route.getTimeDistance(true);
            int etaNumber = timeDistance != null ? timeDistance.getTotalTime() : 0;

            Time time = new Time();
            time.setLocalTime();
            time.setLongValue(time.getLongValue() + etaNumber * 1000L);
            return String.format("%d:%02d", time.getHour(), time.getMinute());
        });
    }

    private String getRtt(Route route) {
        return GemCall.INSTANCE.execute(() -> {
            TimeDistance timeDistance = route.getTimeDistance(true);
            int timeInSeconds = timeDistance != null ? timeDistance.getTotalTime() : 0;
            Pair<String, String> pair = GemUtil.INSTANCE.getTimeText(timeInSeconds, false, false);
            return pair.getFirst() + " " + pair.getSecond();
        });
    }

    private String getRtd(Route route) {
        return GemCall.INSTANCE.execute(() -> {
            TimeDistance timeDistance = route.getTimeDistance(true);
            int distInMeters = timeDistance != null ? timeDistance.getTotalDistance() : 0;
            Pair<String, String> pair = GemUtil.INSTANCE.getDistText(distInMeters, EUnitSystem.Metric, false, false);
            return pair.getFirst() + " " + pair.getSecond();
        });
    }

    private Bitmap getNextTurnImage(
        NavigationInstruction navInstr,
        int width,
        int height,
        TSameImage sameImage
    ) {
        if (!navInstr.hasNextTurnInfo()) return null;

        long imageUid = navInstr.getNextTurnDetails() != null &&
            navInstr.getNextTurnDetails().getAbstractGeometryImage() != null ?
            navInstr.getNextTurnDetails().getAbstractGeometryImage().getUid() : 0;

        if (imageUid == lastTurnImageId) {
            sameImage.value = true;
            return null;
        }

        if (navInstr.getNextTurnDetails() != null &&
            navInstr.getNextTurnDetails().getAbstractGeometryImage() != null) {
            lastTurnImageId = navInstr.getNextTurnDetails().getAbstractGeometryImage().getUid();
        }

        Rgba aInner = new Rgba(255, 255, 255, 255);
        Rgba aOuter = new Rgba(0, 0, 0, 255);
        Rgba iInner = new Rgba(128, 128, 128, 255);
        Rgba iOuter = new Rgba(128, 128, 128, 255);

        return GemUtilImages.INSTANCE.asBitmap(
            navInstr.getNextTurnDetails() != null ?
                navInstr.getNextTurnDetails().getAbstractGeometryImage() : null,
            width,
            height,
            aInner,
            aOuter,
            iInner,
            iOuter
        );
    }

    private String getStatusMessage() {
        if (navigationStatus == ENavigationStatus.WaitingRoute) {
            Route route = getNavRoute();
            ERouteStatus routeStatus = route != null ? route.getStatus() : null;

            if (routeStatus != null) {
                if (routeStatus == ERouteStatus.WaitingInternetConnection) {
                    return getString(R.string.waiting_for_internet_connection);
                } else if (routeStatus == ERouteStatus.Calculating) {
                    return getString(R.string.calculating);
                } else if (routeStatus == ERouteStatus.Ready) {
                    return getString(R.string.gps_accuracy_not_good_enough);
                }
            }
            return getString(R.string.calculating);
        } else if (navigationStatus == ENavigationStatus.WaitingGPS) {
            if (Boolean.TRUE.equals(GemCall.INSTANCE.execute(() -> navigationService.isSimulationActive(navigationListener)))) {
                return getString(R.string.calculating);
            }
            return getString(R.string.getting_position);
        }

        return "";
    }

    private void onNavigationEnded(int errorCode) {
        runOnUiThread(() -> {
            if (errorCode != GemError.NoError && errorCode != GemError.Cancel) {
                showDialog(GemError.INSTANCE.getMessage(errorCode, MainActivity.this));
            }

            binding.mapSearchBar.setVisibility(View.VISIBLE);
            binding.bikeSettingsButton.setVisibility(View.VISIBLE);
            binding.topPanel.setVisibility(View.GONE);
            binding.bottomPanel.setVisibility(View.GONE);
        });

        if (binding.gemSurfaceView.getMapView() != null) {
            binding.gemSurfaceView.getMapView().hideRoutes();
        }
    }

    // ITTSPlayerInitializationListener
    @Override
    public void onTTSPlayerInitialized() {
        SoundPlayingService.INSTANCE.setTTSLanguage("eng-USA");
    }

    // ITTSPlayerInitializationListener
    @Override
    public void onTTSPlayerInitializationFailed() {
        SoundPlayingService.INSTANCE.setDefaultHumanVoice();
    }

    // TESTING
    public static class EspressoIdlingResource {
        public static final CountingIdlingResource espressoIdlingResource =
            new CountingIdlingResource("BikeSimulationTestsIdlingResource");

        public static void increment() {
            espressoIdlingResource.increment();
        }

        public static void decrement() {
            if (!espressoIdlingResource.isIdleNow()) {
                espressoIdlingResource.decrement();
            }
        }
    }
}

