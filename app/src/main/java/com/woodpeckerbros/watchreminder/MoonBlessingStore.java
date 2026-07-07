package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.content.SharedPreferences;

public class MoonBlessingStore {
    private static final String PREFS_NAME = "moon_blessing_state";
    private static final String KEY_HANDLED_MONTH = "handled_month";

    private final SharedPreferences prefs;

    public MoonBlessingStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isHandled(String monthKey) {
        return monthKey != null && monthKey.equals(prefs.getString(KEY_HANDLED_MONTH, ""));
    }

    public void markHandled(String monthKey) {
        prefs.edit().putString(KEY_HANDLED_MONTH, monthKey == null ? "" : monthKey).apply();
    }
}
