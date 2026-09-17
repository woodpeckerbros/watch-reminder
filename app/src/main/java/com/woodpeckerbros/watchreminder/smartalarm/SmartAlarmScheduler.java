package com.woodpeckerbros.watchreminder.smartalarm;

import com.woodpeckerbros.watchreminder.reminder.*;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.woodpeckerbros.watchreminder.AppLog;
import com.woodpeckerbros.watchreminder.entitlement.EntitlementAccess;
import com.woodpeckerbros.watchreminder.reminder.ReminderScheduler;

import java.util.Calendar;

public final class SmartAlarmScheduler {
    public static final String EXTRA_TARGET_AT = "smart_alarm_target_at";
    public static final String EXTRA_ALARM_ID = "smart_alarm_id";
    public static final String EXTRA_WAKE_WINDOW_START_AT = "smart_alarm_wake_window_start_at";
    private SmartAlarmScheduler() {}

    public static void reschedule(Context context) {
        if (!EntitlementAccess.isFeatureAccessGranted(context)) {
            cancel(context, "ENTITLEMENT_REVOKED");
            return;
        }
        cancel(context, "RESCHEDULE_ALL");
        for (int alarmId : SmartAlarmStore.ids(context)) schedule(context, alarmId);
        ReminderMonitoringService.ensureRunning(context);
    }

    /**
     * Restores schedules after a periodic recovery pass without replacing an active snooze.
     * A normal reschedule calculates the next calendar occurrence, which would otherwise discard
     * a snooze whose original alarm time has already passed.
     */
    public static void recover(Context context) {
        if (!EntitlementAccess.isFeatureAccessGranted(context)) {
            cancel(context, "ENTITLEMENT_REVOKED");
            return;
        }
        long now = System.currentTimeMillis();
        for (int alarmId : SmartAlarmStore.ids(context)) {
            SmartAlarmStore store = new SmartAlarmStore(context, alarmId);
            SmartAlarmStateStore state = new SmartAlarmStateStore(context, alarmId);
            long targetAt = state.targetAt();
            boolean shadowMatches = SmartAlarmBootStore.matches(context, alarmId, targetAt);
            boolean directBootDelivered = SmartAlarmBootStore.delivered(context, alarmId, targetAt);
            if (SmartAlarmRecoveryPolicy.shouldFinalizeDirectBootDelivery(
                    shadowMatches, directBootDelivered)) {
                if (state.completeDirectBootDelivery(targetAt)) {
                    AppLog.w(context, "SmartAlarm recovery finalized direct-boot delivery id="
                            + alarmId + " target=" + targetAt + "; scheduling next occurrence");
                    reschedule(context, alarmId);
                    continue;
                }
                AppLog.w(context, "SmartAlarm recovery could not finalize direct-boot delivery id="
                        + alarmId + " target=" + targetAt + "; preserving safety shadow");
            }
            SmartAlarmRecoveryPolicy.Action action = SmartAlarmRecoveryPolicy.decide(
                    store.enabled(), targetAt, now, state.fired(targetAt), state.dismissed(targetAt));
            if (action == SmartAlarmRecoveryPolicy.Action.RESTORE_FUTURE_DEADLINE) {
                AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                if (manager != null) {
                    setDeadlineAlarm(context, manager, alarmId, targetAt, deadlineIntent(context, alarmId, targetAt));
                    scheduleHardStop(context, manager, alarmId, targetAt);
                    if (state.snoozeUsed() > 0) {
                        AppLog.d(context, "SmartAlarm recovery preserved snooze id=" + alarmId
                                + " target=" + targetAt + " used=" + state.snoozeUsed());
                    } else {
                        long wakeWindowStartAt = targetAt - store.windowMinutes() * 60_000L;
                        long monitorAt = monitorAt(context, wakeWindowStartAt);
                        // Once the monitor alarm has fired, scheduling it again would immediately
                        // redeliver the same intent and interrupt the active detector.
                        if (now < monitorAt) {
                            setWindowAlarm(context, manager, monitorAt,
                                    windowIntent(context, alarmId, targetAt, wakeWindowStartAt));
                        } else if (now < wakeWindowStartAt) {
                            // Recovery after the monitor alarm must not leave the baseline absent.
                            SmartWakeMonitoringService.start(context, alarmId, targetAt, wakeWindowStartAt);
                        }
                        if (now < wakeWindowStartAt) setWindowAlarm(context, manager, wakeWindowStartAt,
                                windowStartCheckIntent(context, alarmId, targetAt, wakeWindowStartAt));
                        AppLog.d(context, "SmartAlarm recovery preserved scheduled id=" + alarmId
                                + " target=" + targetAt + " monitorActive=" + (now >= monitorAt));
                    }
                }
                continue;
            }
            if (action == SmartAlarmRecoveryPolicy.Action.DELIVER_RECENT_MISSED_DEADLINE) {
                scheduleMissedDeadlineFire(context, alarmId, targetAt, now);
                AppLog.w(context, "SmartAlarm recovery catch-up id=" + alarmId
                        + " target=" + targetAt + " lateByMs=" + (now - targetAt));
                continue;
            }
            if (action == SmartAlarmRecoveryPolicy.Action.PRESERVE_ACTIVE_ALERT) {
                AppLog.d(context, "SmartAlarm recovery preserved active alert id=" + alarmId
                        + " target=" + targetAt);
                continue;
            }
            if (targetAt > 0L && targetAt <= now && !state.fired(targetAt)
                    && !state.dismissed(targetAt)) {
                AppLog.w(context, "SmartAlarm recovery expired missed occurrence id=" + alarmId
                        + " target=" + targetAt + " lateByMs=" + (now - targetAt)
                        + " policy=RESCHEDULE_NEXT_OCCURRENCE");
            }
            reschedule(context, alarmId);
        }
        ReminderMonitoringService.ensureRunning(context);
    }

    /** Restores only the minimal device-protected occurrence shadow before the watch is unlocked. */
    public static void recoverLockedBoot(Context context) {
        long now = System.currentTimeMillis();
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        for (SmartAlarmBootStore.Entry entry : SmartAlarmBootStore.entries(context)) {
            SmartAlarmRecoveryPolicy.Action action = SmartAlarmRecoveryPolicy.decideLockedBoot(
                    entry.targetAt, now, entry.delivered);
            if (action == SmartAlarmRecoveryPolicy.Action.RESTORE_FUTURE_DEADLINE) {
                setDeadlineAlarm(context, manager, entry.alarmId, entry.targetAt,
                        deadlineIntent(context, entry.alarmId, entry.targetAt));
                scheduleHardStop(context, manager, entry.alarmId, entry.targetAt);
                AppLog.d(context, "FINAL_ALARM_RESTORED_LOCKED_BOOT id=" + entry.alarmId
                        + " occurrence_id=" + entry.alarmId + ":" + entry.targetAt
                        + " deadline=" + entry.targetAt);
            } else if (action == SmartAlarmRecoveryPolicy.Action.DELIVER_RECENT_MISSED_DEADLINE) {
                scheduleMissedDeadlineFire(context, entry.alarmId, entry.targetAt, now);
                AppLog.w(context, "FINAL_ALARM_CATCH_UP_LOCKED_BOOT id=" + entry.alarmId
                        + " occurrence_id=" + entry.alarmId + ":" + entry.targetAt
                        + " lateByMs=" + (now - entry.targetAt));
            }
            SmartWakeDirectBootPolicy.Action monitoringAction = SmartWakeDirectBootPolicy.decide(
                    entry.smartWakeEnabled, entry.delivered, entry.monitoringStartAt,
                    entry.earliestWakeAt, entry.targetAt, now);
            if (monitoringAction == SmartWakeDirectBootPolicy.Action.RESTORE_MONITORING_START) {
                setWindowAlarm(context, manager, entry.monitoringStartAt,
                        windowIntent(context, entry.alarmId, entry.targetAt, entry.earliestWakeAt));
                setWindowAlarm(context, manager, entry.earliestWakeAt,
                        windowStartCheckIntent(context, entry.alarmId, entry.targetAt,
                                entry.earliestWakeAt));
                AppLog.d(context, "SMART_WAKE_MONITOR_RESTORED_LOCKED_BOOT id=" + entry.alarmId
                        + " occurrence_id=" + entry.alarmId + ":" + entry.targetAt
                        + " monitoring_start=" + entry.monitoringStartAt
                        + " earliest_wake=" + entry.earliestWakeAt
                        + " deadline=" + entry.targetAt);
            } else if (monitoringAction == SmartWakeDirectBootPolicy.Action.START_MONITORING_CATCH_UP) {
                if (now < entry.earliestWakeAt) {
                    setWindowAlarm(context, manager, entry.earliestWakeAt,
                            windowStartCheckIntent(context, entry.alarmId, entry.targetAt,
                                    entry.earliestWakeAt));
                }
                SmartWakeMonitoringService.startDirectBoot(context, entry.alarmId, entry.targetAt,
                        entry.earliestWakeAt);
                AppLog.w(context, "SMART_WAKE_MONITOR_CATCH_UP_LOCKED_BOOT id=" + entry.alarmId
                        + " occurrence_id=" + entry.alarmId + ":" + entry.targetAt
                        + " monitoring_start=" + entry.monitoringStartAt
                        + " earliest_wake=" + entry.earliestWakeAt
                        + " deadline=" + entry.targetAt);
            }
        }
    }

    public static void reschedule(Context context, int alarmId) {
        cancel(context, alarmId, "RESCHEDULE");
        schedule(context, alarmId);
        ReminderMonitoringService.ensureRunning(context);
    }

    private static void schedule(Context context, int alarmId) {
        schedule(context, alarmId, System.currentTimeMillis());
    }

    /**
     * Schedules the first calendar occurrence strictly after {@code notBefore}.
     * This matters when Smart Wake fires before its configured deadline: dismissing at (for
     * example) 06:59 must not create a fresh 07:10 occurrence for the same morning.
     */
    private static void schedule(Context context, int alarmId, long notBefore) {
        SmartAlarmStore store = new SmartAlarmStore(context, alarmId);
        if (!store.enabled()) return;
        long targetAt = nextTarget(store.hour(), store.minute(), store.daysMask(), notBefore);
        if (targetAt == Long.MAX_VALUE) return;
        long wakeWindowStartAt = targetAt - store.windowMinutes() * 60_000L;
        // Build a personal sleep baseline before the user-selected wake window, while keeping
        // the actual alarm decision strictly inside that window.
        long monitorAt = monitorAt(context, wakeWindowStartAt);
        SmartAlarmStateStore state = new SmartAlarmStateStore(context, alarmId);
        state.begin(targetAt);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        SmartAlarmBootStore.arm(context, alarmId, targetAt, monitorAt, wakeWindowStartAt,
                store.windowMinutes() > 0);
        setWindowAlarm(context, manager, monitorAt, windowIntent(context, alarmId, targetAt, wakeWindowStartAt));
        setWindowAlarm(context, manager, wakeWindowStartAt,
                windowStartCheckIntent(context, alarmId, targetAt, wakeWindowStartAt));
        setDeadlineAlarm(context, manager, alarmId, targetAt, deadlineIntent(context, alarmId, targetAt));
        scheduleHardStop(context, manager, alarmId, targetAt);
        AppLog.d(context, "SmartAlarm scheduled id=" + alarmId + " monitor=" + monitorAt
                + " wakeWindow=" + wakeWindowStartAt + " target=" + targetAt
                + " baselineBufferMs=" + SmartWakeSamplingProfile.startBufferMs(
                SmartWakeSamplingProfile.observedHrIntervalMs(context)));
    }

    public static void scheduleSnooze(Context context, int alarmId, long originalTargetAt, int minutes) {
        cancelAutoSnooze(context, alarmId);
        cancel(context, alarmId, "SNOOZE_REPLACEMENT");
        long targetAt = System.currentTimeMillis() + minutes * 60_000L;
        SmartAlarmStateStore state = new SmartAlarmStateStore(context, alarmId);
        state.beginSnooze(targetAt, state.snoozeUsed() + 1);
        SmartAlarmBootStore.arm(context, alarmId, targetAt, 0L, 0L, false);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) setDeadlineAlarm(context, manager, alarmId, targetAt, deadlineIntent(context, alarmId, targetAt));
        AppLog.d(context, "SmartAlarm snoozed id=" + alarmId + " original=" + originalTargetAt + " target=" + targetAt);
        ReminderMonitoringService.ensureRunning(context);
    }

    /**
     * Ends the current occurrence and schedules only a later calendar occurrence.  Do not use a
     * plain reschedule here: before the configured deadline it would recreate the same alarm.
     */
    public static void scheduleNextAfterHandled(Context context, int alarmId, long handledTargetAt) {
        cancel(context, alarmId, "OCCURRENCE_HANDLED");
        long occurrenceTargetAt = new SmartAlarmStateStore(context, alarmId).occurrenceTargetAt();
        schedule(context, alarmId, handledOccurrenceBoundary(
                System.currentTimeMillis(), handledTargetAt, occurrenceTargetAt));
        ReminderMonitoringService.ensureRunning(context);
    }

    static long handledOccurrenceBoundary(long now, long handledTargetAt, long occurrenceTargetAt) {
        return Math.max(now, Math.max(handledTargetAt, occurrenceTargetAt));
    }

    public static void scheduleDetectedFire(Context context, int alarmId, long targetAt) {
        scheduleDetectedFire(context, alarmId, targetAt, "WAKE_TEMPORAL_MULTI_SENSOR_CONFIRMATION");
    }

    public static void scheduleDetectedFire(Context context, int alarmId, long targetAt, String decisionReason) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        long fireAt = System.currentTimeMillis() + 500L;
        PendingIntent operation = detectedFireIntent(context, alarmId, targetAt, decisionReason);
        try {
            if (ReminderScheduler.canScheduleExactAlarms(context)) {
                manager.setAlarmClock(new AlarmManager.AlarmClockInfo(
                        fireAt, alertIntent(context, alarmId, targetAt)), operation);
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, operation);
            }
        } catch (SecurityException error) {
            AppLog.e(context, "SmartAlarm detected fire exact permission missing", error);
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, operation);
        }
        AppLog.d(context, "SmartAlarm detected fire handed to AlarmManager id=" + alarmId
                + " target=" + targetAt + " fireAt=" + fireAt + " reason=" + decisionReason);
    }

    public static void scheduleAutoSnooze(Context context, int alarmId, long targetAt, int delaySeconds) {
        scheduleAutoSnooze(context, alarmId, targetAt, delaySeconds, false);
    }

    public static void scheduleAutoSnooze(Context context, int alarmId, long targetAt,
                                          int delaySeconds, boolean wakeCheckEscalation) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        long at = System.currentTimeMillis() + Math.max(5, delaySeconds) * 1000L;
        PendingIntent pending = autoSnoozeIntent(context, alarmId, targetAt, wakeCheckEscalation);
        try {
            if (ReminderScheduler.canScheduleExactAlarms(context))
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
        } catch (SecurityException error) {
            AppLog.e(context, "SmartAlarm auto-snooze exact permission missing", error);
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
        }
        AppLog.d(context, "SmartAlarm auto-snooze scheduled id=" + alarmId + " at=" + at);
    }

    public static void cancelAutoSnooze(Context context, int alarmId) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(autoSnoozeIntent(context, alarmId, 0L, false));
    }

    public static void cancel(Context context) {
        cancel(context, "EXPLICIT_CANCEL_OR_RECONFIGURE");
    }

    private static void cancel(Context context, String reason) {
        for (int alarmId : SmartAlarmStore.ids(context)) {
            cancel(context, alarmId, reason);
            SmartWakeMonitoringService.stop(context, alarmId);
        }
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) {
            manager.cancel(legacyWindowIntent(context)); manager.cancel(legacyDeadlineIntent(context));
        }
    }

    public static void cancel(Context context, int alarmId) {
        cancel(context, alarmId, "EXPLICIT_CANCEL_OR_RECONFIGURE");
    }

    private static void cancel(Context context, int alarmId, String reason) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) {
            manager.cancel(windowIntent(context, alarmId, 0, 0));
            manager.cancel(windowStartCheckIntent(context, alarmId, 0, 0));
            manager.cancel(deadlineIntent(context, alarmId, 0));
            manager.cancel(detectedFireIntent(context, alarmId, 0, null));
            manager.cancel(autoSnoozeIntent(context, alarmId, 0, false));
            manager.cancel(hardStopIntent(context, alarmId, 0L));
        }
        SmartAlarmBootStore.disarm(context, alarmId, 0L);
        AppLog.d(context, "FINAL_ALARM_CANCELLED id=" + alarmId + " reason=" + reason);
        ReminderMonitoringService.ensureRunning(context);
    }

    /**
     * Next normal (credential-protected) Smart Alarm delivery that is still owed.  This is used
     * only to keep the lightweight package-survival FGS alive; it never starts Smart Wake sensors.
     */
    public static long nextFutureDeliveryAt(Context context) {
        long now = System.currentTimeMillis();
        long result = Long.MAX_VALUE;
        for (int alarmId : SmartAlarmStore.ids(context)) {
            SmartAlarmStore store = new SmartAlarmStore(context, alarmId);
            if (!store.enabled()) continue;
            SmartAlarmStateStore state = new SmartAlarmStateStore(context, alarmId);
            long targetAt = state.targetAt();
            if (targetAt > now && !state.fired(targetAt) && !state.dismissed(targetAt)) {
                result = Math.min(result, targetAt);
            } else {
                long next = nextTarget(store.hour(), store.minute(), store.daysMask(), now);
                result = Math.min(result, next);
            }
        }
        return result;
    }

    /** Device-protected counterpart for the locked-boot protection service. */
    public static long nextFutureDirectBootDeliveryAt(Context context) {
        long now = System.currentTimeMillis();
        long result = Long.MAX_VALUE;
        for (SmartAlarmBootStore.Entry entry : SmartAlarmBootStore.entries(context)) {
            if (!entry.delivered && entry.targetAt > now) result = Math.min(result, entry.targetAt);
        }
        return result;
    }

    public static void cancelDeadline(Context context, int alarmId) {
        cancelDeadline(context, alarmId, 0L, "EARLY_ALERT_DELIVERY_DURABLE");
    }

    public static void cancelDeadline(Context context, int alarmId, long targetAt, String reason) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(deadlineIntent(context, alarmId, 0));
        SmartAlarmBootStore.disarm(context, alarmId, targetAt);
        AppLog.d(context, "FINAL_ALARM_CANCELLED id=" + alarmId + " occurrence_id="
                + alarmId + ":" + targetAt + " request_code=" + requestCode(alarmId, 2)
                + " reason=" + reason);
    }

    static void cancelHardStop(Context context, int alarmId) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(hardStopIntent(context, alarmId, 0L));
    }

    static long nextTarget(int hour, int minute, int daysMask, long now) {
        if (daysMask == 0) return Long.MAX_VALUE;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now); calendar.set(Calendar.HOUR_OF_DAY, hour); calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0);
        for (int offset = 0; offset <= 7; offset++) {
            if (offset > 0) calendar.add(Calendar.DAY_OF_YEAR, 1);
            boolean selected = (daysMask & (1 << calendar.get(Calendar.DAY_OF_WEEK))) != 0;
            if (selected && calendar.getTimeInMillis() > now) return calendar.getTimeInMillis();
        }
        return Long.MAX_VALUE;
    }

    private static void setWindowAlarm(Context context, AlarmManager manager, long at, PendingIntent intent) {
        try {
            if (ReminderScheduler.canScheduleExactAlarms(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent);
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent);
        } catch (SecurityException error) {
            AppLog.e(context, "SmartAlarm exact window permission missing", error);
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent);
        }
    }

    static long monitorAt(Context context, long wakeWindowStartAt) {
        return wakeWindowStartAt - SmartWakeSamplingProfile.monitorLeadTime(context);
    }

    private static void setDeadlineAlarm(Context context, AlarmManager manager, int alarmId, long at, PendingIntent intent) {
        try {
            if (ReminderScheduler.canScheduleExactAlarms(context))
                manager.setAlarmClock(new AlarmManager.AlarmClockInfo(at, alertIntent(context, alarmId, at)), intent);
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent);
        } catch (SecurityException error) {
            AppLog.e(context, "SmartAlarm exact deadline permission missing", error);
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent);
        }
        AppLog.d(context, "FINAL_ALARM_SCHEDULED id=" + alarmId + " occurrence_id="
                + alarmId + ":" + at + " deadline=" + at
                + " request_code=" + requestCode(alarmId, 2)
                + " type=ALARM_CLOCK_OR_RTC_WAKEUP");
    }

    private static void scheduleMissedDeadlineFire(Context context, int alarmId,
                                                   long originalTargetAt, long now) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        long fireAt = now + 500L;
        PendingIntent operation = deadlineIntent(context, alarmId, originalTargetAt);
        try {
            if (ReminderScheduler.canScheduleExactAlarms(context)) {
                manager.setAlarmClock(new AlarmManager.AlarmClockInfo(
                        fireAt, alertIntent(context, alarmId, originalTargetAt)), operation);
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, operation);
            }
        } catch (SecurityException error) {
            AppLog.e(context, "SmartAlarm recovery catch-up exact permission missing", error);
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, operation);
        }
        AppLog.w(context, "FINAL_ALARM_SCHEDULED id=" + alarmId + " occurrence_id="
                + alarmId + ":" + originalTargetAt + " deadline=" + originalTargetAt
                + " fireAt=" + fireAt + " request_code=" + requestCode(alarmId, 2)
                + " type=RECOVERY_CATCH_UP");
    }

    private static void scheduleHardStop(Context context, AlarmManager manager,
                                         int alarmId, long targetAt) {
        long hardStopAt = SmartWakeRuntimePolicy.hardStopAt(targetAt);
        setWindowAlarm(context, manager, hardStopAt, hardStopIntent(context, alarmId, targetAt));
    }

    private static PendingIntent windowIntent(Context context, int alarmId, long targetAt, long wakeWindowStartAt) {
        Intent intent = alarmIntent(context, SmartWakeWindowReceiver.class, alarmId, targetAt)
                .putExtra(EXTRA_WAKE_WINDOW_START_AT, wakeWindowStartAt);
        return PendingIntent.getBroadcast(context, requestCode(alarmId, 1), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent windowStartCheckIntent(Context context, int alarmId, long targetAt, long wakeWindowStartAt) {
        Intent intent = alarmIntent(context, SmartWakeWindowReceiver.class, alarmId, targetAt)
                .putExtra(EXTRA_WAKE_WINDOW_START_AT, wakeWindowStartAt)
                .putExtra(SmartWakeWindowReceiver.EXTRA_WINDOW_START_CHECK, true);
        return PendingIntent.getBroadcast(context, requestCode(alarmId, 6), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent deadlineIntent(Context context, int alarmId, long targetAt) {
        Intent intent = alarmIntent(context, SmartAlarmReceiver.class, alarmId, targetAt).putExtra("reason", "deadline");
        return PendingIntent.getBroadcast(context, requestCode(alarmId, 2), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent detectedFireIntent(Context context, int alarmId, long targetAt,
                                                    String decisionReason) {
        Intent intent = alarmIntent(context, SmartAlarmReceiver.class, alarmId, targetAt)
                .putExtra("reason", decisionReason == null
                        ? "WAKE_TEMPORAL_MULTI_SENSOR_CONFIRMATION" : decisionReason);
        return PendingIntent.getBroadcast(context, requestCode(alarmId, 5), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent alertIntent(Context context, int alarmId, long targetAt) {
        Intent source = alarmIntent(context, SmartAlarmAlertActivity.class, alarmId, targetAt);
        return PendingIntent.getActivity(context, requestCode(alarmId, 3), source, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent autoSnoozeIntent(Context context, int alarmId, long targetAt,
                                                  boolean wakeCheckEscalation) {
        Intent intent = alarmIntent(context, SmartAlarmAutoSnoozeReceiver.class, alarmId, targetAt)
                .putExtra("wake_check_escalation", wakeCheckEscalation);
        return PendingIntent.getBroadcast(context, requestCode(alarmId, 4), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent hardStopIntent(Context context, int alarmId, long targetAt) {
        Intent intent = alarmIntent(context, SmartWakeHardStopReceiver.class, alarmId, targetAt);
        return PendingIntent.getBroadcast(context, requestCode(alarmId, 7), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Intent alarmIntent(Context context, Class<?> type, int alarmId, long targetAt) {
        return new Intent(context, type).putExtra(EXTRA_ALARM_ID, alarmId).putExtra(EXTRA_TARGET_AT, targetAt);
    }
    static int requestCode(int alarmId, int kind) { return 0x53000000 | ((alarmId & 0xfffff) << 3) | kind; }
    private static PendingIntent legacyWindowIntent(Context context) { return PendingIntent.getBroadcast(context, 0x534d5701, new Intent(context, SmartWakeWindowReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE); }
    private static PendingIntent legacyDeadlineIntent(Context context) { return PendingIntent.getBroadcast(context, 0x534d5702, new Intent(context, SmartAlarmReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE); }

    public static String diagnosticSummary(Context context) {
        StringBuilder result = new StringBuilder();
        long now = System.currentTimeMillis();
        for (int alarmId : SmartAlarmStore.ids(context)) {
            SmartAlarmStore store = new SmartAlarmStore(context, alarmId);
            SmartAlarmStateStore state = new SmartAlarmStateStore(context, alarmId);
            long targetAt = state.targetAt();
            result.append("id=").append(alarmId)
                    .append(" enabled=").append(store.enabled())
                    .append(" hour=").append(store.hour()).append(':').append(store.minute())
                    .append(" daysMask=").append(store.daysMask())
                    .append(" windowMinutes=").append(store.windowMinutes())
                    .append(" target=").append(targetAt)
                    .append(" fired=").append(state.fired(targetAt))
                    .append(" dismissed=").append(state.dismissed(targetAt))
                    .append(" recoveryAction=").append(SmartAlarmRecoveryPolicy.decide(
                            store.enabled(), targetAt, now, state.fired(targetAt), state.dismissed(targetAt)))
                    .append('\n');
        }
        if (result.length() == 0) result.append("none\n");
        result.append("Device-protected final deadlines:\n")
                .append(SmartAlarmBootStore.diagnosticSummary(context));
        return result.toString();
    }
}
