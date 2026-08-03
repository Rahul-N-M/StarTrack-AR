package com.example.isst;

import android.util.Log;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches current weather + cloud cover from OpenWeatherMap.
 * Free tier: https://openweathermap.org/api/one-call-3
 * Used for: sky visibility card in TonightSkyActivity
 */
public class WeatherFetcher {

    private static final String TAG = "WeatherFetcher";

    public static class WeatherResult {
        public String  description;   // e.g. "clear sky"
        public int     cloudPercent;  // 0–100
        public double  tempC;
        public double  humidity;
        public String  visibility;    // "Excellent" / "Good" / "Poor" / "Very Poor"
        public int     visibilityColor; // android Color int
    }

    public interface Listener {
        void onResult(WeatherResult result);
        void onError(String message);
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    private Call activeCall;

    public void fetch(double lat, double lon, Listener listener) {
        if (!SpaceApiConfig.isWeatherConfigured()) {
            listener.onError("OpenWeatherMap key not configured.\nAdd it to SpaceApiConfig.java");
            return;
        }

        if (activeCall != null) activeCall.cancel();

        String url = "https://api.openweathermap.org/data/2.5/weather"
                + "?lat=" + lat
                + "&lon=" + lon
                + "&units=metric"
                + "&appid=" + SpaceApiConfig.OPENWEATHER_KEY;

        activeCall = client.newCall(new Request.Builder().url(url).build());
        activeCall.enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                if (!call.isCanceled()) {
                    Log.e(TAG, "Network failure: " + e.getMessage());
                    listener.onError("No internet connection");
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = "";
                try {
                    body = response.body().string();
                    if (response.code() == 401) {
                        listener.onError("Invalid OpenWeatherMap key.\nCheck SpaceApiConfig.java");
                        return;
                    }
                    if (response.code() == 429) {
                        listener.onError("OpenWeatherMap rate limit reached.\nTry again in 1 minute.");
                        return;
                    }
                    if (!response.isSuccessful()) {
                        listener.onError("Weather API error " + response.code());
                        return;
                    }

                    JSONObject json  = new JSONObject(body);
                    JSONObject main  = json.getJSONObject("main");
                    JSONObject clouds = json.getJSONObject("clouds");
                    JSONObject weather = json.getJSONArray("weather").getJSONObject(0);

                    WeatherResult result    = new WeatherResult();
                    result.description      = capitalise(weather.getString("description"));
                    result.cloudPercent     = clouds.getInt("all");
                    result.tempC            = main.getDouble("temp");
                    result.humidity         = main.getDouble("humidity");

                    // Derive viewing quality from cloud cover
                    if (result.cloudPercent <= 10) {
                        result.visibility      = "Excellent — Clear sky";
                        result.visibilityColor = android.graphics.Color.parseColor("#00FF9C");
                    } else if (result.cloudPercent <= 30) {
                        result.visibility      = "Good — Mostly clear";
                        result.visibilityColor = android.graphics.Color.parseColor("#7BFF7B");
                    } else if (result.cloudPercent <= 60) {
                        result.visibility      = "Moderate — Partly cloudy";
                        result.visibilityColor = android.graphics.Color.parseColor("#FFB347");
                    } else if (result.cloudPercent <= 85) {
                        result.visibility      = "Poor — Mostly cloudy";
                        result.visibilityColor = android.graphics.Color.parseColor("#FF7043");
                    } else {
                        result.visibility      = "Very Poor — Overcast";
                        result.visibilityColor = android.graphics.Color.parseColor("#FF4757");
                    }

                    listener.onResult(result);

                } catch (Exception e) {
                    Log.e(TAG, "Parse error: " + e.getMessage() + " | body: " + body);
                    listener.onError("Failed to read weather data");
                } finally {
                    response.close();
                }
            }
        });
    }

    public void cancel() {
        if (activeCall != null) activeCall.cancel();
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }
}