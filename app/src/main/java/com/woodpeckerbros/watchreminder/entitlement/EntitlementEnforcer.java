package com.woodpeckerbros.watchreminder.entitlement;

import android.content.Context;

import com.woodpeckerbros.watchreminder.calendar.DafYomiScheduler;
import com.woodpeckerbros.watchreminder.calendar.JewishDayScheduler;
import com.woodpeckerbros.watchreminder.calendar.MoonBlessingScheduler;
import com.woodpeckerbros.watchreminder.calendar.OmerScheduler;
import com.woodpeckerbros.watchreminder.calendar.TekufaScheduler;
import com.woodpeckerbros.watchreminder.reminder.IntermittentFastingScheduler;
import com.woodpeckerbros.watchreminder.reminder.ReminderMonitoringService;
import com.woodpeckerbros.watchreminder.reminder.ReminderScheduler;
import com.woodpeckerbros.watchreminder.reminder.ReminderSettings;
import com.woodpeckerbros.watchreminder.reminder.ReminderStore;
import com.woodpeckerbros.watchreminder.reminder.WaterReminderScheduler;
import com.woodpeckerbros.watchreminder.smartalarm.SmartAlarmScheduler;

/** Applies the one entitlement decision to existing delivery infrastructure. */
public final class EntitlementEnforcer {
    private EntitlementEnforcer() { }

    public static void apply(Context context) {
        Context appContext = context.getApplicationContext();
        if (EntitlementAccess.isFeatureAccessGranted(appContext)) {
            new ReminderStore(appContext).rescheduleAll();
            ReminderScheduler.scheduleWatchdog(appContext);
            SmartAlarmScheduler.reschedule(appContext);
            DafYomiScheduler.schedule(appContext);
            MoonBlessingScheduler.schedule(appContext);
            OmerScheduler.schedule(appContext);
            JewishDayScheduler.schedule(appContext);
            TekufaScheduler.schedule(appContext);
            IntermittentFastingScheduler.schedule(appContext);
            WaterReminderScheduler.schedule(appContext);
            if (new ReminderSettings(appContext).serviceEnabled()) {
                ReminderMonitoringService.start(appContext);
            }
            return;
        }
        disableDeliveries(appContext);
    }

    public static void disableDeliveries(Context context) {
        Context appContext = context.getApplicationContext();
        ReminderScheduler.cancelAllForEntitlement(appContext);
        SmartAlarmScheduler.cancel(appContext);
        DafYomiScheduler.cancel(appContext);
        MoonBlessingScheduler.cancel(appContext);
        OmerScheduler.cancel(appContext);
        JewishDayScheduler.cancel(appContext);
        TekufaScheduler.cancel(appContext);
        IntermittentFastingScheduler.cancel(appContext);
        WaterReminderScheduler.cancel(appContext);
        ReminderMonitoringService.stop(appContext);
    }
}
