package com.jakehilborn.speedr;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatImageButton;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.jakehilborn.speedr.utils.Prefs;
import com.jakehilborn.speedr.utils.UnitUtils;

public class MainActivity extends AppCompatActivity implements MainService.Callback {

    private static final int BIND_IF_SERVICE_RUNNING = 0;
    private static final int REQUEST_LOCATION = 1;

    private MainService mainService;

    private TextView speed;
    private TextView speedUnit;
    private TextView limit;
    private TextView limitUnit;
    private TextView timeDiffH;
    private TextView timeDiffM;
    private TextView timeDiffS;
    private TextView timeDiffS10th;

    private AppCompatImageButton reset;

    private Toast noGPSPermissionToast;
    private Toast noNetworkToast;
    private Toast playServicesErrorToast;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        speed = (TextView) findViewById(R.id.speed);
        speedUnit = (TextView) findViewById(R.id.speed_unit);
        limit = (TextView) findViewById(R.id.limit);
        limitUnit = (TextView) findViewById(R.id.limit_unit);
        timeDiffH = (TextView) findViewById(R.id.time_diff_h);
        timeDiffM = (TextView) findViewById(R.id.time_diff_m);
        timeDiffS = (TextView) findViewById(R.id.time_diff_s);
        timeDiffS10th = (TextView) findViewById(R.id.time_diff_s10th);

        reset = (AppCompatImageButton) findViewById(R.id.reset_session);
        reset.setOnClickListener(new View.OnClickListener() { //xml defined onClick for AppCompatImageButton crashes on Android 4.2
            public void onClick(View view) {
                resetSessionOnClick(view);
            }
        });

        noGPSPermissionToast = Toast.makeText(this, R.string.no_gps_permission_toast, Toast.LENGTH_LONG);
        noNetworkToast = Toast.makeText(this, R.string.no_network_toast, Toast.LENGTH_LONG);
        playServicesErrorToast = Toast.makeText(this, R.string.play_services_error_toast, Toast.LENGTH_LONG);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (Prefs.isUseKph(this)) {
            speedUnit.setText(R.string.kmh);
            limitUnit.setText(R.string.kmh);
        } else {
            speedUnit.setText(R.string.mph);
            limitUnit.setText(R.string.mph);
        }

        setSessionInUI();

        bindService(new Intent(this, MainService.class), mainServiceConn, BIND_IF_SERVICE_RUNNING);
    }

    private ServiceConnection mainServiceConn = new ServiceConnection() { //binder boilerplate
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            MainService.LocalBinder binder = (MainService.LocalBinder) service;
            mainService = binder.getService();
            mainService.setCallback(MainActivity.this);

            //Sets UI values on MainActivity onStart() if MainService was already running
            styleStartStopButton(true);
            setStatsInUI(mainService.pollStats());
        }

        @Override //Only called on service crashes, not called onDestroy or on unbindService
        public void onServiceDisconnected(ComponentName className) {
            mainService = null;
        }
    };

    @Override
    public void onStatsUpdate(Stats stats) {
        setStatsInUI(stats);
    }

    private void setStatsInUI(Stats stats) {
        if (stats.getSpeed() != null) speed.setText(String.valueOf(stats.getSpeed()));
        if (stats.getLimit() != null) limit.setText(String.valueOf(stats.getLimit()));
        if (stats.getTimeDiff() != null) {
            timeDiffH.setText(String.valueOf(UnitUtils.nanosToHoursModuloMinutes(stats.getTimeDiff())));
            timeDiffM.setText(String.valueOf(UnitUtils.nanosToMinutesModuloHours(stats.getTimeDiff())));
            timeDiffS.setText(String.valueOf(UnitUtils.nanosToSecondsModuloMinutes(stats.getTimeDiff())));
            timeDiffS10th.setText(String.valueOf(UnitUtils.nanosTo10thsModuloSeconds(stats.getTimeDiff())));
        }
    }

    private void setSessionInUI() { //Sets timeDiff from the completed MainService session and reset button if necessary
        Double sessionTimeDiff;
        if (mainService != null) {
            sessionTimeDiff = mainService.pollStats().getTimeDiff();
        } else { //If service is not running then read the timeDiff from storage
            sessionTimeDiff = Prefs.getSessionTimeDiff(this);
        }

        if (sessionTimeDiff != 0) {
            Stats stats = new Stats();
            stats.setTimeDiff(sessionTimeDiff);
            setStatsInUI(stats);
            reset.setVisibility(View.VISIBLE);
        } else {
            reset.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //item ID is not checked since there the only menu button is the settings cog
        startActivity(new Intent(this, SettingsActivity.class));
        return true;
    }

    public void startStopButtonOnClick(View view) {
        if (mainService == null) {
            startMainService();
        } else {
            stopMainService();
        }
    }

    private void startMainService() {
        if (requestLocationPermission() && checkGPSPrereq() && checkNetworkPrereq() && checkPlayServicesPrereq()) {
            styleStartStopButton(true);
            reset.setVisibility(View.INVISIBLE);

            startService(new Intent(this, MainService.class));
            bindService(new Intent(this, MainService.class), mainServiceConn, BIND_AUTO_CREATE);
        }
    }

    private void stopMainService() {
        styleStartStopButton(false);
        setSessionInUI();
        unbindService(mainServiceConn);
        stopService(new Intent(this, MainService.class));
        mainService = null;
    }

    private void styleStartStopButton(boolean start) {
        if (start) {
            ((FloatingActionButton) findViewById(R.id.start_stop))
                    .setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.materialRed))); //Only solution I've found to be compatible with Android 4.2
            ((FloatingActionButton) findViewById(R.id.start_stop))
                    .setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pause));
        } else {
            ((FloatingActionButton) findViewById(R.id.start_stop))
                    .setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.materialGreen)));
            ((FloatingActionButton) findViewById(R.id.start_stop))
                    .setImageDrawable(ContextCompat.getDrawable(this, R.drawable.car));
        }
    }

    public void resetSessionOnClick(View view) {
        timeDiffH.setText("-");
        timeDiffM.setText("--");
        timeDiffS.setText("--");
        timeDiffS10th.setText("-");
        reset.setVisibility(View.INVISIBLE);

        Prefs.setSessionTimeDiff(this, 0D);
    }

    private boolean requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) { //Success
            startMainService();
        } else {
            noGPSPermissionToast.show();
        }
    }

    private boolean checkGPSPrereq() {
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return true;
        }

        //Checking if GPS is enabled is spotty on some API levels (18) and some devices. Checking again in case of false negative.
        String networkList = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
        if (networkList != null && networkList.contains("gps")) {
            return true;
        }

        showNoGPSAlert();
        return false;
    }

    private void showNoGPSAlert() {
        int messageRef = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) ?
                R.string.gps_disabled_message_4_4up : R.string.gps_disabled_message_4_3down;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(messageRef)
                .setCancelable(true)
                .setPositiveButton(R.string.go_to_location_settings_alert_button_text, new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private boolean checkNetworkPrereq() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isConnectedOrConnecting()) {
            noNetworkToast.show();
            return false;
        }
        return true;
    }

    private boolean checkPlayServicesPrereq() {
        GoogleApiAvailability googleAPI = GoogleApiAvailability.getInstance();
        int result = googleAPI.isGooglePlayServicesAvailable(this);
        if (result != ConnectionResult.SUCCESS) {
            if (googleAPI.isUserResolvableError(result)) {
                googleAPI.getErrorDialog(this, result, 0).show();
            } else {
                playServicesErrorToast.show();
            }
            return false;
        }
        return true;
    }

    @Override
    public void onStop() {
        noGPSPermissionToast.cancel();
        noNetworkToast.cancel();
        playServicesErrorToast.cancel();

        if (mainService != null) unbindService(mainServiceConn);

        super.onStop();
    }
}
