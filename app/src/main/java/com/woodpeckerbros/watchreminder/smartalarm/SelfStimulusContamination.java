package com.woodpeckerbros.watchreminder.smartalarm;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Monotonic-time record of physical/full-screen output produced by Zmanio itself.
 * Sensor collection continues; callers use the per-modality result to tag, rather
 * than delete, samples which cannot be interpreted as independent wake evidence.
 */
final class SelfStimulusContamination {
    static final long STEP_RECOVERY_MS = 60_000L;
    static final long MOVEMENT_RECOVERY_MS = SmartWakeDetector.RECENT_WINDOW_MS;
    static final long CARDIO_RECOVERY_MS = SmartWakeDetector.RECENT_WINDOW_MS;

    private final Deque<Stimulus> stimuli = new ArrayDeque<>();
    private long nextId;

    synchronized long begin(String type, String sourceOccurrenceId, long elapsedAt, long epochAt) {
        long id = ++nextId;
        stimuli.addLast(new Stimulus(id, type, sourceOccurrenceId, elapsedAt, epochAt));
        return id;
    }

    synchronized void end(long id, long elapsedAt, long epochAt) {
        for (Stimulus stimulus : stimuli) {
            if (stimulus.id == id && stimulus.endedElapsedAt == Long.MIN_VALUE) {
                stimulus.endedElapsedAt = Math.max(stimulus.startedElapsedAt, elapsedAt);
                stimulus.endedEpochAt = Math.max(stimulus.startedEpochAt, epochAt);
                return;
            }
        }
    }

    synchronized Taint taintAt(long sampleElapsedAt) {
        prune(sampleElapsedAt);
        Stimulus latest = null;
        boolean step = false, movement = false, cardio = false;
        for (Stimulus stimulus : stimuli) {
            long end = stimulus.endedElapsedAt == Long.MIN_VALUE ? sampleElapsedAt : stimulus.endedElapsedAt;
            if (sampleElapsedAt < stimulus.startedElapsedAt) continue;
            if (sampleElapsedAt <= end + STEP_RECOVERY_MS) step = true;
            if (sampleElapsedAt <= end + MOVEMENT_RECOVERY_MS) movement = true;
            if (sampleElapsedAt <= end + CARDIO_RECOVERY_MS) cardio = true;
            if ((step || movement || cardio) && (latest == null || stimulus.startedElapsedAt > latest.startedElapsedAt)) {
                latest = stimulus;
            }
        }
        return new Taint(step, movement, cardio, latest);
    }

    synchronized Snapshot snapshot(long elapsedNow) {
        prune(elapsedNow);
        Stimulus latest = null;
        boolean active = false;
        for (Stimulus stimulus : stimuli) {
            long end = stimulus.endedElapsedAt == Long.MIN_VALUE ? elapsedNow : stimulus.endedElapsedAt;
            if (elapsedNow >= stimulus.startedElapsedAt
                    && elapsedNow <= end + Math.max(STEP_RECOVERY_MS,
                    Math.max(MOVEMENT_RECOVERY_MS, CARDIO_RECOVERY_MS))) {
                active = true;
                if (latest == null || stimulus.startedElapsedAt > latest.startedElapsedAt) latest = stimulus;
            }
        }
        return new Snapshot(active, latest);
    }

    private void prune(long elapsedNow) {
        long oldestAllowed = elapsedNow - Math.max(STEP_RECOVERY_MS,
                Math.max(MOVEMENT_RECOVERY_MS, CARDIO_RECOVERY_MS));
        while (!stimuli.isEmpty()) {
            Stimulus first = stimuli.peekFirst();
            if (first.endedElapsedAt == Long.MIN_VALUE || first.endedElapsedAt >= oldestAllowed) return;
            stimuli.removeFirst();
        }
    }

    static final class Taint {
        final boolean step, movement, cardio;
        final Stimulus source;
        Taint(boolean step, boolean movement, boolean cardio, Stimulus source) {
            this.step = step; this.movement = movement; this.cardio = cardio; this.source = source;
        }
    }

    static final class Snapshot {
        final boolean active;
        final Stimulus source;
        Snapshot(boolean active, Stimulus source) { this.active = active; this.source = source; }
    }

    static final class Stimulus {
        final long id, startedElapsedAt, startedEpochAt;
        final String type, sourceOccurrenceId;
        long endedElapsedAt = Long.MIN_VALUE;
        long endedEpochAt = Long.MIN_VALUE;
        Stimulus(long id, String type, String sourceOccurrenceId, long startedElapsedAt, long startedEpochAt) {
            this.id = id; this.type = type == null ? "UNKNOWN" : type;
            this.sourceOccurrenceId = sourceOccurrenceId == null ? "" : sourceOccurrenceId;
            this.startedElapsedAt = startedElapsedAt; this.startedEpochAt = startedEpochAt;
        }
    }
}
