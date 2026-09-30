package com.woodpeckerbros.watchreminder.entitlement;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import com.woodpeckerbros.watchreminder.AppLanguage;
import com.woodpeckerbros.watchreminder.AppLog;
import com.woodpeckerbros.watchreminder.MainActivity;
import com.woodpeckerbros.watchreminder.R;
import com.woodpeckerbros.watchreminder.reminder.ReminderScheduler;

/** One quiet, actionable notice 24 hours before the trial stops delivering alarms. */
public final class TrialExpiryWarningReceiver extends BroadcastReceiver {
    private static final String PREFS = "trial_expiry_warning";
    private static final String KEY_NOTIFIED_START = "notified_trial_start";
    private static final String CHANNEL = "trial_expiry_warning_v1";
    private static final int NOTIFICATION_ID = 74710;

    public static void schedule(Context context) {
        Context app = context.getApplicationContext();
        TrialPolicy.Snapshot trial = new EntitlementStore(app).snapshot();
        AlarmManager alarms = app.getSystemService(AlarmManager.class);
        if (alarms == null) return;
        PendingIntent pending = alarmIntent(app);
        alarms.cancel(pending);
        if (trial.lifetimePurchased || !trial.featureAccessGranted) {
            NotificationManager manager = app.getSystemService(NotificationManager.class);
            if (manager != null) manager.cancel(NOTIFICATION_ID);
            return;
        }
        if (alreadyNotified(app, trial.trialStartedAt)) return;
        long warningAt = TrialPolicy.expiryWarningAt(trial);
        if (warningAt <= System.currentTimeMillis()) {
            notifyIfDue(app);
            return;
        }
        try {
            if (ReminderScheduler.canScheduleExactAlarms(app)) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, warningAt, pending);
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, warningAt, pending);
            }
        } catch (SecurityException error) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, warningAt, pending);
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        notifyIfDue(context.getApplicationContext());
    }

    private static void notifyIfDue(Context context) {
        TrialPolicy.Snapshot trial = new EntitlementStore(context).snapshot();
        if (!TrialPolicy.inExpiryWarningWindow(trial)
                || alreadyNotified(context, trial.trialStartedAt)) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || !manager.areNotificationsEnabled()) return;
        boolean english = AppLanguage.isEnglish(context);
        String title = english ? "Your Zmanio trial ends soon"
                : "תקופת הניסיון של Zmanio מסתיימת בקרוב";
        String message = english
                ? "In less than 24 hours, reminders and Smart Alarm will stop. Unlock Zmanio with a one-time purchase to keep them running."
                : "בתוך פחות מ־24 שעות התזכורות והשעון המעורר החכם יפסיקו לפעול. רכישה חד־פעמית תאפשר לך להמשיך לקבל אותן בזמן.";
        String action = english ? "View access" : "פתיחת רישיון";
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                english ? "Trial ending" : "סיום תקופת הניסיון", NotificationManager.IMPORTANCE_DEFAULT);
        manager.createNotificationChannel(channel);
        Notification notification = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setColor(0xFFC77B58)
                .setAutoCancel(true)
                .setContentIntent(openAccessIntent(context))
                .addAction(new Notification.Action.Builder(null, action, openAccessIntent(context)).build())
                .build();
        manager.notify(NOTIFICATION_ID, notification);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_NOTIFIED_START, trial.trialStartedAt).commit();
        AppLog.d(context, "Trial expiry warning shown trialStart=" + trial.trialStartedAt);
    }

    private static boolean alreadyNotified(Context context, long trialStart) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_NOTIFIED_START, 0L) == trialStart;
    }

    private static PendingIntent alarmIntent(Context context) {
        return PendingIntent.getBroadcast(context, NOTIFICATION_ID,
                new Intent(context, TrialExpiryWarningReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent openAccessIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class)
                .setAction("com.woodpeckerbros.watchreminder.OPEN_ENTITLEMENT")
                .putExtra(MainActivity.EXTRA_OPEN_ENTITLEMENT, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
