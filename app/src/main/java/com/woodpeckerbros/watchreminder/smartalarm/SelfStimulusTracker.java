package com.woodpeckerbros.watchreminder.smartalarm;

import android.os.SystemClock;

/** Process-shared bridge between Zmanio alert output and Smart Wake sensor intake. */
public final class SelfStimulusTracker {
    private static final SelfStimulusContamination CONTAMINATION = new SelfStimulusContamination();

    private SelfStimulusTracker() {}

    public static long begin(String type, String sourceOccurrenceId) {
        return CONTAMINATION.begin(type, sourceOccurrenceId, SystemClock.elapsedRealtime(), System.currentTimeMillis());
    }

    public static void end(long id) {
        if (id != 0L) CONTAMINATION.end(id, SystemClock.elapsedRealtime(), System.currentTimeMillis());
    }

    static SelfStimulusContamination.Taint taintAt(long elapsedAt) {
        return CONTAMINATION.taintAt(elapsedAt);
    }

    static SelfStimulusContamination.Snapshot snapshot() {
        return CONTAMINATION.snapshot(SystemClock.elapsedRealtime());
    }
}
