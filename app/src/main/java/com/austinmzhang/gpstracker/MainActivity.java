package com.austinmzhang.gpstracker;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.HashMap;
import java.util.Map;


public class MainActivity extends AppCompatActivity {

    private FusedLocationProviderClient mFusedLocationClient;
    Location mCurrentLocation;
    Button trackerToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.wtf("Activity", "onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        trackerToggle = findViewById(R.id.trackertoggle);
        if (isMyServiceRunning(TrackerService.class)) {
            trackerToggle.setText(R.string.trackertextstop);
        }
        else {
            trackerToggle.setText(R.string.trackertextstart);
            trackerToggle.setEnabled(false);
        }

        trackerToggle.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isMyServiceRunning(TrackerService.class)) {
                    Log.wtf("RUNNING", "Service was running, terminating now");
                    stopService(new Intent(MainActivity.this, TrackerService.class));
                    trackerToggle.setText(R.string.trackertextstart);
                }
                else {
                    Log.wtf("NOT RUNNING", "Service was not running, launching now");
                    Intent serviceIntent = new Intent(MainActivity.this, TrackerService.class);

                    startService(serviceIntent);
                    trackerToggle.setText(R.string.trackertextstop);
                }
            }
        });

        //Prevent Doze on newer phones

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = this.getPackageName();
            PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                this.startActivity(intent);
            }
        }


        //mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        //getLastLocation();


    }


    @Override
    protected void onDestroy() {
        Log.wtf("Activity", "onDestroy");

        super.onDestroy();
    }

    private void pushLocation(final Location location) {
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(this);
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

        Log.d("Loc", "Pushed");
    }

    private void getLastLocation() {
        //Check Permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("ERROR:", "PERMISSIONS NOT RECEIVED");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        // Got last known location. In some rare situations this can be null.
                        if (location != null) {
                            Log.d("Latitude", ((Double)location.getLatitude()).toString());
                            Log.d("Longitude", ((Double)location.getLongitude()).toString());
                            mCurrentLocation = location;
                            pushLocation(mCurrentLocation);
                        }
                        else {
                            Log.d("NullLoc", "location is null");
                        }
                    }
                });
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
