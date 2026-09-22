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

import java.util.List;

public class DafYomiAlertActivity extends Activity {
    private static final int COLOR_BG = 0xFF061522;
    private static final int COLOR_SURFACE = 0xFF142A3A;
    private static final int COLOR_TEXT = 0xFFF4EBDD;
    private static final int COLOR_MUTED = 0xFFB8B7AE;
    private static final int COLOR_ACCENT = 0xFFE0C38D;
    private static final int COLOR_ACCENT_DARK = 0xFF738368;
    private List<DafYomiHelper.Item> dueItems;
    private DafYomiHelper.Item currentItem;
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
        AppLog.d(this, "daf yomi alert open");
        DafYomiReceiver.cancelNotification(this);
        dueItems = new DafYomiStore(this).dueItems(this);
        if (dueItems.isEmpty()) {
            AppLog.d(this, "daf yomi alert empty close");
            finish();
            return;
        }
        currentItem = dueItems.get(0);
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

        TextView title = text("דף היומי", 22, COLOR_TEXT);
        AppFont.bold(title);
        title.setShadowLayer(dp(2), 0, 0, 0xAAFFF3D5);
        textArea.addView(title);

        TextView question = text(questionText(), 19, COLOR_TEXT);
        question.setPadding(dp(4), dp(8), dp(4), dp(8));
        textArea.addView(question);

        if (dueItems.size() > 1) {
            TextView counter = text(AppLanguage.isEnglish(this) ? "Page 1 of " + dueItems.size() : "דף " + 1 + " מתוך " + dueItems.size(), 12, COLOR_MUTED);
            counter.setPadding(0, 0, 0, dp(4));
            textArea.addView(counter);
        }
        content.addView(textArea, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                0.65f
        ));

        Button yes = button("כן", COLOR_ACCENT_DARK);
        yes.setOnClickListener(v -> {
            actionClosed = true;
            handler.removeCallbacksAndMessages(null);
            stopVibration();
            AppLog.d(this, "daf yomi learned");
            new DafYomiStore(this).markLearned(currentItem);
            DafYomiScheduler.schedule(this);
            closeAndOpenNextIfNeeded();
        });
        Button no = button("לא", 0xFF7E2A35);
        no.setOnClickListener(v -> {
            actionClosed = true;
            handler.removeCallbacksAndMessages(null);
            stopVibration();
            AppLog.d(this, "daf yomi missed");
            new DafYomiStore(this).markMissed(currentItem);
            DafYomiScheduler.schedule(this);
            closeAndOpenNextIfNeeded();
        });
        Button retry = button("תזכר אותי לעוד שעה", COLOR_SURFACE);
        retry.setOnClickListener(v -> {
            actionClosed = true;
            handler.removeCallbacksAndMessages(null);
            stopVibration();
            AppLog.d(this, "daf yomi retry pressed");
            DafYomiScheduler.scheduleRetry(this, 60);
            close();
        });
        LinearLayout buttonArea = new LinearLayout(this);
        buttonArea.setOrientation(LinearLayout.VERTICAL);
        buttonArea.setGravity(Gravity.CENTER);
        buttonArea.addView(yes);
        buttonArea.addView(no);
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

    private String questionText() {
        if (AppLanguage.isEnglish(this)) {
            return "Did you learn " + currentItem.label + "?";
        }
        return "למדת " + currentItem.label + "?";
    }

    private void close() {
        stopVibration();
        finish();
    }

    private void closeAndOpenNextIfNeeded() {
        stopVibration();
        finish();
        if (new DafYomiStore(this).dueItems(this).isEmpty()) {
            return;
        }
        handler.postDelayed(() -> {
            android.content.Intent next = new android.content.Intent(this, DafYomiAlertActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(next);
        }, 250L);
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
            AppLog.w(this, "daf yomi auto retry minutes=" + minutes);
            DafYomiScheduler.scheduleRetry(this, minutes);
            close();
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
        params.setMargins(dp(3), dp(4), dp(3), dp(4));
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
