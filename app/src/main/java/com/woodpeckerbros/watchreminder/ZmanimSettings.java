package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.content.SharedPreferences;

public class ZmanimSettings {
    private static final String PREFS_NAME = "zmanim_settings";
    private static final String KEY_NAME = "name";
    private static final String KEY_LATITUDE = "latitude";
    private static final String KEY_LONGITUDE = "longitude";
    private static final String KEY_ELEVATION = "elevation";
    private static final String KEY_TIME_ZONE = "time_zone";

    public static final String DEFAULT_NAME = "פתח תקווה, ישראל";
    public static final double DEFAULT_LATITUDE = 32.0840;
    public static final double DEFAULT_LONGITUDE = 34.8878;
    public static final double DEFAULT_ELEVATION = 0;
    public static final String DEFAULT_TIME_ZONE = "Asia/Jerusalem";

    private final SharedPreferences prefs;

    public ZmanimSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        repairInvalidValues();
    }

    public String name() {
        return prefs.getString(KEY_NAME, DEFAULT_NAME);
    }

    public double latitude() {
        return Double.longBitsToDouble(prefs.getLong(KEY_LATITUDE, Double.doubleToLongBits(DEFAULT_LATITUDE)));
    }

    public double longitude() {
        return Double.longBitsToDouble(prefs.getLong(KEY_LONGITUDE, Double.doubleToLongBits(DEFAULT_LONGITUDE)));
    }

    public double elevation() {
        return Double.longBitsToDouble(prefs.getLong(KEY_ELEVATION, Double.doubleToLongBits(DEFAULT_ELEVATION)));
    }

    public String timeZoneId() {
        return prefs.getString(KEY_TIME_ZONE, DEFAULT_TIME_ZONE);
    }

    public void update(String name, double latitude, double longitude, double elevation, String timeZoneId) {
        latitude = sanitizeLatitude(latitude);
        longitude = sanitizeLongitude(longitude);
        elevation = sanitizeElevation(elevation);
        prefs.edit()
                .putString(KEY_NAME, name == null || name.trim().isEmpty() ? coordinatesName(latitude, longitude) : name)
                .putLong(KEY_LATITUDE, Double.doubleToLongBits(latitude))
                .putLong(KEY_LONGITUDE, Double.doubleToLongBits(longitude))
                .putLong(KEY_ELEVATION, Double.doubleToLongBits(elevation))
                .putString(KEY_TIME_ZONE, timeZoneId == null || timeZoneId.trim().isEmpty() ? DEFAULT_TIME_ZONE : timeZoneId)
                .apply();
    }

    private void repairInvalidValues() {
        double latitude = rawDouble(KEY_LATITUDE, DEFAULT_LATITUDE);
        double longitude = rawDouble(KEY_LONGITUDE, DEFAULT_LONGITUDE);
        double elevation = rawDouble(KEY_ELEVATION, DEFAULT_ELEVATION);
        double safeLatitude = sanitizeLatitude(latitude);
        double safeLongitude = sanitizeLongitude(longitude);
        double safeElevation = sanitizeElevation(elevation);
        if (latitude != safeLatitude || longitude != safeLongitude || elevation != safeElevation) {
            prefs.edit()
                    .putLong(KEY_LATITUDE, Double.doubleToLongBits(safeLatitude))
                    .putLong(KEY_LONGITUDE, Double.doubleToLongBits(safeLongitude))
                    .putLong(KEY_ELEVATION, Double.doubleToLongBits(safeElevation))
                    .apply();
        }
    }

    private double rawDouble(String key, double fallback) {
        return Double.longBitsToDouble(prefs.getLong(key, Double.doubleToLongBits(fallback)));
    }

    static double sanitizeLatitude(double value) {
        return Double.isFinite(value) && value >= -90 && value <= 90 ? value : DEFAULT_LATITUDE;
    }

    static double sanitizeLongitude(double value) {
        return Double.isFinite(value) && value >= -180 && value <= 180 ? value : DEFAULT_LONGITUDE;
    }

    static double sanitizeElevation(double value) {
        return Double.isFinite(value) && value >= 0 ? value : DEFAULT_ELEVATION;
    }

    public static String coordinatesName(double latitude, double longitude) {
        return String.format(java.util.Locale.US, "%.5f, %.5f", latitude, longitude);
    }
}
