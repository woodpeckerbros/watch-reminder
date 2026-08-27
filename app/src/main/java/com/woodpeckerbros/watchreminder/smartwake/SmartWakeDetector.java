package com.woodpeckerbros.watchreminder.smartwake;

import java.util.ArrayDeque;
import java.util.Deque;

/** Conservative wake-opportunity estimator; it does not claim medical sleep-stage detection. */
public final class SmartWakeDetector {
    private final Deque<Double> heartRates = new ArrayDeque<>();
    private long startedAt;
    private int movementBursts;
    private double movementEnergy;
    private int consecutiveCandidateWindows;
    private boolean asleep;
    private boolean awakeTransition;

    public SmartWakeDetector(long startedAt) { this.startedAt = startedAt; }

    public void addHeartRate(double bpm) {
        if (bpm < 30 || bpm > 240) return;
        heartRates.addLast(bpm);
        while (heartRates.size() > 30) heartRates.removeFirst();
    }

    public void addMotion(double linearMagnitude) {
        if (linearMagnitude > 0.35) movementEnergy += Math.min(3.0, linearMagnitude);
        if (linearMagnitude > 1.15) movementBursts++;
    }

    public void setAsleep(boolean value) {
        if (asleep && !value) awakeTransition = true;
        asleep = value;
    }

    public Decision evaluate(long now) {
        double mean = mean();
        double variability = variability(mean);
        double slope = slope();
        int score = 0;
        if (awakeTransition) score += 70;
        if (movementBursts >= 2) score += 24;
        if (movementEnergy >= 4.0) score += 18;
        if (heartRates.size() >= 5 && slope >= 1.5) score += 16;
        if (heartRates.size() >= 5 && variability >= 2.0) score += 12;
        if (!asleep) score += 8;
        boolean warmedUp = now - startedAt >= 2 * 60_000L;
        boolean candidate = warmedUp && score >= 38;
        consecutiveCandidateWindows = candidate ? consecutiveCandidateWindows + 1 : 0;
        boolean shouldWake = awakeTransition || consecutiveCandidateWindows >= 2;
        Decision result = new Decision(shouldWake, score, mean, variability, slope, movementBursts, movementEnergy);
        movementBursts = 0;
        movementEnergy = 0;
        awakeTransition = false;
        return result;
    }

    private double mean() {
        if (heartRates.isEmpty()) return 0;
        double sum = 0; for (double value : heartRates) sum += value; return sum / heartRates.size();
    }

    private double variability(double mean) {
        if (heartRates.size() < 2) return 0;
        double sum = 0; for (double value : heartRates) { double d = value - mean; sum += d * d; }
        return Math.sqrt(sum / heartRates.size());
    }

    private double slope() {
        if (heartRates.size() < 5) return 0;
        return heartRates.peekLast() - heartRates.peekFirst();
    }

    public static final class Decision {
        public final boolean shouldWake;
        public final int score;
        public final double heartRateMean, heartRateVariability, heartRateSlope;
        public final int movementBursts;
        public final double movementEnergy;

        Decision(boolean shouldWake, int score, double mean, double variability, double slope, int bursts, double energy) {
            this.shouldWake = shouldWake; this.score = score; this.heartRateMean = mean;
            this.heartRateVariability = variability; this.heartRateSlope = slope;
            this.movementBursts = bursts; this.movementEnergy = energy;
        }
    }
}
