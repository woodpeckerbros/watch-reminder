package com.woodpeckerbros.watchreminder.smartalarm;

/** Pure policy for restoring the final Smart Alarm safety deadline. */
final class SmartAlarmRecoveryPolicy {
    static final long MISSED_DEADLINE_CATCH_UP_MS = 30 * 60_000L;

    enum Action {
        RESTORE_FUTURE_DEADLINE,
        DELIVER_RECENT_MISSED_DEADLINE,
        PRESERVE_ACTIVE_ALERT,
        RESCHEDULE_NEXT_OCCURRENCE
    }

    private SmartAlarmRecoveryPolicy() {}

    static Action decide(boolean enabled, long targetAt, long now,
                         boolean fired, boolean dismissed) {
        if (enabled && targetAt > now && !fired && !dismissed) {
            return Action.RESTORE_FUTURE_DEADLINE;
        }
        if (enabled && targetAt > 0L && targetAt <= now && !fired && !dismissed
                && now - targetAt <= MISSED_DEADLINE_CATCH_UP_MS) {
            return Action.DELIVER_RECENT_MISSED_DEADLINE;
        }
        if (fired && !dismissed) {
            return Action.PRESERVE_ACTIVE_ALERT;
        }
        return Action.RESCHEDULE_NEXT_OCCURRENCE;
    }

    static Action decideLockedBoot(long targetAt, long now, boolean delivered) {
        if (!delivered && targetAt > now) {
            return Action.RESTORE_FUTURE_DEADLINE;
        }
        if (!delivered && targetAt > 0L && targetAt <= now
                && now - targetAt <= MISSED_DEADLINE_CATCH_UP_MS) {
            return Action.DELIVER_RECENT_MISSED_DEADLINE;
        }
        return Action.PRESERVE_ACTIVE_ALERT;
    }

    static boolean shouldFinalizeDirectBootDelivery(boolean shadowMatches, boolean delivered) {
        return shadowMatches && delivered;
    }
}
