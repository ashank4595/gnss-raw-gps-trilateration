# Raw GNSS Trilateration

**GPS positioning from scratch using raw Android GNSS measurements.**

An Android app that computes GPS coordinates from satellite signals. It collects raw GNSS timestamps and navigation messages, decodes broadcast ephemeris to calculate satellite positions, and uses signal travel times to compute pseudoranges. The receiver's 3D position and clock bias are estimated with Gauss–Newton least squares, then converted to WGS84 latitude, longitude, and altitude.

**Result:** Position estimate within approximately **500 m** of the reference location using four GPS satellites on a OnePlus device.

## How it works

A GPS position fix requires three pieces of information from each satellite:

1. **Transmit time** — when the satellite sent the signal (`receivedSvTimeNanos`).
2. **Receive time** — when the phone received it, derived from `timeNanos` and `fullBiasNanos`.
3. **Satellite position** — calculated at the transmit time from the satellite's broadcast orbital parameters.

The difference between receive time and transmit time gives the signal travel time. Multiplying by the speed of light produces a *pseudorange*:

$$
\rho_i = c(t_{rx} - t_{tx,i})
$$

The measurement is a pseudorange rather than an exact distance because the phone's clock is not perfectly synchronized with GPS time. The receiver's position and clock bias are solved together:

$$
\rho_i = \sqrt{(x-x_i)^2+(y-y_i)^2+(z-z_i)^2} + c\delta t
$$

Here, $(x,y,z)$ is the receiver position, $(x_i,y_i,z_i)$ is the position of satellite $i$, and $\delta t$ is the receiver clock bias. Four satellites provide four equations for the four unknowns; additional satellites can be included in the least-squares solution.

The pipeline is:

```text
Android GNSS receiver
    |-- GnssMeasurementsEvent --> receiver/satellite timestamps --> pseudoranges --|
    |                                                                             |
    `-- GnssNavigationMessage --> ephemeris --> satellite ECEF positions ---------|
                                                                                  |
                                                                     Gauss–Newton solve
                                                                                  |
                                                                       receiver ECEF
                                                                                  |
                                                                      WGS84 lat/lon/alt
```

## Implementation

### 1. Raw GNSS collection

`GNSSLogger/app/src/main/java/com/ashank/gnsslogger/MainActivity.java`

The Android app uses three `LocationManager` callbacks:

| Callback | Data |
| --- | --- |
| `GnssStatus.Callback` | Visible satellites and SVIDs |
| `GnssMeasurementsEvent.Callback` | Receiver clock, satellite timestamps, and measurement states |
| `GnssNavigationMessage.Callback` | Raw GPS navigation subframes |

The measurement callback supplies `timeNanos`, `fullBiasNanos`, and each satellite's `receivedSvTimeNanos`. Measurements are checked for valid tracking states, including `STATE_CODE_LOCK`, before being used.

The navigation callback provides the broadcast orbital data needed to calculate satellite positions.

### 2. Decoding broadcast ephemeris

GPS L1 C/A navigation subframes contain 300 bits (ten 30-bit words). The orbit and clock parameters are spread across three subframes:

| Subframe | Main fields |
| --- | --- |
| 1 | Satellite clock parameters and timing |
| 2 | Eccentricity, square root of semi-major axis, mean anomaly, mean motion correction, and `toe` |
| 3 | Inclination, right ascension, and argument of perigee |

`extractBits()` reads fields that may cross byte boundaries, and `twosComplement()` interprets signed fields at their specified bit widths. The extracted values are scaled according to IS-GPS-200.

```java
ephem.e = extractBits(data, 166, 32) * Math.pow(2, -33);
ephem.sqrtA = extractBits(data, 226, 32) * Math.pow(2, -19);
ephem.m0 = twosComplement(extractBits(data, 106, 32), 32)
         * Math.pow(2, -31) * Math.PI;
```

The app stores decoded parameters in a `GPSEphemeris` object for each SVID. `isComplete()` checks that all three subframes have arrived before the orbit calculation runs.

### 3. Satellite orbit propagation

`calculateSatellitePosition(int svid, double transmitTime)`

The position of each satellite is calculated at signal transmission time using its broadcast ephemeris.

First, compute the time since the ephemeris reference epoch, adjusting for GPS week rollover:

$$
t_k = t - t_{oe}
$$

The mean motion and mean anomaly are:

$$
n = \sqrt{\frac{\mu}{A^3}} + \Delta n
$$

$$
M_k = M_0 + nt_k
$$

Kepler's equation is solved iteratively to obtain the eccentric anomaly:

$$
M_k = E_k - e\sin(E_k)
$$

The implementation uses fixed-point iteration with a tolerance of `1e-12` and a limit of ten iterations. The resulting orbital coordinates are transformed to Earth-centered, Earth-fixed (ECEF) coordinates using the orbital inclination, right ascension, and argument of perigee.

Earth's rotation is incorporated when calculating the longitude of the ascending node:

```java
double Omegak = ephem.omega0
    + (ephem.omegaDot - EARTH_ROTATION_RATE) * tk
    - EARTH_ROTATION_RATE * ephem.toe;
```

The output is a satellite position `(x_i, y_i, z_i)` in ECEF metres.

### 4. Pseudorange calculation

`compute/calculations.py`

Receiver time is converted to GPS time using the Android GNSS clock and reduced modulo one GPS week to match the satellite time-of-week measurement:

```python
GPS_WEEK_NS = 604_800 * 1_000_000_000

Trx = ReceiverTimeNanos - FullBiasNanos
Tmod = Trx % GPS_WEEK_NS
```

After handling the relevant timing offsets and week boundaries, the pseudorange is computed in metres:

```python
SPEED_OF_LIGHT = 299_792_458.0

travel_time = (receiver_time_ns - transmit_time_ns) * 1e-9
pseudorange = SPEED_OF_LIGHT * travel_time
```

The receiver clock bias remains part of the measurement and is estimated in the position solver.

### 5. Receiver position and clock bias

`compute/calculations.py`

For each satellite, the predicted pseudorange is:

$$
\hat\rho_i = \sqrt{(x-x_i)^2+(y-y_i)^2+(z-z_i)^2} + b
$$

where $b=c\delta t$ is the receiver clock bias expressed in metres.

The solver minimizes the residuals between measured and predicted pseudoranges with Gauss–Newton least squares. Each iteration computes residuals, constructs the Jacobian from satellite/receiver geometry, solves for a correction, and updates the current estimate.

$$
\Delta\mathbf{x} = (J^T J)^{-1}J^T\mathbf{r}
$$

The estimated state contains ECEF position `(x, y, z)` and clock bias `b`. When more than four satellites are available, their measurements are combined in the overdetermined least-squares system.

### 6. ECEF to WGS84

`ecefToLatLonAlt(x, y, z)`

The receiver's ECEF coordinates are converted to geodetic latitude, longitude, and altitude using the WGS84 ellipsoid. Longitude is calculated directly:

$$
\lambda = \operatorname{atan2}(y,x)
$$

Latitude and altitude are obtained iteratively because WGS84 models Earth as an oblate ellipsoid.

## Results

Outdoor GNSS capture on a OnePlus device:

| Metric | Result |
| --- | --- |
| Visible satellites | 27 |
| Satellites used | GPS PRNs 18, 23, 29, 31 |
| Solver | Gauss–Newton least squares |
| Estimated values | Receiver ECEF position and clock bias |
| Output | WGS84 latitude, longitude, altitude |
| Position error | ~500 m from reference location |

The fix was computed from raw signal timestamps and satellite positions derived from broadcast navigation data, without using Android's computed location as an input.

## Project structure

```text
GNSSLogger/
└── app/src/main/java/com/ashank/gnsslogger/MainActivity.java
compute/
└── calculations.py
```

- **`MainActivity.java`** — GNSS callbacks, navigation-message decoding, ephemeris storage, and satellite orbit calculations.
- **`calculations.py`** — Receiver clock conversion, pseudoranges, and least-squares position solution.

## Running

**Requirements:** Android Studio, a physical Android device with raw GNSS/navigation-message support, Python 3, and NumPy.

1. Open `GNSSLogger/` in Android Studio, build the app, and install it on a supported phone.
2. Grant precise location permission and collect measurements outdoors with a clear view of the sky.
3. Read the captured measurements and navigation data in the app or Logcat under `GNSS_APP`. Collect subframes 1–3 for each satellite used.
4. Use the captured data with the calculation script:

   ```bash
   python compute/calculations.py
   ```

The script computes pseudoranges, solves for receiver position and clock bias, and converts the result to WGS84 coordinates.

## References

- [Android GnssMeasurement API](https://developer.android.com/reference/android/location/GnssMeasurement)
- [Android GnssNavigationMessage API](https://developer.android.com/reference/android/location/GnssNavigationMessage)
- [IS-GPS-200 GPS Interface Specification](https://www.gps.gov/technical/icwg/)
- [WGS84 — National Geospatial-Intelligence Agency](https://earth-info.nga.mil/)
