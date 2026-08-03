package com.example.isst;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TonightSkyActivity extends AppCompatActivity {

    private static final String TAG = "TonightSky";

    // ── Views ─────────────────────────────────────────────────────────────
    private TextView     tvWeatherStatus, tvWeatherDetail;
    private TextView     tvCloudCover, tvVisibilityRating;
    private LinearLayout cardWeather;

    private TextView     tvPlanetsContent;
    private LinearLayout cardPlanets;
    private ProgressBar  progressPlanets;

    private TextView     tvISSPass;
    private LinearLayout cardISS;
    private ProgressBar  progressISS;

    private TextView     tvViewingTime;

    // ── Fetchers ──────────────────────────────────────────────────────────
    private final WeatherFetcher        weatherFetcher   = new WeatherFetcher();
    private final AstronomyApiFetcher   astronomyFetcher = new AstronomyApiFetcher();
    private final OkHttpClient          httpClient       = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    // ── Location ──────────────────────────────────────────────────────────
    private FusedLocationProviderClient fusedLocation;
    private double userLat = 0, userLon = 0;
    private boolean locationReady = false;

    // ════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tonight_sky);

        bindViews();
        setupBackButton();

        fusedLocation = LocationServices.getFusedLocationProviderClient(this);
        getLocation();
    }

    private void bindViews() {
        tvWeatherStatus    = findViewById(R.id.tvWeatherStatus);
        tvWeatherDetail    = findViewById(R.id.tvWeatherDetail);
        tvCloudCover       = findViewById(R.id.tvCloudCover);
        tvVisibilityRating = findViewById(R.id.tvVisibilityRating);
        cardWeather        = findViewById(R.id.cardWeather);

        tvPlanetsContent   = findViewById(R.id.tvPlanetsContent);
        cardPlanets        = findViewById(R.id.cardPlanets);
        progressPlanets    = findViewById(R.id.progressPlanets);

        tvISSPass          = findViewById(R.id.tvISSPass);
        cardISS            = findViewById(R.id.cardISS);
        progressISS        = findViewById(R.id.progressISS);

        tvViewingTime      = findViewById(R.id.tvViewingTime);
    }

    private void setupBackButton() {
        View btn = findViewById(R.id.btnBack);
        if (btn != null) btn.setOnClickListener(v -> finish());
    }

    // ── Location ──────────────────────────────────────────────────────────

    private void getLocation() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            showAllError("Location permission not granted.\nGo to Settings → Permissions → Location.");
            return;
        }

        fusedLocation.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                userLat = location.getLatitude();
                userLon = location.getLongitude();
                locationReady = true;
                fetchAll();
            } else {
                showAllError("Could not get GPS location.\nPlease go outdoors or near a window.");
            }
        });
    }

    private void fetchAll() {
        fetchWeather();
        fetchPlanets();
        fetchISSPasses();
    }

    // ── Weather / Sky Conditions ──────────────────────────────────────────

    private void fetchWeather() {
        if (!SpaceApiConfig.isWeatherConfigured()) {
            runOnUiThread(() -> {
                tvWeatherStatus.setText("API key not configured");
                tvWeatherDetail.setText(
                        "Add your OpenWeatherMap key to SpaceApiConfig.java\n" +
                                "Get a free key at openweathermap.org/api");
                tvCloudCover.setText("Cloud cover: —");
                tvVisibilityRating.setText("Visibility: —");
                tvVisibilityRating.setTextColor(
                        getColor(R.color.text_secondary));
            });
            return;
        }

        weatherFetcher.fetch(userLat, userLon, new WeatherFetcher.Listener() {
            @Override
            public void onResult(WeatherFetcher.WeatherResult r) {
                runOnUiThread(() -> {
                    tvWeatherStatus.setText(r.description);
                    tvWeatherDetail.setText(
                            String.format("Temp: %.0f°C   Humidity: %.0f%%", r.tempC, r.humidity));
                    tvCloudCover.setText("Cloud cover: " + r.cloudPercent + "%");
                    tvVisibilityRating.setText(r.visibility);
                    tvVisibilityRating.setTextColor(r.visibilityColor);

                    // Set viewing time recommendation
                    if (r.cloudPercent <= 30) {
                        tvViewingTime.setText("Tonight looks great for stargazing! " +
                                "Best viewing usually 1–2 hours after sunset.");
                    } else if (r.cloudPercent <= 60) {
                        tvViewingTime.setText("Partly cloudy tonight. " +
                                "Look for clear patches between clouds.");
                    } else {
                        tvViewingTime.setText("Heavy cloud cover expected. " +
                                "Stargazing may be difficult tonight.");
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    tvWeatherStatus.setText("Sky conditions unavailable");
                    tvWeatherDetail.setText(message);
                    tvCloudCover.setText("");
                    tvVisibilityRating.setText("");
                });
            }
        });
    }

    // ── Planets / Sun / Moon ──────────────────────────────────────────────

    private void fetchPlanets() {
        runOnUiThread(() -> {
            progressPlanets.setVisibility(View.VISIBLE);
            tvPlanetsContent.setText("Loading...");
        });

        if (!SpaceApiConfig.isAstronomyConfigured()) {
            runOnUiThread(() -> {
                progressPlanets.setVisibility(View.GONE);
                tvPlanetsContent.setText(
                        "AstronomyAPI keys not configured.\n\n" +
                                "1. Register free at astronomyapi.com\n" +
                                "2. Copy your Application ID and Secret\n" +
                                "3. Paste into SpaceApiConfig.java\n\n" +
                                "Student plan is free and sufficient.");
            });
            return;
        }

        astronomyFetcher.fetch(userLat, userLon, new AstronomyApiFetcher.Listener() {
            @Override
            public void onResult(List<AstronomyApiFetcher.BodyPosition> positions) {
                StringBuilder sb = new StringBuilder();
                boolean anyAbove = false;

                // Order we want to display
                String[] order = {"Sun","Moon","Mercury","Venus","Mars","Jupiter","Saturn"};
                for (String name : order) {
                    for (AstronomyApiFetcher.BodyPosition bp : positions) {
                        if (bp.name.equalsIgnoreCase(name)) {
                            String emoji = getEmoji(bp.name);
                            if (bp.aboveHorizon) {
                                anyAbove = true;
                                sb.append(emoji).append("  ").append(bp.name).append("\n");
                                sb.append(String.format("     Az %.0f°   El %.0f°",
                                        bp.azimuth, bp.elevation));
                                if (bp.constellation != null && !bp.constellation.isEmpty()) {
                                    sb.append("   ").append(bp.constellation);
                                }
                                sb.append("\n\n");
                            } else {
                                sb.append(emoji).append("  ").append(bp.name)
                                        .append("  (below horizon)\n\n");
                            }
                            break;
                        }
                    }
                }

                if (sb.length() == 0) {
                    sb.append("No data returned from API.");
                }

                final String text = sb.toString().trim();
                runOnUiThread(() -> {
                    progressPlanets.setVisibility(View.GONE);
                    tvPlanetsContent.setText(text);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressPlanets.setVisibility(View.GONE);
                    tvPlanetsContent.setText(message);
                });
            }
        });
    }

    // ── ISS Passes ────────────────────────────────────────────────────────

    private void fetchISSPasses() {
        runOnUiThread(() -> {
            progressISS.setVisibility(View.VISIBLE);
            tvISSPass.setText("Loading...");
        });

        if (!SpaceApiConfig.isN2YOConfigured()) {
            runOnUiThread(() -> {
                progressISS.setVisibility(View.GONE);
                tvISSPass.setText(
                        "N2YO key not configured.\n\n" +
                                "1. Register free at n2yo.com/api\n" +
                                "2. Copy your API key\n" +
                                "3. Paste into SpaceApiConfig.java");
            });
            return;
        }

        String url = "https://api.n2yo.com/rest/v1/satellite/visualpasses/25544/"
                + userLat + "/" + userLon + "/0/3/300/"
                + "&apiKey=" + SpaceApiConfig.N2YO_KEY;

        httpClient.newCall(new Request.Builder().url(url).build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runOnUiThread(() -> {
                            progressISS.setVisibility(View.GONE);
                            tvISSPass.setText("No internet connection.\n" + e.getMessage());
                        });
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String body = "";
                        try {
                            body = response.body().string();

                            if (response.code() == 401 || response.code() == 403) {
                                final String err = "Invalid N2YO API key.\nCheck SpaceApiConfig.java";
                                runOnUiThread(() -> {
                                    progressISS.setVisibility(View.GONE);
                                    tvISSPass.setText(err);
                                });
                                return;
                            }
                            if (!response.isSuccessful()) {
                                final int code = response.code();
                                runOnUiThread(() -> {
                                    progressISS.setVisibility(View.GONE);
                                    tvISSPass.setText("N2YO error " + code);
                                });
                                return;
                            }

                            JSONObject obj = new JSONObject(body);

                            if (!obj.has("passes")) {
                                runOnUiThread(() -> {
                                    progressISS.setVisibility(View.GONE);
                                    tvISSPass.setText("No ISS passes found in the next 10 days.\n" +
                                            "This is normal — the ISS orbit varies.");
                                });
                                return;
                            }

                            JSONArray passes = obj.getJSONArray("passes");
                            SimpleDateFormat sdf = new SimpleDateFormat(
                                    "EEE dd MMM  hh:mm a", Locale.getDefault());

                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < passes.length(); i++) {
                                JSONObject pass = passes.getJSONObject(i);
                                long   riseUTC  = pass.getLong("startUTC") * 1000L;
                                int    duration = pass.getInt("duration");
                                double maxEl    = pass.getDouble("maxEl");
                                String qual     = maxEl > 60 ? "★ Excellent"
                                        : maxEl > 30 ? "Good"
                                        : "Low";

                                sb.append("🛰  ").append(sdf.format(new Date(riseUTC))).append("\n");
                                sb.append("     Duration: ").append(duration / 60).append("m ")
                                        .append(duration % 60).append("s")
                                        .append("   Max: ").append(String.format("%.0f", maxEl)).append("°")
                                        .append("   ").append(qual).append("\n\n");
                            }

                            final String text = sb.toString().trim();
                            runOnUiThread(() -> {
                                progressISS.setVisibility(View.GONE);
                                tvISSPass.setText(text.isEmpty() ? "No passes found." : text);
                            });

                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                progressISS.setVisibility(View.GONE);
                                tvISSPass.setText("Failed to read ISS pass data.");
                            });
                        } finally {
                            response.close();
                        }
                    }
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void showAllError(String msg) {
        runOnUiThread(() -> {
            tvWeatherStatus.setText(msg);
            tvWeatherDetail.setText("");
            tvCloudCover.setText("");
            tvVisibilityRating.setText("");
            tvPlanetsContent.setText(msg);
            tvISSPass.setText(msg);
            if (progressPlanets != null) progressPlanets.setVisibility(View.GONE);
            if (progressISS     != null) progressISS.setVisibility(View.GONE);
        });
    }

    private String getEmoji(String name) {
        switch (name.toLowerCase()) {
            case "sun":     return "☀";
            case "moon":    return "🌙";
            case "mercury": return "☿";
            case "venus":   return "♀";
            case "mars":    return "♂";
            case "jupiter": return "♃";
            case "saturn":  return "♄";
            default:        return "✦";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        weatherFetcher.cancel();
        astronomyFetcher.cancel();
    }
}