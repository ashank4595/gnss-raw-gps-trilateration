//package com.ashank.gnsslogger;
//
//import android.Manifest;
//import android.content.Context;
//import android.content.pm.PackageManager;
//import android.location.GnssClock;
//import android.location.GnssMeasurement;
//import android.location.GnssMeasurementsEvent;
//import android.location.GnssStatus;
//import android.location.LocationManager;
//import android.os.Build;
//import android.os.Bundle;
//import android.util.Log;
//import android.widget.TextView;
//
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//
//public class MainActivity extends AppCompatActivity {
//
//    private static final String TAG = "GNSS_APP";
//    private static final int REQ_LOCATION_PERMISSION = 1001;
//
//    private LocationManager locationManager;
//    private TextView tvOutput;
//
//    private GnssStatus.Callback statusCallback;
//    private GnssMeasurementsEvent.Callback measurementCallback;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//
//        tvOutput = findViewById(R.id.tvOutput);
//        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
//
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
//                != PackageManager.PERMISSION_GRANTED) {
//
//            ActivityCompat.requestPermissions(
//                    this,
//                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
//                    REQ_LOCATION_PERMISSION
//            );
//        } else {
//            startGnss();
//        }
//    }
//
//    private void startGnss() {
//        appendLine("Starting GNSS...");
//
//        if (locationManager == null) {
//            appendLine("LocationManager is null!");
//            return;
//        }
//
//        // Satellite status callback
//        statusCallback = new GnssStatus.Callback() {
//            @Override
//            public void onSatelliteStatusChanged(GnssStatus status) {
//                int count = status.getSatelliteCount();
//                appendLine("---- Satellite Status (" + count + ") ----");
//                for (int i = 0; i < count; i++) {
//                    int svid = status.getSvid(i);
//                    int constellation = status.getConstellationType(i);
//                    boolean used = status.usedInFix(i);
//
//                    String line = "SVID=" + svid +
//                            " constellation=" + constellation +
//                            "  used=" + used;
//                    appendLine(line);
//                    Log.d(TAG, "STATUS: " + line);
//                }
//            }
//        };
//
//        // Raw GNSS measurements callback
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//            measurementCallback = new GnssMeasurementsEvent.Callback() {
//                @Override
//                public void onGnssMeasurementsReceived(GnssMeasurementsEvent eventArgs) {
//                    GnssClock clock = eventArgs.getClock();
//                    long timeNanos = clock.getTimeNanos();
//                    long fullBias = clock.hasFullBiasNanos() ? clock.getFullBiasNanos() : 0;
//
//                    appendLine("---- GNSS Measurements ----");
//                    appendLine("Receiver timeNanos=" + timeNanos +
//                            " fullBiasNanos=" + fullBias);
//
//                    for (GnssMeasurement m : eventArgs.getMeasurements()) {
//                        int svid = m.getSvid();
//                        int constellation = m.getConstellationType();
//                        long txTime = m.getReceivedSvTimeNanos();
//                        double doppler = m.getPseudorangeRateMetersPerSecond();
//
//                        String line = "SVID=" + svid +
//                                "  Constellation=" + constellation +
//                                "  recvSvTimeNanos=" + txTime +
//                                "  doppler=" + doppler;
//                        appendLine(line);
//                        Log.d(TAG, "MEAS: " + line);
//                    }
//                }
//            };
//        }
//
//        try {
//            locationManager.registerGnssStatusCallback(statusCallback);
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                locationManager.registerGnssMeasurementsCallback(measurementCallback);
//            }
//        } catch (SecurityException e) {
//            appendLine("SecurityException: " + e.getMessage());
//        }
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        if (locationManager != null) {
//            if (statusCallback != null)
//                locationManager.unregisterGnssStatusCallback(statusCallback);
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && measurementCallback != null)
//                locationManager.unregisterGnssMeasurementsCallback(measurementCallback);
//        }
//    }
//
//    @Override
//    public void onRequestPermissionsResult(int requestCode,
//                                           @NonNull String[] permissions,
//                                           @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//
//        if (requestCode == REQ_LOCATION_PERMISSION) {
//            if (grantResults.length > 0 &&
//                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                startGnss();
//            } else {
//                appendLine("Permission denied.");
//            }
//        }
//    }
//
//    private void appendLine(final String text) {
//        runOnUiThread(() -> {
//            tvOutput.append("\n" + text);
//        });
//    }
//}


package com.ashank.gnsslogger;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GNSS_APP";
    private static final int REQ_LOCATION_PERMISSION = 1001;

    private LocationManager locationManager;
    private TextView tvOutput;

    private GnssStatus.Callback statusCallback;
    private GnssMeasurementsEvent.Callback measurementCallback;
    private GnssNavigationMessage.Callback navigationCallback;

    // Store ephemeris data for each satellite
    private Map<Integer, GPSEphemeris> ephemerisData = new HashMap<>();

    // GPS constants
    private static final double GPS_MU = 3.986005e14; // Earth gravitational parameter (m^3/s^2)
    private static final double EARTH_ROTATION_RATE = 7.2921151467e-5; // rad/s
    private static final double SPEED_OF_LIGHT = 299792458.0; // m/s

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

    @RequiresApi(api = Build.VERSION_CODES.N)
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
                            " constellation=" + constellation +
                            " used=" + used;
                    appendLine(line);
                    Log.d(TAG, "STATUS: " + line);
                }
            }
        };

        // Navigation message callback to get ephemeris
        navigationCallback = new GnssNavigationMessage.Callback() {
            @Override
            public void onGnssNavigationMessageReceived(GnssNavigationMessage event) {
                int type = event.getType();
                int svid = event.getSvid();

                appendLine("Nav Message: SVID=" + svid + " Type=" + type);

                // GPS L1 C/A navigation message
                if (type == GnssNavigationMessage.TYPE_GPS_L1CA) {
                    byte[] data = event.getData();
                    parseGPSNavigationMessage(svid, data);
                }
            }

            @Override
            public void onStatusChanged(int status) {
                appendLine("Nav Message Status: " + status);
            }
        };

        // Raw GNSS measurements callback
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
                    int constellation = m.getConstellationType();
                    long receivedSvTimeNanos = m.getReceivedSvTimeNanos();
                    double pseudorangeMeters = 0;

                    // Calculate pseudorange
                    if (m.getState() == GnssMeasurement.STATE_CODE_LOCK) {
                        long tRxNanos = timeNanos - fullBias;
                        long tTxNanos = receivedSvTimeNanos;
                        double travelTimeNanos = tRxNanos - tTxNanos;
                        pseudorangeMeters = (travelTimeNanos * SPEED_OF_LIGHT) / 1e9;
                    }

                    // Calculate satellite position if we have ephemeris
                    double[] satPos = null;
                    if (constellation == GnssStatus.CONSTELLATION_GPS && ephemerisData.containsKey(svid)) {
                        double transmitTimeSeconds = receivedSvTimeNanos / 1e9;
                        satPos = calculateSatellitePosition(svid, transmitTimeSeconds);
                    }

                    String line = "SVID=" + svid +
                            " Constellation=" + constellation +
                            " Pseudorange=" + String.format("%.1f", pseudorangeMeters) + "m";

                    if (satPos != null) {
                        line += String.format("\n  SatPos ECEF: X=%.1f Y=%.1f Z=%.1f m",
                                satPos[0], satPos[1], satPos[2]);

                        // Convert to lat/lon/alt for readability
                        double[] latLonAlt = ecefToLatLonAlt(satPos[0], satPos[1], satPos[2]);
                        line += String.format("\n  Lat=%.4f° Lon=%.4f° Alt=%.1fkm",
                                Math.toDegrees(latLonAlt[0]),
                                Math.toDegrees(latLonAlt[1]),
                                latLonAlt[2] / 1000.0);
                    } else {
                        line += "\n  SatPos: Waiting for ephemeris...";
                    }

                    appendLine(line);
                    Log.d(TAG, "MEAS: " + line);
                }
            }
        };

        try {
            locationManager.registerGnssStatusCallback(statusCallback);
            locationManager.registerGnssMeasurementsCallback(measurementCallback);
            locationManager.registerGnssNavigationMessageCallback(navigationCallback);
            appendLine("All callbacks registered successfully");
        } catch (SecurityException e) {
            appendLine("SecurityException: " + e.getMessage());
        }
    }

    private void parseGPSNavigationMessage(int svid, byte[] data) {
        try {
            // GPS navigation message structure:
            // Each subframe is 300 bits (10 words of 30 bits each)

            if (data.length < 40) { // Need at least 40 bytes (320 bits)
                return;
            }

            // Extract subframe ID (bits 50-52, word 2)
            int subframeId = extractBits(data, 49, 3);

            appendLine("SVID " + svid + " Subframe " + subframeId);

            GPSEphemeris ephem = ephemerisData.get(svid);
            if (ephem == null) {
                ephem = new GPSEphemeris();
                ephemerisData.put(svid, ephem);
            }

            switch (subframeId) {
                case 1:
                    // Subframe 1: Clock correction and health
                    ephem.weekNumber = extractBits(data, 60, 10);
                    ephem.toc = extractBits(data, 218, 16) * 16; // Time of clock
                    ephem.af0 = twosComplement(extractBits(data, 270, 22), 22) * Math.pow(2, -31);
                    ephem.af1 = twosComplement(extractBits(data, 248, 16), 16) * Math.pow(2, -43);
                    ephem.af2 = twosComplement(extractBits(data, 240, 8), 8) * Math.pow(2, -55);
                    ephem.hasSubframe1 = true;
                    appendLine("  Week=" + ephem.weekNumber + " Toc=" + ephem.toc);
                    break;

                case 2:
                    // Subframe 2: Ephemeris (part 1)
                    ephem.crs = twosComplement(extractBits(data, 68, 16), 16) * Math.pow(2, -5);
                    ephem.deltaN = twosComplement(extractBits(data, 90, 16), 16) * Math.pow(2, -43) * Math.PI;
                    ephem.m0 = twosComplement(extractBits(data, 106, 32), 32) * Math.pow(2, -31) * Math.PI;
                    ephem.cuc = twosComplement(extractBits(data, 150, 16), 16) * Math.pow(2, -29);
                    ephem.e = extractBits(data, 166, 32) * Math.pow(2, -33);
                    ephem.cus = twosComplement(extractBits(data, 210, 16), 16) * Math.pow(2, -29);
                    ephem.sqrtA = extractBits(data, 226, 32) * Math.pow(2, -19);
                    ephem.toe = extractBits(data, 270, 16) * 16; // Time of ephemeris
                    ephem.hasSubframe2 = true;
                    appendLine("  e=" + ephem.e + " sqrtA=" + ephem.sqrtA + " toe=" + ephem.toe);
                    break;

                case 3:
                    // Subframe 3: Ephemeris (part 2)
                    ephem.cic = twosComplement(extractBits(data, 60, 16), 16) * Math.pow(2, -29);
                    ephem.omega0 = twosComplement(extractBits(data, 76, 32), 32) * Math.pow(2, -31) * Math.PI;
                    ephem.cis = twosComplement(extractBits(data, 120, 16), 16) * Math.pow(2, -29);
                    ephem.i0 = twosComplement(extractBits(data, 136, 32), 32) * Math.pow(2, -31) * Math.PI;
                    ephem.crc = twosComplement(extractBits(data, 180, 16), 16) * Math.pow(2, -5);
                    ephem.omega = twosComplement(extractBits(data, 196, 32), 32) * Math.pow(2, -31) * Math.PI;
                    ephem.omegaDot = twosComplement(extractBits(data, 240, 24), 24) * Math.pow(2, -43) * Math.PI;
                    ephem.idot = twosComplement(extractBits(data, 278, 14), 14) * Math.pow(2, -43) * Math.PI;
                    ephem.hasSubframe3 = true;
                    appendLine("  i0=" + Math.toDegrees(ephem.i0) + "° omega=" + Math.toDegrees(ephem.omega) + "°");
                    break;
            }

            if (ephem.isComplete()) {
                appendLine("✓ Complete ephemeris for SVID " + svid);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing navigation message for SVID " + svid, e);
            appendLine("Parse error: " + e.getMessage());
        }
    }

    private int extractBits(byte[] data, int startBit, int numBits) {
        int result = 0;
        for (int i = 0; i < numBits; i++) {
            int bitIndex = startBit + i;
            int byteIndex = bitIndex / 8;
            int bitInByte = 7 - (bitIndex % 8); // MSB first

            if (byteIndex >= data.length) break;

            int bit = (data[byteIndex] >> bitInByte) & 1;
            result = (result << 1) | bit;
        }
        return result;
    }

    private int twosComplement(int value, int bits) {
        int sign = 1 << (bits - 1);
        if ((value & sign) != 0) {
            return value - (1 << bits);
        }
        return value;
    }

    private double[] calculateSatellitePosition(int svid, double transmitTimeSeconds) {
        GPSEphemeris ephem = ephemerisData.get(svid);
        if (ephem == null || !ephem.isComplete()) {
            return null;
        }

        try {
            // Time from ephemeris reference epoch
            double tk = transmitTimeSeconds - ephem.toe;

            // Handle week crossovers
            if (tk > 302400) tk -= 604800;
            if (tk < -302400) tk += 604800;

            // Computed mean motion
            double n0 = Math.sqrt(GPS_MU / Math.pow(ephem.sqrtA, 6));
            double n = n0 + ephem.deltaN;

            // Mean anomaly
            double Mk = ephem.m0 + n * tk;

            // Solve Kepler's equation iteratively for eccentric anomaly
            double Ek = Mk;
            for (int i = 0; i < 10; i++) {
                double EkNew = Mk + ephem.e * Math.sin(Ek);
                if (Math.abs(EkNew - Ek) < 1e-12) break;
                Ek = EkNew;
            }

            // True anomaly
            double vk = Math.atan2(
                    Math.sqrt(1 - ephem.e * ephem.e) * Math.sin(Ek),
                    Math.cos(Ek) - ephem.e
            );

            // Argument of latitude
            double phik = vk + ephem.omega;

            // Second harmonic perturbations
            double duk = ephem.cus * Math.sin(2 * phik) + ephem.cuc * Math.cos(2 * phik);
            double drk = ephem.crs * Math.sin(2 * phik) + ephem.crc * Math.cos(2 * phik);
            double dik = ephem.cis * Math.sin(2 * phik) + ephem.cic * Math.cos(2 * phik);

            // Corrected argument of latitude, radius, and inclination
            double uk = phik + duk;
            double rk = Math.pow(ephem.sqrtA, 2) * (1 - ephem.e * Math.cos(Ek)) + drk;
            double ik = ephem.i0 + dik + ephem.idot * tk;

            // Positions in orbital plane
            double xk_prime = rk * Math.cos(uk);
            double yk_prime = rk * Math.sin(uk);

            // Corrected longitude of ascending node
            double Omegak = ephem.omega0 + (ephem.omegaDot - EARTH_ROTATION_RATE) * tk
                    - EARTH_ROTATION_RATE * ephem.toe;

            // Earth-fixed coordinates (ECEF)
            double xk = xk_prime * Math.cos(Omegak) - yk_prime * Math.cos(ik) * Math.sin(Omegak);
            double yk = xk_prime * Math.sin(Omegak) + yk_prime * Math.cos(ik) * Math.cos(Omegak);
            double zk = yk_prime * Math.sin(ik);

            return new double[]{xk, yk, zk};

        } catch (Exception e) {
            Log.e(TAG, "Satellite position calculation failed for SVID " + svid, e);
            return null;
        }
    }

    private double[] ecefToLatLonAlt(double x, double y, double z) {
        // WGS84 parameters
        final double a = 6378137.0; // semi-major axis
        final double f = 1.0 / 298.257223563; // flattening
        final double b = a * (1 - f); // semi-minor axis
        final double e2 = 1 - (b * b) / (a * a); // first eccentricity squared

        double lon = Math.atan2(y, x);
        double p = Math.sqrt(x * x + y * y);
        double lat = Math.atan2(z, p * (1 - e2));

        // Iterate to refine latitude
        for (int i = 0; i < 5; i++) {
            double sinLat = Math.sin(lat);
            double N = a / Math.sqrt(1 - e2 * sinLat * sinLat);
            double alt = p / Math.cos(lat) - N;
            lat = Math.atan2(z, p * (1 - e2 * N / (N + alt)));
        }

        double sinLat = Math.sin(lat);
        double N = a / Math.sqrt(1 - e2 * sinLat * sinLat);
        double alt = p / Math.cos(lat) - N;

        return new double[]{lat, lon, alt}; // radians, radians, meters
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null) {
            if (statusCallback != null)
                locationManager.unregisterGnssStatusCallback(statusCallback);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (measurementCallback != null)
                    locationManager.unregisterGnssMeasurementsCallback(measurementCallback);
                if (navigationCallback != null)
                    locationManager.unregisterGnssNavigationMessageCallback(navigationCallback);
            }
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    startGnss();
                }
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

    // GPS Ephemeris data class
    private static class GPSEphemeris {
        // Subframe 1
        int weekNumber;
        double toc; // Time of clock
        double af0, af1, af2; // Clock correction coefficients
        boolean hasSubframe1 = false;

        // Subframe 2
        double crs, deltaN, m0, cuc, e, cus, sqrtA;
        double toe; // Time of ephemeris
        boolean hasSubframe2 = false;

        // Subframe 3
        double cic, omega0, cis, i0, crc, omega, omegaDot, idot;
        boolean hasSubframe3 = false;

        boolean isComplete() {
            return hasSubframe1 && hasSubframe2 && hasSubframe3;
        }
    }
}