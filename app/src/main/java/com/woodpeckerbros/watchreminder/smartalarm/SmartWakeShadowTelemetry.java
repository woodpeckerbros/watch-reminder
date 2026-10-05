package com.woodpeckerbros.watchreminder.smartalarm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/** Research-only feature extraction. Nothing here can create or confirm a wake decision. */
final class SmartWakeShadowTelemetry {
    static final long BUCKET_MS = 15_000L;
    static final long SHORT_WINDOW_MS = 60_000L;
    static final long LONG_WINDOW_MS = 90_000L;

    enum State {
        SHADOW_QUIET, SHADOW_SINGLE_BURST, SHADOW_REPEATED_CARDIO_ONLY,
        SHADOW_MULTI_EPISODE_ACTIVITY, SHADOW_CROSS_MODAL_RISING
    }

    static final class MovementBucket {
        final long startedAt;
        final boolean active;
        MovementBucket(long startedAt, boolean active) {
            this.startedAt = startedAt;
            this.active = active;
        }
    }

    private static final class Frame {
        final long evaluatedAt, hrEventAt;
        final int score, groups;
        final boolean hrNew, hrElevated, movementEvidence, newMovementEpisode;
        final double hrDelta;
        Frame(long evaluatedAt, long hrEventAt, int score, int groups, boolean hrNew,
              boolean hrElevated, boolean movementEvidence, boolean newMovementEpisode,
              double hrDelta) {
            this.evaluatedAt = evaluatedAt;
            this.hrEventAt = hrEventAt;
            this.score = score;
            this.groups = groups;
            this.hrNew = hrNew;
            this.hrElevated = hrElevated;
            this.movementEvidence = movementEvidence;
            this.newMovementEpisode = newMovementEpisode;
            this.hrDelta = hrDelta;
        }
    }

    static final class Window {
        final long durationMs;
        final int movementEpisodes, movementBuckets, movementRenewals;
        final int hrFreshUpdates, hrElevatedUpdates, crossModalRenewals;
        final int peakScore, twoGroupEvaluations, movementReuseEvaluations;
        final int movementBearingTwoGroupEvaluations, hrTrendConsistencyPercent;
        final long latestEpisodeStartAt, latestHrEventAt;
        final double averageScore, hrDeltaStart, hrDeltaEnd;
        final String hrTrendDirection;
        final State state;

        Window(long durationMs, int movementEpisodes, int movementBuckets, int hrFreshUpdates,
               int hrElevatedUpdates, int crossModalRenewals, int peakScore, double averageScore,
               int twoGroupEvaluations, int movementReuseEvaluations,
               int movementBearingTwoGroupEvaluations, double hrDeltaStart, double hrDeltaEnd,
               String hrTrendDirection, int hrTrendConsistencyPercent,
               long latestEpisodeStartAt, long latestHrEventAt, State state) {
            this.durationMs = durationMs;
            this.movementEpisodes = movementEpisodes;
            this.movementBuckets = movementBuckets;
            this.movementRenewals = Math.max(0, movementEpisodes - 1);
            this.hrFreshUpdates = hrFreshUpdates;
            this.hrElevatedUpdates = hrElevatedUpdates;
            this.crossModalRenewals = crossModalRenewals;
            this.peakScore = peakScore;
            this.averageScore = averageScore;
            this.twoGroupEvaluations = twoGroupEvaluations;
            this.movementReuseEvaluations = movementReuseEvaluations;
            this.movementBearingTwoGroupEvaluations = movementBearingTwoGroupEvaluations;
            this.hrDeltaStart = hrDeltaStart;
            this.hrDeltaEnd = hrDeltaEnd;
            this.hrTrendDirection = hrTrendDirection;
            this.hrTrendConsistencyPercent = hrTrendConsistencyPercent;
            this.latestEpisodeStartAt = latestEpisodeStartAt;
            this.latestHrEventAt = latestHrEventAt;
            this.state = state;
        }

        double movementReuseRatio() {
            return movementBearingTwoGroupEvaluations == 0 ? Double.NaN
                    : (double) movementReuseEvaluations / movementBearingTwoGroupEvaluations;
        }

        String compact() {
            return durationMs / 1_000L + "S=" + movementEpisodes + "/" + movementBuckets
                    + "/" + movementRenewals + "/" + hrFreshUpdates + "/"
                    + crossModalRenewals + "/" + peakScore + "/" + twoGroupEvaluations
                    + "/" + movementReuseEvaluations + "/"
                    + movementBearingTwoGroupEvaluations + "/" + state;
        }

        String telemetry() {
            return "SHADOW_WINDOW_MS=" + durationMs
                    + " DISTINCT_MOVEMENT_EPISODE_COUNT=" + movementEpisodes
                    + " DISTINCT_MOVEMENT_BUCKET_COUNT=" + movementBuckets
                    + " MOVEMENT_RENEWAL_COUNT=" + movementRenewals
                    + " HR_FRESH_UPDATE_COUNT=" + hrFreshUpdates
                    + " HR_ELEVATED_UPDATE_COUNT=" + hrElevatedUpdates
                    + " HR_TREND_DIRECTION=" + hrTrendDirection
                    + " HR_TREND_CONSISTENCY_PCT=" + hrTrendConsistencyPercent
                    + " HR_DELTA_START=" + format(hrDeltaStart)
                    + " HR_DELTA_END=" + format(hrDeltaEnd)
                    + " LATEST_HR_EVENT_AT=" + latestHrEventAt
                    + " LATEST_MOVEMENT_EPISODE_START_AT=" + latestEpisodeStartAt
                    + " CROSS_MODAL_RENEWAL_COUNT=" + crossModalRenewals
                    + " MOVEMENT_REUSE_COUNT=" + movementReuseEvaluations
                    + " MOVEMENT_BEARING_TWO_GROUP_COUNT=" + movementBearingTwoGroupEvaluations
                    + " MOVEMENT_REUSE_RATIO=" + format(movementReuseRatio())
                    + " PEAK_WAKE_SCORE=" + peakScore
                    + " AVG_WAKE_SCORE=" + format(averageScore)
                    + " TWO_GROUP_EVALUATION_COUNT=" + twoGroupEvaluations
                    + " SHADOW_STATE=" + state;
        }
    }

    private final Deque<Frame> frames = new ArrayDeque<>();
    private long lastEvaluationAt = Long.MIN_VALUE;
    private long lastSeenActiveBucketAt = Long.MIN_VALUE;
    private long lastSeenHrEventAt = Long.MIN_VALUE;
    private int maxEpisodes60, maxEpisodes90, maxHrFreshUpdates;
    private int maxCrossModalRenewals, maxMovementReuseEvaluations;
    private double maxMovementReuseRatio = Double.NaN;
    private State maxState = State.SHADOW_QUIET;
    private long strongestTransitionAt = -1L;
    private long minCrossModalTimeGapMs = Long.MAX_VALUE;
    private int crossModalWithin5s, crossModalWithin15s, crossModalWithin30s;
    private int groups2OnlyOneSourceRecent15s, groups2OnlyOneSourceRecent30s;
    private int maxCrossModalConvergenceLevel;
    private Window last60, last90;

    long requiredMovementStartAt(long now) {
        long windowStart = now - LONG_WINDOW_MS - BUCKET_MS;
        long bridgeStart = lastEvaluationAt == Long.MIN_VALUE ? windowStart
                : Math.min(windowStart, lastEvaluationAt - BUCKET_MS);
        // The detector has already pruned older sensor events; do not scan empty hours after a
        // delayed callback or service pause.
        return Math.max(now - SmartWakeDetector.BASELINE_WINDOW_MS, bridgeStart);
    }

    void record(long now, SmartWakeDetector.Decision decision, List<MovementBucket> buckets) {
        recordRecency(decision);
        long hrEventAt = decision.hrNew && decision.heartRateSampleAgeMs >= 0L
                ? now - decision.heartRateSampleAgeMs : Long.MIN_VALUE;
        recordFeatures(now, decision.score, decision.evidenceGroups, decision.hrNew,
                hrEventAt, decision.baselineReady && decision.hrAboveBaseline >= 3.0,
                decision.hrAboveBaseline,
                decision.movementEvidence, buckets);
    }

    /** Package-private deterministic replay entry point; event timestamps remain authoritative. */
    void recordFeatures(long now, int score, int groups, boolean hrNew, long hrEventAt,
                        boolean hrElevated, double hrDelta,
                        boolean movementEvidence, List<MovementBucket> buckets) {
        boolean distinctHr = hrNew && hrEventAt > lastSeenHrEventAt && hrEventAt <= now;
        if (distinctHr) lastSeenHrEventAt = hrEventAt;
        boolean newEpisode = false;
        for (MovementBucket bucket : buckets) {
            if (!bucket.active || bucket.startedAt <= lastSeenActiveBucketAt) continue;
            if (lastSeenActiveBucketAt == Long.MIN_VALUE
                    || bucket.startedAt > lastSeenActiveBucketAt + BUCKET_MS) newEpisode = true;
            lastSeenActiveBucketAt = bucket.startedAt;
        }
        frames.addLast(new Frame(now, distinctHr ? hrEventAt : Long.MIN_VALUE, score, groups,
                distinctHr, distinctHr && hrElevated, movementEvidence, newEpisode,
                hrDelta));
        while (!frames.isEmpty() && frames.peekFirst().evaluatedAt < now - LONG_WINDOW_MS) {
            frames.removeFirst();
        }
        last60 = calculate(now, SHORT_WINDOW_MS, buckets);
        last90 = calculate(now, LONG_WINDOW_MS, buckets);
        maxEpisodes60 = Math.max(maxEpisodes60, last60.movementEpisodes);
        maxEpisodes90 = Math.max(maxEpisodes90, last90.movementEpisodes);
        maxHrFreshUpdates = Math.max(maxHrFreshUpdates,
                Math.max(last60.hrFreshUpdates, last90.hrFreshUpdates));
        maxCrossModalRenewals = Math.max(maxCrossModalRenewals,
                Math.max(last60.crossModalRenewals, last90.crossModalRenewals));
        maxMovementReuseEvaluations = Math.max(maxMovementReuseEvaluations,
                Math.max(last60.movementReuseEvaluations, last90.movementReuseEvaluations));
        for (Window window : new Window[] {last60, last90}) {
            if (window.movementBearingTwoGroupEvaluations < 2) continue;
            maxMovementReuseRatio = Double.isNaN(maxMovementReuseRatio)
                    ? window.movementReuseRatio()
                    : Math.max(maxMovementReuseRatio, window.movementReuseRatio());
        }
        State highest = last60.state.ordinal() > last90.state.ordinal() ? last60.state : last90.state;
        if (highest.ordinal() > maxState.ordinal()) {
            maxState = highest;
            strongestTransitionAt = now;
        }
        lastEvaluationAt = now;
    }

    Window window60() { return last60; }
    Window window90() { return last90; }

    String compact() {
        return " SHADOW_FIELDS=episodes/buckets/renewals/hr_new/cross_modal/peak/2group/"
                + "reused/eligible/state"
                + " SHADOW_" + last60.compact() + " SHADOW_" + last90.compact();
    }

    String telemetry() {
        return "SMART_WAKE_SHADOW " + last60.telemetry() + "\nSMART_WAKE_SHADOW "
                + last90.telemetry();
    }

    String sessionSummary() {
        return "MAX_DISTINCT_MOVEMENT_EPISODES_60S=" + maxEpisodes60
                + " MAX_DISTINCT_MOVEMENT_EPISODES_90S=" + maxEpisodes90
                + " MAX_HR_FRESH_UPDATES_ANY_WINDOW=" + maxHrFreshUpdates
                + " MAX_CROSS_MODAL_RENEWALS_ANY_WINDOW=" + maxCrossModalRenewals
                + " MAX_MOVEMENT_REUSED_TWO_GROUP_EVALUATIONS_ANY_WINDOW="
                + maxMovementReuseEvaluations
                + " MAX_MOVEMENT_REUSE_RATIO_ANY_WINDOW=" + format(maxMovementReuseRatio)
                + " MAX_SHADOW_TRANSITION_LEVEL=" + maxState
                + " STRONGEST_SHADOW_TRANSITION_AT=" + strongestTransitionAt
                + " MIN_CROSS_MODAL_TIME_GAP_MS="
                + (minCrossModalTimeGapMs == Long.MAX_VALUE ? -1L : minCrossModalTimeGapMs)
                + " MAX_CROSS_MODAL_CONVERGENCE_LEVEL="
                + convergenceLabel(maxCrossModalConvergenceLevel)
                + " COUNT_CROSS_MODAL_WITHIN_5S=" + crossModalWithin5s
                + " COUNT_CROSS_MODAL_WITHIN_15S=" + crossModalWithin15s
                + " COUNT_CROSS_MODAL_WITHIN_30S=" + crossModalWithin30s
                + " COUNT_GROUPS_2_BUT_ONLY_ONE_SOURCE_RECENT_15S="
                + groups2OnlyOneSourceRecent15s
                + " COUNT_GROUPS_2_BUT_ONLY_ONE_SOURCE_RECENT_30S="
                + groups2OnlyOneSourceRecent30s;
    }

    /** Aggregates only descriptive timing fields already calculated by the detector. */
    private void recordRecency(SmartWakeDetector.Decision decision) {
        if (decision.crossModalTimeGapMs >= 0L) {
            minCrossModalTimeGapMs = Math.min(minCrossModalTimeGapMs, decision.crossModalTimeGapMs);
        }
        int convergence = convergenceLevel(decision.crossModalConvergenceLevel);
        maxCrossModalConvergenceLevel = Math.max(maxCrossModalConvergenceLevel, convergence);
        if (convergence >= 3) crossModalWithin5s++;
        if (convergence >= 2) crossModalWithin15s++;
        if (convergence >= 1) crossModalWithin30s++;
        // These counters deliberately apply only to the cardio-plus-movement interpretation of
        // a two-group result. A Health Services group is not silently treated as movement.
        if (decision.evidenceGroups == 2 && decision.movementEvidence
                && decision.crossModalTimeGapMs >= 0L) {
            if (decision.recent15sGroupCount == 1) groups2OnlyOneSourceRecent15s++;
            if (decision.recent30sGroupCount == 1) groups2OnlyOneSourceRecent30s++;
        }
    }

    private static int convergenceLevel(String value) {
        if ("5S".equals(value)) return 3;
        if ("15S".equals(value)) return 2;
        return "30S".equals(value) ? 1 : 0;
    }

    private static String convergenceLabel(int value) {
        return value == 3 ? "5S" : value == 2 ? "15S" : value == 1 ? "30S" : "NONE";
    }

    private Window calculate(long now, long durationMs, List<MovementBucket> buckets) {
        long start = now - durationMs;
        int activeBuckets = 0, episodes = 0;
        boolean priorActive = false, firstVisible = true;
        List<Long> episodeStarts = new ArrayList<>();
        for (MovementBucket bucket : buckets) {
            if (bucket.startedAt + BUCKET_MS <= start) {
                priorActive = bucket.active;
                continue;
            }
            if (bucket.active) {
                activeBuckets++;
                if (firstVisible || !priorActive) episodes++;
                if (!priorActive && bucket.startedAt >= start) episodeStarts.add(bucket.startedAt);
            }
            firstVisible = false;
            priorActive = bucket.active;
        }

        int hrFresh = 0, hrElevated = 0, twoGroup = 0, reused = 0, eligible = 0;
        int peak = 0, totalScore = 0, evaluations = 0;
        double firstDelta = Double.NaN, lastDelta = Double.NaN, previousDelta = Double.NaN;
        int rises = 0, falls = 0, flats = 0;
        List<Long> hrTimes = new ArrayList<>();
        for (Frame frame : frames) {
            if (frame.evaluatedAt < start) continue;
            evaluations++;
            peak = Math.max(peak, frame.score);
            totalScore += frame.score;
            if (frame.groups >= 2) twoGroup++;
            if (frame.groups >= 2 && frame.movementEvidence
                    && frame.score >= SmartWakeDetector.TREND_INTERESTING_THRESHOLD) {
                eligible++;
                if (!frame.newMovementEpisode) reused++;
            }
            // A callback received now does not make an old sensor event fresh in this window.
            if (!frame.hrNew || frame.hrEventAt < start) continue;
            hrFresh++;
            if (frame.hrElevated) hrElevated++;
            hrTimes.add(frame.hrEventAt);
            if (Double.isNaN(firstDelta)) firstDelta = frame.hrDelta;
            if (!Double.isNaN(previousDelta)) {
                if (frame.hrDelta > previousDelta) rises++;
                else if (frame.hrDelta < previousDelta) falls++;
                else flats++;
            }
            previousDelta = frame.hrDelta;
            lastDelta = frame.hrDelta;
        }
        String direction = hrFresh < 2 ? "NO_DATA" : lastDelta > firstDelta ? "RISING"
                : lastDelta < firstDelta ? "FALLING" : "FLAT";
        int comparisons = rises + falls + flats;
        int consistent = "RISING".equals(direction) ? rises : "FALLING".equals(direction)
                ? falls : flats;
        int consistencyPercent = comparisons == 0 ? -1 : consistent * 100 / comparisons;
        // A cross-modal renewal is a NEW movement episode preceded by a fresh HR event
        // since the preceding episode. More HR updates on the SAME episode add nothing.
        int crossModal = 0;
        long previousEpisodeAt = start - 1L;
        for (long episodeAt : episodeStarts) {
            for (long hrAt : hrTimes) {
                if (hrAt > previousEpisodeAt && hrAt <= episodeAt) {
                    crossModal++;
                    break;
                }
            }
            previousEpisodeAt = episodeAt;
        }
        State state = episodes >= 2 && crossModal >= 2 && hrFresh >= 2
                && !Double.isNaN(firstDelta) && lastDelta > firstDelta
                ? State.SHADOW_CROSS_MODAL_RISING
                : episodes >= 2 ? State.SHADOW_MULTI_EPISODE_ACTIVITY
                : episodes == 1 && hrFresh >= 2 && eligible >= 2
                    ? State.SHADOW_REPEATED_CARDIO_ONLY
                : episodes == 1 ? State.SHADOW_SINGLE_BURST : State.SHADOW_QUIET;
        return new Window(durationMs, episodes, activeBuckets, hrFresh, hrElevated, crossModal,
                peak, evaluations == 0 ? 0.0 : (double) totalScore / evaluations,
                twoGroup, reused, eligible, firstDelta, lastDelta, direction,
                consistencyPercent,
                episodeStarts.isEmpty() ? -1L : episodeStarts.get(episodeStarts.size() - 1),
                hrTimes.isEmpty() ? -1L : hrTimes.get(hrTimes.size() - 1), state);
    }

    private static String format(double value) {
        return Double.isNaN(value) ? "NO_DATA" : String.format(Locale.US, "%.2f", value);
    }
}
