package com.example.isst;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SatelliteTracker {

    private static final String TAG = "SatelliteTracker";
    private static final String BASE_URL =
            "https://api.n2yo.com/rest/v1/satellite/positions";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final String apiKey;

    public interface Listener {
        void onResult(CelestialObject object, double slantRangeKm);
        void onError(SatelliteTarget target, String message);
    }

    public SatelliteTracker() {
        this(SpaceApiConfig.N2YO_KEY);
    }

    public SatelliteTracker(String apiKey) {
        this.apiKey = apiKey;
    }

    public void fetchLookAngles(
            SatelliteTarget target,
            double observerLat,
            double observerLon,
            double observerAltM,
            Listener listener) {

        if (listener == null || target == null) return;
        if (apiKey == null || apiKey.trim().isEmpty()) {
            mainHandler.post(() -> listener.onError(target, "N2YO key is not configured"));
            return;
        }

        double observerAltKm = Math.max(0.0, observerAltM / 1000.0);
        String urlText = BASE_URL + "/"
                + target.noradId + "/"
                + observerLat + "/"
                + observerLon + "/"
                + observerAltKm + "/1/&apiKey=" + apiKey;

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlText);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(10_000);
                connection.setRequestMethod("GET");

                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                String body = readAll(stream);

                if (code < 200 || code >= 300) {
                    String msg = "N2YO HTTP " + code + ": " + body;
                    Log.e(TAG, msg);
                    mainHandler.post(() -> listener.onError(target, msg));
                    return;
                }

                JSONObject root = new JSONObject(body);
                JSONArray positions = root.optJSONArray("positions");
                if (positions == null || positions.length() == 0) {
                    mainHandler.post(() -> listener.onError(target, "No position returned"));
                    return;
                }

                JSONObject p = positions.getJSONObject(0);
                double satLat = p.getDouble("satlatitude");
                double satLon = p.getDouble("satlongitude");
                double satAltKm = p.getDouble("sataltitude");

                double[] local = SkyProjection.issAzEl(
                        observerLat,
                        observerLon,
                        observerAltM,
                        satLat,
                        satLon,
                        satAltKm);

                CelestialObject object = new CelestialObject(
                        target.name,
                        target.type,
                        target.color,
                        target.emoji,
                        target.size);
                object.azimuth = local[0];
                object.elevation = local[1];
                object.showLabel = true;

                double slantRangeKm = local.length >= 3 ? local[2] : Double.NaN;
                Log.d(TAG, target.name + " az=" + object.azimuth
                        + " el=" + object.elevation
                        + " rangeKm=" + slantRangeKm);

                mainHandler.post(() -> listener.onResult(object, slantRangeKm));
            } catch (Exception e) {
                Log.e(TAG, "Satellite fetch failed for " + target.name, e);
                mainHandler.post(() -> listener.onError(target, e.getMessage()));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";

        StringBuilder result = new StringBuilder();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            result.append(line);
        }
        return result.toString();
    }
}
