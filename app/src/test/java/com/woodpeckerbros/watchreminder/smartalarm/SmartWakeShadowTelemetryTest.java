package com.woodpeckerbros.watchreminder.smartalarm;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SmartWakeShadowTelemetryTest {
    private static final long START = 600_000L;

    @Test public void hundredsOfActiveSamplesAcrossConsecutiveBucketsAreOneEpisode() {
        SmartWakeDetector detector = new SmartWakeDetector(0L);
        for (int i = 0; i < 350; i++) {
            detector.addAccelerometerMotion(.60, START + i * 100L);
        }
        SmartWakeShadowTelemetry shadow = new SmartWakeShadowTelemetry();
        long now = START + 40_000L;
        shadow.recordFeatures(now, 14, 1, false, Long.MIN_VALUE, false, 0, true,
                detector.shadowMovementBuckets(shadow.requiredMovementStartAt(now), now));
        assertEquals(3, shadow.window90().movementBuckets);
        assertEquals(1, shadow.window90().movementEpisodes);
        assertEquals(0, shadow.window90().movementRenewals);
    }

    @Test public void sameMovementObservedAtFiveSecondCadenceNeverRenews() {
        SmartWakeShadowTelemetry shadow = new SmartWakeShadowTelemetry();
        for (int i = 0; i < 4; i++) {
            long now = START + 20_000L + i * 5_000L;
            shadow.recordFeatures(now, 18, 2, false, Long.MIN_VALUE, false, 0, true,
                    buckets(now, START));
        }
        assertEquals(1, shadow.window90().movementEpisodes);
        assertEquals(0, shadow.window90().movementRenewals);
        assertEquals(3, shadow.window90().movementReuseEvaluations);
        assertEquals(.75, shadow.window90().movementReuseRatio(), .001);
    }

    @Test public void completeQuietBucketSeparatesTwoMovementEpisodes() {
        SmartWakeShadowTelemetry shadow = new SmartWakeShadowTelemetry();
        long now = START + 50_000L;
        shadow.recordFeatures(now, 14, 1, false, Long.MIN_VALUE, false, 0, true,
                buckets(now, START, START + 30_000L));
        assertEquals(2, shadow.window60().movementBuckets);
        assertEquals(2, shadow.window60().movementEpisodes);
        assertEquals(1, shadow.window60().movementRenewals);
    }

    @Test public void repeatedFreshHeartRateWithSameMovementOnlyRenewsCardio() {
        SmartWakeShadowTelemetry shadow = new SmartWakeShadowTelemetry();
        for (int i = 0; i < 6; i++) {
            long now = START + 20_000L + i * 5_000L;
            shadow.recordFeatures(now, 58, 2, true, now - 1_000L, true, 6.0, true,
                    buckets(now, START));
        }
        assertEquals(6, shadow.window60().hrFreshUpdates);
        assertEquals(6, shadow.window60().hrElevatedUpdates);
        assertEquals(1, shadow.window60().movementEpisodes);
        assertEquals(0, shadow.window60().movementRenewals);
        assertEquals(0, shadow.window60().crossModalRenewals);
        assertEquals(SmartWakeShadowTelemetry.State.SHADOW_REPEATED_CARDIO_ONLY,
                shadow.window60().state);
    }

    @Test public void sameHeartRateEventTimestampCannotManufactureFreshUpdates() {
        SmartWakeShadowTelemetry shadow = new SmartWakeShadowTelemetry();
        for (int i = 0; i < 4; i++) {
            long now = START + 20_000L + i * 5_000L;
            shadow.recordFeatures(now, 24, 2, true, START + 19_000L,
                    true, 4.0, true, buckets(now, START));
        }
        assertEquals(1, shadow.window60().hrFreshUpdates);
        assertEquals(1, shadow.window60().movementEpisodes);
        assertEquals(0, shadow.window60().movementRenewals);
    }

    @Test public void newHrBeforeEachSeparateMovementCreatesCrossModalRenewals() {
        SmartWakeShadowTelemetry shadow = new SmartWakeShadowTelemetry();
        shadow.recordFeatures(START + 10_000L, 15, 1, true, START + 9_000L,
                true, 3.0, false, buckets(START + 10_000L));
        shadow.recordFeatures(START + 25_000L, 20, 2, false, Long.MIN_VALUE,
                false, 3.0, true, buckets(START + 25_000L, START + 15_000L));
        shadow.recordFeatures(START + 40_000L, 22, 1, true, START + 35_000L,
                true, 5.0, false, buckets(START + 40_000L, START + 15_000L));
        shadow.recordFeatures(START + 55_000L, 27, 2, false, Long.MIN_VALUE,
                false, 5.0, true, buckets(START + 55_000L,
                        START + 15_000L, START + 45_000L));
        assertEquals(2, shadow.window60().movementEpisodes);
        assertEquals(2, shadow.window60().hrFreshUpdates);
        assertEquals(2, shadow.window60().crossModalRenewals);
        assertEquals(SmartWakeShadowTelemetry.State.SHADOW_CROSS_MODAL_RISING,
                shadow.window60().state);
    }

    @Test public void sevenTwoGroupEvaluationsCanReuseOneEpisode() {
        SmartWakeShadowTelemetry shadow = new SmartWakeShadowTelemetry();
        for (int i = 0; i < 7; i++) {
            long now = START + 20_000L + i * 5_000L;
            shadow.recordFeatures(now, 58, 2, true, now - 1_000L, true, 6.0,
                    true, buckets(now, START));
        }
        assertEquals(58, shadow.window60().peakScore);
        assertEquals(7, shadow.window60().twoGroupEvaluations);
        assertEquals(7, shadow.window60().hrFreshUpdates);
        assertEquals(1, shadow.window60().movementEpisodes);
        assertEquals(6, shadow.window60().movementReuseEvaluations);
        assertEquals(6.0 / 7.0, shadow.window60().movementReuseRatio(), .001);
    }

    @Test public void refaelLikeOneHundredSeventyOneSamplesAreNotRenewals() {
        SmartWakeDetector detector = new SmartWakeDetector(0L);
        for (int i = 0; i < 171; i++) {
            long at = START + i * 170L;
            detector.addAccelerometerMotion(1.3, at);
            detector.addGyroscopeMotion(1.6, at);
        }
        SmartWakeShadowTelemetry shadow = new SmartWakeShadowTelemetry();
        long now = START + 35_000L;
        shadow.recordFeatures(now, 9, 1, false, Long.MIN_VALUE, false, -5.8, true,
                detector.shadowMovementBuckets(shadow.requiredMovementStartAt(now), now));
        assertEquals(2, shadow.window90().movementBuckets);
        assertEquals(1, shadow.window90().movementEpisodes);
        assertEquals(0, shadow.window90().movementRenewals);
    }

    @Test public void oldHrEventDeliveredNowDoesNotEnterSixtySecondWindow() {
        SmartWakeShadowTelemetry shadow = new SmartWakeShadowTelemetry();
        shadow.recordFeatures(START + 90_000L, 18, 2, true, START + 20_000L,
                true, 4.0, true, buckets(START + 90_000L, START + 15_000L));
        assertEquals(0, shadow.window60().hrFreshUpdates);
        assertEquals(1, shadow.window90().hrFreshUpdates);
    }

    @Test public void shadowReadsNeverAlterWakeOrCandidateDecisions() {
        SmartWakeDetector control = preparedDetector();
        SmartWakeDetector observed = preparedDetector();
        addGentleChange(control, 610_000L);
        addGentleChange(observed, 610_000L);
        SmartWakeShadowTelemetry shadow = new SmartWakeShadowTelemetry();
        for (long now : new long[] {675_000L, 680_000L, 685_000L}) {
            SmartWakeDetector.Decision expected = control.evaluate(now);
            SmartWakeDetector.Decision actual = observed.evaluate(now);
            shadow.record(now, actual, observed.shadowMovementBuckets(
                    shadow.requiredMovementStartAt(now), now));
            assertEquals(expected.shouldWake, actual.shouldWake);
            assertEquals(expected.candidate, actual.candidate);
            assertEquals(expected.candidateActive, actual.candidateActive);
            assertEquals(expected.candidateConfirmed, actual.candidateConfirmed);
            assertEquals(expected.score, actual.score);
            assertEquals(expected.evidenceGroups, actual.evidenceGroups);
            assertFalse(actual.shouldWake);
        }
        assertTrue(shadow.window90() != null);
    }

    @Test public void sessionSummaryRetainsResearchMaxima() {
        SmartWakeShadowTelemetry shadow = new SmartWakeShadowTelemetry();
        shadow.recordFeatures(START + 20_000L, 58, 2, true, START + 19_000L,
                true, 6.0, true, buckets(START + 20_000L, START));
        String summary = shadow.sessionSummary();
        assertTrue(summary.contains("MAX_DISTINCT_MOVEMENT_EPISODES_60S=1"));
        assertTrue(summary.contains("MAX_HR_FRESH_UPDATES_ANY_WINDOW=1"));
        assertTrue(summary.contains("MAX_SHADOW_TRANSITION_LEVEL="));
        assertTrue(shadow.compact().contains("SHADOW_60S="));
    }

    @Test public void sessionSummarySeparatesCrossModalTimingFromRenewal() {
        SmartWakeDetector detector = preparedDetector();
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, START);
        detector.addHeartRate(64, START + 10_000L);
        detector.addHeartRate(65, START + 40_000L);
        detector.addHeartRate(66, START + 74_000L);
        for (int i = 0; i < 4; i++) detector.addAccelerometerMotion(1.2, START + 44_000L);
        long now = START + 75_000L;
        SmartWakeDetector.Decision decision = detector.evaluate(now);
        SmartWakeShadowTelemetry shadow = new SmartWakeShadowTelemetry();
        shadow.record(now, decision, detector.shadowMovementBuckets(
                shadow.requiredMovementStartAt(now), now));

        String summary = shadow.sessionSummary();
        assertTrue(summary.contains("MIN_CROSS_MODAL_TIME_GAP_MS=30000"));
        assertTrue(summary.contains("MAX_CROSS_MODAL_CONVERGENCE_LEVEL=NONE"));
        assertTrue(summary.contains("COUNT_GROUPS_2_BUT_ONLY_ONE_SOURCE_RECENT_15S=1"));
        assertTrue(summary.contains("COUNT_GROUPS_2_BUT_ONLY_ONE_SOURCE_RECENT_30S=1"));
    }

    private static List<SmartWakeShadowTelemetry.MovementBucket> buckets(long now,
                                                                         long... activeBucketStarts) {
        List<SmartWakeShadowTelemetry.MovementBucket> result = new ArrayList<>();
        long last = Math.floorDiv(now, SmartWakeShadowTelemetry.BUCKET_MS)
                * SmartWakeShadowTelemetry.BUCKET_MS;
        for (long at = START; at <= last; at += SmartWakeShadowTelemetry.BUCKET_MS) {
            boolean active = false;
            for (long selected : activeBucketStarts) if (selected == at) active = true;
            result.add(new SmartWakeShadowTelemetry.MovementBucket(at, active));
        }
        return result;
    }

    private static SmartWakeDetector preparedDetector() {
        SmartWakeDetector detector = new SmartWakeDetector(0L);
        for (int minute = 0; minute < 10; minute++) {
            long at = minute * 60_000L + 5_000L;
            detector.addHeartRate(60 + (minute % 2), at);
            detector.addHeartRate(61 - (minute % 2), at + 10_000L);
            detector.addAccelerometerMotion(.08, at);
            detector.addGyroscopeMotion(.10, at);
        }
        return detector;
    }

    private static void addGentleChange(SmartWakeDetector detector, long start) {
        detector.addHeartRate(64, start);
        detector.addHeartRate(65, start + 20_000L);
        detector.addHeartRate(66, start + 40_000L);
        for (int i = 0; i < 4; i++) {
            detector.addAccelerometerMotion(1.25, start + 5_000L + i * 4_000L);
        }
    }
}
