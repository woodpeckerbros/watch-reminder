package com.woodpeckerbros.watchreminder.smartalarm;

/** Timing only; never contributes to Smart Wake evidence or decisions. */
final class SmartWakeEvaluationTiming {
    final long dueAtElapsedMs;
    final long actualStartAtElapsedMs;
    final long lateByMs;
    final long uptimeLateByMs;
    final long suspendGapMs;

    private SmartWakeEvaluationTiming(long dueAtElapsedMs, long actualStartAtElapsedMs,
                                      long lateByMs, long uptimeLateByMs, long suspendGapMs) {
        this.dueAtElapsedMs = dueAtElapsedMs;
        this.actualStartAtElapsedMs = actualStartAtElapsedMs;
        this.lateByMs = lateByMs;
        this.uptimeLateByMs = uptimeLateByMs;
        this.suspendGapMs = suspendGapMs;
    }

    static SmartWakeEvaluationTiming of(long dueAtElapsedMs, long dueAtUptimeMs,
                                        long actualStartAtElapsedMs, long actualStartAtUptimeMs) {
        long elapsedLateness = actualStartAtElapsedMs - dueAtElapsedMs;
        long uptimeLateness = actualStartAtUptimeMs - dueAtUptimeMs;
        return new SmartWakeEvaluationTiming(dueAtElapsedMs, actualStartAtElapsedMs,
                Math.max(0L, elapsedLateness), Math.max(0L, uptimeLateness),
                Math.max(0L, elapsedLateness - uptimeLateness));
    }

    static final class LatenessStats {
        long count, totalMs, maxMs;
        void add(long latenessMs) {
            count++;
            totalMs += latenessMs;
            maxMs = Math.max(maxMs, latenessMs);
        }
        long average() { return count == 0 ? -1L : totalMs / count; }
    }
}
