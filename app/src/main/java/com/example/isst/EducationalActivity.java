package com.example.isst;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONObject;

import android.widget.ImageButton;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class EducationalActivity extends AppCompatActivity {

    private static final String TAG = "EDUCATIONAL";

    TextView textISSInfo;
    TextView textOrbitInfo;
    TextView textLiveStats;
    TextView textHistory;

    OkHttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_educational);

        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        textISSInfo   = findViewById(R.id.textISSInfo);
        textOrbitInfo = findViewById(R.id.textOrbitInfo);
        textLiveStats = findViewById(R.id.textLiveStats);
        textHistory   = findViewById(R.id.textHistory);

        ImageButton btnBack = findViewById(R.id.btnBackFromEdu);
        btnBack.setOnClickListener(v -> finish());

        // Set static educational content
        setStaticContent();

        // Fetch live ISS stats
        fetchLiveStats();
    }

    private void setStaticContent() {
        textISSInfo.setText(
                "Full Name: International Space Station\n" +
                        "Launched: November 20, 1998\n" +
                        "Size: 109m × 73m (football field size)\n" +
                        "Weight: ~420,000 kg\n" +
                        "Crew: Up to 7 astronauts\n" +
                        "Purpose: Scientific research in microgravity\n" +
                        "Partners: NASA, ESA, JAXA, Roscosmos, CSA"
        );

        textOrbitInfo.setText(
                "Orbit Type: Low Earth Orbit (LEO)\n" +
                        "Altitude: ~408 km above Earth\n" +
                        "Speed: 27,600 km/h (7.66 km/s)\n" +
                        "Orbital Period: 92 minutes per orbit\n" +
                        "Orbits per Day: ~15.5 times\n" +
                        "Inclination: 51.6° to equator\n\n" +
                        "💡 At this speed, the ISS sees\n" +
                        "   15-16 sunrises every single day!"
        );

        textHistory.setText(
                "1984 — President Reagan proposes the station\n\n" +
                        "1998 — First module (Zarya) launched by Russia\n\n" +
                        "2000 — First permanent crew arrives (Expedition 1)\n\n" +
                        "2001 — Destiny laboratory module added by NASA\n\n" +
                        "2011 — Space Shuttle program ends\n\n" +
                        "2012 — SpaceX Dragon becomes first commercial\n" +
                        "        spacecraft to dock with ISS\n\n" +
                        "2020 — SpaceX Crew Dragon carries astronauts\n\n" +
                        "2024 — ISS celebrates 26 years in orbit\n\n" +
                        "2030 — Planned deorbit into Pacific Ocean"
        );
    }

    private void fetchLiveStats() {
        Request request = new Request.Builder()
                .url("https://api.wheretheiss.at/v1/satellites/25544")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() ->
                        textLiveStats.setText("Could not load live stats")
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String     jsonData  = response.body().string();
                    JSONObject obj       = new JSONObject(jsonData);

                    double lat      = obj.getDouble("latitude");
                    double lon      = obj.getDouble("longitude");
                    double alt      = obj.getDouble("altitude");
                    double velocity = obj.getDouble("velocity");

                    String visibility = obj.getString("visibility");

                    runOnUiThread(() ->
                            textLiveStats.setText(
                                    "📍 Current Position\n" +
                                            "Latitude:  " + String.format("%.2f", lat)      + "°\n" +
                                            "Longitude: " + String.format("%.2f", lon)      + "°\n\n" +
                                            "🚀 Speed\n" +
                                            String.format("%.0f", velocity) + " km/h\n" +
                                            "(" + String.format("%.2f", velocity / 3600) + " km/s)\n\n" +
                                            "🌍 Altitude\n" +
                                            String.format("%.1f", alt) + " km above Earth\n\n" +
                                            "☀️ Visibility\n" +
                                            (visibility.equals("daylight") ? "In daylight" : "In Earth's shadow")
                            )
                    );

                } catch (Exception e) {
                    runOnUiThread(() ->
                            textLiveStats.setText("Parse error")
                    );
                } finally {
                    response.close();
                }
            }
        });
    }
}