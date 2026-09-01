package com.woodpeckerbros.watchreminder.phone;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class BackupStorage {
    static final String MESSAGE_PATH = "/watch_reminder_backup";
    static final String MIME_TYPE = "application/octet-stream";
    private static final String PREFS_NAME = "backup_receiver";
    private static final String KEY_LAST_FILE = "last_file";
    private static final String KEY_LAST_URI = "last_uri";
    private static final String KEY_LAST_TIME = "last_time";
    private static final String CHANNEL_ID = "backup_receiver";

    private BackupStorage() {
    }

    static String save(Context context, byte[] data) throws Exception {
        String fileName = newBackupFileName();
        return saveNamed(context, data, fileName, true, true);
    }

    static String importBackup(Context context, Uri sourceUri, String displayName) throws Exception {
        String fileName = isBackupName(displayName)
                ? displayName
                : newBackupFileName();
        try (InputStream input = context.getContentResolver().openInputStream(sourceUri)) {
            return saveNamed(context, readAll(input), fileName, false, false);
        }
    }

    private static String saveNamed(Context context, byte[] data, String fileName, boolean updateLocalDocument, boolean updateLastBackup) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Zmanio");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        }
        Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IllegalStateException("Could not create download file");
        }
        try (OutputStream output = context.getContentResolver().openOutputStream(uri)) {
            if (output == null) {
                throw new IllegalStateException("Could not open download file");
            }
            output.write(data);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            context.getContentResolver().update(uri, done, null, null);
        }
        if (updateLastBackup) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_FILE, fileName)
                    .putString(KEY_LAST_URI, uri.toString())
                    .putLong(KEY_LAST_TIME, System.currentTimeMillis())
                    .apply();
        }
        if (updateLocalDocument) {
            LocalReminderDocument.save(context, new String(data, StandardCharsets.UTF_8));
        }
        notifySaved(context, fileName);
        return fileName;
    }

    static String lastStatus(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String file = prefs.getString(KEY_LAST_FILE, "");
        if (file.isEmpty()) {
            BackupEntry latest = latestBackup(context);
            if (latest != null) {
                file = latest.name;
                prefs.edit()
                        .putString(KEY_LAST_FILE, latest.name)
                        .putString(KEY_LAST_URI, latest.uri.toString())
                        .putLong(KEY_LAST_TIME, latest.modifiedAt)
                        .apply();
            }
        }
        if (file.isEmpty()) {
            return PhoneUiText.t(context, "עדיין לא התקבל גיבוי מהשעון.");
        }
        String time = new SimpleDateFormat("dd/MM HH:mm", Locale.US).format(new Date(prefs.getLong(KEY_LAST_TIME, 0)));
        return PhoneUiText.isEnglish(context)
                ? "Received: " + file + "\nSaved in Downloads/Zmanio\n" + time
                : "התקבל: " + file + "\nנשמר ב-Downloads/Zmanio\n" + time;
    }

    static byte[] lastBackup(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String uriText = prefs.getString(KEY_LAST_URI, "");
        if (uriText.isEmpty()) {
            BackupEntry latest = latestBackup(context);
            if (latest != null) {
                uriText = latest.uri.toString();
                prefs.edit()
                        .putString(KEY_LAST_FILE, latest.name)
                        .putString(KEY_LAST_URI, uriText)
                        .putLong(KEY_LAST_TIME, latest.modifiedAt)
                        .apply();
            }
        }
        if (uriText.isEmpty()) {
            throw new IllegalStateException("No backup received yet");
        }
        try (InputStream input = context.getContentResolver().openInputStream(Uri.parse(uriText))) {
            return readAll(input);
        }
    }

    static byte[] readBackup(Context context, BackupEntry entry) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(entry.uri)) {
            return readAll(input);
        }
    }

    static boolean hasLastBackup(Context context) {
        if (!context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LAST_URI, "")
                .isEmpty()) {
            return true;
        }
        return latestBackup(context) != null;
    }

    static List<BackupEntry> listBackups(Context context) {
        ArrayList<BackupEntry> backups = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // Files copied by a file manager (or by an older app version) are not
        // guaranteed to be exposed through MediaStore.Downloads. The general
        // external-files collection includes both those files and current
        // app-created Downloads entries.
        Uri collection = MediaStore.Files.getContentUri("external");
        queryCollection(context, collection, backups, seen);
        // Keep a provider-specific fallback for devices whose Files provider
        // does not expose the Downloads volume to third-party packages.
        if (backups.isEmpty()) {
            queryCollection(context, MediaStore.Downloads.EXTERNAL_CONTENT_URI, backups, seen);
        }
        if (backups.isEmpty()) {
            scanLegacyDownloadFolders(backups, seen);
        }
        return backups;
    }

    private static void scanLegacyDownloadFolders(ArrayList<BackupEntry> backups, Set<String> seen) {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        scanFolder(new File(downloads, "Zmanio"), backups, seen, 2);
        scanFolder(new File(downloads, "WatchReminder"), backups, seen, 2);
    }

    private static void scanFolder(File folder, ArrayList<BackupEntry> backups,
                                   Set<String> seen, int depth) {
        File[] files = folder.listFiles();
        if (files == null || depth < 0) return;
        for (File file : files) {
            if (file.isDirectory()) {
                scanFolder(file, backups, seen, depth - 1);
            } else if (isBackupName(file.getName()) && seen.add(file.getName())) {
                backups.add(new BackupEntry(file.getName(), Uri.fromFile(file), file.lastModified()));
            }
        }
    }

    private static void queryCollection(Context context, Uri collection,
                                         ArrayList<BackupEntry> backups, Set<String> seen) {
        String[] projection = {
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_MODIFIED
        };
        try (Cursor cursor = context.getContentResolver().query(
                collection,
                projection,
                null,
                null,
                MediaStore.MediaColumns.DATE_MODIFIED + " DESC"
        )) {
            if (cursor == null) {
                return;
            }
            int idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED);
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameIndex);
                if (!isBackupName(name)) {
                    continue;
                }
                if (!seen.add(name)) {
                    continue;
                }
                Uri uri = android.content.ContentUris.withAppendedId(collection, cursor.getLong(idIndex));
                backups.add(new BackupEntry(name, uri, cursor.getLong(modifiedIndex) * 1000L));
            }
        } catch (Exception exception) {
            android.util.Log.e("WatchReminderPhone", "Could not query latest backup", exception);
        }
    }

    private static BackupEntry latestBackup(Context context) {
        List<BackupEntry> backups = listBackups(context);
        return backups.isEmpty() ? null : backups.get(0);
    }

    private static String newBackupFileName() {
        return "Zmanio_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date()) + ".zmbu";
    }

    private static boolean isBackupName(String name) {
        return name != null && (
                (name.startsWith("Zmanio_")
                        && (name.endsWith(".zmbu") || name.endsWith(".zmbu.txt")))
                        || (name.startsWith("WatchReminder_")
                        && (name.endsWith(".wrbu") || name.endsWith(".wrbu.txt")))
        );
    }

    private static void notifySaved(Context context, String fileName) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Zmanio Backup", NotificationManager.IMPORTANCE_DEFAULT));
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        Intent intent = new Intent(context, PhoneMainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.setSmallIcon(com.woodpeckerbros.watchreminder.phone.R.drawable.ic_launcher)
                .setContentTitle("Zmanio")
                .setContentText(PhoneUiText.isEnglish(context) ? "Backup saved: " + fileName : "הגיבוי נשמר: " + fileName)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        try {
            manager.notify(1001, builder.build());
        } catch (SecurityException ignored) {
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        if (input == null) {
            throw new IllegalStateException("Could not open backup");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    static class BackupEntry {
        final String name;
        final Uri uri;
        final long modifiedAt;

        BackupEntry(String name, Uri uri, long modifiedAt) {
            this.name = name;
            this.uri = uri;
            this.modifiedAt = modifiedAt;
        }
    }
}
