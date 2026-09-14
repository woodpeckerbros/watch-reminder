package com.woodpeckerbros.watchreminder.sync;

import android.content.Context;
import android.content.SharedPreferences;

import com.woodpeckerbros.watchreminder.reminder.BackupCrypto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class RestoreFromPhoneStore {
    private static final String PREFS_NAME = "restore_from_phone";
    private static final String KEY_DATA = "data";
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
                .putString(KEY_DATA, Base64.getEncoder().encodeToString(data))
                .putLong(KEY_TIME, System.currentTimeMillis())
                .putString(KEY_MODE, mode == null ? MODE_RESTORE : mode)
                .apply();
    }

    public static String pendingText(Context context) {
        try {
            byte[] data = pendingData(context);
            if (MODE_PATCH.equals(pendingMode(context))) return new String(data, StandardCharsets.UTF_8);
            return BackupCrypto.decryptToText(data);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static boolean hasPending(Context context) {
        return pendingData(context).length > 0;
    }

    public static String pendingMode(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_MODE, MODE_RESTORE);
    }

    private static byte[] pendingData(Context context) {
        String encoded = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_DATA, "");
        return encoded.isEmpty() ? new byte[0] : Base64.getDecoder().decode(encoded);
    }

    public static void clear(Context context) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }
}
