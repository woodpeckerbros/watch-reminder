package com.woodpeckerbros.watchreminder;

import androidx.health.services.client.PassiveListenerService;
import androidx.health.services.client.data.UserActivityInfo;
import androidx.health.services.client.data.UserActivityState;

public class ReminderHealthPassiveService extends PassiveListenerService {
    @Override
    public void onUserActivityInfoReceived(UserActivityInfo info) {
        boolean asleep = UserActivityState.USER_ACTIVITY_ASLEEP.equals(info.getUserActivityState());
        AppLog.d(this, "HealthPassive userActivity asleep=" + asleep + " state=" + info.getUserActivityState());
        new WearStateStore(this).setAsleep(asleep);
        ReminderAlertQueueStore queueStore = new ReminderAlertQueueStore(this);
        if (shouldDispatchAfterUserActivity(asleep, queueStore.hasDeferredAlerts())) {
            AppLog.d(this, "HealthPassive awake with deferred alerts, dispatching");
            DeferredReminderDispatcher.run(this);
        } else if (!asleep) {
            AppLog.d(this, "HealthPassive awake without deferred alerts, no reschedule needed");
        }
    }

    static boolean shouldDispatchAfterUserActivity(boolean asleep, boolean hasDeferredAlerts) {
        return !asleep && hasDeferredAlerts;
    }
}
