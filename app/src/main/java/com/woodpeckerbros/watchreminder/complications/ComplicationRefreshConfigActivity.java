package com.woodpeckerbros.watchreminder;

import android.app.Activity;
import android.os.Bundle;

/**
 * Gives providers without user-facing settings a post-selection callback. Some OnePlus watch-face
 * editors do not activate or request a newly selected provider until a later reboot; returning a
 * successful configuration result lets us request the committed assignment immediately.
 */
public final class ComplicationRefreshConfigActivity extends Activity {
    public static final String ACTION_CONFIGURE =
            "com.woodpeckerbros.watchreminder.CONFIGURE_REFRESH_COMPLICATION";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_OK);
        ComplicationRefresh.requestAfterConfiguration(this);
        finish();
    }
}
