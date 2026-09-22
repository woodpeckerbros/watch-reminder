package com.woodpeckerbros.watchreminder.calendar;

import com.woodpeckerbros.watchreminder.reminder.*;

import com.woodpeckerbros.watchreminder.*;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OmerAlertActivity extends Activity {
    private static final int COLOR_BG = 0xFF061522;
    private static final int COLOR_SURFACE = 0xFF142A3A;
    private static final int COLOR_TEXT = 0xFFF4EBDD;
    private static final int COLOR_MUTED = 0xFFB8B7AE;
    private static final int COLOR_ACCENT = 0xFFE0C38D;
    private static final int COLOR_ACCENT_DARK = 0xFF738368;

    private OmerHelper.Item item;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable autoCloseRunnable;
    private boolean actionClosed;
    private AlertFeedback alertFeedback;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguage.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        AppLog.d(this, "omer alert open");
        OmerReceiver.cancelNotification(this);

        long triggerAt = getIntent().getLongExtra(OmerScheduler.EXTRA_TRIGGER_AT, 0);
        item = triggerAt > 0 ? OmerHelper.itemForTrigger(this, triggerAt) : OmerHelper.dueNow(this, new ReminderSettings(this).omerOffsetMinutes());
        if (item == null || new OmerStore(this).isHandled(item.key)) {
            AppLog.d(this, "omer alert empty close");
            finish();
            return;
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(17), dp(42), dp(17), dp(22));

        LinearLayout textArea = new LinearLayout(this);
        textArea.setOrientation(LinearLayout.VERTICAL);
        textArea.setGravity(Gravity.CENTER);

        FrameLayout iconBadge = new FrameLayout(this);
        iconBadge.setBackground(iconBadgeBackground());
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_jewish_alert);
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        iconBadge.addView(icon, new FrameLayout.LayoutParams(-1, -1));
        textArea.addView(iconBadge, new LinearLayout.LayoutParams(dp(56), dp(56)));

        TextView title = text("ספירת העומר", 22, COLOR_TEXT);
        AppFont.bold(title);
        title.setShadowLayer(dp(2), 0, 0, 0xAAFFF3D5);
        textArea.addView(title);

        TextView count = text(item.label, 18, COLOR_TEXT);
        count.setPadding(dp(4), dp(8), dp(4), dp(4));
        textArea.addView(count);

        TextView hint = text(AppLanguage.isEnglish(this) ? "Tonight is day " + item.day + " of the Omer" : "סופרים הערב יום " + item.day + " לעומר", 12, COLOR_MUTED);
        hint.setPadding(dp(4), 0, dp(4), dp(4));
        textArea.addView(hint);
        content.addView(textArea, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                0.65f
        ));

        Button counted = button("ספרתי", COLOR_ACCENT_DARK);
        counted.setOnClickListener(v -> {
            actionClosed = true;
            handler.removeCallbacksAndMessages(null);
            stopVibration();
            AppLog.d(this, "omer counted day=" + item.day);
            new OmerStore(this).markHandled(item.key);
            OmerScheduler.schedule(this);
            finish();
        });
        Button retry = button("תזכר אותי לעוד שעה", COLOR_SURFACE);
        retry.setOnClickListener(v -> {
            actionClosed = true;
            handler.removeCallbacksAndMessages(null);
            stopVibration();
            AppLog.d(this, "omer retry pressed day=" + item.day);
            OmerScheduler.scheduleRetry(this, 60);
            finish();
        });
        LinearLayout buttonArea = new LinearLayout(this);
        buttonArea.setOrientation(LinearLayout.VERTICAL);
        buttonArea.setGravity(Gravity.CENTER);
        buttonArea.addView(counted);
        buttonArea.addView(retry);
        content.addView(buttonArea, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.35f
        ));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);
        root.addView(new ReminderAlertFrameView(this), new FrameLayout.LayoutParams(-1, -1));
        root.addView(content, new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        TopArcClockView clock = TopArcClockView.addTo(root);
        clock.setTranslationY(dp(4));
        root.addView(new ReminderAlertFrameView(this, false), new FrameLayout.LayoutParams(-1, -1));
        AppTextStyle.apply(root);
        setContentView(root);
        startVibration(new ReminderSettings(this));
        scheduleAutoClose();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopVibration();
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        resetAutoCloseTimer();
        return super.dispatchTouchEvent(event);
    }

    private void scheduleAutoClose() {
        ReminderSettings settings = new ReminderSettings(this);
        autoCloseRunnable = () -> {
            if (actionClosed) {
                return;
            }
            actionClosed = true;
            stopVibration();
            int minutes = new ReminderSettings(this).autoSnoozeMinutes();
            AppLog.w(this, "omer auto retry minutes=" + minutes + " day=" + item.day);
            OmerScheduler.scheduleRetry(this, minutes);
            finish();
        };
        handler.postDelayed(autoCloseRunnable, settings.autoSnoozeDelayMs());
    }

    private void resetAutoCloseTimer() {
        if (actionClosed || autoCloseRunnable == null) {
            return;
        }
        ReminderSettings settings = new ReminderSettings(this);
        handler.removeCallbacks(autoCloseRunnable);
        handler.postDelayed(autoCloseRunnable, settings.autoSnoozeDelayMs());
    }

    private void startVibration(ReminderSettings settings) {
        stopVibration();
        alertFeedback = AlertFeedback.start(this, settings);
    }

    private void stopVibration() {
        if (alertFeedback != null) {
            alertFeedback.stop();
            alertFeedback = null;
        } else {
            AlertFeedback.stopVibration(this);
        }
    }

    private Button button(String value, int color) {
        Button button = new Button(this);
        AppFont.apply(button);
        button.setText(UiText.t(this, value));
        button.setTextColor(COLOR_TEXT);
        button.setTextSize(value.length() > 9 ? 11 : 13);
        button.setAllCaps(false);
        button.setBackground(new DepthButtonDrawable(color, dp(20)));
        button.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        params.setMargins(dp(10), dp(5), dp(10), dp(5));
        button.setLayoutParams(params);
        return button;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        AppFont.apply(view);
        view.setText(UiText.t(this, value));
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setTextDirection(AppLanguage.isRtl(this) ? TextView.TEXT_DIRECTION_RTL : TextView.TEXT_DIRECTION_LTR);
        return view;
    }

    private GradientDrawable iconBadgeBackground() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF28526A, 0xFF0B2537});
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setStroke(dp(1), COLOR_ACCENT);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
