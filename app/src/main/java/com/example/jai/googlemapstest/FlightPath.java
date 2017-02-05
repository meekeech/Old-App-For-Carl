package com.example.jai.googlemapstest;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import android.os.Handler;

import java.util.ArrayList;

public class FlightPath extends AppCompatActivity {

    private GoogleMap mMap;
    protected Marker point;
    final Handler handler = new Handler();
    int i = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flight_path);
        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        setUpMapIfNeeded();
        createGraph();
    }

    private void setUpMapIfNeeded() {
        // Confirm map is not already instantiated
        if (mMap == null) {
            // Attempt to obtain map from SupportMapFragment
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    private void setUpMap() {
        mMap.getUiSettings().setMapToolbarEnabled(true);
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        zoomCameraToLocation();
    }

    public void zoomCameraToLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        String provider = locationManager.getBestProvider(criteria, false);
        Location myLocation = locationManager.getLastKnownLocation(provider);

        double latitude;
        double longitude;

        if (myLocation == null) {
            //locationManager.requestLocationUpdates(provider, 1000, 0, );
            latitude = 0;
            longitude = 0;
        } else {
            latitude = myLocation.getLatitude();
            longitude = myLocation.getLongitude();
        }

        LatLng currentLocation = new LatLng(latitude, longitude);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(currentLocation));
        mMap.moveCamera(CameraUpdateFactory.zoomTo(14));
    }

    protected void createGraph() {
        Intent intent = getIntent();

        final ArrayList<LatLng> waypoints = intent.getParcelableArrayListExtra("WAYPOINT_ID");
        final ArrayList<Double> heights = (ArrayList<Double>) intent.getSerializableExtra("HEIGHT_ID");
        final ArrayList<Double> speeds = (ArrayList<Double>) intent.getSerializableExtra("SPEED_ID");
        final int droppedCount = intent.getIntExtra("DROPPED_COUNT", 0);

        final Bitmap wp = BitmapFactory.decodeResource(getResources(), R.drawable.ic_wp);
        final Bitmap wpHalfSize = Bitmap.createScaledBitmap(wp, wp.getWidth() / 2, wp.getHeight() / 2, false);

        for (int i =0; i < waypoints.size(); i++) {
            double waypointlat = waypoints.get(i).latitude;
            double waypointlong = waypoints.get(i).longitude;
            LatLng waypoint = new LatLng(waypointlat, waypointlong);
            double speed = speeds.get(i);
            double height = heights.get(i);
            if (i == droppedCount && droppedCount != 0) {
                point = mMap.addMarker(new MarkerOptions()
                        .position(waypoint)
                        .anchor(0.5f, 0.5f)
                        .title(Double.toString(height) + " " + Double.toString(speed)));
            }
            point = mMap.addMarker(new MarkerOptions()
                    .position(waypoint)
                    .icon(BitmapDescriptorFactory.fromBitmap(wpHalfSize))
                    .anchor(0.5f, 0.5f)
                    .title(Double.toString(height)));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
    //////////////////////
}
