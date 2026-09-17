package com.woodpeckerbros.watchreminder.smartalarm;

/** Pure policy for reconstructing the bounded Smart Wake lead-in after locked boot. */
final class SmartWakeDirectBootPolicy {
    enum Action {
        RESTORE_MONITORING_START,
        START_MONITORING_CATCH_UP,
        NO_MONITORING
    }

    private SmartWakeDirectBootPolicy() {}

    static Action decide(boolean smartWakeEnabled, boolean delivered, long monitoringStartAt,
                         long earliestWakeAt, long finalDeadlineAt, long now) {
        if (!smartWakeEnabled || delivered || monitoringStartAt <= 0L || earliestWakeAt <= 0L
                || finalDeadlineAt <= now || earliestWakeAt > finalDeadlineAt) {
            return Action.NO_MONITORING;
        }
        if (now < monitoringStartAt) return Action.RESTORE_MONITORING_START;
        return Action.START_MONITORING_CATCH_UP;
    }
}
