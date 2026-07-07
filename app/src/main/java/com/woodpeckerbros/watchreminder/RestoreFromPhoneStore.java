package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;

public class RestoreFromPhoneStore {
    private static final String PREFS_NAME = "restore_from_phone";
    private static final String KEY_TEXT = "text";
    private static final String KEY_TIME = "time";
    private static final String KEY_MODE = "mode";
    public static final String MODE_RESTORE = "restore";
    public static final String MODE_PATCH = "patch";

    private RestoreFromPhoneStore() {
    }

    public static void save(Context context, byte[] data) {
        save(context, data, MODE_RESTORE);
    }

    public static void save(Context context, byte[] data, String mode) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TEXT, new String(data, StandardCharsets.UTF_8))
                .putLong(KEY_TIME, System.currentTimeMillis())
                .putString(KEY_MODE, mode == null ? MODE_RESTORE : mode)
                .apply();
    }

    public static String pendingText(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TEXT, "");
    }

    public static boolean hasPending(Context context) {
        return !pendingText(context).isEmpty();
    }

    public static String pendingMode(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_MODE, MODE_RESTORE);
    }

    public static void clear(Context context) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }
}
