package com.example.isst;

import android.util.Base64;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches real planet / sun / moon positions from astronomyapi.com
 *
 * Registration: https://astronomyapi.com  (free student plan)
 * Provides: azimuth, elevation, magnitude, constellation for each body
 *
 * Keys go in SpaceApiConfig.java:
 *   ASTRONOMY_APP_ID
 *   ASTRONOMY_APP_SECRET
 */
public class AstronomyApiFetcher {

    private static final String TAG = "AstronomyApi";

    public static class BodyPosition {
        public String name;
        public double azimuth;
        public double elevation;
        public double magnitude;
        public String constellation;
        public boolean aboveHorizon;
    }

    public interface Listener {
        void onResult(List<BodyPosition> positions);
        void onError(String message);
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private Call activeCall;

    public void fetch(double lat, double lon, Listener listener) {
        if (!SpaceApiConfig.isAstronomyConfigured()) {
            listener.onError("AstronomyAPI keys not configured.\nAdd them to SpaceApiConfig.java\nGet free keys at astronomyapi.com");
            return;
        }

        String credentials = SpaceApiConfig.ASTRONOMY_APP_ID
                + ":" + SpaceApiConfig.ASTRONOMY_APP_SECRET;
        String encoded = Base64.encodeToString(
                credentials.getBytes(), Base64.NO_WRAP);

        SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String today = dateFmt.format(new Date());
        String now   = timeFmt.format(new Date());

        String url = "https://api.astronomyapi.com/api/v2/bodies/positions"
                + "?latitude="  + lat
                + "&longitude=" + lon
                + "&elevation=0"
                + "&from_date=" + today
                + "&to_date="   + today
                + "&time="      + now;

        if (activeCall != null) activeCall.cancel();

        activeCall = client.newCall(new Request.Builder()
                .url(url)
                .header("Authorization", "Basic " + encoded)
                .build());

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
                    Log.d(TAG, "ASTRONOMY RESPONSE:");
                    Log.d(TAG, body);
                    Log.d(TAG, "HTTP " + response.code());

                    if (response.code() == 401) {
                        listener.onError("Invalid AstronomyAPI credentials.\nCheck SpaceApiConfig.java");
                        return;
                    }
                    if (response.code() == 403) {
                        listener.onError("AstronomyAPI quota exceeded.\nCheck your plan at astronomyapi.com");
                        return;
                    }
                    if (!response.isSuccessful()) {
                        listener.onError("AstronomyAPI error " + response.code());
                        return;
                    }

                    JSONObject root  = new JSONObject(body);
                    JSONObject data  = root.getJSONObject("data");
                    JSONObject table = data.getJSONObject("table");
                    JSONArray  rows  = table.getJSONArray("rows");

                    List<BodyPosition> result = new ArrayList<>();

                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row    = rows.getJSONObject(i);
                        JSONArray  cells  = row.getJSONArray("cells");
                        if (cells.length() == 0) continue;

                        JSONObject cell   = cells.getJSONObject(0);
                        JSONObject pos    = cell.getJSONObject("position");
                        JSONObject horiz  = pos.getJSONObject("horizontal");

                        BodyPosition bp = new BodyPosition();
                        bp.name = cell.getString("name");

                        // altitude = elevation above horizon
                        bp.elevation   = horiz.getJSONObject("altitude").getDouble("degrees");
                        bp.azimuth     = horiz.getJSONObject("azimuth").getDouble("degrees");
                        bp.aboveHorizon = bp.elevation > 0;

                        if (cell.has("extraInfo")) {
                            JSONObject extra = cell.getJSONObject("extraInfo");
                            if (extra.has("magnitude")
                                    && !extra.isNull("magnitude")) {

                                bp.magnitude = extra.getDouble("magnitude");

                            } else {

                                bp.magnitude = 0;
                            }
                        }

                        if (pos.has("constellation")) {
                            bp.constellation = pos.getJSONObject("constellation")
                                    .getString("name");
                        }

                        result.add(bp);
                    }

                    listener.onResult(result);

                } catch (Exception e) {
                    Log.e(TAG, "Parse error: " + e.getMessage() + " | body: " + body);
                    listener.onError(
                            "Parse error: " + e.getMessage()
                    );
                } finally {
                    response.close();
                }
            }
        });
    }

    public void cancel() {
        if (activeCall != null) activeCall.cancel();
    }
}