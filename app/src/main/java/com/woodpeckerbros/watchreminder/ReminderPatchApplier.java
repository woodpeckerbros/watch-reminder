package com.woodpeckerbros.watchreminder;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

public class ReminderPatchApplier {
    private ReminderPatchApplier() {
    }

    public static int apply(Context context, String text) throws Exception {
        JSONObject root = new JSONObject(text);
        if (!"watch-reminder-patch".equals(root.optString("type"))) {
            throw new IllegalArgumentException("not a Watch Reminder patch");
        }
        JSONArray operations = root.optJSONArray("operations");
        if (operations == null) {
            return 0;
        }
        ReminderStore store = new ReminderStore(context);
        int applied = 0;
        for (int i = 0; i < operations.length(); i++) {
            JSONObject operation = operations.optJSONObject(i);
            if (operation == null) {
                continue;
            }
            String action = operation.optString("action");
            if ("upsertReminder".equals(action)) {
                JSONObject reminderJson = operation.optJSONObject("reminder");
                if (reminderJson != null) {
                    store.upsert(Reminder.fromJson(reminderJson));
                    applied++;
                }
            } else if ("deleteReminder".equals(action)) {
                String reminderId = operation.optString("reminderId");
                Reminder reminder = store.find(reminderId);
                if (reminder != null) {
                    store.delete(reminder);
                    applied++;
                }
            } else if ("updateSettings".equals(action)) {
                applySettings(context, operation);
                applied++;
            }
        }
        ReminderScheduler.scheduleWatchdog(context);
        ComplicationRefresh.request(context);
        return applied;
    }

    private static void applySettings(Context context, JSONObject operation) {
        ReminderSettings settings = new ReminderSettings(context);
        JSONObject values = operation.optJSONObject("settings");
        if (values != null) {
            if (values.has("serviceEnabled")) settings.setServiceEnabled(values.optBoolean("serviceEnabled", settings.serviceEnabled()));
            if (values.has("checkIntervalSeconds")) settings.setCheckIntervalSeconds(values.optInt("checkIntervalSeconds", settings.checkIntervalSeconds()));
            if (values.has("autoSnoozeDelaySeconds")) settings.setAutoSnoozeDelaySeconds(values.optInt("autoSnoozeDelaySeconds", settings.autoSnoozeDelaySeconds()));
            if (values.has("autoSnoozeMinutes")) settings.setAutoSnoozeMinutes(values.optInt("autoSnoozeMinutes", settings.autoSnoozeMinutes()));
            if (values.has("vibrationStyle")) settings.setVibrationStyle(values.optString("vibrationStyle", settings.vibrationStyle()));
            if (values.has("vibrationDurationMs")) settings.setAlertDurationMs(values.optInt("vibrationDurationMs", settings.alertDurationMs()));
            if (values.has("alertDurationMs")) settings.setAlertDurationMs(values.optInt("alertDurationMs", settings.alertDurationMs()));
            if (values.has("vibrationEnabled")) settings.setVibrationEnabled(values.optBoolean("vibrationEnabled", settings.vibrationEnabled()));
            if (values.has("alertSoundEnabled")) settings.setAlertSoundEnabled(values.optBoolean("alertSoundEnabled", settings.alertSoundEnabled()));
            if (values.has("alertSoundUri")) settings.setAlertSoundUri(values.optString("alertSoundUri", settings.alertSoundUri()));
            if (values.has("alertVolumePercent")) settings.setAlertVolumePercent(values.optInt("alertVolumePercent", settings.alertVolumePercent()));
            if (values.has("quietMinchaMaariv")) settings.setQuietMinchaMaariv(values.optBoolean("quietMinchaMaariv", settings.quietMinchaMaariv()));
            if (values.has("blessingReminderMinutes")) settings.setBlessingReminderMinutes(values.optInt("blessingReminderMinutes", settings.blessingReminderMinutes()));
            if (values.has("moonBlessingEnabled")) settings.setMoonBlessingEnabled(values.optBoolean("moonBlessingEnabled", settings.moonBlessingEnabled()));
            if (values.has("dafYomiEnabled")) settings.setDafYomiEnabled(values.optBoolean("dafYomiEnabled", settings.dafYomiEnabled()));
            if (values.has("dafYomiHour") || values.has("dafYomiMinute")) {
                settings.setDafYomiTime(
                        values.optInt("dafYomiHour", settings.dafYomiHour()),
                        values.optInt("dafYomiMinute", settings.dafYomiMinute())
                );
            }
            if (values.has("omerEnabled")) settings.setOmerEnabled(values.optBoolean("omerEnabled", settings.omerEnabled()));
            if (values.has("omerOffsetMinutes")) settings.setOmerOffsetMinutes(values.optInt("omerOffsetMinutes", settings.omerOffsetMinutes()));
            if (values.has("jewishDayRemindersEnabled")) settings.setJewishDayRemindersEnabled(values.optBoolean("jewishDayRemindersEnabled", settings.jewishDayRemindersEnabled()));
            if (values.has("language")) settings.setLanguage(values.optString("language", settings.language()));
            if (values.has("jewishMode")) settings.setJewishMode(values.optBoolean("jewishMode", settings.jewishMode()));
            MoonBlessingScheduler.schedule(context);
            DafYomiScheduler.schedule(context);
            OmerScheduler.schedule(context);
            JewishDayScheduler.schedule(context);
        }
        JSONArray quietRules = operation.optJSONArray("quietTimeRules");
        if (quietRules != null) {
            new QuietTimeRuleStore(context).replaceAll(quietRules);
        }
        JSONObject location = operation.optJSONObject("zmanimLocation");
        if (location != null) {
            new ZmanimSettings(context).update(
                    location.optString("name", ZmanimSettings.DEFAULT_NAME),
                    location.optDouble("latitude", ZmanimSettings.DEFAULT_LATITUDE),
                    location.optDouble("longitude", ZmanimSettings.DEFAULT_LONGITUDE),
                    location.optDouble("elevation", ZmanimSettings.DEFAULT_ELEVATION),
                    location.optString("timeZone", ZmanimSettings.DEFAULT_TIME_ZONE)
            );
        }
        if (settings.serviceEnabled()) {
            ReminderForegroundService.start(context);
        } else {
            ReminderForegroundService.stop(context);
        }
        DafYomiScheduler.schedule(context);
        OmerScheduler.schedule(context);
    }
}
