package com.woodpeckerbros.watchreminder.smartalarm;

import com.woodpeckerbros.watchreminder.reminder.*;

import android.content.Context;
import android.content.SharedPreferences;

public final class SmartAlarmStateStore {
    private final SharedPreferences prefs;

    public SmartAlarmStateStore(Context context) {
        this(context, 1);
    }

    public SmartAlarmStateStore(Context context, int alarmId) {
        this(context.getApplicationContext().getSharedPreferences(
                alarmId == 1 ? "smart_alarm_state" : "smart_alarm_state_" + alarmId, Context.MODE_PRIVATE));
    }

    SmartAlarmStateStore(SharedPreferences prefs) { this.prefs = prefs; }

    /** One fixed feedback deadline shared by receiver, service and every Activity recreation. */
    synchronized void beginPresentation(long targetAt, long feedbackEndAt) {
        prefs.edit().putLong("presentation_target_at", targetAt)
                .putLong("feedback_end_at", feedbackEndAt)
                .putBoolean("presentation_timeout_handled", false).commit();
    }

    long feedbackEndAt(long targetAt) {
        return prefs.getLong("presentation_target_at", 0L) == targetAt
                ? prefs.getLong("feedback_end_at", 0L) : 0L;
    }

    boolean presentationMatches(long targetAt, long feedbackEndAt) {
        return feedbackEndAt > 0L && feedbackEndAt(targetAt) == feedbackEndAt;
    }

    synchronized boolean finishPresentationTimeout(long targetAt, long feedbackEndAt) {
        if (!presentationMatches(targetAt, feedbackEndAt)
                || prefs.getBoolean("presentation_timeout_handled", false)) return false;
        return prefs.edit().putBoolean("presentation_timeout_handled", true).commit();
    }

    boolean presentationTimeoutHandled() {
        return prefs.getBoolean("presentation_timeout_handled", false);
    }

    public synchronized void begin(long targetAt) {
        prefs.edit().putLong("target_at", targetAt).putLong("occurrence_target_at", targetAt)
                .putBoolean("fired", false).putBoolean("dismissed", false)
                .putBoolean("awake_confirmed", false).putBoolean("final_deadline_delivered", false)
                .putBoolean("early_chain_exhausted", false).putInt("snooze_used", 0).apply();
    }

    public synchronized void beginSnooze(long targetAt, int snoozeUsed) {
        long occurrenceTargetAt = prefs.getLong("occurrence_target_at", prefs.getLong("target_at", targetAt));
        prefs.edit().putLong("target_at", targetAt).putLong("occurrence_target_at", occurrenceTargetAt)
                .putBoolean("fired", false).putBoolean("dismissed", false)
                .putBoolean("early_chain_exhausted", false).putInt("snooze_used", snoozeUsed).apply();
    }

    public long targetAt() { return prefs.getLong("target_at", 0L); }
    public long occurrenceTargetAt() { return prefs.getLong("occurrence_target_at", targetAt()); }
    public boolean fired(long targetAt) { return targetAt == targetAt() && prefs.getBoolean("fired", false); }
    public boolean dismissed(long targetAt) { return targetAt == targetAt() && prefs.getBoolean("dismissed", false); }

    /**
     * An early Smart Wake attempt and the configured final deadline intentionally have separate
     * delivery state even though the first early attempt carries the same occurrence target.
     */
    public synchronized boolean canFire(long targetAt, boolean finalDeadline) {
        if (finalDeadline) {
            return targetAt == occurrenceTargetAt()
                    && SmartAlarmFinalDeadlinePolicy.mayDeliverFinalDeadline(
                    prefs.getBoolean("awake_confirmed", false),
                    prefs.getBoolean("final_deadline_delivered", false));
        }
        return targetAt == targetAt() && !fired(targetAt) && !dismissed(targetAt)
                && !SmartAlarmFinalDeadlinePolicy.mustIgnoreEarlySnoozeAfterFinalDeadline(
                finalDeadlineDelivered())
                && !prefs.getBoolean("early_chain_exhausted", false);
    }

    /** Records terminal delivery only after NotificationManager accepted the alert. */
    public synchronized boolean markFireDelivered(long targetAt, boolean finalDeadline) {
        if (finalDeadline) {
            if (targetAt != occurrenceTargetAt() || prefs.getBoolean("awake_confirmed", false)) return false;
            if (prefs.getBoolean("final_deadline_delivered", false)) return true;
            return prefs.edit().putLong("target_at", targetAt)
                    .putBoolean("fired", true).putBoolean("dismissed", false)
                    .putBoolean("final_deadline_delivered", true).commit();
        }
        if (targetAt != targetAt() || dismissed(targetAt)) return false;
        if (fired(targetAt)) return true;
        return prefs.edit().putBoolean("fired", true).commit();
    }

    /** Completes an occurrence that was durably alerted while credential storage was locked. */
    public synchronized boolean completeDirectBootDelivery(long targetAt) {
        if (targetAt != occurrenceTargetAt()) return false;
        return prefs.edit().putLong("target_at", targetAt)
                .putBoolean("fired", true).putBoolean("dismissed", true)
                .putBoolean("final_deadline_delivered", true).commit();
    }

    public void dismiss(long targetAt) {
        if (targetAt == targetAt()) prefs.edit().putBoolean("dismissed", true).apply();
    }

    public synchronized void exhaustEarlyChain(long targetAt) {
        if (targetAt == targetAt()) {
            prefs.edit().putBoolean("dismissed", true).putBoolean("early_chain_exhausted", true).apply();
        }
    }

    public synchronized boolean confirmAwake() {
        if (prefs.getBoolean("awake_confirmed", false)) return true;
        return prefs.edit().putBoolean("awake_confirmed", true).putBoolean("dismissed", true).commit();
    }

    public boolean awakeConfirmed() { return prefs.getBoolean("awake_confirmed", false); }
    public boolean finalDeadlineDelivered() { return prefs.getBoolean("final_deadline_delivered", false); }
    public boolean earlyChainExhausted() { return prefs.getBoolean("early_chain_exhausted", false); }
    public boolean finalDeadlineStillRequired() {
        return SmartAlarmFinalDeadlinePolicy.mustKeepFinalDeadline(
                awakeConfirmed(), finalDeadlineDelivered());
    }
    public boolean hasActiveSnooze() {
        return snoozeUsed() > 0 && targetAt() != occurrenceTargetAt()
                && !fired(targetAt()) && !dismissed(targetAt());
    }

    public int snoozeUsed() { return prefs.getInt("snooze_used", 0); }
    public void clear() { prefs.edit().clear().apply(); }
}
