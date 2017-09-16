package spasumarthi.transparentprice;

import android.Manifest;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;



import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;


import java.io.IOException;
import java.util.List;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/*
 * The class uses multiple AsyncActivities which is not great coding practice
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private Button calc;
    private TextView finalPrice;
    private EditText netPrice, salesTax , tipRate;
    private Switch locationChecker;
    private ProgressBar progressBar2;
    GoogleApiClient mGoogleApiClient;
    private FusedLocationProviderClient mFusedLocationClient;
    LocationRequest mLocationRequest;
    int REQUEST_CHECK_SETTINGS = 50;
    Location mLastLocation;
    private static final String TAG = "MAIN_ACTIVITY_ASYNC";
    static String API_KEY = "?country=US&postalCode=94539";
    static final String API_URL = "https://sandbox-rest.avatax.com/api/v2/taxrates/bypostalcode";
    String countryCode;
    String postalCode;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        calc = (Button) findViewById(R.id.calculate);
        netPrice = (EditText) findViewById(R.id.inputPrice);
        salesTax = (EditText) findViewById(R.id.inputTax);
        tipRate = (EditText) findViewById(R.id.inputTip);
        finalPrice = (TextView) findViewById(R.id.finalPrice);
        progressBar2 = (ProgressBar)findViewById(R.id.progressBar1);
        progressBar2.setVisibility(View.INVISIBLE);
        locationChecker = (Switch)findViewById(R.id.taxSwitch);

        //activates the Play services connection
        setUpClient();

        if (mGoogleApiClient!=null){
            mGoogleApiClient.connect();
        }

        //setup for location
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationChecker.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    //this chain will create a tax rate based on location
                    createLocationRequest();
                } else {
                    // do nothing here because user fills in
                    salesTax.setText("");
                }
            }
        });

        calc.setOnClickListener(this);

    }


    // operates when the calculate button is pressed
    @Override
    public void onClick (View view){

        //check to protect against invalid field
        if (netPrice.getText().toString().matches("")||salesTax.getText().toString().matches("")
                ||tipRate.getText().toString().matches("")){
            finalPrice.setText("Please enter values for all fields");
        } else{
            String loadedPrice = netPrice.getText().toString();
            String loadedTax = salesTax.getText().toString();
            String loadedTip = tipRate.getText().toString();


            //does all the calculations
            double netTax = Double.parseDouble(loadedPrice) * Double.parseDouble(loadedTax);
            double subtotal = netTax+Double.parseDouble(loadedPrice);
            double netTip = subtotal * Double.parseDouble(loadedTip);
            double total = subtotal+netTip;
            finalPrice.setText(String.valueOf(total));
        }
    }



    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // An unresolvable error has occurred and a connection to Google APIs
        // could not be established. Display an error message, or handle
        // the failure silently
        finalPrice.setText("Please modify location services");
    }

    //makes the call to google API
    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        //configures location settings
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().setAlwaysShow(true)
                .addLocationRequest(mLocationRequest);
        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient,
                        builder.build());

        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
                final LocationSettingsStates states = result.getLocationSettingsStates();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        getCurrentLocation();
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied, but this can be fixed
                        // by showing the user a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            status.startResolutionForResult(
                                    MainActivity.this,
                                    REQUEST_CHECK_SETTINGS);
                        } catch (IntentSender.SendIntentException e) {
                            // Ignore the error.
                            finalPrice.setText("settings exception");
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way
                        // to fix the settings so we won't show the dialog.
                        finalPrice.setText("settings error");
                        break;
                }

            }
        });
    }

    //handles the result
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == RESULT_OK) {
                getCurrentLocation();
            }
        }
    }

    //makes the proper checks and retrieves location
    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        if (mLastLocation != null) {
            new GeocodeAsyncTask().execute();
        } else {
            startLocationUpdates();
        }
    }

    //configures the googleApi client
    private void setUpClient(){
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .enableAutoManage(this, this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    //this method will be called if there is no stored previous location
    protected void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,mLocationRequest,this);
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        if (mLastLocation!=null){
            new GeocodeAsyncTask().execute();
        } else{
            finalPrice.setText("try again");
        }

    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mGoogleApiClient.isConnected()) {
            startLocationUpdates();
        }else{
            mGoogleApiClient.connect();
        }
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, MainActivity.this);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            mLastLocation = location;
            stopLocationUpdates();
        } else {
           finalPrice.setText("Location was not found");
        }
    }

    //this class translates longitude and latitude to actual address
    class GeocodeAsyncTask extends AsyncTask<Void, Void, android.location.Address> {

        String errorMessage = "";

        @Override
        protected void onPreExecute(){
            progressBar2.setVisibility(View.VISIBLE);
        }

        @Override
        protected android.location.Address doInBackground(Void ... none) {
            Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
            List<android.location.Address> addresses = null;

            double latitude = mLastLocation.getLatitude();
            double longitude = mLastLocation.getLongitude();
            try {
                addresses = geocoder.getFromLocation(latitude, longitude, 1);
            } catch (IOException ioException) {
                errorMessage = "Service Not Available";
                Log.e(TAG, errorMessage, ioException);
            } catch (IllegalArgumentException illegalArgumentException) {
                errorMessage = "Invalid Latitude or Longitude Used";
                Log.e(TAG, errorMessage + ". " +
                        "Latitude = " + latitude + ", Longitude = " +
                        longitude, illegalArgumentException);
            }

            if(addresses != null && addresses.size() > 0) {
                return addresses.get(0);
            }
            return null;
        }

        protected void onPostExecute(android.location.Address address) {
            if(address == null) {
                progressBar2.setVisibility(View.INVISIBLE);
                finalPrice.setText(errorMessage);
            }
            else{
                countryCode=address.getCountryCode();
                postalCode = address.getPostalCode();
                new RetrieveFeedTask().execute();
            }
        }

    }

    //this class makes the call to free TaxRates API
    class RetrieveFeedTask extends AsyncTask<Void, Void, String> {

        //adjusts API call appropriately
        protected void onPreExecute() {
            API_KEY= "?country="+countryCode+"&postalCode="+postalCode;
        }


        protected String doInBackground(Void... urls) {

            // Do some validation here

            try {
                OkHttpClient client = new OkHttpClient();

                Request dis = new Request.Builder()
                        .url(API_URL+API_KEY)
                        .get()
                        .addHeader("authorization", "Basic MjAwMDIzMDk3OToyNzRGMEZFMjY2RTZFNTYx")
                        .build();

                Response response = client.newCall(dis).execute();
                return response.body().string();
            }
            catch(Exception e) {
                Log.e("ERROR", e.getMessage(), e);
                return null;
            }
        }

        protected void onPostExecute(String response) {
            progressBar2.setVisibility(View.INVISIBLE);
            if(response == null) {
                response = "THERE WAS AN ERROR";
                finalPrice.setText(response);
            }else{
                try {
                    JSONObject object = (JSONObject) new JSONTokener(response).nextValue();
                    String totalRate = object.getString("totalRate");
                    salesTax.setText(totalRate);

                } catch (JSONException e) {
                    finalPrice.setText("Error");
                }
            }
        }
    }
    
}
