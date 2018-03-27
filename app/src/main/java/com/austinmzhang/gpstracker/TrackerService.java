package com.austinmzhang.gpstracker;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
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

    PendingIntent locationIntent;
    PendingIntent geofenceIntent;

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
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        // Set the update interval to 60 seconds
        mLocationRequest.setInterval(Constants.UPDATE_INTERVAL);
        // Set the fastest update interval to 5 second
        mLocationRequest.setFastestInterval(Constants.FASTEST_INTERVAL);

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

            Intent intent = new Intent(this, GeofenceReceiver.class);
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

        if (this.servicesAvailable && this.mGeofencingClient != null) {
            this.mGeofencingClient.removeGeofences(geofenceIntent);
            this.mGeofencingClient = null;
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

}