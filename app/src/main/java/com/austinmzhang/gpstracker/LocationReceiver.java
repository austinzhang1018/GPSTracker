package com.austinmzhang.gpstracker;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
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
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.HashMap;
import java.util.Map;

import static android.content.Context.BATTERY_SERVICE;

/**
 * Created by Austin on 3/21/2018.
 */

public class LocationReceiver extends BroadcastReceiver {
    private LocationResult mLocationResult;
    private static String name;
    private LocationCallback mLocationCallback;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.wtf("Called", "onReceive");

        if (name == null) {
            name = "testfail";
        }

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        name = sharedPref.getString(context.getString(R.string.name_key), "namenotsaved");

        if (LocationResult.hasResult(intent)) {

            this.mLocationResult = LocationResult.extractResult(intent);

            if (mLocationResult == null) {
                return;
            }

            if (mLocationResult.getLastLocation().getAccuracy() > 100) {
                LocationServices.getFusedLocationProviderClient(context);//.requestLocationUpdate
            }

            int batLevel = -1;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                BatteryManager bm = (BatteryManager)context.getSystemService(BATTERY_SERVICE);
                batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            }

            pushLocation(mLocationResult.getLastLocation(), context, batLevel);
        }


    }




    private void pushLocation(final Location location, Context context, final int batLevel) {
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
                params.put("longitude", ((Double) location.getLongitude()).toString());
                params.put("latitude", ((Double) location.getLatitude()).toString());
                params.put("accuracy", ((Float) location.getAccuracy()).toString());
                params.put("battery", ((Integer)batLevel).toString());
                return params;
            }
        };
        // Add the request to the RequestQueue.
        queue.add(stringRequest);

        Log.wtf("Loc", "Pushed");
    }

}