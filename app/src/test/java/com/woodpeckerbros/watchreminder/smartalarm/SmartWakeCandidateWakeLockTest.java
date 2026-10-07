package com.woodpeckerbros.watchreminder.smartalarm;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class SmartWakeCandidateWakeLockTest {
    private static final long EARLIEST = 1_000_000L, DEADLINE = 2_000_000L;

    private static final class Fixture implements SmartWakeCandidateWakeLock.Clock,
            SmartWakeCandidateWakeLock.Backend, SmartWakeCandidateWakeLock.Timer {
        long now = EARLIEST, platformExpiry, scheduledAt, requestedTimeout;
        int acquisitions, releases, scheduled, cancelled;
        boolean physicalHeld, failAcquire;
        Runnable callback;
        final List<String> logs = new ArrayList<>();
        final SmartWakeCandidateWakeLock.Session session = new SmartWakeCandidateWakeLock.Session("1:2000000");
        final SmartWakeCandidateWakeLock lock = new SmartWakeCandidateWakeLock(this, this, this, logs::add);
        @Override public long elapsed() { return now; }
        @Override public long wall() { return now; }
        @Override public void acquire(long timeoutMs) {
            if (failAcquire) throw new SecurityException("test failure");
            acquisitions++;
            requestedTimeout = timeoutMs;
            physicalHeld = true;
            platformExpiry = now + timeoutMs;
        }
        @Override public boolean isHeld() { return physicalHeld && now < platformExpiry; }
        @Override public void release() { releases++; physicalHeld = false; }
        @Override public void schedule(Runnable task, long delayMs) {
            assertNull("only one timeout callback", callback);
            scheduled++; callback = task; scheduledAt = now + delayMs;
        }
        @Override public void cancel(Runnable task) {
            cancelled++;
            if (task == callback) callback = null;
        }
        void update(long candidateAt, boolean active, boolean wake) {
            lock.update(session, now, EARLIEST, DEADLINE, true, active, wake,
                    candidateAt, 39, 2, active ? "WAITING_FOR_FRESH_CONFIRMATION" : "EVIDENCE_DECAYED");
        }
        void acquireCandidate() { update(now, true, false); assertTrue(lock.isHeld()); }
        void advance(long ms) { now += ms; }
        void fireTimer() { Runnable task = callback; assertNotNull(task); task.run(); }
        void record(long late, long uptimeLate) {
            SmartWakeEvaluationTiming timing = SmartWakeEvaluationTiming.of(now - late,
                    now - uptimeLate, now, now);
            lock.recordEvaluation(session, now, SmartWakeEvaluationCadence.Mode.CANDIDATE, timing);
        }
    }

    @Test public void candidateBeforeEarliestDoesNotAcquire() {
        Fixture f = new Fixture(); f.now = EARLIEST - 1;
        f.update(f.now, true, false);
        assertFalse(f.lock.isHeld()); assertEquals(0, f.acquisitions);
    }
    @Test public void candidateInsideWindowAcquiresNonOverlappingBoundedObservation() {
        Fixture f = new Fixture(); f.acquireCandidate();
        assertEquals(120_000L, f.requestedTimeout);
        assertEquals(1, f.session.acquireCount); assertEquals(1, f.scheduled);
        assertTrue(f.logs.get(0).contains("reason=CANDIDATE_ACTIVE"));
        assertTrue(f.logs.get(0).contains("origin=39/2"));
    }
    @Test public void normalAndWatchingWithoutActiveCandidateNeverAcquire() {
        Fixture f = new Fixture(); f.update(0, false, false);
        assertEquals(0, f.acquisitions);
    }
    @Test public void inactiveSessionNeverAcquires() {
        Fixture f = new Fixture();
        f.lock.update(f.session, f.now, EARLIEST, DEADLINE, false, true, false, f.now, 39, 2, "CREATED");
        assertFalse(f.lock.isHeld());
    }
    @Test public void candidateCancellationImmediatelyReleasesAndIsIdempotent() {
        Fixture f = new Fixture(); f.acquireCandidate(); f.advance(5_000);
        f.update(EARLIEST, false, false); f.update(EARLIEST, false, false);
        assertFalse(f.lock.isHeld()); assertEquals(1, f.releases);
        assertEquals(5_000L, f.session.totalHeldMs); assertNull(f.callback);
        assertEquals("CANDIDATE_EVIDENCE_DECAYED", f.session.releaseReason);
    }
    @Test public void candidateExpiryReleases() {
        Fixture f = new Fixture(); f.acquireCandidate();
        f.lock.update(f.session, f.now, EARLIEST, DEADLINE, true, false, false, 0, 39, 2, "EXPIRED");
        assertFalse(f.lock.isHeld()); assertEquals("CANDIDATE_EXPIRED", f.session.releaseReason);
    }
    @Test public void confirmedCandidateAndTemporalWakeBothReleaseBeforeDelivery() {
        Fixture f = new Fixture(); f.acquireCandidate(); f.advance(5_000);
        f.update(EARLIEST, true, true);
        assertFalse(f.lock.isHeld()); assertEquals("WAKE_DECISION", f.session.releaseReason);
        assertEquals(5_000L, f.session.maxSingleHoldMs);
    }
    @Test public void deadlineShortensTimeoutAndCannotAcquireAtOrBeyondDeadline() {
        Fixture f = new Fixture(); f.now = DEADLINE - 3_000; f.acquireCandidate();
        assertEquals(3_000L, f.requestedTimeout);
        f.advance(3_000); f.fireTimer(); f.update(f.now, true, false);
        assertFalse(f.lock.isHeld()); assertEquals(1, f.acquisitions);
        assertEquals("FINAL_DEADLINE_REACHED", f.session.releaseReason);
        assertEquals(3_000L, f.session.totalHeldMs);
    }
    @Test public void finalDeadlineDecisionReleasesImmediately() {
        Fixture f = new Fixture(); f.acquireCandidate();
        f.lock.update(f.session, DEADLINE, EARLIEST, DEADLINE, true, true, false, EARLIEST, 39, 2, "CREATED");
        assertFalse(f.lock.isHeld()); assertEquals("FINAL_DEADLINE_REACHED", f.session.releaseReason);
    }
    @Test public void sessionStopReleases() { assertStopReason("EXPLICIT_STOP"); }
    @Test public void hardStopReleases() { assertStopReason("DEADLINE_PLUS_GRACE_HARD_STOP"); }
    @Test public void serviceDestroyReleasesAllEvenAfterRepeatedCleanup() {
        Fixture f = new Fixture(); f.acquireCandidate();
        f.lock.stopAll("SERVICE_DESTROY"); f.lock.stopAll("SERVICE_DESTROY");
        assertFalse(f.lock.isHeld()); assertEquals(1, f.releases); assertNull(f.callback);
    }
    @Test public void alarmCancellationReleases() { assertStopReason("ALARM_EXPLICIT_CANCEL_OR_RECONFIGURE"); }
    @Test public void alarmReplacementReleases() { assertStopReason("OCCURRENCE_REPLACED"); }
    @Test public void unrecoverableErrorReleases() { assertStopReason("DETECTOR_EXCEPTION"); }
    private void assertStopReason(String reason) {
        Fixture f = new Fixture(); f.acquireCandidate(); f.advance(7_000);
        f.lock.stop(f.session, reason);
        assertFalse(f.lock.isHeld()); assertEquals(reason, f.session.releaseReason);
        assertEquals(7_000L, f.session.totalHeldMs); assertNull(f.callback);
    }
    @Test public void nativeTimeoutStillBoundsHoldWhenApplicationCallbackIsDelayed() {
        Fixture f = new Fixture(); f.acquireCandidate(); f.advance(150_000);
        assertFalse(f.isHeld()); // Android's independent acquire(timeout) backstop.
        f.update(EARLIEST, true, false);
        assertFalse(f.lock.isHeld()); assertEquals(1, f.acquisitions);
        assertEquals(120_000L, f.session.totalHeldMs); assertEquals(1, f.session.timeoutCount);
        assertNull(f.callback);
    }
    @Test public void timeoutCallbackAccountsOnceAndSameCandidateCannotRenewIt() {
        Fixture f = new Fixture(); f.acquireCandidate(); f.advance(120_000); f.fireTimer();
        f.advance(5_000); f.update(EARLIEST, true, false);
        assertEquals(1, f.acquisitions); assertEquals(1, f.session.timeoutCount);
        assertEquals(120_000L, f.session.maxSingleHoldMs);
    }
    @Test public void repeatedEvaluationsDoNotReacquireOrStackTimeouts() {
        Fixture f = new Fixture(); f.acquireCandidate();
        for (int i = 0; i < 20; i++) { f.advance(5_000); f.update(EARLIEST, true, false); }
        assertEquals(1, f.acquisitions); assertEquals(1, f.scheduled);
        assertEquals(EARLIEST + 120_000, f.platformExpiry);
    }
    @Test public void candidateAEndsThenCandidateBLaterAcquiresNewBoundedHold() {
        Fixture f = new Fixture(); f.acquireCandidate(); f.advance(5_000);
        f.update(EARLIEST, false, false); f.advance(5_000);
        long second = f.now; f.update(second, true, false);
        assertTrue(f.lock.isHeld()); assertEquals(2, f.acquisitions);
        assertEquals(second + 120_000L, f.platformExpiry);
        f.advance(7_000); f.lock.stop(f.session, "EXPLICIT_STOP");
        assertEquals(12_000L, f.session.totalHeldMs);
        assertEquals(7_000L, f.session.maxSingleHoldMs);
    }
    @Test public void zeroGapCandidateReplacementCannotExtendContinuousHold() {
        Fixture f = new Fixture(); f.acquireCandidate(); f.advance(60_000);
        long second = f.now; f.update(second, true, false);
        assertFalse(f.lock.isHeld());
        f.update(second, true, false); assertFalse(f.lock.isHeld());
        f.advance(5_000); f.update(second, true, false);
        assertTrue(f.lock.isHeld()); assertEquals(2, f.acquisitions);
    }
    @Test public void preWindowCandidateCrossingBoundaryAcquiresOnlyAtEarliest() {
        Fixture f = new Fixture(); f.now = EARLIEST - 30_000;
        long origin = f.now; f.update(origin, true, false); assertFalse(f.lock.isHeld());
        f.advance(30_000); f.update(origin, true, false); assertTrue(f.lock.isHeld());
    }
    @Test public void reconstructedSessionStartsWithoutLockAndUsesNormalEligibility() {
        Fixture restarted = new Fixture(); assertFalse(restarted.lock.isHeld());
        restarted.now = EARLIEST - 1; restarted.update(restarted.now, true, false);
        assertFalse(restarted.lock.isHeld());
        restarted.now = EARLIEST; restarted.update(EARLIEST - 1, true, false);
        assertTrue(restarted.lock.isHeld());
    }
    @Test public void anotherOccurrenceCannotReleaseOrStackOwnersLock() {
        Fixture f = new Fixture(); f.acquireCandidate();
        SmartWakeCandidateWakeLock.Session other = new SmartWakeCandidateWakeLock.Session("2:2000000");
        f.lock.stop(other, "EXPLICIT_STOP"); assertTrue(f.lock.isHeld());
        f.lock.update(other, f.now, EARLIEST, DEADLINE, true, true, false, f.now, 23, 2, "CREATED");
        assertEquals(1, f.acquisitions); assertEquals(0, other.acquireCount);
    }
    @Test public void cadenceTelemetryIncludesMotiWakeFrameBeforeRelease() {
        Fixture f = new Fixture(); f.acquireCandidate(); f.advance(5_000); f.record(0, 0);
        f.update(EARLIEST, true, true);
        assertEquals(1, f.session.evaluationCount); assertEquals(5_000, f.session.intervalTotalMs);
        assertTrue(f.session.summary().contains("CANDIDATE_WITH_WAKELOCK_AVG_INTERVAL_MS=5000"));
        assertTrue(f.session.summary().contains("CANDIDATE_WAKELOCK_TOTAL_HELD_MS=5000"));
    }
    @Test public void suspendTelemetrySeparatesFullHeldIntervalsAndFlagsUnexpectedSuspend() {
        Fixture f = new Fixture(); f.record(30_000, 0);
        f.acquireCandidate(); f.advance(5_100); f.record(100, 0);
        assertEquals(30_000, f.session.suspendWithoutFullLockMs);
        assertEquals(100, f.session.suspendWithLockMs);
        assertEquals(100, f.session.maxLateMs);
        assertTrue(f.logs.stream().anyMatch(s -> s.contains("CANDIDATE_WAKELOCK_SUSPEND_DETECTED")));
    }
    @Test public void acquireFailureDoesNotThrowOrLoopOnSameCandidate() {
        Fixture f = new Fixture(); f.failAcquire = true;
        f.update(EARLIEST, true, false); f.update(EARLIEST, true, false);
        assertFalse(f.lock.isHeld()); assertEquals(0, f.session.acquireCount);
        assertEquals(1, f.logs.size());
    }
    @Test public void oldDequeuedTimeoutCannotReleaseNextCandidatesLease() {
        Fixture f = new Fixture(); f.acquireCandidate();
        Runnable oldTimeout = f.callback;
        f.advance(5_000); f.update(EARLIEST, false, false);
        f.advance(5_000); f.update(f.now, true, false);
        oldTimeout.run();
        assertTrue(f.lock.isHeld()); assertEquals(2, f.acquisitions);
    }
    @Test public void cancelledOccurrenceCannotAcquireAgainEvenIfAnEvaluationWasPending() {
        Fixture f = new Fixture(); f.acquireCandidate();
        f.lock.stop(f.session, "ALARM_EXPLICIT_CANCEL_OR_RECONFIGURE");
        f.advance(30_000); f.update(f.now, true, false);
        assertFalse(f.lock.isHeld()); assertEquals(1, f.acquisitions);
    }
    @Test public void oldOccurrenceCleanupCannotReleaseReplacementOccurrence() {
        Fixture f = new Fixture(); f.acquireCandidate();
        f.lock.stop(f.session, "OCCURRENCE_REPLACED");
        SmartWakeCandidateWakeLock.Session replacement = new SmartWakeCandidateWakeLock.Session("1:2100000");
        f.advance(5_000);
        f.lock.update(replacement, f.now, EARLIEST, DEADLINE, true, true, false, f.now, 23, 2, "CREATED");
        f.lock.stop(f.session, "ALARM_RESCHEDULE");
        assertTrue(f.lock.isHeld()); assertEquals(1, replacement.acquireCount);
    }
    @Test public void heldCpuAndRepeatedFiveSecondFramesCannotManufactureEvidenceOrShadowRenewals() {
        SmartWakeDetector detector = new SmartWakeDetector(0L);
        for (int minute = 0; minute < 10; minute++) {
            long at = minute * 60_000L + 5_000L;
            detector.addHeartRate(60 + minute % 2, at);
            detector.addHeartRate(61 - minute % 2, at + 10_000L);
            detector.addAccelerometerMotion(.08, at);
            detector.addGyroscopeMotion(.10, at);
        }
        detector.setUserActivity(SmartWakeDetector.UserActivity.ASLEEP, 600_000L);
        detector.addHeartRate(64, 610_000L);
        detector.addHeartRate(65, 630_000L);
        detector.addHeartRate(66, 650_000L);
        for (int i = 0; i < 4; i++) detector.addAccelerometerMotion(1.25, 615_000L + i * 4_000L);
        Fixture f = new Fixture(); f.now = 675_000L;
        SmartWakeShadowTelemetry shadow = new SmartWakeShadowTelemetry();
        SmartWakeDetector.Decision first = detector.evaluate(f.now);
        assertTrue(first.candidateActive); assertFalse(first.shouldWake);
        f.lock.update(f.session, f.now, 600_000L, 900_000L, true,
                first.candidateActive, first.shouldWake, f.now - first.candidateAgeMs,
                first.candidateOriginScore, first.candidateOriginGroups, first.candidateConfirmationStatus);
        assertTrue(f.lock.isHeld());
        shadow.record(f.now, first, detector.shadowMovementBuckets(shadow.requiredMovementStartAt(f.now), f.now));
        for (int i = 0; i < 8; i++) {
            f.advance(5_000);
            SmartWakeDetector.Decision repeated = detector.evaluate(f.now);
            f.lock.update(f.session, f.now, 600_000L, 900_000L, true,
                    repeated.candidateActive, repeated.shouldWake, f.now - repeated.candidateAgeMs,
                    repeated.candidateOriginScore, repeated.candidateOriginGroups, repeated.candidateConfirmationStatus);
            shadow.record(f.now, repeated, detector.shadowMovementBuckets(shadow.requiredMovementStartAt(f.now), f.now));
            assertFalse(repeated.hrNew); assertFalse(repeated.movementNew); assertFalse(repeated.stepNew);
            assertFalse(repeated.freshEvidenceChanged); assertEquals(first.interestingFrameCount, repeated.interestingFrameCount);
            assertFalse(repeated.candidateConfirmed); assertFalse(repeated.shouldWake);
            assertEquals(first.heartRateSampleAgeMs + (i + 1) * 5_000L, repeated.heartRateSampleAgeMs);
            assertFalse(repeated.hrRecent5s); assertFalse(repeated.movementRecent5s);
            assertEquals(0, shadow.window90().movementRenewals);
            assertEquals(0, shadow.window90().crossModalRenewals);
        }
        assertEquals(1, f.acquisitions);
    }
}
