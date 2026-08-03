package com.example.isst;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ISSFetcher {

    private static final String TAG = "ISSFetcher";
    private static final int ISS_NORAD_ID = 25544;
    private static final String BASE_URL =
            "https://api.n2yo.com/rest/v1/satellite/positions";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final String apiKey;

    public interface Listener {
        void onResult(double issLat, double issLon, double issAltKm);
        void onError(String msg);
    }

    public interface LookAngleListener {
        void onResult(
                double issLat,
                double issLon,
                double issAltKm,
                double azimuth,
                double elevation,
                double slantRangeKm);
        void onError(String msg);
    }

    public ISSFetcher() {
        this(SpaceApiConfig.N2YO_KEY);
    }


    public ISSFetcher(String apiKey) {
        this.apiKey = apiKey;
    }

    public void fetch(Listener listener) {
        if (listener == null) return;

        fetchPositionUrl(BASE_URL + "/" +
                ISS_NORAD_ID + "/0/0/0/1/&apiKey=" + apiKey, listener);
    }

    public void fetchLookAngles(
            double observerLat,
            double observerLon,
            double observerAltM,
            LookAngleListener listener) {

        if (listener == null) return;

        if (apiKey == null || apiKey.trim().isEmpty()) {
            mainHandler.post(() -> listener.onError(
                    "Missing N2YO API key. Define N2YO_API_KEY in BuildConfig or pass it to ISSFetcher."));
            return;
        }

        double observerAltKm = Math.max(0.0, observerAltM / 1000.0);
        String urlText = BASE_URL + "/" +
                ISS_NORAD_ID + "/" +
                observerLat + "/" +
                observerLon + "/" +
                observerAltKm + "/1/&apiKey=" + apiKey;

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
                    mainHandler.post(() -> listener.onError(msg));
                    return;
                }

                JSONObject root = new JSONObject(body);
                JSONArray positions = root.optJSONArray("positions");
                if (positions == null || positions.length() == 0) {
                    String msg = "N2YO response did not contain positions[0]";
                    Log.e(TAG, msg + ": " + body);
                    mainHandler.post(() -> listener.onError(msg));
                    return;
                }

                JSONObject p = positions.getJSONObject(0);
                double issLat = p.getDouble("satlatitude");
                double issLon = p.getDouble("satlongitude");
                double issAltKm = p.getDouble("sataltitude");
                double azimuth = p.has("azimuth")
                        ? p.getDouble("azimuth")
                        : Double.NaN;
                double elevation = p.has("elevation")
                        ? p.getDouble("elevation")
                        : Double.NaN;

                double[] local = SkyProjection.issAzEl(
                        observerLat,
                        observerLon,
                        observerAltM,
                        issLat,
                        issLon,
                        issAltKm);
                double localAzimuth = local[0];
                double localElevation = local[1];
                double localSlantRangeKm = local.length >= 3 ? local[2] : Double.NaN;

                if (Double.isNaN(azimuth) || Double.isNaN(elevation)) {
                    azimuth = localAzimuth;
                    elevation = localElevation;
                }

                Log.d(TAG,
                        "USING localAz=" + localAzimuth +
                                " localEl=" + localElevation +
                                " n2yoAz=" + azimuth +
                                " n2yoEl=" + elevation);

                double finalAzimuth = localAzimuth;
                double finalElevation = localElevation;
                mainHandler.post(() -> listener.onResult(
                        issLat,
                        issLon,
                        issAltKm,
                        finalAzimuth,
                        finalElevation,
                        localSlantRangeKm));
            } catch (Exception e) {
                Log.e(TAG, "ISS fetch failed", e);
                mainHandler.post(() -> listener.onError(e.getMessage()));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void fetchPositionUrl(String urlText, Listener listener) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            mainHandler.post(() -> listener.onError(
                    "Missing N2YO API key. Define N2YO_API_KEY in BuildConfig or pass it to ISSFetcher."));
            return;
        }

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
                    mainHandler.post(() -> listener.onError(msg));
                    return;
                }

                JSONObject root = new JSONObject(body);
                JSONArray positions = root.optJSONArray("positions");
                if (positions == null || positions.length() == 0) {
                    String msg = "N2YO response did not contain positions[0]";
                    Log.e(TAG, msg + ": " + body);
                    mainHandler.post(() -> listener.onError(msg));
                    return;
                }

                JSONObject p = positions.getJSONObject(0);
                double issLat = p.getDouble("satlatitude");
                double issLon = p.getDouble("satlongitude");
                double issAltKm = p.getDouble("sataltitude");

                Log.d(TAG, String.format(
                        "N2YO ISS parsed: lat=%.6f lon=%.6f altKm=%.3f",
                        issLat,
                        issLon,
                        issAltKm));

                mainHandler.post(() -> listener.onResult(issLat, issLon, issAltKm));
            } catch (Exception e) {
                Log.e(TAG, "ISS fetch failed", e);
                mainHandler.post(() -> listener.onError(e.getMessage()));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
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

    private static String readApiKeyFromBuildConfig() {
        try {
            Class<?> buildConfig = Class.forName("com.example.isst.BuildConfig");
            Field field = buildConfig.getField("N2YO_API_KEY");
            Object value = field.get(null);
            return value == null ? "" : value.toString();
        } catch (Exception ignored) {
            return "";
        }
    }
}
