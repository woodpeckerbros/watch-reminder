package com.woodpeckerbros.watchreminder.phone;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

class LogStorage {
    static final String MESSAGE_PATH = "/watch_reminder_logs";
    static final String MIME_TYPE = "text/plain";
    private static final String CHANNEL_ID = "watch_reminder_logs";

    private LogStorage() {
    }

    static String save(Context context, byte[] data) throws Exception {
        String fileName = "WatchReminderLog_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date()) + ".txt";
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/WatchReminder/Logs");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        }
        Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IllegalStateException("Could not create log file");
        }
        try (OutputStream output = context.getContentResolver().openOutputStream(uri)) {
            if (output == null) {
                throw new IllegalStateException("Could not open log file");
            }
            output.write(data);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            context.getContentResolver().update(uri, done, null, null);
        }
        notifySaved(context, fileName);
        return fileName;
    }

    static List<LogEntry> listLogs(Context context) {
        ArrayList<LogEntry> logs = new ArrayList<>();
        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_MODIFIED
        };
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?";
        String[] args = new String[]{"WatchReminderLog_%"};
        try (Cursor cursor = context.getContentResolver().query(
                collection,
                projection,
                selection,
                args,
                MediaStore.MediaColumns.DATE_MODIFIED + " DESC"
        )) {
            if (cursor == null) {
                return logs;
            }
            int idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED);
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameIndex);
                if (name == null || !name.startsWith("WatchReminderLog_") || !name.endsWith(".txt")) {
                    continue;
                }
                Uri uri = android.content.ContentUris.withAppendedId(collection, cursor.getLong(idIndex));
                logs.add(new LogEntry(name, uri, cursor.getLong(modifiedIndex) * 1000L));
            }
        } catch (Exception exception) {
            android.util.Log.e("WatchReminderPhone", "Could not query logs", exception);
        }
        return logs;
    }

    static String readLog(Context context, LogEntry entry) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(entry.uri)) {
            if (input == null) {
                throw new IllegalStateException("Could not open log");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString("UTF-8");
        }
    }

    private static void notifySaved(Context context, String fileName) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "WristRemind Logs", NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent intent = new Intent(context, PhoneMainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                1002,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(com.woodpeckerbros.watchreminder.phone.R.drawable.ic_launcher)
                .setContentTitle("WristRemind")
                .setContentText("לוג התקבל: " + fileName)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        try {
            manager.notify(1002, builder.build());
        } catch (SecurityException ignored) {
        }
    }

    static class LogEntry {
        final String name;
        final Uri uri;
        final long modifiedAt;

        LogEntry(String name, Uri uri, long modifiedAt) {
            this.name = name;
            this.uri = uri;
            this.modifiedAt = modifiedAt;
        }
    }
}
