package com.example.isst;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import android.widget.Switch;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ISS_APP";
    private static final String CHANNEL_ID = "ISS_CHANNEL";
    private static final int NOTIF_ID = 1;
    private static final String PREFS_NAME = "startrack_prefs";
    private static final String PREF_ISS_NOTIFICATIONS = "iss_notifications_enabled";
    private static final String N2YO_API_KEY = SpaceApiConfig.N2YO_KEY;

    LinearLayout btnTonightSky, btnChatbot, btnEducation, btnARCamera;
    TextView textViewISS;
    TextView textViewGuidance;
    TextView txtLocation;
    TextView txtVisiblePlanets;
    TextView txtBestViewing;
    TextView txtSkyCondition;
    TextView txtNotificationTitle;
    TextView txtNotificationSubtitle;
    Switch switchNotifications;
    ImageView arrowView;
    ImageView issIcon;

    Handler handler;
    Handler passHandler;
    Handler dashboardHandler;
    Handler notificationHandler;
    OkHttpClient client;
    AstronomyApiFetcher astronomyApiFetcher;
    WeatherFetcher weatherFetcher;
    SharedPreferences prefs;

    FusedLocationProviderClient fusedLocationClient;
    LocationCallback locationCallback;
    LocationRequest locationRequest;
    double userLat = 0;
    double userLon = 0;
    boolean locationReceived = false;
    boolean passUpdatesStarted = false;
    boolean dashboardUpdatesStarted = false;
    boolean issNotificationsEnabled = false;

    double issLat = 0;
    double issLon = 0;
    double issAltitudeKm = 408.0;

    SensorManager sensorManager;
    float[] gravity = new float[3];
    float[] magnetic = new float[3];
    double phoneAzimuth = 0;
    double phonePitch = 0;

    double issAzimuth = 0;
    double issElevation = 0;
    double azimuthDiff = 0;
    double elevationDiff = 0;
    boolean issInView = false;

    String passesText = "Fetching passes...";
    long lastNotifTime = 0;
    long scheduledNotificationPassUtc = 0;
    Runnable dashboardRunnable;
    Runnable pendingPassNotificationRunnable;

    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                gravity = lowPassFilter(event.values.clone(), gravity);
            }

            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                magnetic = lowPassFilter(event.values.clone(), magnetic);
            }

            float[] rotationMatrix = new float[9];
            float[] orientation = new float[3];

            if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, magnetic)) {
                SensorManager.getOrientation(rotationMatrix, orientation);
                phoneAzimuth = Math.toDegrees(orientation[0]);
                phonePitch = Math.toDegrees(orientation[1]);
                phoneAzimuth = (phoneAzimuth + 360.0) % 360.0;
                calculateDifference();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NotificationHelper.createChannel(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }

        handler = new Handler(getMainLooper());
        passHandler = new Handler(getMainLooper());
        dashboardHandler = new Handler(getMainLooper());
        notificationHandler = new Handler(getMainLooper());
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        issNotificationsEnabled = prefs.getBoolean(PREF_ISS_NOTIFICATIONS, false);

        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        astronomyApiFetcher = new AstronomyApiFetcher();
        weatherFetcher = new WeatherFetcher();

        textViewISS = findViewById(R.id.textViewISS);
        textViewGuidance = findViewById(R.id.textViewGuidance);
        txtLocation = findViewById(R.id.txtLocation);
        txtVisiblePlanets = findViewById(R.id.txtVisiblePlanets);
        txtBestViewing = findViewById(R.id.txtBestViewing);
        txtSkyCondition = findViewById(R.id.txtSkyCondition);
        txtNotificationTitle = findViewById(R.id.txtNotificationTitle);
        txtNotificationSubtitle = findViewById(R.id.txtNotificationSubtitle);
        switchNotifications = findViewById(R.id.switchNotifications);
        arrowView = findViewById(R.id.arrowView);
        issIcon = findViewById(R.id.issIcon);

        btnTonightSky = findViewById(R.id.btnTonightSky);
        btnChatbot = findViewById(R.id.btnChatbot);
        btnEducation = findViewById(R.id.btnEducation);
        btnARCamera = findViewById(R.id.btnARCamera);

        btnTonightSky.setOnClickListener(v ->
                startActivity(new Intent(this, TonightSkyActivity.class)));
        btnChatbot.setOnClickListener(v ->
                startActivity(new Intent(this, ChatbotActivity.class)));
        btnEducation.setOnClickListener(v ->
                startActivity(new Intent(this, EducationalActivity.class)));
        btnARCamera.setOnClickListener(v ->
                startActivity(new Intent(this, ARCameraActivity.class)));

        textViewISS.setText("Starting...");
        txtLocation.setText("Locating...");
        txtVisiblePlanets.setText("Loading...");
        txtBestViewing.setText("Checking...");
        txtSkyCondition.setText("Loading...");
        setupNotificationToggle();

        locationRequest = LocationRequest.create()
                .setInterval(5000)
                .setFastestInterval(2000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setSmallestDisplacement(10f);

        requestAllPermissions();
        createNotificationChannel();
        registerSensors();
        startFetchingISS();
    }

    private void setupNotificationToggle() {
        switchNotifications.setOnCheckedChangeListener(null);
        switchNotifications.setChecked(issNotificationsEnabled);
        updateNotificationToggleUi();
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            issNotificationsEnabled = isChecked;
            prefs.edit()
                    .putBoolean(PREF_ISS_NOTIFICATIONS, issNotificationsEnabled)
                    .apply();
            updateNotificationToggleUi();

            if (issNotificationsEnabled) {
                startISSNotificationMonitoring();
            } else {
                stopISSNotificationMonitoring();
            }
        });

        if (issNotificationsEnabled) {
            startISSNotificationMonitoring();
        } else {
            stopISSNotificationMonitoring();
        }
    }

    private void updateNotificationToggleUi() {
        String state = issNotificationsEnabled
                ? "\uD83D\uDD14 ISS Notifications ON"
                : "\uD83D\uDD14 ISS Notifications OFF";
        switchNotifications.setText(state);
        txtNotificationTitle.setText(state);
        txtNotificationSubtitle.setText(issNotificationsEnabled
                ? "Alerts scheduled from upcoming ISS passes"
                : "Tap to enable ISS pass alerts");
        switchNotifications.setTextColor(android.graphics.Color.parseColor(
                issNotificationsEnabled ? "#00FF9C" : "#8FA3B8"));
    }

    private void startISSNotificationMonitoring() {
        if (!locationReceived) return;
        fetchISSPasses();
    }

    private void stopISSNotificationMonitoring() {
        cancelScheduledISSNotification();
        NotificationManagerCompat.from(this).cancel(NOTIF_ID);
        lastNotifTime = 0;
        scheduledNotificationPassUtc = 0;
    }

    private void cancelScheduledISSNotification() {
        if (pendingPassNotificationRunnable != null) {
            notificationHandler.removeCallbacks(pendingPassNotificationRunnable);
            pendingPassNotificationRunnable = null;
        }
    }

    private void requestAllPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            setupLocation();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, 2);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupLocation();
            } else {
                textViewISS.setText("Location permission denied.\nCannot calculate ISS direction.");
            }
        }
    }

    private void setupLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                Location location = locationResult.getLastLocation();
                if (location == null) return;

                userLat = location.getLatitude();
                userLon = location.getLongitude();
                locationReceived = true;
                Log.d(TAG, "Location updated: " + userLat + ", " + userLon);
                updateLocationCard();
                refreshDashboardCards();
                if (issNotificationsEnabled) {
                    startISSNotificationMonitoring();
                }
            }
        };

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    userLat = location.getLatitude();
                    userLon = location.getLongitude();
                    locationReceived = true;
                    Log.d(TAG, "Last known location: " + userLat + ", " + userLon);
                    updateLocationCard();
                    startFetchingPasses();
                    startDashboardUpdates();
                    if (issNotificationsEnabled) {
                        startISSNotificationMonitoring();
                    }
                } else {
                    Log.d(TAG, "No last known location, waiting for GPS...");
                    runOnUiThread(() ->
                            textViewGuidance.setText("Waiting for GPS...\nPlease go near a window"));
                }
            });

            fusedLocationClient.requestLocationUpdates(
                    locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    private void updateLocationCard() {
        runOnUiThread(() -> txtLocation.setText(String.format(
                Locale.getDefault(),
                "%.2f, %.2f",
                userLat,
                userLon)));
    }

    private void startDashboardUpdates() {
        if (dashboardUpdatesStarted) return;
        dashboardUpdatesStarted = true;

        dashboardRunnable = new Runnable() {
            @Override
            public void run() {
                refreshDashboardCards();
                dashboardHandler.postDelayed(this, 10 * 60 * 1000);
            }
        };
        dashboardHandler.post(dashboardRunnable);
    }

    private void refreshDashboardCards() {
        if (!locationReceived) return;
        fetchSkyCondition();
        fetchVisiblePlanets();
    }

    private void fetchSkyCondition() {
        weatherFetcher.fetch(userLat, userLon, new WeatherFetcher.Listener() {
            @Override
            public void onResult(WeatherFetcher.WeatherResult result) {
                String status;
                if (result.cloudPercent < 20) {
                    status = "Excellent";
                } else if (result.cloudPercent <= 50) {
                    status = "Good";
                } else {
                    status = "Poor";
                }

                runOnUiThread(() -> txtSkyCondition.setText(status));
                Log.d(TAG, "Sky condition: clouds=" + result.cloudPercent + "% status=" + status);
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Weather card failed: " + message);
                runOnUiThread(() -> txtSkyCondition.setText("Unavailable"));
            }
        });
    }

    private void fetchVisiblePlanets() {
        astronomyApiFetcher.fetch(userLat, userLon, new AstronomyApiFetcher.Listener() {
            @Override
            public void onResult(java.util.List<AstronomyApiFetcher.BodyPosition> positions) {
                int visibleCount = 0;
                for (AstronomyApiFetcher.BodyPosition body : positions) {
                    if (body.aboveHorizon && isPlanetName(body.name)) {
                        visibleCount++;
                    }
                }

                final int finalVisibleCount = visibleCount;
                runOnUiThread(() -> txtVisiblePlanets.setText(String.valueOf(finalVisibleCount)));
                Log.d(TAG, "Visible planets above horizon: " + finalVisibleCount);
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Visible planets card failed: " + message);
                runOnUiThread(() -> txtVisiblePlanets.setText("--"));
            }
        });
    }

    private boolean isPlanetName(String name) {
        if (name == null) return false;
        switch (name.toLowerCase(Locale.US)) {
            case "mercury":
            case "venus":
            case "mars":
            case "jupiter":
            case "saturn":
            case "uranus":
            case "neptune":
                return true;
            default:
                return false;
        }
    }

    private void calculateISSDirection() {
        if (!locationReceived) {
            runOnUiThread(() ->
                    textViewGuidance.setText("Waiting for GPS fix...\nPlease go near a window"));
            return;
        }

        double[] azEl = SkyProjection.issAzEl(
                userLat,
                userLon,
                issLat,
                issLon,
                issAltitudeKm);

        issAzimuth = azEl[0];
        issElevation = azEl[1];

        Log.d(TAG, String.format(
                "ISS direction from SkyProjection: issLat=%.6f issLon=%.6f altKm=%.3f "
                        + "az=%.2f el=%.2f",
                issLat,
                issLon,
                issAltitudeKm,
                issAzimuth,
                issElevation));
    }

    private void registerSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener(sensorListener,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(sensorListener,
                sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_UI);
    }

    private float[] lowPassFilter(float[] input, float[] output) {
        float alpha = 0.1f;
        for (int i = 0; i < input.length; i++) {
            output[i] = output[i] + alpha * (input[i] - output[i]);
        }
        return output;
    }

    private void calculateDifference() {
        azimuthDiff = issAzimuth - phoneAzimuth;

        // SensorManager pitch is negative when the phone is tilted upward in
        // this home-screen guidance mode, so camera elevation is -phonePitch.
        elevationDiff = issElevation - (-phonePitch);

        if (azimuthDiff > 180) azimuthDiff -= 360;
        if (azimuthDiff < -180) azimuthDiff += 360;

        issInView = Math.abs(azimuthDiff) < 5 && Math.abs(elevationDiff) < 5;

        updateDisplay();
        moveISSIcon();

        if (issNotificationsEnabled && issElevation > 10) sendISSNotification();
    }

    private void updateDisplay() {
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = (int) ((issAzimuth + 22.5) / 45) % 8;
        String dirLabel = directions[index];

        String viewStatus = issInView ? "ISS IN VIEW!" : "Not aligned";

        String guidance;
        if (azimuthDiff > 0) {
            guidance = "Turn RIGHT " + (int) azimuthDiff + " deg";
        } else if (azimuthDiff < 0) {
            guidance = "Turn LEFT " + (int) Math.abs(azimuthDiff) + " deg";
        } else {
            guidance = "Perfect direction!";
        }

        String tilt;
        if (elevationDiff > 0) {
            tilt = "Tilt UP " + (int) elevationDiff + " deg";
        } else if (elevationDiff < 0) {
            tilt = "Tilt DOWN " + (int) Math.abs(elevationDiff) + " deg";
        } else {
            tilt = "Perfect tilt!";
        }

        runOnUiThread(() -> {
            arrowView.setRotation((float) azimuthDiff);

            int arrowColor = issInView
                    ? android.graphics.Color.parseColor("#00FF9C")
                    : android.graphics.Color.parseColor("#00D4FF");
            arrowView.setColorFilter(arrowColor, android.graphics.PorterDuff.Mode.SRC_IN);

            textViewGuidance.setText(
                    "Look " + dirLabel + " - " + (int) issElevation + " deg above horizon\n"
                            + guidance + "  |  " + tilt + "\n\n" + viewStatus);

            textViewISS.setText(
                    "ISS  " + String.format("%.2f", issLat) + " / "
                            + String.format("%.2f", issLon) + "\n"
                            + "You  " + String.format("%.2f", userLat) + " / "
                            + String.format("%.2f", userLon) + "\n"
                            + "Az: " + String.format("%.1f", phoneAzimuth) + " deg  "
                            + "Pitch: " + String.format("%.1f", phonePitch) + " deg\n"
                            + passesText);
        });
    }

    private void moveISSIcon() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        float pixelsPerDegree = 20f;

        float x = (screenWidth / 2f) + (float) (azimuthDiff * pixelsPerDegree);
        float y = (screenHeight / 2f) - (float) (elevationDiff * pixelsPerDegree);

        runOnUiThread(() -> {
            issIcon.setX(x - issIcon.getWidth() / 2f);
            issIcon.setY(y - issIcon.getHeight() / 2f);

            int iconColor = issInView
                    ? android.graphics.Color.parseColor("#00FF9C")
                    : android.graphics.Color.parseColor("#FF4757");
            issIcon.setColorFilter(iconColor, android.graphics.PorterDuff.Mode.SRC_IN);
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "ISS Alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifies when ISS is visible overhead");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void sendISSNotification() {
        if (!issNotificationsEnabled) return;

        long now = System.currentTimeMillis();
        if (now - lastNotifTime < 5 * 60 * 1000) return;
        lastNotifTime = now;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;

        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = (int) ((issAzimuth + 22.5) / 45) % 8;

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_arrow)
                        .setContentTitle("ISS is visible!")
                        .setContentText("Look " + directions[index]
                                + " at " + (int) issElevation + " deg elevation")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);

        NotificationManagerCompat.from(this).notify(NOTIF_ID, builder.build());
        Log.d(TAG, "ISS notification sent");
    }

    private void startFetchingPasses() {
        if (passUpdatesStarted) return;
        passUpdatesStarted = true;

        passHandler.post(new Runnable() {
            @Override
            public void run() {
                fetchISSPasses();
                passHandler.postDelayed(this, 10 * 60 * 1000);
            }
        });
    }

    private void fetchISSPasses() {
        if (!locationReceived) return;

        String url = "https://api.n2yo.com/rest/v1/satellite/visualpasses/25544/"
                + userLat + "/" + userLon + "/0/4/300/&apiKey=" + N2YO_API_KEY;

        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Pass fetch failed: " + e.getMessage(), e);
                passesText = "\nPasses: Failed to load";
                runOnUiThread(() -> txtBestViewing.setText("Unavailable"));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        throw new IOException("HTTP error: " + response.code());
                    }

                    String jsonData = response.body().string();
                    JSONObject obj = new JSONObject(jsonData);

                    if (!obj.has("passes")) {
                        passesText = "\nPasses: None found";
                        runOnUiThread(() -> txtBestViewing.setText("No pass tonight"));
                        return;
                    }

                    org.json.JSONArray passes = obj.getJSONArray("passes");
                    if (passes.length() == 0) {
                        passesText = "\nPasses: None found";
                        runOnUiThread(() -> txtBestViewing.setText("No pass tonight"));
                        return;
                    }

                    JSONObject firstPass = passes.getJSONObject(0);
                    long firstRiseTime = firstPass.getLong("startUTC") * 1000L;
                    SimpleDateFormat timeOnly = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    String nextPassTime = timeOnly.format(new Date(firstRiseTime));
                    runOnUiThread(() -> txtBestViewing.setText(nextPassTime));
                    scheduleISSNotificationForPass(firstRiseTime);

                    StringBuilder sb = new StringBuilder("\nUpcoming passes:\n");
                    SimpleDateFormat sdf = new SimpleDateFormat(
                            "dd MMM  hh:mm a", Locale.getDefault());

                    for (int i = 0; i < passes.length(); i++) {
                        JSONObject pass = passes.getJSONObject(i);
                        long riseTime = pass.getLong("startUTC") * 1000L;
                        int duration = pass.getInt("duration");
                        double maxEl = pass.getDouble("maxEl");
                        String time = sdf.format(new java.util.Date(riseTime));
                        sb.append("#").append(i + 1).append(" ").append(time)
                                .append("  ").append(duration / 60).append("m")
                                .append("  Max: ").append(String.format("%.0f", maxEl)).append(" deg\n");
                    }
                    passesText = sb.toString();

                } catch (Exception e) {
                    Log.e(TAG, "Pass parse error: " + e.getMessage(), e);
                    passesText = "\nPasses: Parse error";
                    runOnUiThread(() -> txtBestViewing.setText("Unavailable"));
                } finally {
                    response.close();
                }
            }
        });
    }

    private void scheduleISSNotificationForPass(long passStartMillis) {
        if (!issNotificationsEnabled) return;
        if (passStartMillis <= 0) return;

        if (scheduledNotificationPassUtc == passStartMillis
                && pendingPassNotificationRunnable != null) {
            return;
        }

        cancelScheduledISSNotification();
        scheduledNotificationPassUtc = passStartMillis;

        long delayMillis = passStartMillis - System.currentTimeMillis();
        if (delayMillis < 0) delayMillis = 0;

        pendingPassNotificationRunnable = () -> {
            if (!issNotificationsEnabled) return;
            sendISSPassNotification(passStartMillis);
            pendingPassNotificationRunnable = null;
        };

        notificationHandler.postDelayed(pendingPassNotificationRunnable, delayMillis);
        Log.d(TAG, "Scheduled ISS pass notification in " + delayMillis + " ms");
    }

    private void sendISSPassNotification(long passStartMillis) {
        if (!issNotificationsEnabled) return;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;

        String time = new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date(passStartMillis));

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_arrow)
                        .setContentTitle("ISS pass starting")
                        .setContentText("Look for the ISS around " + time)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);

        NotificationManagerCompat.from(this).notify(NOTIF_ID, builder.build());
        lastNotifTime = System.currentTimeMillis();
        Log.d(TAG, "Scheduled ISS pass notification sent");
    }

    private void startFetchingISS() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                fetchISSPosition();
                handler.postDelayed(this, 3000);
            }
        });
    }

    private void fetchISSPosition() {
        client.newCall(new Request.Builder()
                .url("https://api.wheretheiss.at/v1/satellites/25544")
                .build()).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "ISS fetch failed: " + e.getMessage(), e);
                runOnUiThread(() -> textViewISS.setText("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        throw new IOException("HTTP error: " + response.code());
                    }

                    String jsonData = response.body().string();
                    JSONObject obj = new JSONObject(jsonData);
                    issLat = obj.getDouble("latitude");
                    issLon = obj.getDouble("longitude");
                    issAltitudeKm = obj.optDouble("altitude", 408.0);

                    Log.d(TAG, String.format(
                            "ISS position: lat=%.6f lon=%.6f altKm=%.3f",
                            issLat,
                            issLon,
                            issAltitudeKm));
                    calculateISSDirection();

                } catch (Exception e) {
                    Log.e(TAG, "Parsing error: " + e.getMessage(), e);
                    runOnUiThread(() -> textViewISS.setText("Parse error: " + e.getMessage()));
                } finally {
                    response.close();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerSensors();
        startFetchingISS();
        if (locationReceived) startFetchingPasses();
        if (locationReceived) startDashboardUpdates();
        if (locationReceived && issNotificationsEnabled) startISSNotificationMonitoring();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(sensorListener);
        handler.removeCallbacksAndMessages(null);
        passHandler.removeCallbacksAndMessages(null);
        dashboardHandler.removeCallbacksAndMessages(null);
        notificationHandler.removeCallbacksAndMessages(null);
        passUpdatesStarted = false;
        dashboardUpdatesStarted = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        passHandler.removeCallbacksAndMessages(null);
        dashboardHandler.removeCallbacksAndMessages(null);
        notificationHandler.removeCallbacksAndMessages(null);
        astronomyApiFetcher.cancel();
        weatherFetcher.cancel();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}
