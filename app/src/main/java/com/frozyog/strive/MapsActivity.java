package com.frozyog.strive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
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
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
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
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        GoogleMap.OnMyLocationButtonClickListener,
        LocationListener,
        OnMapReadyCallback {

    private GoogleApiClient mGoogleApiClient;

    private LocalBroadcastManager mLocalBroadcastManager;
    private BroadcastReceiver mReceiver;
    static final String ACTION_UPDATE = "com.example.android.supportv4.UPDATE";

    private GoogleMap map;
    private Polyline track;
    private double distance = 0.0;
    private Location lastLocation;
    private boolean isTracking = false;

    private final int ZOOM = 15;   // map's zoom

    private final double MIN_ACCURACY        = 10;   // min accuracy to start accepting lcoation updates, in m
    private final double MIN_DISTANCE        = 10;   // min distance to pan the map's view, in m
    private final long   UPDATE_INTERVAL     = 3000; // min time to ask for new updates, in ms
    private final float  UPDATE_MIN_DISTANCE = 3;    // min distance to ask for location updates, in m



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
            // build the client
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .build();
        } else {
            // finish the app, the user should now be prompted for gms install
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onStart();

        // connect the client
        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        // disconnect the client
        mGoogleApiClient.disconnect();

        super.onPause();
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
        if (item.getItemId() == R.id.toggle) {
            if (isTracking) {
                // stop recording
                stopTrackingService();
                isTracking = false;
                item.setIcon(R.drawable.ic_play_black);
            } else {
                // start recording
                startTrackingService();
                isTracking = true;
                item.setIcon(R.drawable.ic_pause_black);

                // create our polyline
                track = map.addPolyline(new PolylineOptions().width(5).color(Color.RED));
            }
        }

        // update the menu
        return super.onMenuItemSelected(featureId, item);
    }



    /**
     *      SERVICE METHODS
     */
    public void startTrackingService() {
        // register broadcast receiver
        registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(getString(R.string.broadcast_name))) {
                        // TODO support Parcelable http://stackoverflow.com/questions/20121159/how-to-serialize-for-android-location-class
                        double lat   = intent.getDoubleExtra("lat",  0);
                        double lng   = intent.getDoubleExtra("lng",  0);
                        float  delta = intent.getFloatExtra("delta", 0);
                        Log.d("MapsActivity", "Received location update: " + lat + ", " + lng);
                        updateLocationFromService(new LatLng(lat, lng), delta);
                    }
                }
            }, new IntentFilter(getString(R.string.broadcast_name)));

        // start service
        Intent trackingService = new Intent(getBaseContext(), StriveService.class);
        startService(trackingService);
    }

    public void stopTrackingService() {
        Intent trackingService = new Intent(getBaseContext(), StriveService.class);
        stopService(trackingService);
    }



    /**
     *      LOCATION METHODS
     */
    protected void updateLocationFromService(LatLng latLng, float delta) {
        // draw on the map
        List<LatLng> points = track.getPoints();
        points.add(latLng);
        track.setPoints(points);

        // set the number format
        NumberFormat formatter = NumberFormat.getNumberInstance();
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        // update the distance
        distance += delta;
        TextView txtDistance = (TextView) findViewById(R.id.txtDistance);
        txtDistance.setText(formatter.format(distance/1000) + " km.");

        // only move the camera if the distance is farther than MIN_DISTANCE
        // TODO disable auto movement if the user's interacting with the map
        if (delta > MIN_DISTANCE) {
            map.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d("MapsActivity", "onLocationChanged()");

        // save the Location
        this.lastLocation = location;

        // build our new latLng
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        map.moveCamera(CameraUpdateFactory.newLatLng(latLng));

        // only consider location updates better than MIN_ACCURACY thresholds
        if (location.getAccuracy() < MIN_ACCURACY) {
            // remove location updates
            Log.d("MapsActivity", "Accuracy is now " + location.getAccuracy() + ", removing location updates.");
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);

            // disconnect the client
            mGoogleApiClient.disconnect();
        }
    }



    /**
     *      MAP METHODS
     */
    @Override
    public void onMapReady(GoogleMap map) {
        this.map = map;

        // get our last location
        lastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        LatLng latLng;
        if (lastLocation != null) {
            latLng = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
        } else {
            Log.d("MapsActivity", "LastLocation was null, setting arbitrary point");
            latLng = new LatLng(31, 116);
        }

        // map configuration
        map.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        map.animateCamera(CameraUpdateFactory.zoomTo(ZOOM));
        map.setMyLocationEnabled(true);
        map.setOnMyLocationButtonClickListener(this);
    }

    @Override
    public boolean onMyLocationButtonClick() {
        // move our Map's Camera where due
        if (lastLocation == null) {
            Log.d("MapsActivity", "LastLocation was null, should wait for location update");
            Toast.makeText(getBaseContext(), getString(R.string.location_waiting), Toast.LENGTH_SHORT).show();
        } else {
            Log.d("MapsActivity", "Moving map to LastLocation");
            LatLng latLng = new LatLng(lastLocation .getLatitude(), lastLocation .getLongitude());
            map.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        }

        return false;
    }



    /**
     *      SERVICES METHODS
     */
    @Override
    public void onConnected(Bundle dataBundle) {
        // start location updates
        LocationRequest mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setSmallestDisplacement(UPDATE_MIN_DISTANCE);
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Toast.makeText(this, "Connection has been suspended", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Toast.makeText(this, "Connection has failed", Toast.LENGTH_SHORT).show();
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
