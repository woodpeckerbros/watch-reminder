package com.woodpeckerbros.watchreminder;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.os.RemoteException;

import androidx.wear.watchface.complications.data.ComplicationData;
import androidx.wear.watchface.complications.data.ComplicationType;
import androidx.wear.watchface.complications.data.LongTextComplicationData;
import androidx.wear.watchface.complications.data.NoDataComplicationData;
import androidx.wear.watchface.complications.data.PlainComplicationText;
import androidx.wear.watchface.complications.data.MonochromaticImage;
import androidx.wear.watchface.complications.data.ShortTextComplicationData;
import androidx.wear.watchface.complications.data.SmallImage;
import androidx.wear.watchface.complications.data.SmallImageComplicationData;
import androidx.wear.watchface.complications.data.SmallImageType;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService;
import androidx.wear.watchface.complications.datasource.ComplicationRequest;

public class NextReminderComplicationService extends ComplicationDataSourceService {
    @Override
    public void onComplicationRequest(ComplicationRequest request, ComplicationRequestListener listener) {
        try {
            listener.onComplicationData(createData(request.getComplicationType(), false));
        } catch (RemoteException ignored) {
        }
    }

    @Override
    public ComplicationData getPreviewData(ComplicationType type) {
        return createData(type, true);
    }

    private ComplicationData createData(ComplicationType type, boolean preview) {
        NextReminderCalculator.NextReminder next = preview
                ? new NextReminderCalculator.NextReminder("preview", UiText.t(this, "בדיקת תרופות"), previewTime(), false)
                : NextReminderCalculator.next(this, false);
        if (next == null) {
            if (type.equals(ComplicationType.SHORT_TEXT)) {
                return shortText("--:--", UiText.t(this, "אין תזכורות"), UiText.t(this, "אין תזכורות קרובות"));
            }
            if (type.equals(ComplicationType.LONG_TEXT)) {
                return longText(UiText.t(this, "אין תזכורות"), UiText.t(this, "אין תזכורות קרובות"));
            }
            if (type.equals(ComplicationType.SMALL_IMAGE)) {
                return smallImage("--:--", UiText.t(this, "אין תזכורות קרובות"));
            }
            return new NoDataComplicationData();
        }

        String time = NextReminderCalculator.formatTime(next.scheduledAt);
        String title = next.reminderName;
        String description = AppLanguage.isEnglish(this)
                ? "Upcoming reminder " + title + " at " + time
                : "התראה קרובה " + title + " בשעה " + time;
        if (type.equals(ComplicationType.SHORT_TEXT)) {
            return shortText(time, title, description);
        }
        if (type.equals(ComplicationType.LONG_TEXT)) {
            return longText(time + "  " + title, description);
        }
        if (type.equals(ComplicationType.SMALL_IMAGE)) {
            return smallImage(time, description);
        }
        return new NoDataComplicationData();
    }

    private ComplicationData shortText(String text, String title, String description) {
        return new ShortTextComplicationData.Builder(
                new PlainComplicationText.Builder(text).build(),
                new PlainComplicationText.Builder(description).build()
        )
                .setTitle(new PlainComplicationText.Builder(title).build())
                .setMonochromaticImage(clockImage())
                .setTapAction(openAppIntent())
                .build();
    }

    private ComplicationData longText(String text, String description) {
        return new LongTextComplicationData.Builder(
                new PlainComplicationText.Builder(text).build(),
                new PlainComplicationText.Builder(description).build()
        )
                .setMonochromaticImage(clockImage())
                .setTapAction(openAppIntent())
                .build();
    }

    private MonochromaticImage clockImage() {
        return new MonochromaticImage.Builder(
                Icon.createWithResource(this, R.drawable.ic_complication_clock)
        ).build();
    }

    private ComplicationData smallImage(String time, String description) {
        SmallImage image = new SmallImage.Builder(
                Icon.createWithBitmap(clockBitmap(time)),
                SmallImageType.PHOTO
        ).build();
        return new SmallImageComplicationData.Builder(
                image,
                new PlainComplicationText.Builder(description).build()
        )
                .setTapAction(openAppIntent())
                .build();
    }

    private Bitmap clockBitmap(String time) {
        int size = 120;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float center = size / 2f;
        float radius = 54f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.TRANSPARENT);
        canvas.drawColor(Color.TRANSPARENT);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(center, center, radius, paint);

        paint.setStrokeWidth(2.2f);
        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i * 30 - 90);
            float outerX = center + (float) Math.cos(angle) * (radius - 5);
            float outerY = center + (float) Math.sin(angle) * (radius - 5);
            float innerX = center + (float) Math.cos(angle) * (radius - (i % 3 == 0 ? 15 : 11));
            float innerY = center + (float) Math.sin(angle) * (radius - (i % 3 == 0 ? 15 : 11));
            canvas.drawLine(innerX, innerY, outerX, outerY, paint);
        }

        int hour = 0;
        int minute = 0;
        try {
            String[] parts = time.split(":");
            hour = Integer.parseInt(parts[0]) % 12;
            minute = Integer.parseInt(parts[1]);
        } catch (Exception ignored) {
        }
        double minuteAngle = Math.toRadians(minute * 6 - 90);
        double hourAngle = Math.toRadians((hour * 30) + (minute * 0.5) - 90);
        paint.setStrokeWidth(4f);
        canvas.drawLine(center, center, center + (float) Math.cos(hourAngle) * 24, center + (float) Math.sin(hourAngle) * 24, paint);
        paint.setStrokeWidth(3f);
        canvas.drawLine(center, center, center + (float) Math.cos(minuteAngle) * 36, center + (float) Math.sin(minuteAngle) * 36, paint);

        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(center, center, 4f, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(21f);
        paint.setFakeBoldText(true);
        paint.setShadowLayer(1.5f, 0f, 0f, 0xFF000000);
        Rect bounds = new Rect();
        paint.getTextBounds(time, 0, time.length(), bounds);
        canvas.drawText(time, center, center + radius - 13, paint);
        return bitmap;
    }

    private PendingIntent openAppIntent() {
        NextReminderCalculator.NextReminder next = NextReminderCalculator.next(this, false);
        Intent intent = new Intent(this, MainActivity.class)
                .setAction("com.woodpeckerbros.watchreminder.FOCUS_NEXT_REMINDER")
                .putExtra(MainActivity.EXTRA_FOCUS_NEXT_REMINDER, true)
                .putExtra(MainActivity.EXTRA_FOCUS_REMINDER_ID, next == null ? "" : next.reminderId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                this,
                2026,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private long previewTime() {
        return System.currentTimeMillis() + 45 * 60_000L;
    }
}
