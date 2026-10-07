package com.woodpeckerbros.watchreminder.smartalarm;

/** CPU observation only. This class never feeds evidence or changes a detector decision. */
final class SmartWakeCandidateWakeLock {
    static final long SMART_WAKE_CANDIDATE_WAKELOCK_MAX_MS = 120_000L;

    interface Backend {
        void acquire(long timeoutMs);
        boolean isHeld();
        void release();
    }
    interface Clock {
        long elapsed();
        long wall();
    }
    interface Timer {
        void schedule(Runnable task, long delayMs);
        void cancel(Runnable task);
    }
    interface Log { void write(String message); }

    static final class Session {
        final String id;
        boolean observationStopped;
        long attemptedCandidateAt = Long.MIN_VALUE;
        long acquireCount, totalHeldMs, maxSingleHoldMs, timeoutCount;
        String releaseReason = "NONE";
        long evaluationCount, intervalCount, intervalTotalMs, lateTotalMs, maxLateMs;
        long suspendWithLockMs, maxSuspendWithLockMs, suspendWithoutFullLockMs;
        long lastEvaluationAt = Long.MIN_VALUE;

        Session(String id) { this.id = id; }

        String summary() {
            return "CANDIDATE_WAKELOCK_ACQUIRE_COUNT=" + acquireCount
                    + " CANDIDATE_WAKELOCK_TOTAL_HELD_MS=" + totalHeldMs
                    + " CANDIDATE_WAKELOCK_MAX_SINGLE_HOLD_MS=" + maxSingleHoldMs
                    + " CANDIDATE_WAKELOCK_TIMEOUT_COUNT=" + timeoutCount
                    + " CANDIDATE_WAKELOCK_RELEASE_REASON=" + releaseReason
                    + " CANDIDATE_WITH_WAKELOCK_EVALUATION_COUNT=" + evaluationCount
                    + " CANDIDATE_WITH_WAKELOCK_AVG_INTERVAL_MS="
                    + (intervalCount == 0 ? -1L : intervalTotalMs / intervalCount)
                    + " CANDIDATE_WITH_WAKELOCK_AVG_LATE_BY_MS="
                    + (evaluationCount == 0 ? -1L : lateTotalMs / evaluationCount)
                    + " CANDIDATE_WITH_WAKELOCK_MAX_LATE_BY_MS=" + maxLateMs
                    + " TOTAL_SUSPEND_GAP_WITH_CANDIDATE_WAKELOCK_MS=" + suspendWithLockMs
                    + " MAX_SUSPEND_GAP_WITH_CANDIDATE_WAKELOCK_MS=" + maxSuspendWithLockMs
                    + " TOTAL_SUSPEND_GAP_WITHOUT_FULL_CANDIDATE_WAKELOCK_MS="
                    + suspendWithoutFullLockMs;
        }
    }

    private final Backend backend;
    private final Clock clock;
    private final Timer timer;
    private final Log log;
    private Session owner;
    private long candidateAt, acquiredAt, expiresAt;
    private long lastReleasedAt = Long.MIN_VALUE;
    private int originScore, originGroups;
    private String expiryReason;
    private Runnable timeout;
    private long leaseGeneration;

    private synchronized void onTimeout(long generation) {
        // Android acquire(timeout) is the independent safety backstop if this callback is late.
        if (owner != null && generation == leaseGeneration) release(expiryReason);
    }

    SmartWakeCandidateWakeLock(Backend backend, Clock clock, Timer timer, Log log) {
        this.backend = backend;
        this.clock = clock;
        this.timer = timer;
        this.log = log;
    }

    synchronized boolean isHeld() {
        reconcile();
        return owner != null && backend.isHeld();
    }

    synchronized void update(Session session, long now, long earliestWakeAt, long deadline,
                boolean sessionActive, boolean candidateActive, boolean wake,
                long candidateStartedAt, int score, int groups, String candidateStatus) {
        reconcile();
        if (session.observationStopped) return;
        String endReason = !sessionActive ? "SESSION_STOP"
                : now >= deadline ? "FINAL_DEADLINE_REACHED"
                : wake ? "WAKE_DECISION"
                : !candidateActive ? "CANDIDATE_" + candidateStatus
                : now < earliestWakeAt ? "BEFORE_EARLIEST_WAKE" : null;
        if (endReason != null) {
            if (!sessionActive || now >= deadline || wake) session.observationStopped = true;
            if (owner == session) release(endReason);
            return;
        }
        if (owner != null) {
            if (owner == session && candidateAt != candidateStartedAt) {
                // No zero-gap chain can renew the timeout. Observe the new candidate on the
                // next existing evaluation, after this observation lock has been released.
                release("CANDIDATE_REPLACED");
            }
            return;
        }
        if (session.attemptedCandidateAt == candidateStartedAt) return;
        // Guarantee a CPU-unprotected gap even if multiple sessions/candidates are processed
        // in one evaluator pass. The adaptive evaluator's delay and targets stay untouched.
        if (lastReleasedAt != Long.MIN_VALUE
                && clock.elapsed() - lastReleasedAt < SmartWakeEvaluationCadence.MIN_SAFE_DELAY_MS) return;
        session.attemptedCandidateAt = candidateStartedAt;
        long timeoutMs = Math.min(SMART_WAKE_CANDIDATE_WAKELOCK_MAX_MS, deadline - now);
        acquiredAt = clock.elapsed();
        expiresAt = acquiredAt + timeoutMs;
        expiryReason = timeoutMs < SMART_WAKE_CANDIDATE_WAKELOCK_MAX_MS
                ? "FINAL_DEADLINE_REACHED" : "TIMEOUT";
        candidateAt = candidateStartedAt;
        originScore = score;
        originGroups = groups;
        try {
            backend.acquire(timeoutMs);
            if (!backend.isHeld()) throw new IllegalStateException("acquire returned without a held lock");
            owner = session;
            session.acquireCount++;
            session.lastEvaluationAt = acquiredAt;
            long generation = ++leaseGeneration;
            timeout = () -> onTimeout(generation);
            timer.schedule(timeout, timeoutMs);
            log.write("WAKELOCK_ACQUIRED timestamp=" + clock.wall() + " session_id=" + session.id
                    + " candidate_started_at=" + candidateAt + " origin=" + score + "/" + groups
                    + " reason=CANDIDATE_ACTIVE held_duration_ms=0 timeout_ms=" + timeoutMs
                    + " remaining_to_deadline_ms=" + (deadline - now));
        } catch (RuntimeException error) {
            // CPU assistance must not break the detector or independent alarm delivery.
            if (owner != null) release("ACQUIRE_ERROR");
            else if (backend.isHeld()) backend.release();
            log.write("WAKELOCK_ACQUIRE_FAILED timestamp=" + clock.wall()
                    + " session_id=" + session.id + " error=" + error.getClass().getSimpleName());
        }
    }

    synchronized void stop(Session session, String reason) {
        session.observationStopped = true;
        reconcile();
        if (owner == session) release(reason);
    }

    synchronized void stopAll(String reason) {
        reconcile();
        release(reason);
    }

    synchronized void recordEvaluation(Session session, long startedAt,
                          SmartWakeEvaluationCadence.Mode mode, SmartWakeEvaluationTiming timing) {
        reconcile();
        // Exclude acquisition evaluations and intervals partially outside a held observation.
        // Also exclude an interval containing timeout/reacquisition for a different candidate.
        boolean fullIntervalHeld = owner == session && backend.isHeld()
                && session.lastEvaluationAt != Long.MIN_VALUE
                && session.lastEvaluationAt >= acquiredAt;
        if (fullIntervalHeld) {
            session.suspendWithLockMs += timing.suspendGapMs;
            session.maxSuspendWithLockMs = Math.max(session.maxSuspendWithLockMs, timing.suspendGapMs);
            if (timing.suspendGapMs > 10L) {
                log.write("CANDIDATE_WAKELOCK_SUSPEND_DETECTED timestamp=" + clock.wall()
                        + " session_id=" + session.id + " SUSPEND_GAP_MS=" + timing.suspendGapMs);
            }
            if (mode == SmartWakeEvaluationCadence.Mode.CANDIDATE) {
                session.evaluationCount++;
                session.intervalCount++;
                session.intervalTotalMs += Math.max(0L, startedAt - session.lastEvaluationAt);
                session.lateTotalMs += timing.lateByMs;
                session.maxLateMs = Math.max(session.maxLateMs, timing.lateByMs);
            }
        } else session.suspendWithoutFullLockMs += timing.suspendGapMs;
        session.lastEvaluationAt = startedAt;
    }

    private void reconcile() {
        if (owner != null && (clock.elapsed() >= expiresAt || !backend.isHeld())) {
            release(clock.elapsed() >= expiresAt ? expiryReason : "PLATFORM_RELEASED_EARLY");
        }
    }

    private void release(String reason) {
        if (owner == null) return;
        Session ended = owner;
        owner = null;
        lastReleasedAt = clock.elapsed();
        timer.cancel(timeout);
        timeout = null;
        boolean physicallyHeld = backend.isHeld();
        long heldMs = Math.max(0L,
                (physicallyHeld ? clock.elapsed() : Math.min(clock.elapsed(), expiresAt)) - acquiredAt);
        try {
            if (physicallyHeld) backend.release();
        } finally {
            ended.totalHeldMs += heldMs;
            ended.maxSingleHoldMs = Math.max(ended.maxSingleHoldMs, heldMs);
            if ("TIMEOUT".equals(reason)) ended.timeoutCount++;
            ended.releaseReason = reason;
            log.write("WAKELOCK_RELEASED timestamp=" + clock.wall() + " session_id=" + ended.id
                    + " candidate_started_at=" + candidateAt + " origin=" + originScore + "/" + originGroups
                    + " held_duration_ms=" + heldMs + " reason=" + reason
                    + " " + ended.summary());
        }
    }
}
