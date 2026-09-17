package com.woodpeckerbros.watchreminder.reminder;

/**
 * Pure, small policy for the foreground process anchor.  It deliberately knows nothing about
 * sensor monitoring: it answers only whether the package still has a future delivery obligation
 * that an OEM must not be allowed to force-stop.
 */
final class ReminderMonitoringPolicy {
    enum Reason { NONE, NORMAL_REMINDER, SMART_ALARM, SMART_WAKE, WAKE_CHECK, MULTIPLE }

    static final class Requirement {
        final boolean required;
        final Reason reason;
        final long nextDeliveryAt;
        final boolean futureNormalReminders;
        final boolean futureSmartAlarm;
        final boolean activeSmartWake;
        final boolean pendingWakeCheck;

        Requirement(boolean required, Reason reason, long nextDeliveryAt,
                    boolean futureNormalReminders, boolean futureSmartAlarm,
                    boolean activeSmartWake, boolean pendingWakeCheck) {
            this.required = required;
            this.reason = reason;
            this.nextDeliveryAt = nextDeliveryAt;
            this.futureNormalReminders = futureNormalReminders;
            this.futureSmartAlarm = futureSmartAlarm;
            this.activeSmartWake = activeSmartWake;
            this.pendingWakeCheck = pendingWakeCheck;
        }
    }

    private ReminderMonitoringPolicy() { }

    static Requirement decide(long nextNormalReminderAt, long nextSmartAlarmAt,
                              boolean activeSmartWake, boolean pendingWakeCheck) {
        boolean normal = nextNormalReminderAt > 0L && nextNormalReminderAt != Long.MAX_VALUE;
        boolean smart = nextSmartAlarmAt > 0L && nextSmartAlarmAt != Long.MAX_VALUE;
        int count = (normal ? 1 : 0) + (smart ? 1 : 0)
                + (activeSmartWake ? 1 : 0) + (pendingWakeCheck ? 1 : 0);
        if (count == 0) return new Requirement(false, Reason.NONE, 0L,
                false, false, false, false);
        Reason reason;
        if (count > 1) reason = Reason.MULTIPLE;
        else if (normal) reason = Reason.NORMAL_REMINDER;
        else if (smart) reason = Reason.SMART_ALARM;
        else if (activeSmartWake) reason = Reason.SMART_WAKE;
        else reason = Reason.WAKE_CHECK;
        long next = Math.min(normal ? nextNormalReminderAt : Long.MAX_VALUE,
                smart ? nextSmartAlarmAt : Long.MAX_VALUE);
        return new Requirement(true, reason, next == Long.MAX_VALUE ? 0L : next,
                normal, smart, activeSmartWake, pendingWakeCheck);
    }
}
