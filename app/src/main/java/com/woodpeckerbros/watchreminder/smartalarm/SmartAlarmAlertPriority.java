package com.woodpeckerbros.watchreminder.smartalarm;

/** Pure priority rule shared by the persistent owner and deterministic unit tests. */
public final class SmartAlarmAlertPriority {
    private SmartAlarmAlertPriority() {
    }

    public static boolean normalReminderMustDefer(boolean smartAlarmOwnsAttention,
                                                   boolean finalDeadlineImminent) {
        return smartAlarmOwnsAttention || finalDeadlineImminent;
    }
}
