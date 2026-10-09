package com.woodpeckerbros.watchreminder.smartalarm;

import android.content.SharedPreferences;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/** Exercises persisted production state across recreation without an Android runtime dependency. */
public class SmartAlarmStateStoreTest {
    private static final long FINAL = 1_000_000L;

    @Test public void successfulEarlyDismissBlocksSnoozeAndOriginalDeadlineAfterRestart() {
        SharedPreferences prefs = memoryPreferences();
        SmartAlarmStateStore state = new SmartAlarmStateStore(prefs);
        state.begin(FINAL);
        assertTrue(state.markFireDelivered(FINAL, false));
        assertTrue(state.confirmAwake());
        SmartAlarmStateStore restarted = new SmartAlarmStateStore(prefs);
        assertFalse(restarted.canFire(FINAL, false));
        assertFalse(restarted.canFire(FINAL, true));
        assertFalse(restarted.finalDeadlineStillRequired());
        restarted.begin(FINAL + 86_400_000L);
        assertFalse(restarted.canFire(FINAL, true));
    }

    @Test public void successfulTaskCompletionDuringSilentGraceStillEndsOccurrence() {
        SmartAlarmStateStore state = new SmartAlarmStateStore(memoryPreferences());
        state.begin(FINAL);
        state.markFireDelivered(FINAL, false);
        state.beginPresentation(FINAL, 30_000L);
        // A retained silent task must remain actionable; timeout is not finalized until idle.
        assertTrue(state.fired(FINAL));
        assertFalse(state.dismissed(FINAL));
        assertFalse(state.presentationTimeoutHandled());
        assertTrue(state.confirmAwake());
        assertFalse(state.canFire(FINAL, true));
    }

    @Test public void lateEarlyTimeoutCannotStopFinalWithSameOriginalTarget() {
        SmartAlarmStateStore state = new SmartAlarmStateStore(memoryPreferences());
        state.begin(FINAL);
        state.markFireDelivered(FINAL, false);
        state.beginPresentation(FINAL, 30_000L);
        assertTrue(state.canFire(FINAL, true));
        state.markFireDelivered(FINAL, true);
        state.beginPresentation(FINAL, 90_000L);
        assertFalse(state.finishPresentationTimeout(FINAL, 30_000L));
        assertTrue(state.finishPresentationTimeout(FINAL, 90_000L));
        assertFalse(state.finishPresentationTimeout(FINAL, 90_000L));
    }

    @Test public void unansweredTimeoutPreservesFinalAndFinalDeliveryIsOnceOnly() {
        SmartAlarmStateStore state = new SmartAlarmStateStore(memoryPreferences());
        state.begin(FINAL);
        state.markFireDelivered(FINAL, false);
        state.beginPresentation(FINAL, 30_000L);
        assertTrue(state.finishPresentationTimeout(FINAL, 30_000L));
        state.exhaustEarlyChain(FINAL);
        assertTrue(state.finalDeadlineStillRequired());
        assertTrue(state.canFire(FINAL, true));
        assertTrue(state.markFireDelivered(FINAL, true));
        assertFalse(state.canFire(FINAL, true));
    }

    private static SharedPreferences memoryPreferences() {
        Map<String, Object> values = new HashMap<>();
        return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("edit")) {
                        Map<String, Object> pending = new HashMap<>();
                        return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                                new Class<?>[]{SharedPreferences.Editor.class}, (editor, editMethod, editArgs) -> {
                                    String editName = editMethod.getName();
                                    if (editName.startsWith("put")) { pending.put((String) editArgs[0], editArgs[1]); return editor; }
                                    if (editName.equals("commit") || editName.equals("apply")) {
                                        values.putAll(pending);
                                        return editName.equals("commit") ? true : null;
                                    }
                                    if (editName.equals("clear")) { values.clear(); return editor; }
                                    throw new UnsupportedOperationException(editName);
                                });
                    }
                    if (name.startsWith("get")) return values.getOrDefault((String) args[0], args[1]);
                    throw new UnsupportedOperationException(name);
                });
    }
}
