package com.austinmzhang.gpstracker;

import android.Manifest;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class TrackerService extends Service {

    IBinder mBinder = new LocalBinder();

    private FusedLocationProviderClient mFusedLocationClient;
    private PowerManager.WakeLock mWakeLock;
    private LocationRequest mLocationRequest;
    //private LocationCallback mLocationCallback;

    // Flag that indicates if a request is underway.
    private boolean mInProgress;

    //Flag that indicates whether google play servies is available
    private Boolean servicesAvailable = false;

    PendingIntent locationIntent;

    String name;

    //binds service to stuff (need to figure out what exactly this does)
    public class LocalBinder extends Binder {
        public TrackerService getServerInstance() {
            return TrackerService.this;
        }
    }


    //Creates LocaitonRequest, sets priority of location request, sets the update interval and fastest update interval
    @Override
    public void onCreate() {
        Log.wtf("Called", "onCreate");

        super.onCreate();

        mInProgress = false;
        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create();
        // Use high accuracy
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        // Set the update interval to 60 seconds
        mLocationRequest.setInterval(Constants.UPDATE_INTERVAL);
        // Set the fastest update interval to 5 second
        mLocationRequest.setFastestInterval(Constants.FASTEST_INTERVAL);

        servicesAvailable = servicesConnected();

        /*mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Log.wtf("Called", "onLocationResult");
                if (locationResult == null) {
                    return;
                }
                pushLocation(locationResult.getLastLocation());
                Log.d("LOCATION UPLOADED", locationResult.getLastLocation().toString());
            }

        };

        /*
         * Create a new location client, using the enclosing class to
         * handle callbacks.
         */

        setUpLocationClientIfNeeded();

    }

    //Checks to see if google play services are connected
    private boolean servicesConnected() {
        Log.wtf("Called", "servicesConnected");

        // Check that Google Play services is available
        int resultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);

        // If Google Play services is available
        if (ConnectionResult.SUCCESS == resultCode) {

            return true;
        } else {

            return false;
        }
    }


    //Sets up the wakelock (keeps the cpu going when the screen is off) and sets up the location client if it wasn't already setup
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.wtf("Called", "onStartCommand");

        super.onStartCommand(intent, flags, startId);

        PowerManager mgr = (PowerManager) getSystemService(Context.POWER_SERVICE);



        /*
        WakeLock is reference counted so we don't want to create multiple WakeLocks. So do a check before initializing and acquiring.
        This will fix the "java.lang.Exception: WakeLock finalized while still held: MyWakeLock" error that you may find.
        */

        if (this.mWakeLock == null) { //**Added this
            this.mWakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakeLock");
        }

        if (!this.mWakeLock.isHeld()) { //**Added this
            this.mWakeLock.acquire();
        }


        if (!servicesAvailable || mInProgress)
            return START_STICKY;

        setUpLocationClientIfNeeded();
        if (!mInProgress) {
            mInProgress = true;
        }

        return START_STICKY;
    }

    //Sets up location client if it hasn't already been setup
    private void setUpLocationClientIfNeeded() {
        Log.wtf("Called", "setUpLocationClientIfNeeded");

        if (mFusedLocationClient == null) {
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.d("NEEDS PERMISSION", "LOCATION");
                return;
            }
            Intent intent = new Intent(this, LocationReceiver.class);
            locationIntent = PendingIntent.getBroadcast(this, 54321, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, locationIntent);  //** This line changes
        }
    }

    //Binder stuff??
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    //gets current time
    public String getTime() {
        SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return mDateFormat.format(new Date());
    }

    //Used for logging
    public void appendLog(String text, String filename) {
        File logFile = new File(filename);
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(text);
            buf.newLine();
            buf.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    //
    @Override
    public void onDestroy() {
        // Turn off the request flag
        this.mInProgress = false;

        if (this.servicesAvailable && this.mFusedLocationClient != null) {
            this.mFusedLocationClient.flushLocations();
            this.mFusedLocationClient.removeLocationUpdates(locationIntent);
            // Destroy the current location client
            this.mFusedLocationClient = null;
        }
        // Display the connection status
        // Toast.makeText(this, DateFormat.getDateTimeInstance().format(new Date()) + ":
        // Disconnected. Please re-connect.", Toast.LENGTH_SHORT).show();

        //Let go of the cpu
        if (this.mWakeLock != null) {
            this.mWakeLock.release();
            this.mWakeLock = null;
        }

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

//        Log.d("Loc", "Pushed");
    }




}