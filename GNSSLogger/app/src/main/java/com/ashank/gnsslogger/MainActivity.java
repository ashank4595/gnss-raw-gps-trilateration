package com.ashank.gnsslogger;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GNSS_APP";
    private static final int REQ_LOCATION_PERMISSION = 1001;

    private LocationManager locationManager;
    private TextView tvOutput;

    private GnssStatus.Callback statusCallback;
    private GnssMeasurementsEvent.Callback measurementCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvOutput = findViewById(R.id.tvOutput);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_LOCATION_PERMISSION
            );
        } else {
            startGnss();
        }
    }

    private void startGnss() {
        appendLine("Starting GNSS...");

        if (locationManager == null) {
            appendLine("LocationManager is null!");
            return;
        }

        // Satellite status callback
        statusCallback = new GnssStatus.Callback() {
            @Override
            public void onSatelliteStatusChanged(GnssStatus status) {
                int count = status.getSatelliteCount();
                appendLine("---- Satellite Status (" + count + ") ----");
                for (int i = 0; i < count; i++) {
                    int svid = status.getSvid(i);
                    int constellation = status.getConstellationType(i);
                    boolean used = status.usedInFix(i);

                    String line = "SVID=" + svid +
                            "  constellation=" + constellation +
                            "  used=" + used;
                    appendLine(line);
                    Log.d(TAG, "STATUS: " + line);
                }
            }
        };

        // Raw GNSS measurements callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            measurementCallback = new GnssMeasurementsEvent.Callback() {
                @Override
                public void onGnssMeasurementsReceived(GnssMeasurementsEvent eventArgs) {
                    GnssClock clock = eventArgs.getClock();
                    long timeNanos = clock.getTimeNanos();
                    long fullBias = clock.hasFullBiasNanos() ? clock.getFullBiasNanos() : 0;

                    appendLine("---- GNSS Measurements ----");
                    appendLine("Receiver timeNanos=" + timeNanos +
                            " fullBiasNanos=" + fullBias);

                    for (GnssMeasurement m : eventArgs.getMeasurements()) {
                        int svid = m.getSvid();
                        long txTime = m.getReceivedSvTimeNanos();
                        double doppler = m.getPseudorangeRateMetersPerSecond();

                        String line = "SVID=" + svid +
                                "  recvSvTimeNanos=" + txTime +
                                "  doppler=" + doppler;
                        appendLine(line);
                        Log.d(TAG, "MEAS: " + line);
                    }
                }
            };
        }

        try {
            locationManager.registerGnssStatusCallback(statusCallback);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                locationManager.registerGnssMeasurementsCallback(measurementCallback);
            }
        } catch (SecurityException e) {
            appendLine("SecurityException: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null) {
            if (statusCallback != null)
                locationManager.unregisterGnssStatusCallback(statusCallback);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && measurementCallback != null)
                locationManager.unregisterGnssMeasurementsCallback(measurementCallback);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_LOCATION_PERMISSION) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startGnss();
            } else {
                appendLine("Permission denied.");
            }
        }
    }

    private void appendLine(final String text) {
        runOnUiThread(() -> {
            tvOutput.append("\n" + text);
        });
    }
}
