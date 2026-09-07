package com.woodpeckerbros.watchreminder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Configuration and recovery screen shared by complications that require Jewish Mode.
 * The Wear OS complication picker launches this activity before assigning such a source.
 */
public class JewishModeComplicationConfigActivity extends Activity {
    private static final int COLOR_BG = 0xFF0B2133;
    private static final int COLOR_SURFACE_2 = 0xFF1A3042;
    private static final int COLOR_ACCENT_DARK = 0xFF747D63;
    private static final int COLOR_TEXT = 0xFFF4EBDD;
    private static final int COLOR_MUTED = 0xFFB8B7AE;

    public static final String ACTION_CONFIGURE =
            "com.woodpeckerbros.watchreminder.CONFIGURE_JEWISH_MODE_COMPLICATION";

    public static Intent createIntent(Context context) {
        return new Intent(context, JewishModeComplicationConfigActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguage.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (new ReminderSettings(this).jewishMode()) {
            setResult(RESULT_OK);
            finish();
            return;
        }
        showEnableScreen();
    }

    private void showEnableScreen() {
        int padding = dp(18);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(padding, dp(24), padding, dp(10));
        content.setBackgroundColor(COLOR_BG);

        TextView title = text(getString(R.string.ui_jewish_mode), 21);
        AppFont.bold(title);
        title.setGravity(Gravity.CENTER);
        content.addView(title, matchParams());

        TextView message = text(getString(R.string.ui_jewish_mode_complication_message), 14);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, dp(12), 0, dp(24));
        content.addView(message, matchParams());

        Button enable = button(getString(R.string.ui_enable_jewish_mode), COLOR_ACCENT_DARK);
        enable.setOnClickListener(v -> enableJewishMode());
        content.addView(enable, matchParams());

        Button cancel = button(getString(R.string.ui_cancel), COLOR_SURFACE_2);
        cancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        content.addView(cancel, matchParams());
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);
        scroll.addView(content);
        setContentView(scroll);
    }

    private void enableJewishMode() {
        ReminderSettings settings = new ReminderSettings(this);
        settings.setJewishMode(true);
        settings.setJewishDayRemindersEnabled(true);
        settings.setTekufaRemindersEnabled(true);
        new ReminderStore(this).rescheduleAll();
        JewishDayScheduler.schedule(this);
        TekufaScheduler.schedule(this);
        ComplicationRefresh.requestAll(this);
        setResult(RESULT_OK);
        finish();
    }

    private TextView text(String value, int sizeSp) {
        TextView text = new TextView(this);
        AppFont.apply(text);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(COLOR_TEXT);
        text.setTextDirection(AppLanguage.isRtl(this)
                ? android.view.View.TEXT_DIRECTION_RTL
                : android.view.View.TEXT_DIRECTION_LTR);
        AppTextStyle.apply(text);
        return text;
    }

    private Button button(String value, int color) {
        Button button = new Button(this);
        AppFont.apply(button);
        button.setText(value);
        button.setTextColor(COLOR_TEXT);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(40));
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setLayerType(Button.LAYER_TYPE_SOFTWARE, null);
        button.setBackground(new ElegantButtonDrawable(color, dp(18)));
        AppTextStyle.apply(button);
        return button;
    }

    private LinearLayout.LayoutParams matchParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
