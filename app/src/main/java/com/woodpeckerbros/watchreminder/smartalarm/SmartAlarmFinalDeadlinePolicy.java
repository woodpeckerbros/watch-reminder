package com.woodpeckerbros.watchreminder.smartalarm;

/**
 * Small, pure safety policy shared by the alert lifecycle and its deterministic tests.
 * It intentionally says nothing about Smart Wake detection or scoring.
 */
final class SmartAlarmFinalDeadlinePolicy {
    private SmartAlarmFinalDeadlinePolicy() {}

    static boolean mustKeepFinalDeadline(boolean awakeConfirmed, boolean finalDeadlineDelivered) {
        return !awakeConfirmed && !finalDeadlineDelivered;
    }

    static boolean earlySnoozeFitsBeforeFinalDeadline(long snoozeAt, long finalDeadlineAt) {
        return finalDeadlineAt > 0L && snoozeAt < finalDeadlineAt;
    }

    static boolean shouldRestoreFinalDeadlineAfterEarlyState(boolean awakeConfirmed,
                                                              boolean finalDeadlineDelivered,
                                                              long finalDeadlineAt, long now) {
        return mustKeepFinalDeadline(awakeConfirmed, finalDeadlineDelivered)
                && finalDeadlineAt > now;
    }

    static boolean isFinalDeadlineDelivery(String reason) {
        return "deadline".equals(reason);
    }

    static boolean mayDeliverFinalDeadline(boolean awakeConfirmed, boolean finalDeadlineDelivered) {
        return !awakeConfirmed && !finalDeadlineDelivered;
    }

    static boolean mustIgnoreEarlySnoozeAfterFinalDeadline(boolean finalDeadlineDelivered) {
        return finalDeadlineDelivered;
    }

    static boolean mayRestoreSmartWakeMonitoring(boolean smartWakeEnabled,
                                                  boolean earlyAttemptAlreadyDelivered) {
        return smartWakeEnabled && !earlyAttemptAlreadyDelivered;
    }
}
