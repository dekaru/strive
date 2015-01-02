package com.frozyog.strive;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

public class StriveService extends Service implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private boolean isTracking = false;
    private GoogleApiClient mGoogleApiClient;
    private final long   UPDATE_INTERVAL     = 3000; // min time to ask for new updates, in ms
    private final float  UPDATE_MIN_DISTANCE = 0;    // min distance to ask for location updates, in m
    private final int    NOTIFICATION_ID = 655321;   // min distance to ask for location updates, in m



    public StriveService() {}

    public class LocalBinder extends Binder {
        StriveService getService() {
            return StriveService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    public IBinder onBind(Intent intent) {
        return mBinder;
    }



    @Override
    public void onCreate() {
        Log.d("StriveService", "onCreate()");
        super.onCreate();

        // build the client
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("StriveService", "onStartCommand()");

        // connect the services
        mGoogleApiClient.connect();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        // disconnect client
        mGoogleApiClient.disconnect();

        super.onDestroy();
    }



    /**
     *      GEO METHODS
     */
    @Override
    public void onConnected(Bundle dataBundle) {
        Log.d("StriveService", "onConnected()");

        // start location updates for initial location update
        registerLocationUpdates();

        if (isTracking) {
            // start service with notification on the foreground
            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.tracking));
            Intent notificationIntent   = new Intent(this, MapsActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
            mBuilder.setContentIntent(pendingIntent);
            startForeground(NOTIFICATION_ID, mBuilder.build());
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        // TODO show dialog stating it's impossible to track gps
        // TODO recover if isTracking=true
        Log.w("StriveService", "onConnectionSuspended: " + i);
        Toast.makeText(this, "Connection has been suspended", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        // TODO show dialog stating it's impossible to track gps
        // TODO recover if isTracking=true
        Log.e("StriveService", "onConnectionFailed: " + connectionResult);
        Toast.makeText(this, "Connection has failed", Toast.LENGTH_SHORT).show();
    }

    public void startTracking() {
        Log.d("StriveService", "startTracking()");

        isTracking = true;

        // start listening for location updates
        mGoogleApiClient.connect();
    }

    public void stopTracking() {
        Log.d("StriveService", "stopTracking()");

        isTracking = false;

        // hide notification
        stopForeground(true);

        // unregister location updates
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, mLocationListener);

        // disconnect client
        mGoogleApiClient.disconnect();
    }



    /**
     *      LOCATION LISTENER CLASS
     */
    private MyLocationListener mLocationListener;

    public void registerLocationUpdates() {
        mLocationListener = new MyLocationListener();
        LocationRequest mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setSmallestDisplacement(UPDATE_MIN_DISTANCE);
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, mLocationListener);
    }

    public class MyLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location location) {
            Log.i("StriveService", "Location updated with accuracy " + location.getAccuracy());

            // send the broadcast message
            Intent update = new Intent(getString(R.string.broadcast_name));
            update.putExtra("lat",   location.getLatitude());
            update.putExtra("lng",   location.getLongitude());
            update.putExtra("accuracy", location.getAccuracy());
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(update);
        }
    }
}
