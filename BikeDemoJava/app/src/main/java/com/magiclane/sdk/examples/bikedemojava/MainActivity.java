/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemojava;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.test.espresso.idling.CountingIdlingResource;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.magiclane.sdk.places.EAddressField;
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
import com.magiclane.sdk.d3scene.OverlayItem;
import com.magiclane.sdk.examples.bikedemojava.databinding.ActivityMainBinding;
import com.magiclane.sdk.examples.bikedemojava.databinding.DialogLayoutBinding;
import com.magiclane.sdk.places.Coordinates;
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
import com.magiclane.sdk.sensordatasource.PositionData;
import com.magiclane.sdk.sensordatasource.PositionListener;
import com.magiclane.sdk.sensordatasource.PositionService;
import com.magiclane.sdk.sensordatasource.enums.EDataType;
import com.magiclane.sdk.util.GemCall;
import com.magiclane.sdk.core.XyF;
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

    private ActivityMainBinding binding;
    private int searchIconSize = 0;
    private static final long SEARCH_DEBOUNCE_MS = 400L;
    private HandlerThread searchDebounceThread;
    private Handler searchDebounceHandler;
    private Runnable pendingSearchTask;
    private volatile String searchFilter = "";

    // Index of the selected category chip (NO_CATEGORY when none), the reference point used for
    // around-position searches and distance display, and a guard to ignore programmatic edits
    // to the search field (e.g. when a category name is written into it).
    private int activeCategoryIndex = CategoryAdapter.NO_CATEGORY;
    private volatile boolean isProgrammaticQuery = false;
    private Coordinates reference = null;
    private int categoryIconSize = 0;

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
    private static final float ROUTE_PANEL_LANDSCAPE_RATIO = 0.45f;
    private static final float NAV_PANEL_LANDSCAPE_RATIO = 0.40f;
    private static final float CAMERA_FOCUS_X_CENTER = 0.5f;
    private static final float CAMERA_FOCUS_X_SHIFTED = 0.7f;
    private static final float CAMERA_FOCUS_Y = 0.75f;

    private ConstraintSet portraitConstraintSet;

    private final SearchAdapter searchAdapter = new SearchAdapter();
    private final CategoryAdapter categoryAdapter = new CategoryAdapter();
    private MainActivityViewModel viewModel;
    private final NavigationService navigationService = new NavigationService();
    private final SoundPlayingListener playingListener = new SoundPlayingListener() {};
    private final SoundPlayingPreferences soundPreference = new SoundPlayingPreferences();
    private PositionListener positionListener;

    private final ProgressListener checkAuthorizationListener = new ProgressListener() {
        @Override
        public void notifyComplete(int errorCode, @NonNull String message) {
            if (errorCode != GemError.NoError) {
                runOnUiThread(() -> showInvalidTokenDialog());
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
                binding.topPanel.setVisibility(View.VISIBLE);
                binding.bottomPanel.setVisibility(View.VISIBLE);
                applyCameraFocus();
                binding.mapRoot.post(() -> updateFocusViewport());
            });
        }

        @Override
        public void onNavigationInstructionUpdated(NavigationInstruction instr) {
            boolean[] sameTurnImage = {false};

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
                if (!sameTurnImage[0]) {
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
                                            getString(R.string.route_navigation_error, GemCall.INSTANCE.runSynced(() -> GemError.INSTANCE.getMessage(error, MainActivity.this)))
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
                                                getString(R.string.route_simulation_error, GemCall.INSTANCE.runSynced(() -> GemError.INSTANCE.getMessage(error, MainActivity.this)))
                                        ));
                                    }

                                    removeAllRoutesFromMapExceptMainRoute(mapView.getPreferences().getRoutes(), mainRoute);
                                }
                            }

                            return null;
                        }),
                        () -> {
                            binding.mapSearchBar.setVisibility(View.GONE);
                            boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
                            int panelWidth = isLandscape ? (int)(getResources().getDisplayMetrics().widthPixels * ROUTE_PANEL_LANDSCAPE_RATIO) : 0;
                            int leftEdge = isLandscape ? panelWidth : leftInset;
                            int bottomEdge = isLandscape ? inflate : bottomDialogHeight + inflate;
                            GemCall.INSTANCE.execute(() -> {
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
                                            new Rect(leftEdge, getTopInset(), rightInset, bottomEdge)
                                    );
                                }
                                return null;
                            });
                        },
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
                            GemCall.INSTANCE.runSynced(() -> GemError.INSTANCE.getMessage(errorCode, MainActivity.this))
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
        // Iterate in reverse so that removing an element doesn't shift subsequent indices.
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

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupRoutingServiceCallbacks();
        EspressoIdlingResource.increment();

        portraitConstraintSet = new ConstraintSet();
        portraitConstraintSet.clone(binding.mapRoot);

        registerSdkListeners();

        // Apply initial orientation layout after first layout pass
        binding.mapRoot.post(() -> {
            applyOrientationLayout();
            updateFollowGpsButtonMargins(isAnyPanelVisible());
        });

        // Measure app bar height after layout
        binding.appBarLayout.post(() -> appBarHeight = binding.appBarLayout.getHeight());

        // Set up window insets listener
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            topInset = systemBars.top + inflate;
            leftInset = systemBars.left + inflate;
            rightInset = systemBars.right + inflate;
            updateFollowGpsButtonMargins(isAnyPanelVisible());
            return insets;
        });

        searchIconSize = (int) getResources().getDimension(R.dimen.icon_size);
        categoryIconSize = (int) getResources().getDimension(R.dimen.category_icon_size);
        inflate = (int) getResources().getDimension(R.dimen.padding_40);

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
                    () -> GemCall.INSTANCE.execute(() -> {
                        PositionData position = PositionService.INSTANCE.getPosition();
                        if ((position != null) && position.isValid()) {
                            Landmark departure = new Landmark(getString(R.string.my_position), position.getLatitude(), position.getLongitude());
                            Landmark destination = item.getLandmark();
                            calculateRoute(departure, destination);
                        } else {
                            runOnUiThread(() -> showDialog(getString(R.string.current_position_not_available)));
                        }

                        return null;
                    }),
                    () -> highlightLandmarkOnMap(viewModel.destination),
                    this::deactivateHighlights
                );
                return null;
            });
        });

        EspressoIdlingResource.decrement();

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
                // Ignore edits we make programmatically (e.g. writing a category name into the field).
                if (isProgrammaticQuery) return;

                String filter = s.toString().trim();

                if (filter.equals(searchFilter) && activeCategoryIndex == CategoryAdapter.NO_CATEGORY) {
                    return;
                }

                searchFilter = filter;

                // Typing clears any active category selection.
                if (activeCategoryIndex != CategoryAdapter.NO_CATEGORY) {
                    activeCategoryIndex = CategoryAdapter.NO_CATEGORY;
                    viewModel.selectedCategory.setValue(CategoryAdapter.NO_CATEGORY);
                }

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
                        setSearchReferencePoint();

                        // Re-enable free-text address search after a category-only search disabled it.
                        if (!searchService.getPreferences().getSearchAddressesEnabled()) {
                            searchService.getPreferences().removeAllCategoryFilters();
                            searchService.getPreferences().setSearchAddressesEnabled(true);
                        }

                        searchService.searchByFilter(
                            currentFilter,
                            reference,
                            null,
                            (results, errorCode, errorMessage) -> {
                                if (errorCode == GemError.Cancel) return Unit.INSTANCE;
                                if (errorCode == GemError.NoError) {
                                    GemCall.INSTANCE.execute(() -> {
                                        viewModel.searchResultListLivedata.postValue(buildSearchItems(results));
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
        });

        viewModel = new ViewModelProvider(this).get(MainActivityViewModel.class);

        viewModel.isElectricBikeProfile.observe(this, isElectric -> invalidateOptionsMenu());

        setSupportActionBar(binding.mapSearchBar);

        binding.mapSearchBar.setOnMenuItemClickListener(menuItem -> {
            int itemId = menuItem.getItemId();

            if (itemId == R.id.settings) {
                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new BikeSettingsFragment())
                    .commit();
                return true;
            }

            EBikeProfile selectedProfile = EBikeProfile.City;
            if (itemId == R.id.bike_cross || itemId == R.id.e_bike_cross) {
                selectedProfile = EBikeProfile.Cross;
            } else if (itemId == R.id.bike_mountain || itemId == R.id.e_bike_mountain) {
                selectedProfile = EBikeProfile.Mountain;
            } else if (itemId == R.id.bike_road || itemId == R.id.e_bike_road) {
                selectedProfile = EBikeProfile.Road;
            }

            viewModel.setBikeProfile(selectedProfile);
            binding.mapSearchBar.getMenu().findItem(
                viewModel.isElectric ? R.id.e_bike_type : R.id.bike_type
            ).setIcon(ContextCompat.getDrawable(this, getBikeProfileIcon(selectedProfile, viewModel.isElectric)));
            return true;
        });

        binding.searchResultsList.setAdapter(searchAdapter);
        binding.searchResultsList.setLayoutManager(new LinearLayoutManager(this));

        binding.categoriesView.setAdapter(categoryAdapter);
        binding.categoriesView.setLayoutManager(
            new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.categoriesView.setItemAnimator(null);
        categoryAdapter.setOnCategoryClickListener(this::selectCategory);

        viewModel.searchResultListLivedata.observe(this, list -> {
            searchAdapter.submitList(list);
            binding.searchProgressBar.setVisibility(View.INVISIBLE);
            binding.noResultsTextView.setVisibility(
                list.isEmpty() && (!searchFilter.isEmpty() || activeCategoryIndex != CategoryAdapter.NO_CATEGORY)
                    ? View.VISIBLE : View.GONE
            );
        });

        viewModel.categoriesLivedata.observe(this, categoryAdapter::submitList);

        viewModel.selectedCategory.observe(this, selectedIndex -> {
            categoryAdapter.setSelectedIndex(selectedIndex);
            if (selectedIndex != CategoryAdapter.NO_CATEGORY) {
                List<CategoryItem> categories = viewModel.categoriesLivedata.getValue();
                String name = (categories != null && selectedIndex >= 0 && selectedIndex < categories.size())
                    ? categories.get(selectedIndex).getName() : "";
                isProgrammaticQuery = true;
                binding.mapSearchView.getEditText().setText(name);
                binding.mapSearchView.getEditText().setSelection(name.length());
                searchFilter = name;
                isProgrammaticQuery = false;
            }
        });

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
                // The voice list sits on the back stack above the settings fragment.
                if (fragment instanceof VoiceFragment) {
                    getSupportFragmentManager().popBackStack();
                    return;
                }
                if (fragment instanceof BikeSettingsFragment) {
                    getSupportFragmentManager().beginTransaction().remove(fragment).commit();
                    return;
                }

                if (binding.calculateRoutePanel.getRoot().getVisibility() == View.VISIBLE) {
                    dismissPanel(binding.calculateRoutePanel.getRoot());
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
                    dismissPanel(binding.startNavigationPanel.getRoot());
                    binding.followGpsButton.setVisibility(View.VISIBLE);
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
                    return;
                }

                boolean navigationIsActive = Boolean.TRUE.equals(GemCall.INSTANCE.execute(() -> navigationService.isNavigationActive(navigationListener)));
                boolean simulationIsActive = Boolean.TRUE.equals(GemCall.INSTANCE.execute(() -> navigationService.isSimulationActive(navigationListener)));

                if (navigationIsActive || simulationIsActive) {
                    GemCall.INSTANCE.execute(() -> {
                        navigationService.cancelNavigation(navigationListener);
                        // Also stop any instruction sound that is playing or queued, so it
                        // doesn't keep playing after the navigation / simulation was cancelled.
                        SoundPlayingService.INSTANCE.cancel(playingListener);
                        return null;
                    });
                    return;
                }

                finish();
            }
        });
    }

    @Nullable
    private static Landmark getLandmark(MapView mapView) {
        Landmark selectedLandmark = null;
        List<Landmark> landmarks = mapView.getCursorSelectionLandmarks();
        if (landmarks != null && !landmarks.isEmpty()) {
            selectedLandmark = landmarks.get(0);
        } else {
            List<OverlayItem> overlayItems = mapView.getCursorSelectionOverlayItems();
            if (overlayItems != null && !overlayItems.isEmpty()) {
                OverlayItem overlay = overlayItems.get(0);

                Coordinates coordinates = overlay.getCoordinates();
                if (coordinates != null) {
                    String name;
                    if (overlay.getName() != null && !overlay.getName().isEmpty()) {
                        name = overlay.getName();
                    } else if (overlay.getOverlayInfo() != null && overlay.getOverlayInfo().getName() != null && !overlay.getOverlayInfo().getName().isEmpty()) {
                        name = overlay.getOverlayInfo().getName();
                    } else {
                        name = "Unknown";
                    }

                    selectedLandmark = new Landmark(
                            name,
                            coordinates.getLatitude(),
                            coordinates.getLongitude()
                    );

                    selectedLandmark.setDescription(getLandmarkDescription(mapView, coordinates, false));
                }
            }
        }
        return selectedLandmark;
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
                        EHighlightOptions.ShowContour.getValue() | EHighlightOptions.ShowLandmark.getValue() | EHighlightOptions.Overlap.getValue(),
                        new Rgba(255, 98, 0, 255),
                        new Rgba(255, 98, 0, 255),
                        0.75,
                        1.0
                    );
                    highlightSettings.setImageSize(6.0);
                } else {
                    highlightSettings = new HighlightRenderSettings();
                    highlightSettings.setOptions(EHighlightOptions.ShowLandmark.getValue() | EHighlightOptions.Overlap.getValue());
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
                    0
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
        int mapWidth = binding.gemSurfaceView.getWidth();
        int mapHeight = binding.gemSurfaceView.getHeight();

        if (mapWidth <= 0 || mapHeight <= 0) {
            int fallbackWidth = binding.gemSurfaceView.getMeasuredWidth();
            int fallbackHeight = binding.gemSurfaceView.getMeasuredHeight();
            return new Rect(0, 0, fallbackWidth, fallbackHeight);
        }

        WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(binding.getRoot());
        androidx.core.graphics.Insets insets = windowInsets != null
            ? windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout())
            : null;
        int leftSysInset = insets != null ? insets.left : 0;
        int rightSysInset = insets != null ? insets.right : 0;
        int bottomSysInset = insets != null ? insets.bottom : 0;

        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int padding = getResources().getDimensionPixelSize(R.dimen.map_free_space_padding);
        int topLimit = getAppBarHeight();

        int left;
        int right;
        int bottom;

        if (isLandscape) {
            if (binding.calculateRoutePanel.getRoot().getVisibility() == View.VISIBLE) {
                left = Math.max(
                    binding.calculateRoutePanel.getRoot().getRight() - binding.gemSurfaceView.getLeft(),
                    leftSysInset
                );
            } else if (binding.startNavigationPanel.getRoot().getVisibility() == View.VISIBLE) {
                left = Math.max(
                    binding.startNavigationPanel.getRoot().getRight() - binding.gemSurfaceView.getLeft(),
                    leftSysInset
                );
            } else {
                left = leftSysInset;
            }
            right = Math.max(mapWidth - rightSysInset, left + 1);
            bottom = Math.max(mapHeight - bottomSysInset, topLimit + 1);
        } else {
            left = Math.max(0, Math.min(leftSysInset, Math.max(mapWidth - 1, 0)));
            right = Math.max(left + 1, Math.min(mapWidth - rightSysInset, mapWidth));
            int bottomLimitRaw = getBottomLimitRaw(mapHeight);
            bottom = Math.max(topLimit + 1, Math.min(bottomLimitRaw, mapHeight));
        }

        int paddedLeft = Math.min(left + padding, right - 1);
        int paddedRight = Math.max(right - padding, paddedLeft + 1);
        int paddedTop = Math.min(topLimit + padding, bottom - 1);
        int paddedBottom = Math.max(bottom - padding, paddedTop + 1);

        return new Rect(paddedLeft, paddedTop, paddedRight, paddedBottom);
    }

    private int getBottomLimitRaw(int mapHeight) {
        int bottomLimitRaw;
        if (binding.calculateRoutePanel.getRoot().getVisibility() == View.VISIBLE) {
            bottomLimitRaw = binding.calculateRoutePanel.getRoot().getTop() - binding.gemSurfaceView.getTop();
        } else if (binding.startNavigationPanel.getRoot().getVisibility() == View.VISIBLE) {
            bottomLimitRaw = binding.startNavigationPanel.getRoot().getTop() - binding.gemSurfaceView.getTop();
        } else {
            bottomLimitRaw = mapHeight;
        }
        return bottomLimitRaw;
    }

    private int getBikeProfileIcon(EBikeProfile profile, boolean isElectric) {
        switch (profile) {
            case Cross:    return isElectric ? R.drawable.ebikecross    : R.drawable.bikecross;
            case Mountain: return isElectric ? R.drawable.ebikemountain : R.drawable.bikemountain;
            case Road:     return isElectric ? R.drawable.ebikeroad     : R.drawable.bikeroad;
            default:       return isElectric ? R.drawable.ebikecity     : R.drawable.bikecity;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem electric = menu.findItem(R.id.e_bike_type);
        electric.setVisible(viewModel.isElectric);
        MenuItem notElectric = menu.findItem(R.id.bike_type);
        notElectric.setVisible(!viewModel.isElectric);
        MenuItem icon = viewModel.isElectric ? electric : notElectric;
        icon.setIcon(ContextCompat.getDrawable(this, getBikeProfileIcon(viewModel.bikeProfile, viewModel.isElectric)));
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.search_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onDestroy() {
        clearSdkListeners();
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

    // Selects a category chip and runs an around-position search restricted to it. Tapping the
    // already-selected chip is a no-op.
    private void selectCategory(int index) {
        if (activeCategoryIndex == index) return;
        activeCategoryIndex = index;
        viewModel.selectedCategory.setValue(index);

        cancelPendingSearchTask();
        GemCall.INSTANCE.postAsync(() -> {
            searchService.cancelSearch();
            return null;
        });

        if (index == CategoryAdapter.NO_CATEGORY) return;

        binding.searchProgressBar.setVisibility(View.VISIBLE);

        GemCall.INSTANCE.postAsync(() -> {
            searchService.getPreferences().removeAllCategoryFilters();
            searchService.getPreferences().setSearchAddressesEnabled(false);
            setSearchReferencePoint();

            List<CategoryItem> categories = viewModel.categoriesLivedata.getValue();
            if (categories == null || index < 0 || index >= categories.size()) {
                return null;
            }

            CategoryItem category = categories.get(index);
            if (searchService.getPreferences().getLandmarkStores() != null) {
                searchService.getPreferences().getLandmarkStores()
                    .addStoreCategoryId(category.getLandmarkStoreId(), category.getCategoryId());
            }

            searchService.searchAroundPosition(
                reference,
                "",
                null,
                (results, errorCode, errorMessage) -> {
                    if (errorCode == GemError.Cancel) return Unit.INSTANCE;
                    if (errorCode == GemError.NoError) {
                        GemCall.INSTANCE.execute(() -> {
                            viewModel.searchResultListLivedata.postValue(buildSearchItems(results));
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
    }

    // Uses the current GPS position as the reference for searches and distance display when valid.
    private void setSearchReferencePoint() {
        PositionData position = PositionService.INSTANCE.getPosition();
        if (position != null && position.isValid()) {
            reference = position.getCoordinates();
        }
    }

    // Maps SDK landmarks to list items, computing the distance from the reference point.
    // Must be called from an SDK thread.
    private List<SearchResultItem> buildSearchItems(ArrayList<Landmark> landmarks) {
        List<SearchResultItem> list = new ArrayList<>();
        for (Landmark landmark : landmarks) {
            int meters = 0;
            if (reference != null && landmark.getCoordinates() != null) {
                meters = (int) landmark.getCoordinates().getDistance(reference);
            }
            Pair<String, String> dist = GemUtil.INSTANCE.getDistText(meters, EUnitSystem.Metric, true, false);
            list.add(new SearchResultItem(
                landmark.getImage() != null ? landmark.getImage().asBitmap(searchIconSize, searchIconSize) : null,
                GemUtil.INSTANCE.formatName(landmark),
                GemUtil.INSTANCE.getLandmarkDescription(landmark, true),
                dist.getFirst(),
                dist.getSecond(),
                landmark
            ));
        }
        return list;
    }

    // Presents [landmark] as a potential destination: opens the calculate-route panel, highlights
    // it on the map, and routes to it from the current position on confirmation. Must be called
    // from an SDK thread (e.g. inside an onTouch / onLongDown handler).
    private void presentLandmarkForRouting(Landmark landmark) {
        viewModel.destination = landmark;
        Pair<String, String> details = GemUtil.INSTANCE.pairFormatLandmarkDetails(landmark, true);
        showCalculateRouteDialog(
            details.getFirst(),
            details.getSecond(),
            () -> GemCall.INSTANCE.execute(() -> {
                PositionData position = PositionService.INSTANCE.getPosition();
                if ((position != null) && position.isValid()) {
                    Landmark departure = new Landmark(getString(R.string.my_position), position.getLatitude(), position.getLongitude());
                    calculateRoute(departure, landmark);
                } else {
                    runOnUiThread(() -> showDialog(getString(R.string.current_position_not_available)));
                }
                return null;
            }),
            () -> highlightLandmarkOnMap(landmark),
            this::deactivateHighlights
        );
    }

    private void enableGPSButton() {
        MapView mapView = binding.gemSurfaceView.getMapView();
        if (mapView != null) {
            mapView.setOnExitFollowingPosition(() -> {
                binding.followGpsButton.setVisibility(View.VISIBLE);
                updateFollowGpsButtonMargins(isAnyPanelVisible());
                binding.topPanel.setVisibility(View.GONE);
                binding.bottomPanel.setVisibility(View.GONE);
                applyCameraFocus();
                updateFocusViewport();
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

                applyCameraFocus();

                if (binding.calculateRoutePanel.getRoot().getVisibility() == View.VISIBLE) {
                    binding.calculateRoutePanel.getRoot().setVisibility(View.GONE);
                    deactivateHighlights();
                }

                binding.mapRoot.post(this::updateFocusViewport);
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
            int error = routingService.calculateRoute(
                waypoints,
                viewModel.routePreferences.getTransportMode(),
                false,
                null,
                null,
                null
            );
            if (error != GemError.NoError) {
                // The computation never started, so onStarted/onCompleted won't fire: clear any
                // progress UI and report the failure.
                String message = GemError.INSTANCE.getMessage(error, this);
                runOnAliveUi(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.cancelButton.setVisibility(View.GONE);
                    showDialog(getString(R.string.routing_error, message));
                });
            }
            return null;
        });
    }

    // Resolves the app bar height with three fallbacks: cached value → live layout height
    // → forced measure. The measure fallback handles early calls before the first layout pass.
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
                dismissPanel(binding.calculateRoutePanel.getRoot());
                if (onViewClosed != null) {
                    onViewClosed.run();
                }
            });

            binding.calculateRoutePanel.buttonCalculateRoute.setOnClickListener(v -> {
                dismissPanel(binding.calculateRoutePanel.getRoot());
                if (onViewClosed != null) {
                    onViewClosed.run();
                }
                onCalculateRoute.run();
            });

            binding.calculateRoutePanel.getRoot().setVisibility(View.VISIBLE);

            binding.calculateRoutePanel.getRoot().post(() -> {
                bottomDialogHeight = binding.calculateRoutePanel.getRoot().getHeight();
                updateFollowGpsButtonMargins(isAnyPanelVisible());
                updateFocusViewport();
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
                dismissPanel(binding.startNavigationPanel.getRoot());
                binding.followGpsButton.setVisibility(View.VISIBLE);
                if (onViewClosed != null) {
                    onViewClosed.run();
                }
            });

            binding.startNavigationPanel.buttonStartNavigation.setOnClickListener(v -> {
                dismissPanel(binding.startNavigationPanel.getRoot());
                onStartNavigation.run();
            });

            binding.startNavigationPanel.buttonStartSimulation.setOnClickListener(v -> {
                dismissPanel(binding.startNavigationPanel.getRoot());
                onStartSimulation.run();
            });

            binding.startNavigationPanel.getRoot().setVisibility(View.VISIBLE);
            binding.followGpsButton.setVisibility(View.GONE);

            binding.startNavigationPanel.getRoot().post(() -> {
                bottomDialogHeight = binding.startNavigationPanel.getRoot().getHeight();
                updateFollowGpsButtonMargins(isAnyPanelVisible());
                updateFocusViewport();
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

    // sameImage[0] is set to true if the turn icon hasn't changed since last call, allowing the
    // caller to skip an unnecessary ImageView update.
    private Bitmap getNextTurnImage(
        NavigationInstruction navInstr,
        int width,
        int height,
        boolean[] sameImage
    ) {
        if (!navInstr.hasNextTurnInfo()) return null;

        long imageUid = navInstr.getNextTurnDetails() != null &&
            navInstr.getNextTurnDetails().getAbstractGeometryImage() != null ?
            navInstr.getNextTurnDetails().getAbstractGeometryImage().getUid() : 0;

        if (imageUid == lastTurnImageId) {
            sameImage[0] = true;
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

    // Returns an empty string when the navigation panel should display normally.
    // A non-empty string is shown as a full-width status overlay, hiding the turn icon.
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
                showDialog(GemCall.INSTANCE.runSynced(() -> GemError.INSTANCE.getMessage(errorCode, MainActivity.this)));
            }

            binding.mapSearchBar.setVisibility(View.VISIBLE);
            binding.topPanel.setVisibility(View.GONE);
            binding.bottomPanel.setVisibility(View.GONE);
            applyCameraFocus();
            updateFocusViewport();
        });

        if (binding.gemSurfaceView.getMapView() != null) {
            binding.gemSurfaceView.getMapView().hideRoutes();
        }
    }

    // Cascades through progressively wider search radii (50 m → 300 m → 2500 m) and falls
    // back to raw lat/lon when no address can be resolved.
    @SuppressWarnings("SameParameterValue")
    @SuppressLint("DefaultLocale")
    private static String getLandmarkDescription(
            @NonNull MapView mapView,
            @NonNull Coordinates coordinates,
            boolean isMyPosition
    ) {
        String description = "";
        boolean descriptionContainsLatLon = false;

        Landmark address = mapView.getClosestAddress(coordinates, 50, false);
        if (address != null) {
            description = GemUtil.INSTANCE.formatLandmarkDetails(address, true);
        }

        if (description.isEmpty()) {
            address = mapView.getClosestAddress(coordinates, 300, false);
            if (address != null && address.getAddressInfo() != null) {
                String city = address.getAddressInfo().getField(EAddressField.City);
                description = city != null ? city : "";
            }

            if (description.isEmpty()) {
                address = mapView.getClosestAddress(coordinates, 2500, true);
                if (address != null && address.getAddressInfo() != null) {
                    String city = address.getAddressInfo().getField(EAddressField.City);
                    if (city != null && !city.isEmpty()) {
                        description = "Near " + city;
                    }
                }

                if (description.isEmpty()) {
                    description = String.format("%.5f, %.5f", coordinates.getLatitude(), coordinates.getLongitude());
                    descriptionContainsLatLon = true;
                }
            }
        }

        if (isMyPosition) {
            if (!descriptionContainsLatLon) {
                description += "\nLatitude: " + String.format("%.5f", coordinates.getLatitude());
                description += "\nLongitude: " + String.format("%.5f", coordinates.getLongitude());
            }

            description += "\nAltitude: " + (int) coordinates.getAltitude() + "m";
        }

        return description;
    }

    // region SDK listeners

    private void registerSdkListeners() {
        SdkSettings.INSTANCE.setOnConnectionStatusUpdated(isConnected -> {
            if (isConnected) {
                String appAuth = SdkSettings.INSTANCE.getAppAuthorization();
                if (appAuth != null) {
                    GemCall.INSTANCE.execute(() -> {
                        SdkSettings.INSTANCE.verifyAppAuthorization(appAuth, checkAuthorizationListener);
                        return null;
                    });
                } else {
                    runOnAliveUi(this::showInvalidTokenDialog);
                }
                SdkSettings.INSTANCE.setOnConnectionStatusUpdated(status -> Unit.INSTANCE);
            }
            return Unit.INSTANCE;
        });

        binding.gemSurfaceView.setOnSdkInitFailed(error -> {
            String errorMessage = getString(R.string.sdk_init_failed, GemError.INSTANCE.getMessage(error, this));
            runOnAliveUi(() -> showDialog(errorMessage, () -> {
                finish();
                System.exit(0);
            }));
            return Unit.INSTANCE;
        });

        binding.gemSurfaceView.setOnDefaultMapViewCreated(mapView -> {
            mapView.followPosition(true, new Animation(EAnimation.Linear, 900, null, null), -1, Double.MAX_VALUE, null, null, false);

            if (PositionService.INSTANCE.getPosition() != null && PositionService.INSTANCE.getPosition().isValid()) {
                Util.INSTANCE.postOnMain(this::enableGPSButton);
            } else {
                positionListener = new PositionListener() {
                    @Override
                    public void onNewPosition(@NonNull com.magiclane.sdk.sensordatasource.PositionData position) {
                        if (!position.isValid()) return;
                        PositionService.INSTANCE.removeListener(positionListener);
                        Util.INSTANCE.postOnMain(MainActivity.this::enableGPSButton);
                    }
                };
                PositionService.INSTANCE.addListener(positionListener, EDataType.Position);
            }

            viewModel.initPreferences();
            viewModel.loadCategories(categoryIconSize);

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
                            MapViewRoutesCollection routesCollection = mapView.getPreferences().getRoutes();
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

                        if (!routesList.isEmpty()) return null;

                        Landmark landmark = getLandmark(mapView);

                        if ((landmark != null) && (landmark.getCoordinates() != null)) {
                            presentLandmarkForRouting(landmark);
                        }
                        return null;
                    });
                    return Unit.INSTANCE;
                });

                // Long press: route to the closest street under the cursor.
                mapView.setOnLongDown(xy -> {
                    GemCall.INSTANCE.execute(() -> {
                        if (navigationService.isNavigationActive(navigationListener) ||
                            navigationService.isSimulationActive(navigationListener)) {
                            return null;
                        }

                        mapView.setCursorScreenPosition(xy);

                        List<Landmark> streets = mapView.getCursorSelectionStreets();
                        if (streets != null && !streets.isEmpty()) {
                            presentLandmarkForRouting(streets.get(0));
                        }
                        return null;
                    });
                    return Unit.INSTANCE;
                });

                invalidateOptionsMenu();
                updateFocusViewport();
            });
            return Unit.INSTANCE;
        });

        binding.gemSurfaceView.setOnSurfaceChanged((w, h) -> {
            Util.INSTANCE.postOnMain(this::updateFocusViewport);
            return Unit.INSTANCE;
        });

        SdkSettings.INSTANCE.setOnApiTokenRejected(() -> {
            runOnAliveUi(this::showInvalidTokenDialog);
            return Unit.INSTANCE;
        });
    }

    private void clearSdkListeners() {
        SdkSettings.INSTANCE.setOnConnectionStatusUpdated(status -> Unit.INSTANCE);
        SdkSettings.INSTANCE.setOnApiTokenRejected(() -> Unit.INSTANCE);
        binding.gemSurfaceView.setOnSdkInitFailed(error -> Unit.INSTANCE);
        binding.gemSurfaceView.setOnDefaultMapViewCreated(mapView -> Unit.INSTANCE);
        binding.gemSurfaceView.setOnSurfaceChanged((w, h) -> Unit.INSTANCE);
    }

    private boolean isActivityAlive() {
        return !isFinishing() && !isDestroyed();
    }

    private void runOnAliveUi(Runnable block) {
        Util.INSTANCE.postOnMain(() -> { if (isActivityAlive()) block.run(); });
    }

    // endregion

    // region Orientation & layout

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        binding.mapRoot.post(() -> {
            applyOrientationLayout();
            updateFollowGpsButtonMargins(isAnyPanelVisible());
            applyCameraFocus();
            updateFocusViewport();
        });
    }

    private void applyOrientationLayout() {
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        // ConstraintSet.applyTo() resets all view states (visibility, margins), so we snapshot
        // them before cloning and restore them after the new constraints are applied.
        int calcVis = binding.calculateRoutePanel.getRoot().getVisibility();
        int navVis = binding.startNavigationPanel.getRoot().getVisibility();
        int fabVis = binding.followGpsButton.getVisibility();
        int topPanelVis = binding.topPanel.getVisibility();
        int bottomPanelVis = binding.bottomPanel.getVisibility();
        int cancelVis = binding.cancelButton.getVisibility();
        int progressVis = binding.progressBar.getVisibility();

        int savedTopTopMargin = 0, savedTopLeftMargin = 0, savedTopRightMargin = 0;
        if (binding.topPanel.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) binding.topPanel.getLayoutParams();
            savedTopTopMargin = lp.topMargin;
            savedTopLeftMargin = lp.leftMargin;
            savedTopRightMargin = lp.rightMargin;
        }

        int savedBottomBottomMargin = 0, savedBottomLeftMargin = 0, savedBottomRightMargin = 0;
        if (binding.bottomPanel.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) binding.bottomPanel.getLayoutParams();
            savedBottomBottomMargin = lp.bottomMargin;
            savedBottomLeftMargin = lp.leftMargin;
            savedBottomRightMargin = lp.rightMargin;
        }

        ConstraintSet cs = new ConstraintSet();
        cs.clone(portraitConstraintSet);

        if (isLandscape) {
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int routePanelWidth = (int)(screenWidth * ROUTE_PANEL_LANDSCAPE_RATIO);
            int navPanelWidth = (int)(screenWidth * NAV_PANEL_LANDSCAPE_RATIO);

            for (int panelId : new int[]{R.id.calculate_route_panel, R.id.start_navigation_panel}) {
                cs.constrainWidth(panelId, routePanelWidth);
                cs.constrainHeight(panelId, 0);
                cs.connect(panelId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0);
                cs.connect(panelId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0);
                cs.clear(panelId, ConstraintSet.END);
            }
            for (int panelId : new int[]{R.id.top_panel, R.id.bottom_panel}) {
                cs.constrainWidth(panelId, navPanelWidth);
                cs.connect(panelId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0);
                cs.clear(panelId, ConstraintSet.END);
            }
            cs.clear(R.id.follow_gps_button, ConstraintSet.BOTTOM);
            cs.connect(R.id.follow_gps_button, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0);
        }

        cs.applyTo(binding.mapRoot);

        binding.calculateRoutePanel.getRoot().setVisibility(calcVis);
        binding.startNavigationPanel.getRoot().setVisibility(navVis);
        binding.followGpsButton.setVisibility(fabVis);
        binding.topPanel.setVisibility(topPanelVis);
        binding.bottomPanel.setVisibility(bottomPanelVis);
        binding.cancelButton.setVisibility(cancelVis);
        binding.progressBar.setVisibility(progressVis);

        if (binding.topPanel.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) binding.topPanel.getLayoutParams();
            lp.topMargin = savedTopTopMargin;
            lp.leftMargin = savedTopLeftMargin;
            lp.rightMargin = savedTopRightMargin;
            binding.topPanel.setLayoutParams(lp);
        }
        if (binding.bottomPanel.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) binding.bottomPanel.getLayoutParams();
            lp.bottomMargin = savedBottomBottomMargin;
            lp.leftMargin = savedBottomLeftMargin;
            lp.rightMargin = savedBottomRightMargin;
            binding.bottomPanel.setLayoutParams(lp);
        }
    }

    private void updateFollowGpsButtonMargins(boolean panelVisible) {
        if (!(binding.followGpsButton.getLayoutParams() instanceof ConstraintLayout.LayoutParams)) return;
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) binding.followGpsButton.getLayoutParams();

        int padding = getResources().getDimensionPixelSize(R.dimen.padding_10);
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(binding.getRoot());
        androidx.core.graphics.Insets insets = windowInsets != null
            ? windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout())
            : null;
        int bottomInset = insets != null ? insets.bottom : 0;
        int endInset = insets != null ? insets.right : 0;

        int targetBottomMargin = (!isLandscape && panelVisible) ? bottomDialogHeight + padding : bottomInset + padding;
        int targetEndMargin = endInset + padding;

        if (params.bottomMargin != targetBottomMargin || params.rightMargin != targetEndMargin) {
            params.bottomMargin = targetBottomMargin;
            params.rightMargin = targetEndMargin;
            binding.followGpsButton.setLayoutParams(params);
        }
    }

    private boolean isAnyPanelVisible() {
        return binding.calculateRoutePanel.getRoot().getVisibility() == View.VISIBLE ||
               binding.startNavigationPanel.getRoot().getVisibility() == View.VISIBLE;
    }

    private void dismissPanel(View panel) {
        panel.setVisibility(View.GONE);
        bottomDialogHeight = 0;
        updateFollowGpsButtonMargins(isAnyPanelVisible());
        updateFocusViewport();
    }

    // endregion

    // region Camera & focus

    private void applyCameraFocus() {
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        boolean navPanelVisible = binding.topPanel.getVisibility() == View.VISIBLE;
        GemCall.INSTANCE.execute(() -> {
            MapView mapView = binding.gemSurfaceView.getMapView();
            if (mapView != null && mapView.getPreferences() != null &&
                mapView.getPreferences().getFollowPositionPreferences() != null) {
                XyF focus = (isLandscape && navPanelVisible)
                    ? new XyF(CAMERA_FOCUS_X_SHIFTED, CAMERA_FOCUS_Y)
                    : new XyF(CAMERA_FOCUS_X_CENTER, CAMERA_FOCUS_Y);
                mapView.getPreferences().getFollowPositionPreferences().setCameraFocus(focus);
            }
            return null;
        });
    }

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
        WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(root);
        androidx.core.graphics.Insets insets = windowInsets != null
            ? windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout())
            : null;

        int rootWidth = root.getWidth();
        int rootHeight = root.getHeight();
        int width = rootWidth > 0 ? rootWidth : getResources().getDisplayMetrics().widthPixels;
        int height = rootHeight > 0 ? rootHeight : getResources().getDisplayMetrics().heightPixels;

        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int insetsLeft = insets != null ? insets.left : 0;
        int insetsTop = insets != null ? insets.top : 0;
        int insetsRight = insets != null ? insets.right : 0;
        int insetsBottom = insets != null ? insets.bottom : 0;

        if (isLandscape) {
            int w = Math.max(width, height);
            int h = Math.min(width, height);
            int panelRight = getPanelRight(insetsLeft);
            int right = Math.max(w - insetsRight, panelRight);
            int bottom = Math.max(h - insetsBottom, insetsTop);
            return new Rect(panelRight, insetsTop, right, bottom);
        } else {
            int w = Math.min(width, height);
            int h = Math.max(width, height);
            int right = Math.max(w - insetsRight, insetsLeft);
            int top = binding.topPanel.getVisibility() == View.VISIBLE
                ? binding.topPanel.getBottom() : insetsTop;
            int bottom = getBottom(h, insetsBottom, top);
            return new Rect(insetsLeft, top, right, bottom);
        }
    }

    private int getBottom(int h, int insetsBottom, int top) {
        int bottomCandidate = h - insetsBottom;
        if (binding.bottomPanel.getVisibility() == View.VISIBLE)
            bottomCandidate = Math.min(bottomCandidate, binding.bottomPanel.getTop());
        else if (binding.calculateRoutePanel.getRoot().getVisibility() == View.VISIBLE)
            bottomCandidate = Math.min(bottomCandidate, binding.calculateRoutePanel.getRoot().getTop());
        else if (binding.startNavigationPanel.getRoot().getVisibility() == View.VISIBLE)
            bottomCandidate = Math.min(bottomCandidate, binding.startNavigationPanel.getRoot().getTop());
        return Math.max(bottomCandidate, top);
    }

    private int getPanelRight(int insetsLeft) {
        int panelRight = insetsLeft;
        if (binding.topPanel.getVisibility() == View.VISIBLE)
            panelRight = Math.max(panelRight, binding.topPanel.getRight());
        else if (binding.calculateRoutePanel.getRoot().getVisibility() == View.VISIBLE)
            panelRight = Math.max(panelRight, binding.calculateRoutePanel.getRoot().getRight());
        else if (binding.startNavigationPanel.getRoot().getVisibility() == View.VISIBLE)
            panelRight = Math.max(panelRight, binding.startNavigationPanel.getRoot().getRight());
        return panelRight;
    }

    // endregion

    // ITTSPlayerInitializationListener
    @Override
    public void onTTSPlayerInitialized() {
        SoundPlayingService.INSTANCE.setTTSLanguage(MainActivityViewModel.TTS_LANGUAGE);
        runOnUiThread(() -> {
            if (viewModel != null) {
                viewModel.refreshCurrentVoice();
            }
        });
    }

    // ITTSPlayerInitializationListener
    @Override
    public void onTTSPlayerInitializationFailed() {
        SoundPlayingService.INSTANCE.setDefaultHumanVoice();
        runOnUiThread(() -> {
            if (viewModel != null) {
                viewModel.refreshCurrentVoice();
            }
        });
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

