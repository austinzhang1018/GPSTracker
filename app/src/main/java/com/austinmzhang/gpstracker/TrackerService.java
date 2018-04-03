package com.austinmzhang.gpstracker;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
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
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

//TODO: ADD SCHOOL TO THE LIST OF GEOFENCES
public class TrackerService extends Service {

    IBinder mBinder = new LocalBinder();

    private FusedLocationProviderClient mFusedLocationClient;
    private PowerManager.WakeLock mWakeLock;
    private LocationRequest mLocationRequest;

    //Declare geofencing client
    private GeofencingClient mGeofencingClient;
    private List<Geofence> mGeofenceList;

    // Flag that indicates if a request is underway.
    private boolean mInProgress;

    //Flag that indicates whether google play services is available
    private Boolean servicesAvailable = false;

    PendingIntent geofenceIntent;

    private LocationCallback mLocationCallback;

    private LocationCallback mExtraLocationCallback;

    LocationRequest mExtraLocationRequests;

    private String name;

    private int locationRequestCounter;

    private BroadcastReceiver geofenceReceiver;


    //binds service to stuff (need to figure out what exactly this does)
    public class LocalBinder extends Binder {
        public TrackerService getServerInstance() {
            return TrackerService.this;
        }
    }


    //Creates LocationRequest, sets priority of location request, sets the update interval and fastest update interval
    @Override
    public void onCreate() {

        locationRequestCounter = 0;

        Log.wtf("Called", "onCreate");

        super.onCreate();

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        name = sharedPref.getString(getApplicationContext().getString(R.string.name_key), "namenotsaved");

        mInProgress = false;
        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create();
        // Use high accuracy
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);//LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        // Set the update interval to 60 seconds
        mLocationRequest.setInterval(Constants.UPDATE_INTERVAL);
        // Set the fastest update interval to 5 second
        mLocationRequest.setFastestInterval(Constants.FASTEST_INTERVAL);

        mExtraLocationRequests = LocationRequest.create();
        mExtraLocationRequests.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mExtraLocationRequests.setInterval(Constants.UPDATE_INTERVAL_CALIBRATING);
        mExtraLocationRequests.setFastestInterval(Constants.UPDATE_INTERVAL_CALIBRATING);

        servicesAvailable = servicesConnected();

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
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification = new Notification.Builder(this)
                .setContentTitle("GPS Service")
                .setContentText("running...")
                .setContentIntent(pendingIntent)
                .setTicker("Tracker")
                .setPriority(Notification.PRIORITY_HIGH)
                .build();

        startForeground(1, notification);

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

    private void setUpLocationClientIfNeeded() {
        if (geofenceReceiver == null) {
            geofenceReceiver = new BroadcastReceiver() {
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
                    } else {
                        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);//PRIORITY_BALANCED_POWER_ACCURACY);
                        // Set the update interval to 60 seconds
                        mLocationRequest.setInterval(Constants.UPDATE_INTERVAL);
                        // Set the fastest update interval to 5 second
                        mLocationRequest.setFastestInterval(Constants.FASTEST_INTERVAL);
                    }

                    mFusedLocationClient.removeLocationUpdates(mLocationCallback);
                    mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);  //** This line changes
                }
            };

            IntentFilter filter = new IntentFilter("GEOFENCE_ALERT");
            registerReceiver(geofenceReceiver, filter);
            Log.wtf("RECEIVER: ", "REGISTERED");
        }

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {

                Log.wtf("CALLEDLOCATIONCALLBACK", "LOCATIONCALLBACK");
                if (locationResult == null) {
                    return;
                }

                if (locationResult.getLastLocation().getAccuracy() < 100) {
                    int batLevel = -1;

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        BatteryManager bm = (BatteryManager) getApplicationContext().getSystemService(BATTERY_SERVICE);
                        batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                    }

                    pushLocation(locationResult.getLastLocation(), getApplicationContext(), batLevel);
                } else {
                    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    Log.wtf("WASN'T ACCURATE ENOUGH", "CALLING EXTRALOCATIONCALLBACK");
                    mFusedLocationClient.requestLocationUpdates(mExtraLocationRequests, mExtraLocationCallback, null);
                }
            }
        };

        mExtraLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Log.wtf("EXTRALOCATIONCALLBACK", "CALLED");
                Log.wtf("EXTRALOCATIONCALLBACK", Integer.toString(locationRequestCounter));


                if (locationResult == null) {
                    return;
                }

                if (locationResult.getLastLocation().getAccuracy() < 100) {
                    int batLevel = -1;

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        BatteryManager bm = (BatteryManager) getApplicationContext().getSystemService(BATTERY_SERVICE);
                        batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                    }

                    pushLocation(locationResult.getLastLocation(), getApplicationContext(), batLevel);

                    mFusedLocationClient.removeLocationUpdates(mExtraLocationCallback);
                    locationRequestCounter = 0;
                    Log.wtf("EXTRALOCATIONCALLBACK", "ACCURATE ENOUGH PUSHING");
                } else {
                    locationRequestCounter++;
                    Log.wtf("EXTRALOCATIONCALLBACK", "NOT ACCURATE ENOUGH");

                }

                if (locationRequestCounter > 5) {
                    mFusedLocationClient.removeLocationUpdates(mExtraLocationCallback);
                    locationRequestCounter = 0;
                    Log.wtf("EXTRALOCATIONCALLBACK", "MAX_TRIES_REACHED_ABORT");
                }
            }
        };


        Log.wtf("Called", "setUpLocationClientIfNeeded");


        if (mFusedLocationClient == null) {
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.d("NEEDS PERMISSION", "LOCATION");
                return;
            }


            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);  //** This line changes
        }

        if (mGeofencingClient == null) {
            //setup geofence client
            mGeofencingClient = LocationServices.getGeofencingClient(this);

            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            mGeofenceList = parseGeofenceInput(sharedPref.getString(getString(R.string.geofence_string_key), null));


            if (mGeofenceList == null || mGeofenceList.size() == 0) {
                return;
            }

            GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
            builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
            builder.addGeofences(mGeofenceList);
            GeofencingRequest geoRequest = builder.build();

            Intent intent = new Intent();
            intent.setAction("GEOFENCE_ALERT");
            geofenceIntent = PendingIntent.getBroadcast(this, 12345, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            mGeofencingClient.addGeofences(geoRequest, geofenceIntent);
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

    //
    @Override
    public void onDestroy() {
        // Turn off the request flag
        this.mInProgress = false;

        unregisterReceiver(geofenceReceiver);


        if (this.servicesAvailable && this.mFusedLocationClient != null) {
            this.mFusedLocationClient.flushLocations();
            this.mFusedLocationClient.removeLocationUpdates(mLocationCallback);
            // Destroy the current location client
            this.mFusedLocationClient = null;
        }

        if (this.servicesAvailable && this.mGeofencingClient != null) {
            this.mGeofencingClient.removeGeofences(geofenceIntent);
            this.mGeofencingClient = null;
            geofenceReceiver = null;
        }
        // Display the connection status
        // Toast.makeText(this, DateFormat.getDateTimeInstance().format(new Date()) + ":
        // Disconnected. Please re-connect.", Toast.LENGTH_SHORT).show();

        //Let go of the cpu
        if (this.mWakeLock != null) {
            this.mWakeLock.release();
            this.mWakeLock = null;
        }

        Log.wtf("SERVICE", "DESTROYED");

        super.onDestroy();
    }

    private static List<Geofence> parseGeofenceInput(String geofenceInput) {
        if (geofenceInput == null) {
            Log.wtf("GEOFENCESTRINGINPUT", "INPUT WAS NULL, GEOFENCE DATA NEVER PASSED TO SERVICE");
            return null;
        }

        Scanner scanner = new Scanner(geofenceInput);

        List<Geofence> geofenceList = new ArrayList<Geofence>();

        while (scanner.hasNext()) {
            String geofenceName = scanner.next();
            double latitude = Double.parseDouble(scanner.next());
            double longitude = Double.parseDouble(scanner.next());
            float radius = Float.parseFloat(scanner.next());

            geofenceList.add(new Geofence.Builder()
                    .setRequestId(geofenceName)
                    .setCircularRegion(latitude, longitude, radius)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT | Geofence.GEOFENCE_TRANSITION_ENTER)
                    .build()
            );

            if (scanner.hasNext()) {
                if (!scanner.next().equals(",")) {
                    //User didn't give the correct format if the next character isn't a ,
                    return null;
                }
            }
        }

        geofenceList.add(new Geofence.Builder()
                .setRequestId("Columbus_Academy")
                .setCircularRegion(40.049676, -82.871829, 650)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT | Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        );

        return geofenceList;
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
                params.put("battery", ((Integer) batLevel).toString());
                return params;
            }
        };
        // Add the request to the RequestQueue.
        queue.add(stringRequest);

        Log.wtf("Loc", "Pushed");
    }


}