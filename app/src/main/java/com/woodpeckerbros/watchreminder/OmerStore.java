package com.woodpeckerbros.watchreminder;

import android.content.Context;
import android.content.SharedPreferences;

public class OmerStore {
    private static final String PREFS_NAME = "omer_state";
    private static final String KEY_HANDLED = "handled_key";
    private static final String KEY_RETRY_UNTIL = "retry_until";

    private final SharedPreferences prefs;

    public OmerStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isHandled(String key) {
        return key != null && key.equals(prefs.getString(KEY_HANDLED, ""));
    }

    public void markHandled(String key) {
        prefs.edit()
                .putString(KEY_HANDLED, key == null ? "" : key)
                .remove(KEY_RETRY_UNTIL)
                .apply();
    }

    public long retryUntil() {
        return prefs.getLong(KEY_RETRY_UNTIL, 0);
    }

    public void setRetryUntil(long triggerAt) {
        prefs.edit().putLong(KEY_RETRY_UNTIL, triggerAt).apply();
    }

    public void clearRetryUntil() {
        prefs.edit().remove(KEY_RETRY_UNTIL).apply();
    }
}
