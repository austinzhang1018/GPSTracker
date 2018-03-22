package com.austinmzhang.gpstracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.LocationResult;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Austin on 3/21/2018.
 */

public class LocationReceiver extends BroadcastReceiver {
    private LocationResult mLocationResult;

    @Override
    public void onReceive(Context context, Intent intent) {
        // Need to check and grab the Intent's extras like so
        if(LocationResult.hasResult(intent)) {
            this.mLocationResult = LocationResult.extractResult(intent);
            Log.wtf("Called", "onLocationResult");
            if (mLocationResult == null) {
                return;
            }
            pushLocation(mLocationResult.getLastLocation(), context);
            Log.d("LOCATION UPLOADED", mLocationResult.getLastLocation().toString());
        }
    }

    private void pushLocation(final Location location, Context context) {
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
                params.put("name", "austin");
                params.put("longitude", ((Double) location.getLongitude()).toString());
                params.put("latitude", ((Double) location.getLatitude()).toString());
                params.put("accuracy", ((Float) location.getAccuracy()).toString());
                return params;
            }
        };
        // Add the request to the RequestQueue.
        queue.add(stringRequest);

        Log.wtf("Loc", "Pushed");
    }

}