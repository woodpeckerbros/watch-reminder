package com.woodpeckerbros.watchreminder;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class ReminderAlertActivity extends Activity {
    private static final int COLOR_BG = 0xFF000000;
    private static final int COLOR_SURFACE = 0xFF12171A;
    private static final int COLOR_SURFACE_2 = 0xFF1A2024;
    private static final int COLOR_TEXT = 0xFFF4F7F5;
    private static final int COLOR_MUTED = 0xFFAEB8B2;
    private static final int COLOR_ACCENT = 0xFF52D273;
    private static final int COLOR_ACCENT_DARK = 0xFF136F45;
    private static WeakReference<ReminderAlertActivity> activeActivity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ScrollView activeScrollView;
    private Runnable autoCloseRunnable;
    private long autoCloseDelayMs;
    private String activeOccurrenceId;
    private boolean actionClosed;
    private AlertFeedback alertFeedback;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguage.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activeActivity = new WeakReference<>(this);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String reminderName = getIntent().getStringExtra(ReminderScheduler.EXTRA_REMINDER_NAME);
        if (reminderName == null || reminderName.trim().isEmpty()) {
            reminderName = UiText.t(this, "תזכורת");
        }
        final String alertReminderName = reminderName;
        String occurrenceId = getIntent().getStringExtra(ReminderScheduler.EXTRA_OCCURRENCE_ID);
        activeOccurrenceId = occurrenceId;
        String reminderId = getIntent().getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID);
        Reminder reminder = reminderId == null ? null : new ReminderStore(this).find(reminderId);
        String reminderDescription = reminder == null ? "" : reminder.description;
        long scheduledAt = getIntent().getLongExtra(ReminderScheduler.EXTRA_SCHEDULED_AT, System.currentTimeMillis());
        long originalScheduledAt = getIntent().getLongExtra(ReminderScheduler.EXTRA_ORIGINAL_SCHEDULED_AT, scheduledAt);
        ReminderAlertQueueStore queueStore = new ReminderAlertQueueStore(this);
        ReminderAlertQueueStore.QueuedAlert groupedAlert = queueStore.getActiveAlert(occurrenceId);
        if (groupedAlert == null) {
            groupedAlert = new ReminderAlertQueueStore.QueuedAlert(
                    occurrenceId,
                    reminderId,
                    alertReminderName,
                    scheduledAt,
                    originalScheduledAt,
                    getIntent().getIntExtra(ReminderScheduler.EXTRA_DAY, -1),
                    getIntent().getBooleanExtra(ReminderScheduler.EXTRA_IS_SNOOZE, false)
            );
        }
        final ReminderAlertQueueStore.QueuedAlert alertGroup = groupedAlert;
        final ArrayList<String> occurrenceIds = new ArrayList<>(alertGroup.occurrenceIds);
        final long snoozeOriginalScheduledAt = alertGroup.latestOriginalScheduledAt();
        AppLog.d(this, "alertActivity open id=" + reminderId + " occurrence=" + occurrenceId + " name=" + alertReminderName + " at=" + NextReminderCalculator.formatDateTime(scheduledAt));
        ReminderSettings settings = new ReminderSettings(this);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(14), dp(18), dp(14), dp(18));
        content.setBackgroundColor(COLOR_BG);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(COLOR_SURFACE, dp(8), 0x223A4540));

        TextView title = text("תזכורת", 18, COLOR_ACCENT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView name = text(alertReminderName, 23, COLOR_TEXT);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setPadding(0, dp(8), 0, dp(10));
        TextView description = text(reminderDescription, 14, COLOR_MUTED);
        description.setPadding(dp(6), 0, dp(6), dp(8));

        card.addView(title);
        card.addView(name);
        if (!reminderDescription.isEmpty()) {
            card.addView(description);
        }
        if (alertGroup.count() > 1) {
            TextView missedTitle = text("פוספס " + alertGroup.count() + " פעמים", 14, COLOR_ACCENT);
            missedTitle.setTypeface(Typeface.DEFAULT_BOLD);
            missedTitle.setPadding(0, 0, 0, dp(4));
            card.addView(missedTitle);
            TextView missedDates = text(formatMissedDates(alertGroup.scheduledAts), 12, COLOR_MUTED);
            missedDates.setPadding(dp(6), 0, dp(6), dp(8));
            card.addView(missedDates);
        }
        String originalTimeLabel = UiText.t(this, alertGroup.count() > 1 ? "שעה אחרונה" : "שעה מקורית");
        TextView originalTime = text(originalTimeLabel + ": " + NextReminderCalculator.formatTime(snoozeOriginalScheduledAt), 13, COLOR_MUTED);
        originalTime.setPadding(0, 0, 0, dp(8));
        card.addView(originalTime);

        Button done = actionButton("בוצע", COLOR_ACCENT_DARK);
        done.setOnClickListener(v -> {
            stopVibration();
            AppLog.d(this, "alert done occurrence=" + occurrenceId);
            handler.removeCallbacksAndMessages(null);
            cancelNotification(occurrenceId);
            ReminderEventStore eventStore = new ReminderEventStore(this);
            for (String id : currentOccurrenceIds(occurrenceId, occurrenceIds)) {
                if (id != null) {
                    eventStore.markDone(id);
                    ReminderScheduler.cancelAutoSnooze(this, id, reminderId, alertReminderName);
                }
            }
            if (reminderId != null) {
                ReminderScheduler.cancelSnooze(this, reminderId, alertReminderName);
                ReminderScheduler.cancelDeferredRetry(this, reminderId);
                Reminder doneReminder = new ReminderStore(this).find(reminderId);
                if (doneReminder != null && doneReminder.isOneTime()) {
                    new ReminderStore(this).delete(doneReminder);
                }
            }
            closeAfterAction();
        });
        card.addView(done);

        TextView snoozeTitle = text("תזכר אותי בעוד:", 13, COLOR_MUTED);
        snoozeTitle.setPadding(0, dp(10), 0, dp(3));
        card.addView(snoozeTitle);

        LinearLayout firstRow = actionRow();
        firstRow.addView(snoozeButton("15 דקות", occurrenceId, occurrenceIds, reminderId, alertReminderName, 15, snoozeOriginalScheduledAt));
        firstRow.addView(snoozeButton("30 דקות", occurrenceId, occurrenceIds, reminderId, alertReminderName, 30, snoozeOriginalScheduledAt));
        card.addView(firstRow);

        LinearLayout secondRow = actionRow();
        secondRow.addView(snoozeButton("שעה", occurrenceId, occurrenceIds, reminderId, alertReminderName, 60, snoozeOriginalScheduledAt));
        secondRow.addView(snoozeButton("שעתיים", occurrenceId, occurrenceIds, reminderId, alertReminderName, 120, snoozeOriginalScheduledAt));
        card.addView(secondRow);

        LinearLayout customRow = new LinearLayout(this);
        customRow.setGravity(Gravity.CENTER);
        customRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        NumberPicker customMinutes = numberPicker(1, 240, settings.autoSnoozeMinutes());
        customRow.addView(pickerColumn("דקות", customMinutes));
        Button customSnooze = actionButton("דחייה", COLOR_ACCENT_DARK);
        customSnooze.setOnClickListener(v -> snooze(occurrenceId, occurrenceIds, reminderId, alertReminderName, customMinutes.getValue(), snoozeOriginalScheduledAt));
        customRow.addView(customSnooze);
        card.addView(customRow);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(dp(4), 0, dp(4), 0);
        content.addView(card, cardParams);

        ScrollView scrollView = new ScrollView(this);
        activeScrollView = scrollView;
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_BG);
        scrollView.addView(content);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        scrollParams.topMargin = dp(15);
        root.addView(scrollView, scrollParams);
        addTopClock(root);
        setContentView(root);
        startVibration(settings);

        autoCloseDelayMs = settings.autoSnoozeDelayMs();
        autoCloseRunnable = () -> runAutoSnoozeIfPending(occurrenceId, occurrenceIds, reminderId, alertReminderName, snoozeOriginalScheduledAt);
        scheduleAutoClose();
    }

    @Override
    protected void onDestroy() {
        stopVibration();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        handler.removeCallbacksAndMessages(null);
        ReminderAlertActivity active = activeActivity == null ? null : activeActivity.get();
        if (active == this) {
            activeActivity = null;
        }
        if (!actionClosed) {
            AppLog.d(this, "alertActivity destroyed without action occurrence=" + activeOccurrenceId);
            new ReminderAlertQueueStore(this).complete(activeOccurrenceId);
            ReminderReceiver.dispatchNextQueued(this);
        }
        super.onDestroy();
    }

    public static boolean closeAutoSnoozed(String occurrenceId) {
        ReminderAlertActivity activity = activeActivity == null ? null : activeActivity.get();
        if (activity == null || activity.actionClosed || occurrenceId == null || !occurrenceId.equals(activity.activeOccurrenceId)) {
            return false;
        }
        activity.handler.post(() -> {
            if (!activity.actionClosed && occurrenceId.equals(activity.activeOccurrenceId)) {
                AppLog.w(activity, "alert close from AutoSnoozeReceiver occurrence=" + occurrenceId);
                activity.handler.removeCallbacksAndMessages(null);
                activity.cancelNotification(occurrenceId);
                activity.closeAfterAction();
            }
        });
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (activeScrollView != null
                && event.getAction() == MotionEvent.ACTION_SCROLL
                && (event.getSource() & InputDevice.SOURCE_ROTARY_ENCODER) == InputDevice.SOURCE_ROTARY_ENCODER) {
            resetAutoCloseTimer();
            float delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL);
            activeScrollView.smoothScrollBy(0, Math.round(delta * dp(42)));
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        resetAutoCloseTimer();
        return super.dispatchTouchEvent(event);
    }

    private void resetAutoCloseTimer() {
        if (actionClosed || autoCloseRunnable == null) {
            return;
        }
        scheduleAutoClose();
    }

    private void scheduleAutoClose() {
        if (autoCloseRunnable == null) {
            return;
        }
        handler.removeCallbacks(autoCloseRunnable);
        handler.postDelayed(autoCloseRunnable, autoCloseDelayMs);
    }

    private Button snoozeButton(String label, String activeOccurrenceId, List<String> occurrenceIds, String reminderId, String reminderName, int minutes, long originalScheduledAt) {
        Button button = actionButton(label, COLOR_SURFACE_2);
        button.setOnClickListener(v -> snooze(activeOccurrenceId, occurrenceIds, reminderId, reminderName, minutes, originalScheduledAt));
        return button;
    }

    private void snooze(String activeOccurrenceId, List<String> occurrenceIds, String reminderId, String reminderName, int minutes, long originalScheduledAt) {
        stopVibration();
        List<String> currentIds = currentOccurrenceIds(activeOccurrenceId, occurrenceIds);
        AppLog.d(this, "alert snooze occurrence=" + activeOccurrenceId + " count=" + currentIds.size() + " minutes=" + minutes);
        handler.removeCallbacksAndMessages(null);
        cancelNotification(activeOccurrenceId);
        if (reminderId != null) {
            if (!currentIds.isEmpty()) {
                long nextScheduledAt = ReminderScheduler.scheduleSnooze(this, reminderId, reminderName, minutes, originalScheduledAt);
                ReminderEventStore eventStore = new ReminderEventStore(this);
                for (String occurrenceId : currentIds) {
                    if (occurrenceId != null) {
                        eventStore.markSnoozed(occurrenceId, minutes, nextScheduledAt);
                        ReminderScheduler.cancelAutoSnooze(this, occurrenceId, reminderId, reminderName);
                    }
                }
            } else {
                ReminderScheduler.scheduleSnooze(this, reminderId, reminderName, minutes, originalScheduledAt);
            }
        }
        closeAfterAction();
    }

    private void runAutoSnoozeIfPending(String activeOccurrenceId, List<String> occurrenceIds, String reminderId, String reminderName, long originalScheduledAt) {
        if (activeOccurrenceId == null || reminderId == null || reminderName == null) {
            closeAfterAction();
            return;
        }
        ReminderEventStore eventStore = new ReminderEventStore(this);
        ReminderEventStore.Event event = eventStore.find(activeOccurrenceId);
        if (event != null && ReminderEventStore.STATUS_FIRED.equals(event.status)) {
            List<String> currentIds = currentOccurrenceIds(activeOccurrenceId, occurrenceIds);
            int minutes = new ReminderSettings(this).autoSnoozeMinutes();
            AppLog.w(this, "alert autoSnooze occurrence=" + activeOccurrenceId + " count=" + currentIds.size() + " minutes=" + minutes);
            long nextScheduledAt = ReminderScheduler.scheduleSnooze(this, reminderId, reminderName, minutes, originalScheduledAt);
            for (String occurrenceId : currentIds) {
                if (occurrenceId != null) {
                    eventStore.markAutoSnoozed(occurrenceId, minutes, nextScheduledAt);
                    ReminderScheduler.cancelAutoSnooze(this, occurrenceId, reminderId, reminderName);
                }
            }
            cancelNotification(activeOccurrenceId);
        }
        closeAfterAction();
    }

    private void closeAfterAction() {
        stopVibration();
        actionClosed = true;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        new ReminderAlertQueueStore(this).complete(activeOccurrenceId);
        finishAndRemoveTask();
        ReminderReceiver.dispatchNextQueued(this);
    }

    private List<String> currentOccurrenceIds(String occurrenceId, List<String> fallback) {
        ReminderAlertQueueStore.QueuedAlert activeAlert = new ReminderAlertQueueStore(this).getActiveAlert(occurrenceId);
        if (activeAlert != null && !activeAlert.occurrenceIds.isEmpty()) {
            return new ArrayList<>(activeAlert.occurrenceIds);
        }
        return new ArrayList<>(fallback);
    }

    private void startVibration(ReminderSettings settings) {
        stopVibration();
        alertFeedback = AlertFeedback.start(this, settings);
    }

    private void stopVibration() {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.cancelAll();
        } catch (Exception ignored) {
        }
        if (alertFeedback != null) {
            alertFeedback.stop();
            alertFeedback = null;
        } else {
            AlertFeedback.stopVibration(this);
        }
    }

    private LinearLayout actionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        return row;
    }

    private Button actionButton(String value, int color) {
        Button button = new Button(this);
        button.setText(UiText.t(this, value));
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setBackground(rounded(color, dp(8), 0));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(76), dp(36));
        params.setMargins(dp(3), dp(2), dp(3), dp(2));
        button.setLayoutParams(params);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private void addTopClock(FrameLayout content) {
        View band = new View(this);
        band.setBackgroundColor(COLOR_BG);
        FrameLayout.LayoutParams bandParams = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(27)
        );
        bandParams.gravity = Gravity.TOP;
        content.addView(band, bandParams);
        TopArcClockView clock = new TopArcClockView(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(30)
        );
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        clock.setLayoutParams(params);
        content.addView(clock);
    }

    private NumberPicker numberPicker(int min, int max, int value) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(Math.max(min, Math.min(max, value)));
        picker.setWrapSelectorWheel(true);
        picker.setFormatter(number -> String.format(java.util.Locale.US, "%02d", number));
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(78), dp(82));
        params.setMargins(dp(3), 0, dp(3), 0);
        picker.setLayoutParams(params);
        return picker;
    }

    private LinearLayout pickerColumn(String label, NumberPicker picker) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        TextView title = text(label, 12, COLOR_MUTED);
        column.addView(title);
        column.addView(picker);
        return column;
    }

    private void cancelNotification(String occurrenceId) {
        if (occurrenceId == null) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(occurrenceId.hashCode());
    }

    private String formatMissedDates(List<Long> scheduledAts) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < scheduledAts.size(); i++) {
            if (i > 0) {
                builder.append("\n");
            }
            builder.append(NextReminderCalculator.formatDateTime(scheduledAts.get(i)));
        }
        return builder.toString();
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(UiText.t(this, value));
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setTextDirection(AppLanguage.isRtl(this) ? TextView.TEXT_DIRECTION_RTL : TextView.TEXT_DIRECTION_LTR);
        return view;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
