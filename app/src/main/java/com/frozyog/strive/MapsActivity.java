package com.frozyog.strive;

import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
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
        LocationListener,
        OnMapReadyCallback {

    private GoogleApiClient mGoogleApiClient;

    private GoogleMap map;
    private Polyline track;
    private Location location;
    private boolean isTracking = false;

    private double distance = 0.0;
    private final int    ZOOM                = 15;   // map's zoom
    private final long   UPDATE_INTERVAL     = 3000; // min time to ask for new updates, in ms
    private final float  UPDATE_MIN_DISTANCE = 3;    // min distance to ask for location updates, in m
    private final double MIN_DISTANCE        = 10;   // min distance in order to move the map's camera, in m
    private final double MIN_ACCURACY        = 10;   // min accuracy to start accepting lcoation updates, in m


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
                    .addOnConnectionFailedListener(this)
                    .build();
        } else {
            // finish the app
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // connect the client
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        // disconnect the client
        mGoogleApiClient.disconnect();

        super.onStop();
    }



    /**
     *   MENU METHODS
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        // TODO unregister location updates when we're not tracking
        if (item.getItemId() == R.id.toggle) {
            if (isTracking) {
                // stop recording
                isTracking = false;
                item.setIcon(R.drawable.ic_play_black);
            } else {
                // start recording
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
     *   GEO METHODS
     */
    @Override
    public void onMapReady(GoogleMap map) {
        this.map = map;

        // map configuration
        map.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(31, 116)));
        map.animateCamera(CameraUpdateFactory.zoomTo(ZOOM));
        map.setMyLocationEnabled(true);
    }

    @Override
    public void onLocationChanged(Location location) {
        // only consider location updates better than MIN_ACCURACY thresholds
        if (location.getAccuracy() < MIN_ACCURACY) {

            // only consider location updates if we have a previous location
            if (this.location != null) {
                // measure the distance to current location
                float delta = this.location.distanceTo(location);

                // get our LatLng object
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

                // only move the camera if the distance is farther than MIN_DISTANCE
                // TODO only update the map's camera if the user hasn't panned it
                if (delta > MIN_DISTANCE) {
                    map.moveCamera(CameraUpdateFactory.newLatLng(latLng));
                }

                // only perform these actions if the user is tracking
                if (isTracking) {
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
                }
            }

            // update our location
            this.location = location;
        }
    }

    @Override
    public void onConnected(Bundle dataBundle) {
        // start location updates
        LocationRequest mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setSmallestDisplacement(UPDATE_MIN_DISTANCE);
//        mLocationRequest.setFastestInterval(1000);
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);

    }

    @Override
    public void onConnectionSuspended(int i) {
        // Display the connection status
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
