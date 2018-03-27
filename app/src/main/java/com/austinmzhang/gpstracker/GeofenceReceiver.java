package com.austinmzhang.gpstracker;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Austin on 3/23/2018.
 */

public class GeofenceReceiver extends BroadcastReceiver {

    private static String name;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.wtf("Called onReceive", "GEO");

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        name = sharedPref.getString(context.getString(R.string.name_key), "namenotsaved");

        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);

        if (!GeofencingEvent.fromIntent(intent).hasError() && geofencingEvent.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_EXIT) {
            pushGeofence(context, geofencingEvent);
            Log.wtf("GEO", "exit");
            alterLocationSettings(context, true);
        }

        if (!GeofencingEvent.fromIntent(intent).hasError() && geofencingEvent.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_ENTER) {
            //pushGeofence(context, geofencingEvent);
            Log.wtf("GEO", "enter");
            alterLocationSettings(context, false);
        }
    }


    private void pushGeofence(Context context, final GeofencingEvent geofencingEvent) {
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(context);
        String url = "http://24.208.163.239:37896/trackers";
        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
            }
        }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<String, String>();
                params.put("name", name);
                params.put("triggeredgeofence", geofencingEvent.getTriggeringGeofences().get(0).getRequestId());
                return params;
            }
        };
        // Add the request to the RequestQueue.
        queue.add(stringRequest);

        Log.wtf("Geo", "Pushed");
    }

    private void alterLocationSettings(Context context, boolean precise) {
        FusedLocationProviderClient mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("NEEDS PERMISSION", "LOCATION");
            return;
        }

        LocationRequest mLocationRequest;

        mLocationRequest = LocationRequest.create();
        // Use high accuracy
        if (precise) {
            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);  // Set the update interval to 60 seconds
            mLocationRequest.setInterval(Constants.UPDATE_INTERVAL_TRACKING);
            // Set the fastest update interval to 5 second
            mLocationRequest.setFastestInterval(Constants.FASTEST_INTERVAL_TRACKING);
        }
        else {
            mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
            // Set the update interval to 60 seconds
            mLocationRequest.setInterval(Constants.UPDATE_INTERVAL);
            // Set the fastest update interval to 5 second
            mLocationRequest.setFastestInterval(Constants.FASTEST_INTERVAL);
        }

        Intent intent = new Intent(context, LocationReceiver.class);
        PendingIntent locationIntent = PendingIntent.getBroadcast(context, 54321, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        mFusedLocationClient.removeLocationUpdates(locationIntent);
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, locationIntent);  //** This line changes
    }
}
