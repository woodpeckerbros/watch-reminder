package com.woodpeckerbros.watchreminder.smartalarm;

/** Pure lifecycle bounds used by the monitoring service and its deterministic tests. */
final class SmartWakeRuntimePolicy {
    static final long HARD_STOP_GRACE_MS = 60_000L;

    private SmartWakeRuntimePolicy() {}

    static long hardStopAt(long deadline) {
        return deadline + HARD_STOP_GRACE_MS;
    }

    static boolean isPastHardStop(long now, long deadline) {
        return now >= hardStopAt(deadline);
    }

    static boolean isSameSession(int existingAlarmId, long existingTargetAt,
                                 int requestedAlarmId, long requestedTargetAt) {
        return existingAlarmId == requestedAlarmId && existingTargetAt == requestedTargetAt;
    }

    static boolean shouldRegisterMotion(boolean registered, boolean currentActiveWindow,
                                        boolean requestedActiveWindow) {
        return !registered || currentActiveWindow != requestedActiveWindow;
    }

    static boolean shouldReleaseResources(int remainingSessionCount) {
        return remainingSessionCount == 0;
    }
}
