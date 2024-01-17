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

// -------------------------------------------------------------------------------------------------

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.magiclane.sdk.core.GemError;
import com.magiclane.sdk.core.GemSurfaceView;
import com.magiclane.sdk.core.SdkSettings;
import com.magiclane.sdk.d3scene.Animation;
import com.magiclane.sdk.d3scene.EAnimation;
import com.magiclane.sdk.d3scene.ERouteDisplayMode;
import com.magiclane.sdk.d3scene.MapView;
import com.magiclane.sdk.places.Landmark;
import com.magiclane.sdk.routesandnavigation.Route;
import com.magiclane.sdk.routesandnavigation.RoutingService;
import com.magiclane.sdk.util.GemCall;
import com.magiclane.sdk.util.Util;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;

// -------------------------------------------------------------------------------------------------

@SuppressWarnings("ALL")
public class MainActivityJava extends AppCompatActivity
{
    private ProgressBar progressBar;
    private GemSurfaceView gemSurfaceView;
    private RoutingService routingService;
    private ArrayList<Route> routesList = new ArrayList<Route>();

    // ---------------------------------------------------------------------------------------------

    public MainActivityJava()
    {
        routingService = new RoutingService();

        routingService.setOnStarted(hasProgress ->
        {
            progressBar.setVisibility(View.VISIBLE);
            return null;
        });

        routingService.setOnCompleted((routes, errorCode, hint) ->
        {
            progressBar.setVisibility(View.GONE);

            switch (errorCode)
            {
                case GemError.NoError:
                {
                    routesList = routes;

                    GemCall.INSTANCE.execute(() ->
                    {
                        MapView mapView = gemSurfaceView.getMapView();
                        if (mapView != null)
                        {
                            Animation animation = new Animation(EAnimation.Linear, 1000, null, null);

                            mapView.presentRoutes(routes, null, true,
                                true, true, true,
                                true, true, animation,
                                ERouteDisplayMode.Full, null);
                        }

                        return null;
                    });
                    break;
                }
                case GemError.Cancel:
                {
                    // The routing action was cancelled.
                    break;
                }
                default:
                {
                    // There was a problem at computing the routing operation.
                    showDialog("Routing service error: ${GemError.getMessage(errorCode)}");
                }
            }
            return null;
        });
    }

    // ---------------------------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_java);

        progressBar = findViewById(R.id.progressBar);
        gemSurfaceView = findViewById(R.id.gem_surface);
        
        SdkSettings.INSTANCE.setOnMapDataReady(isReady ->
        {
            if (!isReady)
                return null;

            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            calculateRoute();

            // onTouch event callback
            gemSurfaceView.getMapView().setOnTouch((xy -> 
            {
                // xy are the coordinates of the touch event
                GemCall.INSTANCE.execute(() ->
                {
                    // tell the map view where the touch event happened
                   gemSurfaceView.getMapView().setCursorScreenPosition(xy);

                    // get the visible routes at the touch event point
                   ArrayList<Route> routes = gemSurfaceView.getMapView().getCursorSelectionRoutes();

                    // check if there is any route
                   if (routes != null && !routes.isEmpty())
                   {
                       // set the touched route as the main route and center on it
                       Route route = routes.get(0);
                       
                       gemSurfaceView.getMapView().getPreferences().getRoutes().setMainRoute(route);
                       gemSurfaceView.getMapView().centerOnRoutes(routesList, ERouteDisplayMode.Full, null, new Animation(EAnimation.Linear, null, null, null));
                   }
                   
                   return 0;
                });

                return null;
            }));
            
            return null;
        });

        SdkSettings.INSTANCE.setOnApiTokenRejected(() ->
        {
            /* 
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one. 
             */
            showDialog("TOKEN REJECTED");
            return null;
        });
        
        if (!Util.INSTANCE.isInternetConnected(this))
        {
            showDialog("You must be connected to the internet!");
        }
    }

    // ---------------------------------------------------------------------------------------------


    private void calculateRoute()
    {
        GemCall.INSTANCE.execute(() ->
        {
            ArrayList<Landmark> waypoints = new ArrayList<>();
            waypoints.add(new Landmark("London", 51.5073204, -0.1276475));
            waypoints.add(new Landmark("Paris", 48.8566932, 2.3514616));

            routingService.calculateRoute(waypoints, null, false, null, null, null);
            return 0;
        });
    }

    // ---------------------------------------------------------------------------------------------
    
    private void showDialog(String text)
    {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        
        View view = getLayoutInflater().inflate(R.layout.dialog_layout, null);
        
        TextView title = view.findViewById(R.id.title);
        TextView message = view.findViewById(R.id.message);
        Button button = view.findViewById(R.id.button);
        
        title.setText(getString(R.string.error));
        message.setText(text);
        button.setOnClickListener(v -> dialog.dismiss());
        
        dialog.setCancelable(false);
        dialog.setContentView(view);
        dialog.show();
    }
    
    // ---------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------
