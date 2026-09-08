package com.woodpeckerbros.watchreminder.reminder;

import com.woodpeckerbros.watchreminder.*;

import androidx.health.services.client.PassiveListenerService;
import androidx.health.services.client.data.UserActivityInfo;
import androidx.health.services.client.data.UserActivityState;
import com.woodpeckerbros.watchreminder.smartalarm.SmartWakeDetector;
import com.woodpeckerbros.watchreminder.smartalarm.SmartWakeMonitoringService;

public class ReminderHealthPassiveService extends PassiveListenerService {
    @Override
    public void onUserActivityInfoReceived(UserActivityInfo info) {
        boolean asleep = UserActivityState.USER_ACTIVITY_ASLEEP.equals(info.getUserActivityState());
        SmartWakeDetector.UserActivity activity = userActivity(info.getUserActivityState());
        AppLog.d(this, "HealthPassive userActivity asleep=" + asleep + " state=" + info.getUserActivityState()
                + " smartWakeState=" + activity);
        WearStateStore stateStore = new WearStateStore(this);
        stateStore.setUserActivityState(activity.name());
        if (asleep) {
            stateStore.setAsleep(true);
        } else {
            stateStore.markAvailable();
        }
        SmartWakeMonitoringService.updateActivity(this, activity);
        ReminderAlertQueueStore queueStore = new ReminderAlertQueueStore(this);
        if (shouldDispatchAfterUserActivity(asleep, queueStore.hasDeferredAlerts())) {
            AppLog.d(this, "HealthPassive awake with deferred alerts, dispatching");
            DeferredWearRetryReceiver.cancel(this);
            DeferredReminderDispatcher.run(this);
        } else if (!asleep) {
            AppLog.d(this, "HealthPassive awake without deferred alerts, no reschedule needed");
        }
    }

    public static boolean shouldDispatchAfterUserActivity(boolean asleep, boolean hasDeferredAlerts) {
        return !asleep && hasDeferredAlerts;
    }

    private static SmartWakeDetector.UserActivity userActivity(UserActivityState state) {
        if (UserActivityState.USER_ACTIVITY_ASLEEP.equals(state)) return SmartWakeDetector.UserActivity.ASLEEP;
        if (UserActivityState.USER_ACTIVITY_PASSIVE.equals(state)) return SmartWakeDetector.UserActivity.PASSIVE;
        if (UserActivityState.USER_ACTIVITY_EXERCISE.equals(state)) return SmartWakeDetector.UserActivity.EXERCISE;
        return SmartWakeDetector.UserActivity.UNKNOWN;
    }
}
