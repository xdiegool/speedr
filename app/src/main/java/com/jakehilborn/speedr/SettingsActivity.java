package com.jakehilborn.speedr;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.jakehilborn.speedr.utils.Prefs;

public class SettingsActivity extends AppCompatActivity {

    private EditText appIdField;
    private EditText appCodeField;
    private Toast emptyCredentials;
    private AppCompatButton hereMapsButton;
    private AppCompatButton openStreetMapButton;
    private Spinner speedUnitSpinner;
    private GoogleApiClient googleApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        appIdField = (EditText) findViewById(R.id.here_app_id);
        appIdField.setText(Prefs.getHereAppId(this));

        appCodeField = (EditText) findViewById(R.id.here_app_code);
        appCodeField.setText(Prefs.getHereAppCode(this));

        emptyCredentials = Toast.makeText(this, R.string.enter_here_maps_credentials_toast, Toast.LENGTH_LONG);

        openStreetMapButton = (AppCompatButton) findViewById(R.id.open_street_map_button);
        openStreetMapButton.setSupportBackgroundTintList(ColorStateList.valueOf(getResources().getColor(
                Prefs.isUseHereMaps(this) ? R.color.unselectedButtonGray : R.color.colorAccent
        )));
        openStreetMapButton.setOnClickListener(new View.OnClickListener() { //xml defined onClick for AppCompatButton crashes on Android 4.2
            public void onClick(View view) {
                limitProviderSelectorHandler(false);
            }
        });

        hereMapsButton = (AppCompatButton) findViewById(R.id.here_maps_button);
        hereMapsButton.setSupportBackgroundTintList(ColorStateList.valueOf(getResources().getColor(
                Prefs.isUseHereMaps(this) ? R.color.colorAccent : R.color.unselectedButtonGray
        )));
        hereMapsButton.setOnClickListener(new View.OnClickListener() { //xml defined onClick for AppCompatButton crashes on Android 4.2
            public void onClick(View view) {
                limitProviderSelectorHandler(true);
            }
        });

        speedUnitSpinner = (Spinner) findViewById(R.id.speed_unit);
        speedUnitSpinner.setSelection(Prefs.isUseKph(this) ? 1 : 0); //defaults to mph
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.dev_info, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.dev_info:
                devInfoOnClick();
                return true;
            case android.R.id.home:
                if (newHereCredentials()) {
                    return true;
                } else {
                    return super.onOptionsItemSelected(item);
                }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void limitProviderSelectorHandler(boolean isUseHereMaps) {
        if (isUseHereMaps && (appIdField.getText().toString().trim().isEmpty() || appCodeField.getText().toString().trim().isEmpty())) {
            emptyCredentials.show();
            isUseHereMaps = false;
        }

        if (isUseHereMaps && !Prefs.isHereMapsTermsAccepted(this)) {
            showHereMapsTerms();
            isUseHereMaps = false;
        }

        saveHereCredsIfChanged();
        Prefs.setUseHereMaps(this, isUseHereMaps);

        openStreetMapButton.setSupportBackgroundTintList(ColorStateList.valueOf(getResources().getColor(
                isUseHereMaps ? R.color.unselectedButtonGray : R.color.colorAccent
        )));
        hereMapsButton.setSupportBackgroundTintList(ColorStateList.valueOf(getResources().getColor(
                isUseHereMaps ? R.color.colorAccent : R.color.unselectedButtonGray
        )));
    }

    //Detect if user input HERE credentials but did not click the HERE MAPS button, activate HERE for them.
    private boolean newHereCredentials() {
        if (appIdField.getText().toString().trim().length() < 20) {
            return false;
        }
        if (appCodeField.getText().toString().trim().length() < 22) {
            return false;
        }
        if (appIdField.getText().toString().trim().equals(Prefs.getHereAppId(this)) &&
                appCodeField.getText().toString().trim().equals(Prefs.getHereAppCode(this))) {
            return false;
        }
        if (Prefs.isUseHereMaps(this)) {
            return false;
        }

        limitProviderSelectorHandler(true);
        Toast.makeText(this, R.string.enabled_here_maps_toast, Toast.LENGTH_LONG).show();
        return true;
    }

    private void saveHereCredsIfChanged() {
        if (appIdField.getText().toString().trim().equals(Prefs.getHereAppId(this)) &&
                appCodeField.getText().toString().trim().equals(Prefs.getHereAppCode(this))) {
            return;
        }

        Prefs.setHereAppId(this, appIdField.getText().toString().trim());
        Prefs.setHereAppCode(this, appCodeField.getText().toString().trim());
        Prefs.setTimeOfHereCreds(this, System.currentTimeMillis());
    }

    private void showHereMapsTerms() {
        WebView webView = new WebView(this);
        webView.loadUrl(getString(R.string.here_maps_terms_url));

        new AlertDialog.Builder(this)
                .setView(webView)
                .setCancelable(true)
                .setPositiveButton(R.string.accept_here_maps_terms_alert_button_text, new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        Prefs.setHereMapsTermsAccepted(SettingsActivity.this, true);
                        limitProviderSelectorHandler(true); //Set limit provider now that terms have been accepted
                    }})
                .setNegativeButton(R.string.reject_here_maps_terms_alert_button_text, null)
                .show();
    }

    private void launchWebpage(String uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        startActivity(intent);
    }

    public void hereMapsCreateAccountOnClick(View view) {
        final View dialogView = getLayoutInflater().inflate(R.layout.here_account_dialog, null);

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .setPositiveButton(R.string.here_maps_create_account_dialog_button, new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        launchWebpage("https://developer.here.com/plans?create=Public_Free_Plan_Monthly&keepState=true&step=account");
                    }
                })
                .setNeutralButton(R.string.help_dialog_button, new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        showHereMapsCreateAccountHelpDialog();
                    }
                })
                .show();

        Handler handler = new Handler();

        int delay = 250;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dialogView.findViewById(R.id.here_tutorial_step_1_text).setVisibility(View.VISIBLE);
            }
        }, delay);

        delay += 500;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dialogView.findViewById(R.id.here_tutorial_step_1_image).setVisibility(View.VISIBLE);
            }
        }, delay);

        delay += 1600;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dialogView.findViewById(R.id.here_tutorial_step_2_text).setVisibility(View.VISIBLE);
            }
        }, delay);

        delay += 500;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dialogView.findViewById(R.id.here_tutorial_step_2_image).setVisibility(View.VISIBLE);
            }
        }, delay);
    }

    public void showHereMapsCreateAccountHelpDialog() {
        final View dialogView = getLayoutInflater().inflate(R.layout.here_account_help_dialog, null);

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .setPositiveButton(R.string.close_dialog_button, null)
                .show();

        Handler handler = new Handler();

        int delay = 250;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dialogView.findViewById(R.id.here_tutorial_help_1_text).setVisibility(View.VISIBLE);
            }
        }, delay);

        delay += 500;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dialogView.findViewById(R.id.here_tutorial_help_1_image).setVisibility(View.VISIBLE);
            }
        }, delay);

        delay += 1600;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dialogView.findViewById(R.id.here_tutorial_help_2_text).setVisibility(View.VISIBLE);
            }
        }, delay);

        delay += 500;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dialogView.findViewById(R.id.here_tutorial_help_2_image).setVisibility(View.VISIBLE);
            }
        }, delay);
    }

    public void openStreetMapCoverageOnClick(View view) {
        if (ContextCompat.checkSelfPermission(SettingsActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        @SuppressWarnings("MissingPermission")
                        public void onConnected(Bundle bundle) {
                            String uri;
                            Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
                            if (lastLocation != null) {
                                uri = "http://product.itoworld.com/map/124?lat=" + lastLocation.getLatitude() + "&lon=" + lastLocation.getLongitude() + "&zoom=14";
                            } else {
                                uri = "http://product.itoworld.com/map/124?lat=37.77557&lon=-100.44588&zoom=4"; //map of United States
                            }

                            launchWebpage(uri);
                            googleApiClient.disconnect();
                        }

                        @Override
                        public void onConnectionSuspended(int i) {}
                    })
                    .addApi(LocationServices.API)
                    .build();

            googleApiClient.connect();
        } else {
            launchWebpage("http://product.itoworld.com/map/124?lat=37.77557&lon=-100.44588&zoom=4"); //map of United States
        }
    }

    public void openStreetMapDonateOnClick(View view) {
        launchWebpage("https://donate.openstreetmap.org");
    }

    public void privacyAndTermsOnClick(View view) {
        String content = getString(R.string.privacy_policy_content).replace("HERE_TERMS_PLACEHOLDER", getString(R.string.here_maps_terms_url));

        ((TextView) new AlertDialog.Builder(this)
                .setTitle(R.string.privacy_policy_title)
                .setMessage(Html.fromHtml(content))
                .setCancelable(true)
                .setNegativeButton(R.string.close_dialog_button, null)
                .show()
                .findViewById(android.R.id.message)) //These 2 lines make the hyperlinks clickable
                .setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void devInfoOnClick() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.developed_by_jake_hilborn_dialog_title)
                .setCancelable(true)
                .setNeutralButton(R.string.github_link_text, new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        launchWebpage("https://github.com/jakehilborn");
                    }
                })
                .setNegativeButton(R.string.linkedin_link_text, new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        launchWebpage("https://www.linkedin.com/in/jakehilborn");
                    }
                })
                .setPositiveButton(R.string.email_link_text, new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        Intent intent = new Intent(Intent.ACTION_SENDTO);
                        intent.setData(Uri.parse("mailto: jakehilborn@gmail.com"));
                        startActivity(Intent.createChooser(intent, getString(R.string.email_speedr_developer_chooser_text)));
                    }
                })
                .show();
    }

    public void versionOnClick(View view) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.changelog_dialog_title)
                .setMessage(R.string.changelog_content)
                .setCancelable(true)
                .setNegativeButton(R.string.close_dialog_button, null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (!newHereCredentials()) {
            super.onBackPressed();
        }
    }

    @Override
    public void onPause() {
        saveHereCredsIfChanged();
        Prefs.setUseKph(this, (speedUnitSpinner.getSelectedItemPosition() == 1)); //0 is mph, 1 is km/h
        emptyCredentials.cancel();
        super.onPause();
    }
}
