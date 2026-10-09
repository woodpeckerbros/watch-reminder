package com.woodpeckerbros.watchreminder.smartalarm;

import java.util.ArrayList;
import java.util.List;

/**
 * Observational-only description of a wake opportunity.  This class is deliberately separate
 * from {@link SmartWakeDetector}: callers invoke it only after a production WAKE decision, and
 * none of its output is fed back to scoring, candidates, cadence, or alarm delivery.
 */
final class SmartWakePracticalWakeQuality {
    enum Level { HIGH, MEDIUM, LOW, INSUFFICIENT_DATA }

    static final class Result {
        final Level level;
        final String reasons, cardioStrength, movementQuality, crossModalQuality;

        Result(Level level, List<String> reasons, String cardioStrength,
               String movementQuality, String crossModalQuality) {
            this.level = level;
            this.reasons = String.join(",", reasons);
            this.cardioStrength = cardioStrength;
            this.movementQuality = movementQuality;
            this.crossModalQuality = crossModalQuality;
        }

        String compact() {
            return " SHADOW_PRACTICAL_WAKE_QUALITY=" + level
                    + " SHADOW_PRACTICAL_WAKE_REASONS=" + reasons
                    + " SHADOW_CARDIO_STRENGTH=" + cardioStrength
                    + " SHADOW_MOVEMENT_QUALITY=" + movementQuality
                    + " SHADOW_CROSS_MODAL_QUALITY=" + crossModalQuality;
        }

        String telemetry(SmartWakeDetector.Decision decision,
                         SmartWakeShadowTelemetry.Window window) {
            return compact()
                    + " SHADOW_HR_DELTA_AT_WAKE=" + format(decision.hrAboveBaseline)
                    + " SHADOW_HR_SLOPE_AT_WAKE=" + format(decision.heartRateRecentSlope)
                    + " SHADOW_HR_TREND_CONSISTENCY_PCT="
                    + (window == null ? "NO_DATA" : window.hrTrendConsistencyPercent)
                    + " SHADOW_MOVEMENT_AGE_MS=" + decision.movementSampleAgeMs
                    + " SHADOW_CROSS_MODAL_GAP_MS=" + decision.crossModalTimeGapMs
                    + " SHADOW_CROSS_MODAL_CONVERGENCE=" + decision.crossModalConvergenceLevel
                    + " SHADOW_MOVEMENT_EPISODE_COUNT="
                    + (window == null ? "NO_DATA" : window.movementEpisodes)
                    + " SHADOW_MOVEMENT_RENEWAL_COUNT="
                    + (window == null ? "NO_DATA" : window.movementRenewals)
                    + " SHADOW_CROSS_MODAL_RENEWAL_COUNT="
                    + (window == null ? "NO_DATA" : window.crossModalRenewals)
                    + " SHADOW_RECENT_GROUP_COUNTS_5S_15S_30S="
                    + decision.recent5sGroupCount + "/" + decision.recent15sGroupCount
                    + "/" + decision.recent30sGroupCount;
        }
    }

    private SmartWakePracticalWakeQuality() { }

    static Result evaluate(SmartWakeDetector.Decision decision,
                           SmartWakeShadowTelemetry.Window window) {
        return evaluate(decision.heartRateSampleAgeMs, decision.movementSampleAgeMs,
                decision.steps, decision.steps60Seconds, decision.clearlyAwake,
                decision.recent15sGroupCount, decision.crossModalConvergenceLevel,
                decision.hrAboveBaseline, decision.heartRateRecentSlope, window);
    }

    /** Package-private deterministic entry point; production uses the Decision overload above. */
    static Result evaluate(long heartRateAgeMs, long movementAgeMs, int steps, int steps60Seconds,
                           boolean clearlyAwake, int recent15sGroupCount,
                           String crossModalConvergenceLevel, double hrDelta, double hrSlope,
                           SmartWakeShadowTelemetry.Window window) {
        List<String> reasons = new ArrayList<>();
        boolean hasCardio = heartRateAgeMs >= 0L;
        boolean hasMovement = movementAgeMs >= 0L;
        boolean hasSteps = steps60Seconds > 0 || steps > 0;
        if (clearlyAwake) reasons.add("CLEARLY_AWAKE");
        if (hasSteps) reasons.add("STEPS_PRESENT");

        String crossModal = crossModalQuality(crossModalConvergenceLevel);
        if ("5S".equals(crossModal)) reasons.add("FRESH_CROSS_MODAL_5S");
        else if ("15S".equals(crossModal)) reasons.add("FRESH_CROSS_MODAL_15S");

        String movement = movementQuality(movementAgeMs);
        if ("STALE".equals(movement)) reasons.add("STALE_MOVEMENT_AT_WAKE");
        if (recent15sGroupCount <= 1) reasons.add("ONLY_ONE_RECENT_GROUP_AT_WAKE");

        int episodes = window == null ? -1 : window.movementEpisodes;
        int renewals = window == null ? -1 : window.movementRenewals;
        int crossRenewals = window == null ? -1 : window.crossModalRenewals;
        if (episodes == 1) reasons.add("SINGLE_MOVEMENT_EPISODE");
        else if (episodes >= 2) reasons.add("MULTIPLE_MOVEMENT_EPISODES");
        if (renewals >= 1) reasons.add("MOVEMENT_RENEWAL");
        if (crossRenewals >= 2) reasons.add("MULTIPLE_CROSS_MODAL_RENEWALS");

        String cardio = cardioStrength(heartRateAgeMs, hrDelta, hrSlope, window);
        if ("STRONG".equals(cardio)) reasons.add("STRONG_RISING_CARDIO");
        else if ("MODERATE".equals(cardio)) reasons.add("MODERATE_RISING_CARDIO");
        else reasons.add("WEAK_CARDIO_RESPONSE");

        if (!hasCardio && !hasMovement && !clearlyAwake && !hasSteps) {
            return new Result(Level.INSUFFICIENT_DATA, reasons, cardio, movement, crossModal);
        }

        boolean strong = "STRONG".equals(cardio);
        boolean moderate = "MODERATE".equals(cardio);
        boolean fresh5 = "5S".equals(crossModal);
        boolean fresh15 = fresh5 || "15S".equals(crossModal);
        boolean usableMovement = "5S".equals(movement) || "15S".equals(movement)
                || "30S".equals(movement);
        boolean multipleRenewed = renewals >= 1 || crossRenewals >= 2 || episodes >= 2;

        Level level;
        if (clearlyAwake || (strong && fresh5 && multipleRenewed)) {
            level = Level.HIGH;
        } else if ((strong && usableMovement) || (moderate && fresh15)
                || (fresh15 && multipleRenewed)) {
            // One episode is deliberately allowed here: a sustained, strongly rising cardio
            // pattern plus reasonably fresh movement can still describe a developing wake-up.
            level = Level.MEDIUM;
        } else {
            level = Level.LOW;
        }
        return new Result(level, reasons, cardio, movement, crossModal);
    }

    private static String cardioStrength(long heartRateAgeMs, double hrDelta, double hrSlope,
                                         SmartWakeShadowTelemetry.Window window) {
        if (heartRateAgeMs < 0L) return "NO_DATA";
        int consistency = window == null ? -1 : window.hrTrendConsistencyPercent;
        boolean rising = window != null && "RISING".equals(window.hrTrendDirection)
                && (consistency < 0 || consistency >= 60);
        if (hrDelta >= 6.0 && hrSlope >= 2.0
                && rising && window.hrFreshUpdates >= 3) return "STRONG";
        if (hrDelta >= 3.0 && hrSlope >= 1.0
                && (window == null || window.hrFreshUpdates >= 2)) return "MODERATE";
        return "WEAK";
    }

    private static String movementQuality(long movementAgeMs) {
        if (movementAgeMs < 0L) return "NO_DATA";
        if (movementAgeMs <= 5_000L) return "5S";
        if (movementAgeMs <= 15_000L) return "15S";
        if (movementAgeMs <= 30_000L) return "30S";
        return "STALE";
    }

    private static String crossModalQuality(String value) {
        return "5S".equals(value) || "15S".equals(value) || "30S".equals(value)
                ? value : "NONE";
    }

    private static String format(double value) {
        return Double.isNaN(value) ? "NO_DATA" : String.format(java.util.Locale.US, "%.2f", value);
    }
}
