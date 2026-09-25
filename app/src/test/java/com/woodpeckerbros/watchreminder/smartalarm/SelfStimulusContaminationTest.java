package com.woodpeckerbros.watchreminder.smartalarm;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SelfStimulusContaminationTest {
    @Test public void automaticAlertTimeoutDoesNotCreateAwakeState() {
        SelfStimulusContamination contamination = new SelfStimulusContamination();
        long id = contamination.begin("REMINDER_ALERT", "normal-1", 1_000L, 10_000L);
        contamination.end(id, 5_000L, 14_000L); // automatic close is only an end timestamp.
        SelfStimulusContamination.Snapshot snapshot = contamination.snapshot(10_000L);
        assertTrue(snapshot.active);
        // The contamination object has no user-activity mutation path: auto-close is not awake proof.
        assertFalse(snapshot.source.sourceOccurrenceId.isEmpty());
    }

    @Test public void modalityLifetimesAreBoundedToTheirActualEvidenceWindows() {
        SelfStimulusContamination contamination = new SelfStimulusContamination();
        long id = contamination.begin("REMINDER_ALERT", "normal-1", 1_000L, 10_000L);
        contamination.end(id, 5_000L, 14_000L);
        SelfStimulusContamination.Taint duringRecovery = contamination.taintAt(64_000L);
        assertTrue(duringRecovery.step);
        assertTrue(duringRecovery.movement);
        assertTrue(duringRecovery.cardio);
        SelfStimulusContamination.Taint expired = contamination.taintAt(81_000L);
        assertFalse(expired.step);
        assertFalse(expired.movement);
        assertFalse(expired.cardio);
    }

    @Test public void explicitAwakeIsNotReclassifiedAsContaminatedSensorEvidence() {
        // “I'm Awake” is handled by SmartAlarmWakeCheckReceiver.confirm after Smart Wake has
        // already stopped. This tracker only tags sensor samples and never invents awake state.
        SelfStimulusContamination contamination = new SelfStimulusContamination();
        long id = contamination.begin("WAKE_CHECK", "", 1_000L, 10_000L);
        contamination.end(id, 2_000L, 11_000L);
        assertTrue(contamination.taintAt(2_500L).movement);
        assertFalse(contamination.snapshot(80_000L).active);
    }
}
