package com.frozyog.strive;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.text.NumberFormat;
import java.util.List;

public class MapsActivity extends FragmentActivity implements
        GoogleMap.OnMyLocationButtonClickListener,
        OnMapReadyCallback {

    private GoogleMap map;
    private Polyline track;
    private double distance = 0.0;
    private Location location;
    private boolean isTracking = false;

    private final int ZOOM = 15;        // map's zoom
    private final double MIN_ACCURACY   = 15;   // min accuracy to start accepting lcoation updates, in m
    private final double MIN_DISTANCE   = 10;   // min distance to pan the map's view, in m



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // initialize MapFragment
        MapFragment mapFragment = (MapFragment) getFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // check for google play services
        if (servicesAvailable()) {
            // create the service and ask for our first update
            startStriveService();
        } else {
            // finish the app, the user should now be prompted for gms install
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onStart();
        // TODO when tapping on the notification, our activity is being restarted
        // TODO if activity was killed but service is still running, should update UI
        // TODO make sure isTracking flag survives app's dismisal
    }

    @Override
    protected void onDestroy() {
        // unregister receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);

        // stop and unbind service if we're not recording anything
        if (!isTracking) {
            stopStriveService();
        }

        super.onDestroy();
    }



    /**
     *      MENU METHODS
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case (R.id.toggle):
                if (isTracking) {

                    // stop recording
                    stopTrackingFromService();
                    isTracking = false;
                    item.setIcon(R.drawable.ic_play_black);
                } else {

                    // start recording
                    startTrackingFromService();
                    isTracking = true;
                    item.setIcon(R.drawable.ic_pause_black);

                    // create our polyline
                    track = map.addPolyline(new PolylineOptions().width(5).color(Color.RED));
                }
                break;
        }

        // update the menu
        return super.onMenuItemSelected(featureId, item);
    }



    /**
     *      SERVICE METHODS
     */
    public void startTrackingFromService() {
        // prevent distance (deltea) to be added when restarting tracking
        this.location = null;

        if (mIsBound) {
            mBoundStriveService.startTracking();
        }
    }

    public void stopTrackingFromService() {
        if (mIsBound) {
            mBoundStriveService.stopTracking();
        }
    }

    public void startStriveService() {
        Log.d("MapsActivity", "startStriveService()");

        // register broadcast receiver for service
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver,
                new IntentFilter(getString(R.string.broadcast_name)));

        // actually start the service
        Intent trackingService = new Intent(getApplicationContext(), StriveService.class);
        startService(trackingService);

        // bind to the service
        doBindService();
    }

    public void stopStriveService() {
        Intent trackingService = new Intent(getApplicationContext(), StriveService.class);
        stopService(trackingService);

        // unbind to the service
        doUnbindService();
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(getString(R.string.broadcast_name))) {
//                Log.d("MapsActivity", "Received location update.");
                // TODO support Parcelable http://stackoverflow.com/questions/20121159/how-to-serialize-for-android-location-class

                // build a Location object
                Location location = new Location(getString(R.string.broadcast_name));
                location.setLatitude(intent.getDoubleExtra("lat", 0));
                location.setLongitude(intent.getDoubleExtra("lng", 0));
                location.setAccuracy(intent.getFloatExtra("accuracy", -1)); // could induce bugs

                // update location
                updateLocationFromService(location);
            }
        }
    };



    /**
     *      LOCATION METHODS
     */
    protected void updateLocationFromService(Location location) {
//        Log.d("MapsActivity", "Processing location update.");

        // compute distance to last location update
        float delta = 0;
        if (this.location != null) {
            delta = this.location.distanceTo(location);
        }

        // build a LatLng object
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        if (isTracking) {
            // draw on the map
            List<LatLng> points = track.getPoints();
            points.add(latLng);
            track.setPoints(points);

            // set the number format
            NumberFormat formatter = NumberFormat.getNumberInstance();
            formatter.setMinimumFractionDigits(2);
            formatter.setMaximumFractionDigits(2);

            // update the distance's TextView
            distance += delta;
            TextView txtDistance = (TextView) findViewById(R.id.txtDistance);
            txtDistance.setText(formatter.format(distance / 1000) + " km.");

            // only move the camera if the distance is farther than MIN_DISTANCE
            // TODO disable auto movement if the user's interacting with the map
            if (delta > MIN_DISTANCE) {
                map.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            }
        } else {
            // update our map
            map.moveCamera(CameraUpdateFactory.newLatLng(latLng));

            // once get a good fix, we can stop listening for location updates
            if (location.getAccuracy() < MIN_ACCURACY) {
                Log.d("MapsActivity", "Accuracy is now " + location.getAccuracy() + "," +
                        " removing location updates since we're not tracking.");
                stopTrackingFromService();
            }
        }

        // update our app's last location
        this.location = location;
    }



    /**
     *      MAP METHODS
     */
    @Override
    public void onMapReady(GoogleMap map) {
        this.map = map;

        // update our map with the last ocation available (in case the background service is still connecting)
        LatLng latLng;
        if (this.location == null) {
            Log.d("MapsActivity", "onMapReady(): LastLocation was null, setting arbitrary point");
            latLng = new LatLng(31, 116); // TODO set it to a more interesting spot
        } else {
            latLng = new LatLng(this.location.getLatitude(), this.location.getLongitude());
        }

        // map configuration
        map.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        map.animateCamera(CameraUpdateFactory.zoomTo(ZOOM));
        map.setMyLocationEnabled(true);
        map.setOnMyLocationButtonClickListener(this);
    }

    @Override
    public boolean onMyLocationButtonClick() {
        // move our Map's Camera when due
        if (this.location == null) {
            Log.d("MapsActivity", "LastLocation was null, should wait for location update");
            Toast.makeText(getBaseContext(), getString(R.string.location_waiting), Toast.LENGTH_SHORT).show();
        } else {
            Log.d("MapsActivity", "Moving map to LastLocation");
            LatLng latLng = new LatLng(this.location.getLatitude(), this.location.getLongitude());
            map.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        }

        return false;
    }



    /**
     *      SERVICES METHODS
     */
    private boolean mIsBound;
    private StriveService mBoundStriveService;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d("MapsActivity", "Service has been bound");
            mBoundStriveService = ((StriveService.LocalBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBoundStriveService = null;
        }
    };

    void doBindService() {
        bindService(new Intent(this, StriveService.class),
                mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    private boolean servicesAvailable() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

        if (resultCode == ConnectionResult.SUCCESS) {
            return true;
        } else {
            GooglePlayServicesUtil.getErrorDialog(resultCode, this, 0).show();
            return false;
        }
    }
}
