package com.woodpeckerbros.watchreminder.smartalarm;

/** Selects the next evaluation interval from the detector's existing state, not sensor cadence. */
final class SmartWakeEvaluationCadence {
    // An evaluation can occasionally overrun its intended cadence because all work runs on the
    // main service looper. Keep a bounded yield before re-entering the same Runnable.
    static final long MIN_SAFE_DELAY_MS = 1_000L;
    enum Mode {
        NORMAL(30_000L), WATCHING(10_000L), CANDIDATE(5_000L);

        final long intervalMs;
        Mode(long intervalMs) { this.intervalMs = intervalMs; }
    }

    private SmartWakeEvaluationCadence() {}

    static Mode forDecision(SmartWakeDetector.Decision decision) {
        return forSignals(decision.candidateActive, decision.score, decision.evidenceGroups,
                decision.wakeabilityState);
    }

    static Mode forSignals(boolean candidateActive, int score, int groups,
                           SmartWakeDetector.WakeabilityState wakeabilityState) {
        if (candidateActive) return Mode.CANDIDATE;
        if (groups > 0
                && (score >= SmartWakeDetector.TREND_INTERESTING_THRESHOLD
                    || wakeabilityState == SmartWakeDetector.WakeabilityState.WAKEABILITY_RISING)) {
            return Mode.WATCHING;
        }
        return Mode.NORMAL;
    }

    static boolean shouldSchedule(int sessionCount) {
        return sessionCount > 0;
    }

    static long nextDelayMs(long targetIntervalMs, long evaluationDurationMs) {
        return Math.max(MIN_SAFE_DELAY_MS, targetIntervalMs - Math.max(0L, evaluationDurationMs));
    }
}
