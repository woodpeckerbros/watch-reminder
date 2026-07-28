package com.woodpeckerbros.watchreminder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import java.util.List;

public final class NotificationChannelMaintenance {
    private static final String REMINDER_ALERTS_ID = "reminder_alerts_no_system_vibration_v2";
    private static final String FASTING_ALERTS_ID = "intermittent_fasting_alerts_no_system_vibration_v2";
    private static final String BACKGROUND_CHECK_ID = "reminder_service";
    private static final String WEAR_WAIT_ID = "deferred_wear_state";

    private NotificationChannelMaintenance() {
    }

    public static void run(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        List<NotificationChannel> channels = manager.getNotificationChannels();
        for (NotificationChannel channel : channels) {
            String id = channel.getId();
            if (isObsolete(id)) {
                manager.deleteNotificationChannel(id);
                continue;
            }
            String name = currentName(context, id);
            if (name != null && !name.contentEquals(channel.getName())) {
                channel.setName(name);
                manager.createNotificationChannel(channel);
            }
        }
    }

    private static boolean isObsolete(String id) {
        return (id.startsWith("reminder_alerts") && !REMINDER_ALERTS_ID.equals(id))
                || (id.startsWith("intermittent_fasting_alerts") && !FASTING_ALERTS_ID.equals(id));
    }

    private static String currentName(Context context, String id) {
        if (REMINDER_ALERTS_ID.equals(id)) {
            return UiText.t(context, "תזכורות רגילות");
        }
        if (FASTING_ALERTS_ID.equals(id)) {
            return UiText.t(context, "צום לסירוגין");
        }
        if (BACKGROUND_CHECK_ID.equals(id)) {
            return UiText.t(context, "בדיקת רקע לתזכורות");
        }
        if (WEAR_WAIT_ID.equals(id)) {
            return UiText.t(context, "המתנה לענידת השעון");
        }
        return null;
    }
}
