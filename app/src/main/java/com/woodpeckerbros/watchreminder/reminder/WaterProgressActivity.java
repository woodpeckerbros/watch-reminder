package com.woodpeckerbros.watchreminder.reminder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.woodpeckerbros.watchreminder.AppFont;
import com.woodpeckerbros.watchreminder.AppLanguage;
import com.woodpeckerbros.watchreminder.AppTextStyle;
import com.woodpeckerbros.watchreminder.ComplicationRefresh;
import com.woodpeckerbros.watchreminder.MainActivity;
import com.woodpeckerbros.watchreminder.R;

/** Daily hydration dashboard opened from the home screen and the water complication. */
public final class WaterProgressActivity extends Activity {
    private static final String EXTRA_FROM_COMPLICATION = "water_progress_from_complication";
    private static final int COLOR_BG = 0xFF061522;
    private static final int COLOR_SURFACE = 0xFF142A3A;
    private static final int COLOR_TEXT = 0xFFF4EBDD;
    private static final int COLOR_MUTED = 0xFFB8C5CA;
    private static final int COLOR_WATER = 0xFF38BDE8;
    private boolean fromComplication;

    public static Intent createIntent(Context context, boolean fromComplication) {
        return new Intent(context, WaterProgressActivity.class)
                .putExtra(EXTRA_FROM_COMPLICATION, fromComplication);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguage.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fromComplication = getIntent().getBooleanExtra(EXTRA_FROM_COMPLICATION, false);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    public void onBackPressed() {
        if (fromComplication) {
            finishAndRemoveTask();
        } else {
            super.onBackPressed();
        }
    }

    private void render() {
        ReminderSettings settings = new ReminderSettings(this);
        int consumed = new WaterReminderStore(this).consumedTodayMl();
        int target = dailyTarget(settings);
        int remaining = Math.max(0, target - consumed);
        int percent = Math.min(100, Math.round(consumed * 100f / Math.max(1, target)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalFadingEdgeEnabled(false);
        scroll.setBackgroundColor(COLOR_BG);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(18), dp(12), dp(18), dp(28));
        content.setBackgroundColor(COLOR_BG);

        TextView title = text(getString(R.string.water_dashboard_title), 19, COLOR_TEXT);
        AppFont.bold(title);
        content.addView(title);
        TextView subtitle = text(getString(R.string.water_dashboard_subtitle), 11, COLOR_MUTED);
        subtitle.setPadding(0, dp(1), 0, dp(4));
        content.addView(subtitle);

        WaterPitcherView pitcher = new WaterPitcherView(this);
        pitcher.setProgress(consumed / (float) Math.max(1, target));
        content.addView(pitcher, new LinearLayout.LayoutParams(dp(120), dp(140)));

        TextView percentText = text(getString(R.string.water_dashboard_percent, percent), 24, COLOR_WATER);
        AppFont.bold(percentText);
        content.addView(percentText);
        TextView progress = text(getString(R.string.water_today_progress, consumed, target), 15, COLOR_TEXT);
        AppFont.bold(progress);
        content.addView(progress);
        TextView remainingText = text(remaining == 0
                ? getString(R.string.water_dashboard_goal_reached)
                : getString(R.string.water_dashboard_remaining, remaining), 12, COLOR_MUTED);
        remainingText.setPadding(0, dp(2), 0, dp(8));
        content.addView(remainingText);

        Button addGlass = button(getString(R.string.water_dashboard_add_glass), COLOR_WATER, COLOR_BG);
        addGlass.setOnClickListener(v -> {
            new WaterReminderStore(this).addConsumedMl(250);
            WaterReminderScheduler.schedule(this);
            ComplicationRefresh.requestWater(this);
            render();
        });
        content.addView(addGlass, matchParams());
        Button settingsButton = button(getString(R.string.water_dashboard_settings), COLOR_SURFACE, Color.WHITE);
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_OPEN_WATER_SETTINGS, true)));
        content.addView(settingsButton, matchParams());

        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        AppTextStyle.apply(scroll);
        setContentView(scroll);
    }

    private int dailyTarget(ReminderSettings settings) {
        if (ReminderSettings.WATER_MODE_DAILY_TARGET.equals(settings.waterMode())) {
            return Math.max(1, settings.waterDailyTargetMl());
        }
        return Math.max(1, WaterReminderScheduler.remindersPerDay(settings) * settings.waterAmountMl());
    }

    private Button button(String value, int color, int textColor) {
        Button button = new Button(this);
        AppFont.apply(button);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextSize(12);
        button.setTextColor(textColor);
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(18));
        button.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(38));
        params.setMargins(dp(2), dp(3), dp(2), dp(3));
        button.setLayoutParams(params);
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        AppFont.apply(view);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setTextDirection(AppLanguage.isRtl(this)
                ? TextView.TEXT_DIRECTION_RTL : TextView.TEXT_DIRECTION_LTR);
        return view;
    }

    private LinearLayout.LayoutParams matchParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
