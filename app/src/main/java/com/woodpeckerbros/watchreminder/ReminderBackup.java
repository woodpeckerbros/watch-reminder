package com.woodpeckerbros.watchreminder;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReminderBackup {
    private static final int VERSION = 1;
    private static final String EXTENSION = ".wrbu";
    private static final String MIME_TYPE = "application/octet-stream";

    private ReminderBackup() {
    }

    public static String exportText(Context context) {
        try {
            ReminderSettings settings = new ReminderSettings(context);
            ZmanimSettings zmanim = new ZmanimSettings(context);
            JSONArray reminders = new JSONArray();
            for (Reminder reminder : new ReminderStore(context).getAll()) {
                reminders.put(reminder.toJson());
            }
            JSONObject root = new JSONObject()
                    .put("type", "watch-reminder-backup")
                    .put("version", VERSION)
                    .put("exportedAt", System.currentTimeMillis())
                    .put("exportedAtText", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()))
                    .put("reminders", reminders)
                    .put("settings", new JSONObject()
                            .put("serviceEnabled", settings.serviceEnabled())
                            .put("checkIntervalSeconds", settings.checkIntervalSeconds())
                            .put("autoSnoozeDelaySeconds", settings.autoSnoozeDelaySeconds())
                            .put("autoSnoozeMinutes", settings.autoSnoozeMinutes())
                            .put("vibrationStyle", settings.vibrationStyle())
                            .put("vibrationDurationMs", settings.vibrationDurationMs())
                            .put("alertDurationMs", settings.alertDurationMs())
                            .put("vibrationEnabled", settings.vibrationEnabled())
                            .put("alertSoundEnabled", settings.alertSoundEnabled())
                            .put("alertSoundUri", settings.alertSoundUri())
                            .put("alertVolumePercent", settings.alertVolumePercent())
                            .put("quietMinchaMaariv", settings.quietMinchaMaariv())
                            .put("blessingReminderMinutes", settings.blessingReminderMinutes())
                            .put("shemaOnTimeOffsetMinutes", settings.shemaOnTimeOffsetMinutes())
                            .put("moonBlessingEnabled", settings.moonBlessingEnabled())
                            .put("dafYomiEnabled", settings.dafYomiEnabled())
                            .put("dafYomiHour", settings.dafYomiHour())
                            .put("dafYomiMinute", settings.dafYomiMinute())
                            .put("omerEnabled", settings.omerEnabled())
                            .put("omerOffsetMinutes", settings.omerOffsetMinutes())
                            .put("jewishDayRemindersEnabled", settings.jewishDayRemindersEnabled())
                            .put("tekufaRemindersEnabled", settings.tekufaRemindersEnabled())
                            .put("intermittentFastingEnabled", settings.intermittentFastingEnabled())
                            .put("fastingHours", settings.fastingHours())
                            .put("fastingStartHour", settings.fastingStartHour())
                            .put("fastingStartMinute", settings.fastingStartMinute())
                            .put("language", settings.language())
                            .put("jewishMode", settings.jewishMode()))
                    .put("quietTimeRules", new QuietTimeRuleStore(context).toJsonArray())
                    .put("zmanimLocation", new JSONObject()
                            .put("name", zmanim.name())
                            .put("latitude", zmanim.latitude())
                            .put("longitude", zmanim.longitude())
                            .put("elevation", zmanim.elevation())
                            .put("timeZone", zmanim.timeZoneId()));
            return root.toString(2);
        } catch (Exception exception) {
            AppLog.e(context, "backup export failed", exception);
            return "";
        }
    }

    public static void share(Context context) {
        String text = exportText(context);
        if (text.isEmpty()) {
            Toast.makeText(context, UiText.t(context, "לא הצלחתי ליצור גיבוי"), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Watch Reminder backup")
                .putExtra(Intent.EXTRA_TEXT, text);
        try {
            context.startActivity(Intent.createChooser(intent, "ייצוא גיבוי"));
        } catch (Exception exception) {
            AppLog.e(context, "backup share failed", exception);
            Toast.makeText(context, UiText.t(context, "אין אפליקציה זמינה לשליחה"), Toast.LENGTH_SHORT).show();
        }
    }

    public static String suggestedFileName() {
        return "WatchReminder_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date()) + EXTENSION;
    }

    public static File saveToDocuments(Context context) throws Exception {
        File dir = documentsDir(context);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Could not create backup directory");
        }
        File file = new File(dir, suggestedFileName());
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(exportText(context).getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    public static String saveToPublicDocuments(Context context) throws Exception {
        String fileName = suggestedFileName();
        saveToPublicFolder(context, Environment.DIRECTORY_DOCUMENTS, fileName);
        saveToPublicFolder(context, Environment.DIRECTORY_DOWNLOADS, fileName);
        return fileName;
    }

    private static void saveToPublicFolder(Context context, String folder, String fileName) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, folder + "/");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri collection = Environment.DIRECTORY_DOWNLOADS.equals(folder)
                    ? MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    : MediaStore.Files.getContentUri("external");
            Uri uri = context.getContentResolver().insert(collection, values);
            if (uri == null) {
                throw new IllegalStateException("Could not create public backup file");
            }
            writeToUri(context, uri);
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            context.getContentResolver().update(uri, done, null, null);
            scanPublicFile(context, folder, fileName);
            return;
        }
        File dir = Environment.getExternalStoragePublicDirectory(folder);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Could not create public backup directory");
        }
        File file = new File(dir, fileName);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(exportText(context).getBytes(StandardCharsets.UTF_8));
        }
        scanFile(context, file);
    }

    public static List<File> listDocumentBackups(Context context) {
        File dir = documentsDir(context);
        File[] files = dir.listFiles((file, name) -> isBackupFileName(name));
        if (files == null) {
            return new ArrayList<>();
        }
        Arrays.sort(files, (left, right) -> Long.compare(right.lastModified(), left.lastModified()));
        return new ArrayList<>(Arrays.asList(files));
    }

    public static List<BackupEntry> listAllDocumentBackups(Context context) {
        ArrayList<BackupEntry> entries = new ArrayList<>();
        for (File file : listDocumentBackups(context)) {
            entries.add(new BackupEntry(file.getName(), file.lastModified(), file, null));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Uri collection = MediaStore.Files.getContentUri("external");
            String[] projection = {
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATE_MODIFIED
            };
            String selection = MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?";
            String[] args = new String[]{"WatchReminder_%"};
            try (Cursor cursor = context.getContentResolver().query(
                    collection,
                    projection,
                    selection,
                    args,
                    MediaStore.MediaColumns.DATE_MODIFIED + " DESC"
            )) {
                if (cursor != null) {
                    int idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                    int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
                    int modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED);
                    while (cursor.moveToNext()) {
                        String name = cursor.getString(nameIndex);
                        if (name == null || !isBackupFileName(name)) {
                            continue;
                        }
                        Uri uri = ContentUris.withAppendedId(collection, cursor.getLong(idIndex));
                        entries.add(new BackupEntry(name, cursor.getLong(modifiedIndex) * 1000L, null, uri));
                    }
                }
            } catch (Exception exception) {
                AppLog.e(context, "backup list public documents failed", exception);
            }
        } else {
            addPublicFolderEntries(entries, Environment.DIRECTORY_DOCUMENTS);
            addPublicFolderEntries(entries, Environment.DIRECTORY_DOWNLOADS);
        }
        entries.sort((left, right) -> Long.compare(right.modifiedAt, left.modifiedAt));
        return entries;
    }

    public static int importFile(Context context, File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) {
            return importText(context, readAll(input));
        }
    }

    public static int importEntry(Context context, BackupEntry entry) throws Exception {
        if (entry.uri != null) {
            return importUri(context, entry.uri);
        }
        return importFile(context, entry.file);
    }

    public static void writeToUri(Context context, Uri uri) throws Exception {
        try (OutputStream output = context.getContentResolver().openOutputStream(uri)) {
            if (output == null) {
                throw new IllegalStateException("Could not open backup output");
            }
            output.write(exportText(context).getBytes(StandardCharsets.UTF_8));
        }
    }

    public static int importUri(Context context, Uri uri) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IllegalStateException("Could not open backup input");
            }
            return importText(context, readAll(input));
        }
    }

    public static int importText(Context context, String text) throws Exception {
        JSONObject root = new JSONObject(text.trim());
        if (!"watch-reminder-backup".equals(root.optString("type"))) {
            throw new IllegalArgumentException("not a Watch Reminder backup");
        }
        JSONArray array = root.getJSONArray("reminders");
        List<Reminder> reminders = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            reminders.add(Reminder.fromJson(array.getJSONObject(i)));
        }
        JSONObject settingsJson = root.optJSONObject("settings");
        if (settingsJson != null) {
            restoreSettings(context, settingsJson);
        }
        JSONArray quietRules = root.optJSONArray("quietTimeRules");
        if (quietRules != null) {
            new QuietTimeRuleStore(context).replaceAll(quietRules);
        } else if (new ReminderSettings(context).quietMinchaMaariv()) {
            new QuietTimeRuleStore(context).ensureDefaultMinchaMaarivRule();
        }
        JSONObject locationJson = root.optJSONObject("zmanimLocation");
        if (locationJson != null) {
            new ZmanimSettings(context).update(
                    locationJson.optString("name", ZmanimSettings.DEFAULT_NAME),
                    locationJson.optDouble("latitude", ZmanimSettings.DEFAULT_LATITUDE),
                    locationJson.optDouble("longitude", ZmanimSettings.DEFAULT_LONGITUDE),
                    locationJson.optDouble("elevation", ZmanimSettings.DEFAULT_ELEVATION),
                    locationJson.optString("timeZone", ZmanimSettings.DEFAULT_TIME_ZONE)
            );
        }
        new ReminderStore(context).replaceAll(reminders);
        ReminderScheduler.scheduleWatchdog(context);
        if (new ReminderSettings(context).serviceEnabled()) {
            ReminderForegroundService.start(context);
        } else {
            ReminderForegroundService.stop(context);
        }
        ComplicationRefresh.request(context);
        return reminders.size();
    }

    private static void restoreSettings(Context context, JSONObject json) {
        ReminderSettings settings = new ReminderSettings(context);
        settings.setServiceEnabled(json.optBoolean("serviceEnabled", settings.serviceEnabled()));
        settings.setCheckIntervalSeconds(json.optInt("checkIntervalSeconds", settings.checkIntervalSeconds()));
        settings.setAutoSnoozeDelaySeconds(json.optInt("autoSnoozeDelaySeconds", settings.autoSnoozeDelaySeconds()));
        settings.setAutoSnoozeMinutes(json.optInt("autoSnoozeMinutes", settings.autoSnoozeMinutes()));
        settings.setVibrationStyle(json.optString("vibrationStyle", settings.vibrationStyle()));
        settings.setAlertDurationMs(json.optInt("alertDurationMs", json.optInt("vibrationDurationMs", settings.alertDurationMs())));
        settings.setVibrationEnabled(json.optBoolean("vibrationEnabled", settings.vibrationEnabled()));
        settings.setAlertSoundEnabled(json.optBoolean("alertSoundEnabled", settings.alertSoundEnabled()));
        settings.setAlertSoundUri(json.optString("alertSoundUri", settings.alertSoundUri()));
        settings.setAlertVolumePercent(json.optInt("alertVolumePercent", settings.alertVolumePercent()));
        settings.setQuietMinchaMaariv(json.optBoolean("quietMinchaMaariv", settings.quietMinchaMaariv()));
        settings.setBlessingReminderMinutes(json.optInt("blessingReminderMinutes", settings.blessingReminderMinutes()));
        settings.setShemaOnTimeOffsetMinutes(json.optInt("shemaOnTimeOffsetMinutes", settings.shemaOnTimeOffsetMinutes()));
        settings.setMoonBlessingEnabled(json.optBoolean("moonBlessingEnabled", settings.moonBlessingEnabled()));
        settings.setDafYomiEnabled(json.optBoolean("dafYomiEnabled", settings.dafYomiEnabled()));
        settings.setDafYomiTime(
                json.optInt("dafYomiHour", settings.dafYomiHour()),
                json.optInt("dafYomiMinute", settings.dafYomiMinute())
        );
        settings.setOmerEnabled(json.optBoolean("omerEnabled", settings.omerEnabled()));
        settings.setOmerOffsetMinutes(json.optInt("omerOffsetMinutes", settings.omerOffsetMinutes()));
        settings.setJewishDayRemindersEnabled(json.optBoolean("jewishDayRemindersEnabled", settings.jewishDayRemindersEnabled()));
        settings.setTekufaRemindersEnabled(json.optBoolean("tekufaRemindersEnabled", settings.tekufaRemindersEnabled()));
        settings.setIntermittentFastingEnabled(json.optBoolean("intermittentFastingEnabled", settings.intermittentFastingEnabled()));
        settings.setFastingHours(json.optInt("fastingHours", settings.fastingHours()));
        settings.setFastingStartTime(
                json.optInt("fastingStartHour", settings.fastingStartHour()),
                json.optInt("fastingStartMinute", settings.fastingStartMinute())
        );
        settings.setLanguage(json.optString("language", settings.language()));
        settings.setJewishMode(json.optBoolean("jewishMode", settings.jewishMode()));
        MoonBlessingScheduler.schedule(context);
        DafYomiScheduler.schedule(context);
        OmerScheduler.schedule(context);
        JewishDayScheduler.schedule(context);
        TekufaScheduler.schedule(context);
        IntermittentFastingScheduler.schedule(context);
    }

    private static File documentsDir(Context context) {
        File external = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        return external == null ? new File(context.getFilesDir(), "Documents") : external;
    }

    private static void addPublicFolderEntries(ArrayList<BackupEntry> entries, String folder) {
        File dir = Environment.getExternalStoragePublicDirectory(folder);
        File[] files = dir.listFiles((file, name) -> isBackupFileName(name));
        if (files != null) {
            for (File file : files) {
                entries.add(new BackupEntry(file.getName(), file.lastModified(), file, null));
            }
        }
    }

    private static void scanPublicFile(Context context, String folder, String fileName) {
        scanFile(context, new File(Environment.getExternalStoragePublicDirectory(folder), fileName));
    }

    private static void scanFile(Context context, File file) {
        try {
            MediaScannerConnection.scanFile(
                    context.getApplicationContext(),
                    new String[]{file.getAbsolutePath()},
                    new String[]{MIME_TYPE},
                    null
            );
        } catch (Exception exception) {
            AppLog.e(context, "backup media scan failed", exception);
        }
    }

    private static String readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toString("UTF-8");
    }

    private static boolean isBackupFileName(String name) {
        return name != null
                && name.startsWith("WatchReminder_")
                && (name.endsWith(".wrbu")
                || name.endsWith(".wrbu.txt"));
    }

    public static class BackupEntry {
        public final String name;
        public final long modifiedAt;
        final File file;
        final Uri uri;

        BackupEntry(String name, long modifiedAt, File file, Uri uri) {
            this.name = name;
            this.modifiedAt = modifiedAt;
            this.file = file;
            this.uri = uri;
        }
    }
}
