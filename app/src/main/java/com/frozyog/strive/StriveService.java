package com.frozyog.strive;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
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

    private GoogleApiClient mGoogleApiClient;

    private final long   UPDATE_INTERVAL     = 3000; // min time to ask for new updates, in ms
    private final float  UPDATE_MIN_DISTANCE = 3;    // min distance to ask for location updates, in m

    public StriveService() { }



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
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
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
        // start service with notification on the foreground
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.tracking));
        Intent notificationIntent   = new Intent(this, MapsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        mBuilder.setContentIntent(pendingIntent);

        startForeground(1, mBuilder.build());

        // start location updates
        LocationRequest mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setSmallestDisplacement(UPDATE_MIN_DISTANCE);
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, new MyLocationListener());
    }

    @Override
    public void onConnectionSuspended(int i) {
        // TODO show dialog stating it's impossible to track gps
        Log.w("StriveService", "onConnectionSuspended: " + i);
        Toast.makeText(this, "Connection has been suspended", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        // TODO show dialog stating it's impossible to track gps
        Log.e("StriveService", "onConnectionFailed: " + connectionResult);
        Toast.makeText(this, "Connection has failed", Toast.LENGTH_SHORT).show();
    }



    /**
     *      LOCATION LISTENER CLASS
     */
    public class MyLocationListener implements LocationListener {

        private Location location;
        private final double MIN_ACCURACY = 10;   // min accuracy to start accepting lcoation updates, in m

        @Override
        public void onLocationChanged(Location location) {
            Log.i("StriveService", "Location updated with accuracy " + location.getAccuracy());
            // only consider location updates better than MIN_ACCURACY thresholds
            if (location.getAccuracy() < MIN_ACCURACY) {

                // measure the distance of update to previous location
                float delta = 0;
                if (this.location != null) {
                    delta = this.location.distanceTo(location);
                }

                // send the broadcast message
                Intent update = new Intent(getString(R.string.broadcast_name));
                update.putExtra("lat",   location.getLatitude());
                update.putExtra("lng",   location.getLongitude());
                update.putExtra("delta", delta);
                sendBroadcast(update);

                // update our location
                this.location = location;
            }
        }
    }
}
