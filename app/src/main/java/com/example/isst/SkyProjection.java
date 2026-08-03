package com.example.isst;

import android.util.Log;

/**
 * Projects local horizontal sky coordinates onto the portrait camera screen.
 *
 * Coordinate contract:
 *
 * World frame, ENU:
 *   +X = East
 *   +Y = North
 *   +Z = Up
 *
 * Android device frame:
 *   +X = right side of the phone
 *   +Y = top of the phone
 *   +Z = out of the screen, toward the user
 *
 * Back camera optical frame:
 *   camera forward = -device Z
 *   screen right    = +device X
 *   screen up       = +device Y
 *
 * The rotation matrix passed to project() must be Android's device-to-world
 * matrix, corrected so that world +Y is true north. Android stores the matrix
 * in row-major order, and the columns are the device basis vectors expressed
 * in world ENU coordinates. Therefore world-to-device is R^T.
 */
public class SkyProjection {

    private static final String TAG = "SkyProjection";
    private static final double MIN_DEPTH = 1.0e-4;
    private static final float SCREEN_MARGIN_PX = 120f;

    public static float[] project(
            double objAzDeg,
            double objElDeg,
            float[] deviceToWorldEnu,
            int screenW,
            int screenH,
            float cameraVFovPortrait) {

        if (deviceToWorldEnu == null || deviceToWorldEnu.length < 9) return null;
        if (screenW <= 0 || screenH <= 0) return null;
        if (cameraVFovPortrait <= 1f || cameraVFovPortrait >= 179f) return null;

        // 1. Azimuth/elevation to a unit vector on the celestial sphere.
        // Azimuth is degrees clockwise from north: 0=N, 90=E.
        double az = Math.toRadians(objAzDeg);
        double el = Math.toRadians(objElDeg);
        double cosEl = Math.cos(el);

        double worldE = cosEl * Math.sin(az);
        double worldN = cosEl * Math.cos(az);
        double worldU = Math.sin(el);

        // 2. World ENU to Android device coordinates.
        // R is device-to-world, so the inverse is the transpose because R is a
        // rotation matrix. These dot products express the world vector in the
        // phone's right/top/out-of-screen axes.
        double devX =
                deviceToWorldEnu[0] * worldE +
                        deviceToWorldEnu[3] * worldN +
                        deviceToWorldEnu[6] * worldU;
        double devY =
                deviceToWorldEnu[1] * worldE +
                        deviceToWorldEnu[4] * worldN +
                        deviceToWorldEnu[7] * worldU;
        double devZ =
                deviceToWorldEnu[2] * worldE +
                        deviceToWorldEnu[5] * worldN +
                        deviceToWorldEnu[8] * worldU;

        // 3. Back camera looks along -device Z.
        double depth = -devZ;
        if (depth <= MIN_DEPTH) return null;

        // 4. Pinhole projection. In portrait, the physical camera sensor's
        // wide FOV maps to the screen's vertical axis after CameraX rotation,
        // so this value is the vertical FOV of the portrait preview.
        double halfVFov = Math.toRadians(cameraVFovPortrait * 0.5);
        double focalPx = (screenH * 0.5) / Math.tan(halfVFov);

        double screenX = (screenW * 0.5) + (devX / depth) * focalPx;
        double screenY = (screenH * 0.5) - (devY / depth) * focalPx;

        // 5. Cull only after projection. Keep a small margin so labels and
        // marker glows can enter smoothly at the screen edge.
        if (screenX < -SCREEN_MARGIN_PX || screenX > screenW + SCREEN_MARGIN_PX ||
                screenY < -SCREEN_MARGIN_PX || screenY > screenH + SCREEN_MARGIN_PX) {
            return null;
        }

        return new float[]{(float) screenX, (float) screenY};
    }

    public static double[] issAzEl(
            double obsLat,
            double obsLon,
            double issLat,
            double issLon,
            double issAltKm) {

        return issAzEl(obsLat, obsLon, 0.0, issLat, issLon, issAltKm);
    }

    public static double[] issAzEl(
            double obsLat,
            double obsLon,
            double obsAltM,
            double issLat,
            double issLon,
            double issAltKm) {

        final double earthRadiusKm = 6371.0;
        double obsAltKm = obsAltM / 1000.0;

        double obsLatRad = Math.toRadians(obsLat);
        double obsLonRad = Math.toRadians(obsLon);
        double issLatRad = Math.toRadians(issLat);
        double issLonRad = Math.toRadians(issLon);

        // Observer ECEF position. The overload above preserves the original
        // public API; this overload includes GPS altitude when the caller has it.
        double obsRadiusKm = earthRadiusKm + obsAltKm;
        double obsX = obsRadiusKm * Math.cos(obsLatRad) * Math.cos(obsLonRad);
        double obsY = obsRadiusKm * Math.cos(obsLatRad) * Math.sin(obsLonRad);
        double obsZ = obsRadiusKm * Math.sin(obsLatRad);

        // N2YO sataltitude is kilometres above mean sea level.
        double satRadiusKm = earthRadiusKm + issAltKm;
        double satX = satRadiusKm * Math.cos(issLatRad) * Math.cos(issLonRad);
        double satY = satRadiusKm * Math.cos(issLatRad) * Math.sin(issLonRad);
        double satZ = satRadiusKm * Math.sin(issLatRad);

        double dx = satX - obsX;
        double dy = satY - obsY;
        double dz = satZ - obsZ;

        // Rotate observer-to-satellite vector from ECEF into the observer's
        // local ENU tangent frame.
        double sinLat = Math.sin(obsLatRad);
        double cosLat = Math.cos(obsLatRad);
        double sinLon = Math.sin(obsLonRad);
        double cosLon = Math.cos(obsLonRad);

        double east = -sinLon * dx + cosLon * dy;
        double north = -sinLat * cosLon * dx - sinLat * sinLon * dy + cosLat * dz;
        double up = cosLat * cosLon * dx + cosLat * sinLon * dy + sinLat * dz;

        double horizontalRange = Math.sqrt(east * east + north * north);
        double slantRangeKm = Math.sqrt(
                east * east +
                        north * north +
                        up * up);
        double az = (Math.toDegrees(Math.atan2(east, north)) + 360.0) % 360.0;
        double el = Math.toDegrees(Math.atan2(up, horizontalRange));

        Log.d(TAG, String.format(
                "ISS az/el: issLat=%.6f issLon=%.6f issAltKm=%.3f "
                        + "az=%.2f el=%.2f slantRangeKm=%.2f",
                issLat,
                issLon,
                issAltKm,
                az,
                el,
                slantRangeKm));

        return new double[]{az, el, slantRangeKm};
    }
}
