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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ReminderAlertActivity extends Activity {
    private static final int COLOR_BG = 0xFF061522;
    private static final int COLOR_SURFACE = 0xFF0B2133;
    private static final int COLOR_SURFACE_2 = 0xFF142A3A;
    private static final int COLOR_TEXT = 0xFFF4EBDD;
    private static final int COLOR_MUTED = 0xFFB8B7AE;
    private static final int COLOR_ACCENT = 0xFFC77B58;
    private static final int COLOR_ACCENT_DARK = 0xFF66745D;
    private static final int COLOR_ACTION_GREEN = 0xFF738368;
    private static WeakReference<ReminderAlertActivity> activeActivity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ScrollView activeScrollView;
    private Runnable autoCloseRunnable;
    private long autoCloseDelayMs;
    private String activeOccurrenceId;
    private boolean actionClosed;
    private boolean complicationRefreshRequested;
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
        content.setPadding(dp(17), dp(42), dp(17), dp(22));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(9), dp(4), dp(9), dp(12));

        OrnateBellView icon = new OrnateBellView(this);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(58), dp(54));
        iconParams.setMargins(0, dp(1), 0, dp(2));
        icon.setLayoutParams(iconParams);
        TextView name = text(alertReminderName, 24, COLOR_TEXT);
        AppFont.bold(name);
        name.setPadding(0, dp(2), 0, dp(5));
        TextView description = text(reminderDescription, 14, COLOR_MUTED);
        description.setPadding(dp(6), 0, dp(6), dp(5));

        card.addView(icon);
        card.addView(name);
        if (!reminderDescription.isEmpty()) {
            card.addView(description);
        }
        if (alertGroup.count() > 1) {
            String missedCount = AppLanguage.isEnglish(this)
                    ? "Missed " + alertGroup.count() + " times"
                    : "פוספס " + alertGroup.count() + " פעמים";
            TextView missedTitle = text(missedCount, 14, COLOR_ACCENT);
            AppFont.bold(missedTitle);
            missedTitle.setPadding(0, 0, 0, dp(4));
            card.addView(missedTitle);
            TextView missedDates = text(formatMissedDates(alertGroup.scheduledAts), 12, COLOR_MUTED);
            missedDates.setPadding(dp(6), 0, dp(6), dp(8));
            card.addView(missedDates);
        }
        String originalTimeLabel = UiText.t(this, alertGroup.count() > 1 ? "שעה אחרונה" : "שעה מקורית");
        TextView originalTime = text(originalTimeLabel + ": " + NextReminderCalculator.formatTime(snoozeOriginalScheduledAt), 13, COLOR_MUTED);
        originalTime.setPadding(0, 0, 0, dp(5));
        card.addView(originalTime);

        Button done = actionButton("בוצע", COLOR_ACCENT_DARK);
        done.setTextColor(COLOR_TEXT);
        done.setBackground(new DepthButtonDrawable(COLOR_ACTION_GREEN, dp(24)));
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        doneParams.setMargins(dp(3), dp(4), dp(3), dp(3));
        done.setLayoutParams(doneParams);
        done.setTextSize(18);
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

        TextView snoozeTitle = text("אפשר לדחות", 13, COLOR_MUTED);
        snoozeTitle.setPadding(0, dp(8), 0, dp(3));
        card.addView(snoozeTitle);

        LinearLayout firstRow = actionRow();
        firstRow.addView(snoozeButton("15 דקות", occurrenceId, occurrenceIds, reminderId, alertReminderName, 15, snoozeOriginalScheduledAt));
        firstRow.addView(snoozeButton("30 דקות", occurrenceId, occurrenceIds, reminderId, alertReminderName, 30, snoozeOriginalScheduledAt));
        card.addView(firstRow);

        LinearLayout secondRow = actionRow();
        secondRow.addView(snoozeButton("שעה", occurrenceId, occurrenceIds, reminderId, alertReminderName, 60, snoozeOriginalScheduledAt));
        secondRow.addView(snoozeButton("שעתיים", occurrenceId, occurrenceIds, reminderId, alertReminderName, 120, snoozeOriginalScheduledAt));
        card.addView(secondRow);

        Button snoozeOneDay = actionButton("דחה ביום", COLOR_ACTION_GREEN);
        LinearLayout.LayoutParams snoozeOneDayParams = new LinearLayout.LayoutParams(dp(158), dp(36));
        snoozeOneDayParams.setMargins(dp(3), dp(5), dp(3), dp(2));
        snoozeOneDay.setLayoutParams(snoozeOneDayParams);
        snoozeOneDay.setOnClickListener(v -> snoozeOneDay(
                occurrenceId,
                occurrenceIds,
                reminderId,
                alertReminderName,
                snoozeOriginalScheduledAt
        ));
        card.addView(snoozeOneDay);

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
        scrollView.addView(content);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);
        root.addView(new ReminderAlertFrameView(this), new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        root.addView(scrollView, scrollParams);
        TopArcClockView alertClock = TopArcClockView.addTo(root);
        alertClock.setTranslationY(dp(4));
        ReminderAlertFrameView rim = new ReminderAlertFrameView(this, false);
        root.addView(rim, new FrameLayout.LayoutParams(-1, -1));
        AppTextStyle.apply(root);
        applyGlow(name, 0xAAFFF3D5, dp(2));
        applyGlow(done, 0xCCFFF4D2, dp(3));
        setContentView(root);
        startVibration(settings);

        autoCloseDelayMs = settings.autoSnoozeDelayMs();
        autoCloseRunnable = () -> runAutoSnoozeIfPending(occurrenceId, occurrenceIds, reminderId, alertReminderName, snoozeOriginalScheduledAt);
        scheduleAutoClose();
    }

    @Override
    protected void onDestroy() {
        requestComplicationRefreshOnce();
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
        Button button = actionButton(label, COLOR_ACTION_GREEN);
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

    private void snoozeOneDay(String activeOccurrenceId, List<String> occurrenceIds, String reminderId, String reminderName, long originalScheduledAt) {
        stopVibration();
        List<String> currentIds = currentOccurrenceIds(activeOccurrenceId, occurrenceIds);
        long nextScheduledAt = nextDayAtOriginalTime(originalScheduledAt);
        AppLog.d(this, "alert snooze one day occurrence=" + activeOccurrenceId
                + " count=" + currentIds.size()
                + " original=" + NextReminderCalculator.formatDateTime(originalScheduledAt)
                + " next=" + NextReminderCalculator.formatDateTime(nextScheduledAt));
        handler.removeCallbacksAndMessages(null);
        cancelNotification(activeOccurrenceId);
        if (reminderId != null) {
            nextScheduledAt = ReminderScheduler.scheduleSnoozeAt(
                    this,
                    reminderId,
                    reminderName,
                    nextScheduledAt,
                    originalScheduledAt
            );
            ReminderEventStore eventStore = new ReminderEventStore(this);
            String note = "למחר בשעה " + NextReminderCalculator.formatTime(nextScheduledAt);
            for (String occurrenceId : currentIds) {
                if (occurrenceId != null) {
                    eventStore.markSnoozedUntil(occurrenceId, nextScheduledAt, note);
                    ReminderScheduler.cancelAutoSnooze(this, occurrenceId, reminderId, reminderName);
                }
            }
        }
        Toast.makeText(
                this,
                UiText.t(this, "ההתראה נדחתה למחר לשעה") + " " + NextReminderCalculator.formatTime(nextScheduledAt),
                Toast.LENGTH_LONG
        ).show();
        closeAfterAction();
    }

    private long nextDayAtOriginalTime(long originalScheduledAt) {
        Calendar original = Calendar.getInstance();
        original.setTimeInMillis(originalScheduledAt);
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        tomorrow.set(Calendar.HOUR_OF_DAY, original.get(Calendar.HOUR_OF_DAY));
        tomorrow.set(Calendar.MINUTE, original.get(Calendar.MINUTE));
        tomorrow.set(Calendar.SECOND, 0);
        tomorrow.set(Calendar.MILLISECOND, 0);
        return tomorrow.getTimeInMillis();
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
        requestComplicationRefreshOnce();
        finishAndRemoveTask();
        ReminderReceiver.dispatchNextQueued(this);
    }

    private void requestComplicationRefreshOnce() {
        if (complicationRefreshRequested) {
            return;
        }
        complicationRefreshRequested = true;
        ComplicationRefresh.request(this);
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
        AppFont.apply(button);
        button.setText(UiText.t(this, value));
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setBackground(new DepthButtonDrawable(color, dp(18)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(76), dp(36));
        params.setMargins(dp(3), dp(2), dp(3), dp(2));
        button.setLayoutParams(params);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private void applyGlow(TextView view, int color, float radius) {
        view.setShadowLayer(radius, 0, 0, color);
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
        AppFont.apply(view);
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
