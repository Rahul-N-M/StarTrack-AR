package com.example.isst;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ARCameraActivity extends AppCompatActivity {

    private static final String TAG = "ARCamera";
    private static final String PREFS_NAME = "ar_sky_filters";
    private static final String PREF_SHOW_PLANETS = "show_planets";
    private static final String PREF_SHOW_STARS = "show_stars";
    private static final String PREF_SHOW_SATELLITES = "show_satellites";

    // Back-camera horizontal FOV in the sensor's landscape orientation.
    // In portrait preview that same wide sensor axis maps to screen vertical.
    private static final float CAMERA_VFOV_PORTRAIT_DEG = 70f;

    private PreviewView cameraPreview;
    private SkyOverlayView skyOverlay;
    private TextView tvStatus;
    private CheckBox cbPlanets;
    private CheckBox cbStars;
    private CheckBox cbSatellites;

    private final List<CelestialObject> solarSystemObjects = new ArrayList<>();
    private final List<CelestialObject> starObjects = new ArrayList<>();
    private final Map<CelestialObject.Type, CelestialObject> satelliteObjects = new HashMap<>();
    private final SatelliteTracker satelliteTracker = new SatelliteTracker();

    private SharedPreferences filterPrefs;
    private boolean showPlanets = true;
    private boolean showStars = true;
    private boolean showSatellites = true;

    private FusedLocationProviderClient fusedLocation;
    private double userLat = 0;
    private double userLon = 0;
    private float userAltM = 0;
    private boolean locationReady = false;
    private boolean orientationReady = false;
    private boolean celestialObjectsReady = false;
    private boolean issReady = false;
    private float magneticDeclination = 0f;

    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private Sensor fallbackAccel;
    private Sensor fallbackMag;
    private boolean sensorsRegistered = false;

    private final float[] accelValues = new float[3];
    private final float[] magValues = new float[3];
    private boolean accelReady = false;
    private boolean magReady = false;

    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_ROTATION_VECTOR:
                    handleRotationVector(event.values);
                    break;
                case Sensor.TYPE_ACCELEROMETER:
                    System.arraycopy(event.values, 0, accelValues, 0, 3);
                    accelReady = true;
                    if (magReady) handleFallback();
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    System.arraycopy(event.values, 0, magValues, 0, 3);
                    magReady = true;
                    if (accelReady) handleFallback();
                    break;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private final AstronomyApiFetcher astronomyFetcher = new AstronomyApiFetcher();
    private final ISSFetcher issFetcher = new ISSFetcher();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private Runnable satelliteRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar_camera);

        cameraPreview = findViewById(R.id.cameraPreview);
        skyOverlay = findViewById(R.id.skyOverlay);
        tvStatus = findViewById(R.id.tvARStatus);
        cbPlanets = findViewById(R.id.cbPlanets);
        cbStars = findViewById(R.id.cbStars);
        cbSatellites = findViewById(R.id.cbSatellites);

        tvStatus.setText("Initializing...");
        tvStatus.setVisibility(View.VISIBLE);

        for (CelestialObject obj : CelestialObject.createSolarSystemDefaults()) {
            solarSystemObjects.add(obj);
        }

        setupFilters();
        updateDisplayedObjects();

        fusedLocation = LocationServices.getFusedLocationProviderClient(this);

        startCamera();
        setupSensors();
        fetchLocation();
    }

    private void setupFilters() {
        filterPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        showPlanets = filterPrefs.getBoolean(PREF_SHOW_PLANETS, true);
        showStars = filterPrefs.getBoolean(PREF_SHOW_STARS, true);
        showSatellites = filterPrefs.getBoolean(PREF_SHOW_SATELLITES, true);

        cbPlanets.setChecked(showPlanets);
        cbStars.setChecked(showStars);
        cbSatellites.setChecked(showSatellites);

        cbPlanets.setOnCheckedChangeListener((buttonView, isChecked) -> {
            showPlanets = isChecked;
            filterPrefs.edit().putBoolean(PREF_SHOW_PLANETS, showPlanets).apply();
            updateDisplayedObjects();
        });
        cbStars.setOnCheckedChangeListener((buttonView, isChecked) -> {
            showStars = isChecked;
            filterPrefs.edit().putBoolean(PREF_SHOW_STARS, showStars).apply();
            updateDisplayedObjects();
        });
        cbSatellites.setOnCheckedChangeListener((buttonView, isChecked) -> {
            showSatellites = isChecked;
            filterPrefs.edit().putBoolean(PREF_SHOW_SATELLITES, showSatellites).apply();
            updateDisplayedObjects();
        });
    }

    private void updateDisplayedObjects() {
        List<CelestialObject> visible = new ArrayList<>();
        if (showPlanets) visible.addAll(solarSystemObjects);
        if (showStars) visible.addAll(starObjects);
        if (showSatellites) visible.addAll(satelliteObjects.values());
        skyOverlay.setObjects(visible.toArray(new CelestialObject[0]));
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
                provider.unbindAll();
                provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview);
            } catch (Exception e) {
                Log.e(TAG, "Camera error", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void setupSensors() {
        if (sensorsRegistered) return;

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager == null) return;

        rotationVectorSensor =
                sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        if (rotationVectorSensor != null) {
            sensorManager.registerListener(
                    sensorListener,
                    rotationVectorSensor,
                    SensorManager.SENSOR_DELAY_GAME);
            sensorsRegistered = true;
            return;
        }

        fallbackAccel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        fallbackMag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        if (fallbackAccel != null && fallbackMag != null) {
            sensorManager.registerListener(
                    sensorListener,
                    fallbackAccel,
                    SensorManager.SENSOR_DELAY_GAME);
            sensorManager.registerListener(
                    sensorListener,
                    fallbackMag,
                    SensorManager.SENSOR_DELAY_GAME);
            sensorsRegistered = true;
        }
    }

    private void handleRotationVector(float[] values) {
        float[] magneticDeviceToWorld = new float[9];
        SensorManager.getRotationMatrixFromVector(magneticDeviceToWorld, values);
        deliverTrueNorthMatrix(magneticDeviceToWorld);
    }

    private void handleFallback() {
        float[] magneticDeviceToWorld = new float[9];
        if (!SensorManager.getRotationMatrix(
                magneticDeviceToWorld,
                null,
                accelValues,
                magValues)) {
            return;
        }
        deliverTrueNorthMatrix(magneticDeviceToWorld);
    }

    private void deliverTrueNorthMatrix(float[] magneticDeviceToWorld) {
        float[] trueDeviceToWorld = applyDeclination(magneticDeviceToWorld);

        if (!orientationReady) {
            orientationReady = true;
            updateStatusText();
        }

        // Do not portrait-remap this matrix. Android's device frame is already
        // the physical phone frame: +X right, +Y top, +Z out of screen. The
        // portrait screen mapping belongs in SkyProjection, where +device Y is
        // converted to negative canvas Y.
        skyOverlay.setRotationMatrix(
                trueDeviceToWorld.clone(),
                trueDeviceToWorld.clone(),
                CAMERA_VFOV_PORTRAIT_DEG);
    }

    private float[] applyDeclination(float[] magneticDeviceToWorld) {
        float dec = (float) Math.toRadians(magneticDeclination);
        float cos = (float) Math.cos(dec);
        float sin = (float) Math.sin(dec);
        float[] trueDeviceToWorld = magneticDeviceToWorld.clone();

        for (int col = 0; col < 3; col++) {
            float eastMag = magneticDeviceToWorld[col];
            float northMag = magneticDeviceToWorld[col + 3];

            trueDeviceToWorld[col] = cos * eastMag + sin * northMag;
            trueDeviceToWorld[col + 3] = -sin * eastMag + cos * northMag;
        }

        return trueDeviceToWorld;
    }

    private void fetchLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocation.getLastLocation().addOnSuccessListener(this, location -> {
            if (location == null) return;

            userLat = location.getLatitude();
            userLon = location.getLongitude();
            userAltM = (float) location.getAltitude();
            locationReady = true;
            magneticDeclination = new GeomagneticField(
                    (float) userLat,
                    (float) userLon,
                    userAltM,
                    System.currentTimeMillis()).getDeclination();

            Log.d(TAG, String.format(
                    "Location ready: lat=%.6f lon=%.6f altM=%.1f declination=%.2f",
                    userLat,
                    userLon,
                    userAltM,
                    magneticDeclination));

            updateStatusText();
            startUpdating();
        });
    }

    private void startUpdating() {
        if (updateRunnable != null) {
            mainHandler.removeCallbacks(updateRunnable);
        }
        if (satelliteRunnable != null) {
            mainHandler.removeCallbacks(satelliteRunnable);
        }

        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateCelestialPositions();
                updateBrightStars();
                mainHandler.postDelayed(this, 60_000);
            }
        };

        satelliteRunnable = new Runnable() {
            @Override
            public void run() {
                updateSatellites();
                mainHandler.postDelayed(this, 10_000);
            }
        };

        mainHandler.post(updateRunnable);
        mainHandler.post(satelliteRunnable);
    }

    private void updateCelestialPositions() {
        if (!locationReady) return;

        astronomyFetcher.fetch(userLat, userLon, new AstronomyApiFetcher.Listener() {
            @Override
            public void onResult(List<AstronomyApiFetcher.BodyPosition> positions) {
                for (AstronomyApiFetcher.BodyPosition bp : positions) {
                    for (CelestialObject obj : solarSystemObjects) {
                        if (obj.name.equalsIgnoreCase(bp.name)) {
                            obj.azimuth = bp.azimuth;
                            obj.elevation = bp.elevation;
                            obj.showLabel = true;
                            break;
                        }
                    }
                }
                mainHandler.post(() -> {
                    celestialObjectsReady = true;
                    updateDisplayedObjects();
                    updateStatusText();
                });
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, message);
            }
        });
    }

    private void updateBrightStars() {
        if (!locationReady) return;

        List<CelestialObject> visibleStars =
                BrightStar.visibleStars(userLat, userLon, System.currentTimeMillis());
        mainHandler.post(() -> {
            starObjects.clear();
            starObjects.addAll(visibleStars);
            updateDisplayedObjects();
        });
    }

    private void updateSatellites() {
        if (!locationReady) return;
        updateISS();
        updateAdditionalSatellite(new SatelliteTarget(
                "Hubble",
                20580,
                CelestialObject.Type.HUBBLE,
                Color.parseColor("#8EE6FF"),
                "✦",
                16f));
        updateAdditionalSatellite(new SatelliteTarget(
                "Tiangong",
                48274,
                CelestialObject.Type.TIANGONG,
                Color.parseColor("#FFDE7A"),
                "✦",
                16f));
    }

    private void updateISS() {
        issFetcher.fetchLookAngles(userLat, userLon, userAltM, new ISSFetcher.LookAngleListener() {
            @Override
            public void onResult(
                    double issLat,
                    double issLon,
                    double issAltKm,
                    double azimuth,
                    double elevation,
                    double slantRangeKm) {
                Log.d(TAG, String.format(
                        "ISS AR update: lat=%.6f lon=%.6f altKm=%.3f "
                                + "az=%.2f el=%.2f slantRangeKm=%.2f",
                        issLat,
                        issLon,
                        issAltKm,
                        azimuth,
                        elevation,
                        slantRangeKm));

                CelestialObject iss = new CelestialObject(
                        "ISS",
                        CelestialObject.Type.ISS,
                        Color.parseColor("#00E5FF"),
                        "🛰",
                        20f);
                iss.azimuth = azimuth;
                iss.elevation = elevation;
                iss.showLabel = true;

                mainHandler.post(() -> {
                    satelliteObjects.put(CelestialObject.Type.ISS, iss);
                    issReady = true;
                    updateDisplayedObjects();
                    updateStatusText();
                });
            }

            @Override
            public void onError(String msg) {
                Log.e(TAG, "ISS update failed: " + msg);
                mainHandler.post(() -> updateStatusText());
            }
        });
    }

    private void updateAdditionalSatellite(SatelliteTarget target) {
        satelliteTracker.fetchLookAngles(
                target,
                userLat,
                userLon,
                userAltM,
                new SatelliteTracker.Listener() {
                    @Override
                    public void onResult(CelestialObject object, double slantRangeKm) {
                        mainHandler.post(() -> {
                            satelliteObjects.put(object.type, object);
                            updateDisplayedObjects();
                        });
                    }

                    @Override
                    public void onError(SatelliteTarget target, String message) {
                        Log.e(TAG, target.name + " update failed: " + message);
                    }
                });
    }

    private void updateStatusText() {
        if (tvStatus == null) return;

        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::updateStatusText);
            return;
        }

        Log.d(TAG, "AR readiness: location=" + locationReady
                + " orientation=" + orientationReady
                + " celestial=" + celestialObjectsReady
                + " iss=" + issReady);

        if (locationReady && orientationReady && celestialObjectsReady) {
            tvStatus.setText("Ready");
            tvStatus.postDelayed(() -> {
                if (tvStatus != null
                        && locationReady
                        && orientationReady
                        && celestialObjectsReady) {
                    tvStatus.setVisibility(View.GONE);
                }
            }, 1200);
            return;
        }

        tvStatus.setVisibility(View.VISIBLE);

        if (!locationReady) {
            tvStatus.setText("Acquiring location...");
        } else if (!orientationReady) {
            tvStatus.setText("Calibrating orientation...");
        } else {
            tvStatus.setText("Loading sky objects...");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupSensors();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null && sensorsRegistered) {
            sensorManager.unregisterListener(sensorListener);
        }
        sensorsRegistered = false;
        accelReady = false;
        magReady = false;
        orientationReady = false;
        updateStatusText();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updateRunnable != null) {
            mainHandler.removeCallbacks(updateRunnable);
        }
        if (satelliteRunnable != null) {
            mainHandler.removeCallbacks(satelliteRunnable);
        }
        satelliteTracker.shutdown();
        executor.shutdownNow();
    }
}
