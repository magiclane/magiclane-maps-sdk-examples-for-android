package com.generalmagic.sdk.examples.routingonmapjava;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.generalmagic.sdk.core.GemError;
import com.generalmagic.sdk.core.GemSurfaceView;
import com.generalmagic.sdk.core.SdkSettings;
import com.generalmagic.sdk.d3scene.Animation;
import com.generalmagic.sdk.d3scene.EAnimation;
import com.generalmagic.sdk.d3scene.ERouteDisplayMode;
import com.generalmagic.sdk.d3scene.MapView;
import com.generalmagic.sdk.examples.R;
import com.generalmagic.sdk.places.Landmark;
import com.generalmagic.sdk.routesandnavigation.Route;
import com.generalmagic.sdk.routesandnavigation.RoutingService;
import com.generalmagic.sdk.util.GemCall;

import java.util.ArrayList;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

////////////////////////////////////////////////////////////////////////////////////////////////////

@SuppressWarnings("ALL")
public class MainActivityJava extends AppCompatActivity {
    ProgressBar progressBar;
    GemSurfaceView gemSurfaceView;

    RoutingService routingService;

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public MainActivityJava() {
        routingService = new RoutingService();

        routingService.setOnStarted(hasProgress -> {
            progressBar.setVisibility(View.VISIBLE);
            return null;
        });

        routingService.setOnCompleted(new Function3<ArrayList<Route>, Integer, String, Unit>() {
            @Override
            public Unit invoke(ArrayList<Route> routes, Integer errorCode, String hint) {
                progressBar.setVisibility(View.GONE);

                switch (errorCode) {
                    case GemError.NoError: {
                        GemCall.INSTANCE.execute(() -> {
                            MapView mapView = gemSurfaceView.getMapView();
                            if (mapView != null) {
                                Animation animation = new Animation(EAnimation.Linear, 1000, null, null);

                                mapView.presentRoutes(routes, null, true,
                                    true, true, true,
                                    true, true, animation, null,
                                    ERouteDisplayMode.Full, null);
                            }

                            return null;
                        });
                        break;
                    }
                    case GemError.Cancel: {
                        // The routing action was cancelled.
                        break;
                    }
                    default: {
                        // There was a problem at computing the routing operation.
                        Toast.makeText(MainActivityJava.this,
                            "Routing service error: ${GemError.getMessage(errorCode)}",
                            Toast.LENGTH_SHORT
                        ).show();
                    }
                }
                return null;
            }
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_java);

        progressBar = findViewById(R.id.progressBar);
        gemSurfaceView = findViewById(R.id.gem_surface);

        SdkSettings.Companion.setOnMapDataReady(isReady -> {
            if (!isReady)
                return null;

            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            calculateRoute();
            return null;
        });

        SdkSettings.Companion.setOnApiTokenRejected(() -> {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the generalmagic.com website, sign up/ sing in and generate one. 
             */
            Toast.makeText(this, "TOKEN REJECTED", Toast.LENGTH_LONG).show();
            return null;
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////


    private void calculateRoute() {
        GemCall.INSTANCE.execute(() -> {
            ArrayList<Landmark> waypoints = new ArrayList<>();
            waypoints.add(new Landmark("London", 51.5073204, -0.1276475));
            waypoints.add(new Landmark("Paris", 48.8566932, 2.3514616));

            routingService.calculateRoute(waypoints, null, false, null, null);
            return 0;
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}

////////////////////////////////////////////////////////////////////////////////////////////////////
