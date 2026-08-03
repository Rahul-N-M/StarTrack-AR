package com.example.isst;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

public class BrightStar {

    public final String name;
    public final double rightAscensionHours;
    public final double declinationDeg;
    public final double magnitude;
    public final int color;

    public BrightStar(
            String name,
            double rightAscensionHours,
            double declinationDeg,
            double magnitude,
            int color) {
        this.name = name;
        this.rightAscensionHours = rightAscensionHours;
        this.declinationDeg = declinationDeg;
        this.magnitude = magnitude;
        this.color = color;
    }

    public CelestialObject toCelestialObject(double observerLat, double observerLon, long timeMillis) {
        double[] azEl = raDecToAzEl(
                rightAscensionHours,
                declinationDeg,
                observerLat,
                observerLon,
                timeMillis);

        if (azEl[1] <= 0.0) return null;

        float markerSize = magnitude <= 0.0 ? 9f : 7f;
        CelestialObject object = new CelestialObject(
                name,
                CelestialObject.Type.STAR,
                color,
                "·",
                markerSize,
                magnitude,
                magnitude <= 1.5);
        object.azimuth = azEl[0];
        object.elevation = azEl[1];
        return object;
    }

    public static List<CelestialObject> visibleStars(
            double observerLat,
            double observerLon,
            long timeMillis) {
        List<CelestialObject> result = new ArrayList<>();
        for (BrightStar star : catalog()) {
            CelestialObject obj = star.toCelestialObject(observerLat, observerLon, timeMillis);
            if (obj == null) continue;
            result.add(obj);
        }
        return result;
    }

    public static List<BrightStar> catalog() {
        List<BrightStar> stars = new ArrayList<>();
        stars.add(new BrightStar("Sirius", 6.7525, -16.7161, -1.46, Color.parseColor("#DDEBFF")));
        stars.add(new BrightStar("Canopus", 6.3992, -52.6957, -0.74, Color.parseColor("#FFF1D6")));
        stars.add(new BrightStar("Arcturus", 14.2610, 19.1825, -0.05, Color.parseColor("#FFD39B")));
        stars.add(new BrightStar("Vega", 18.6156, 38.7837, 0.03, Color.parseColor("#EAF3FF")));
        stars.add(new BrightStar("Capella", 5.2782, 45.9980, 0.08, Color.parseColor("#FFF0B8")));
        stars.add(new BrightStar("Rigel", 5.2423, -8.2016, 0.13, Color.parseColor("#D8E8FF")));
        stars.add(new BrightStar("Procyon", 7.6550, 5.2250, 0.34, Color.parseColor("#FFF5DE")));
        stars.add(new BrightStar("Betelgeuse", 5.9195, 7.4071, 0.42, Color.parseColor("#FF9A62")));
        stars.add(new BrightStar("Achernar", 1.6286, -57.2368, 0.46, Color.parseColor("#DDEBFF")));
        stars.add(new BrightStar("Hadar", 14.0637, -60.3730, 0.61, Color.parseColor("#DDEBFF")));
        stars.add(new BrightStar("Altair", 19.8464, 8.8683, 0.76, Color.parseColor("#F4F7FF")));
        stars.add(new BrightStar("Acrux", 12.4433, -63.0991, 0.77, Color.parseColor("#DDEBFF")));
        stars.add(new BrightStar("Aldebaran", 4.5987, 16.5093, 0.86, Color.parseColor("#FFB16E")));
        stars.add(new BrightStar("Antares", 16.4901, -26.4320, 0.91, Color.parseColor("#FF7A58")));
        stars.add(new BrightStar("Spica", 13.4199, -11.1613, 0.97, Color.parseColor("#DDEBFF")));
        stars.add(new BrightStar("Pollux", 7.7553, 28.0262, 1.14, Color.parseColor("#FFD7A0")));
        stars.add(new BrightStar("Fomalhaut", 22.9608, -29.6222, 1.16, Color.parseColor("#EEF5FF")));
        stars.add(new BrightStar("Deneb", 20.6905, 45.2803, 1.25, Color.parseColor("#EEF5FF")));
        stars.add(new BrightStar("Regulus", 10.1395, 11.9672, 1.35, Color.parseColor("#DDEBFF")));
        stars.add(new BrightStar("Castor", 7.5767, 31.8883, 1.58, Color.parseColor("#EEF5FF")));
        return stars;
    }

    private static double[] raDecToAzEl(
            double raHours,
            double decDeg,
            double latDeg,
            double lonDeg,
            long timeMillis) {

        double jd = julianDate(timeMillis);
        double gmstDeg = greenwichMeanSiderealTimeDeg(jd);
        double lstDeg = normalizeDeg(gmstDeg + lonDeg);
        double hourAngleDeg = normalizeDeg(lstDeg - raHours * 15.0);
        if (hourAngleDeg > 180.0) hourAngleDeg -= 360.0;

        double lat = Math.toRadians(latDeg);
        double dec = Math.toRadians(decDeg);
        double ha = Math.toRadians(hourAngleDeg);

        double sinDec = Math.sin(dec);
        double cosDec = Math.cos(dec);
        double sinLat = Math.sin(lat);
        double cosLat = Math.cos(lat);
        double sinHa = Math.sin(ha);
        double cosHa = Math.cos(ha);

        double east = -cosDec * sinHa;
        double north = sinDec * cosLat - cosDec * cosHa * sinLat;
        double up = sinDec * sinLat + cosDec * cosHa * cosLat;

        double az = (Math.toDegrees(Math.atan2(east, north)) + 360.0) % 360.0;
        double el = Math.toDegrees(Math.asin(up));
        return new double[]{az, el};
    }

    private static double julianDate(long timeMillis) {
        return timeMillis / 86400000.0 + 2440587.5;
    }

    private static double greenwichMeanSiderealTimeDeg(double jd) {
        double t = (jd - 2451545.0) / 36525.0;
        double gmst = 280.46061837
                + 360.98564736629 * (jd - 2451545.0)
                + 0.000387933 * t * t
                - t * t * t / 38710000.0;
        return normalizeDeg(gmst);
    }

    private static double normalizeDeg(double value) {
        value %= 360.0;
        if (value < 0.0) value += 360.0;
        return value;
    }
}
