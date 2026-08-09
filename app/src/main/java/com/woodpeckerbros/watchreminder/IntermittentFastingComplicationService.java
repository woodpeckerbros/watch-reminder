package com.woodpeckerbros.watchreminder;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.RemoteException;

import androidx.wear.watchface.complications.data.ComplicationData;
import androidx.wear.watchface.complications.data.ComplicationType;
import androidx.wear.watchface.complications.data.LongTextComplicationData;
import androidx.wear.watchface.complications.data.MonochromaticImage;
import androidx.wear.watchface.complications.data.NoDataComplicationData;
import androidx.wear.watchface.complications.data.PlainComplicationText;
import androidx.wear.watchface.complications.data.ShortTextComplicationData;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService;
import androidx.wear.watchface.complications.datasource.ComplicationRequest;

public class IntermittentFastingComplicationService extends ComplicationDataSourceService {
    @Override
    public void onComplicationRequest(ComplicationRequest request, ComplicationRequestListener listener) {
        try {
            listener.onComplicationData(createData(request.getComplicationType()));
        } catch (RemoteException ignored) {
        }
    }

    @Override
    public ComplicationData getPreviewData(ComplicationType type) {
        return createData(type);
    }

    private ComplicationData createData(ComplicationType type) {
        FastingComplicationText text = fastingText();
        if (type.equals(ComplicationType.SHORT_TEXT)) {
            return new ShortTextComplicationData.Builder(
                    new PlainComplicationText.Builder(text.time).build(),
                    new PlainComplicationText.Builder(text.full).build()
            )
                    .setTitle(new PlainComplicationText.Builder(text.label).build())
                    .setMonochromaticImage(image())
                    .setTapAction(openFastingSettingsIntent())
                    .build();
        }
        if (type.equals(ComplicationType.LONG_TEXT)) {
            return new LongTextComplicationData.Builder(
                    new PlainComplicationText.Builder(text.time).build(),
                    new PlainComplicationText.Builder(text.full).build()
            )
                    .setTitle(new PlainComplicationText.Builder(text.label).build())
                    .setTapAction(openFastingSettingsIntent())
                    .build();
        }
        return new NoDataComplicationData();
    }

    private FastingComplicationText fastingText() {
        ReminderSettings settings = new ReminderSettings(this);
        if (!settings.intermittentFastingEnabled()) {
            int startMinutes = settings.fastingStartHour() * 60 + settings.fastingStartMinute();
            return new FastingComplicationText(UiText.t(this, "הצום נגמר ב:"), formatClock(startMinutes));
        }
        long now = System.currentTimeMillis();
        IntermittentFastingStore.Window window = new IntermittentFastingStore(this).window();
        if (window.eatingOpen(now)) {
            return new FastingComplicationText(UiText.t(this, "הצום מתחיל ב:"), NextReminderCalculator.formatTime(window.endAt));
        }
        long startsAt = window.finished ? window.nextStartAt : window.startAt;
        return new FastingComplicationText(UiText.t(this, "הצום נגמר ב:"), NextReminderCalculator.formatTime(startsAt));
    }

    private String formatClock(int minutesOfDay) {
        int normalized = ((minutesOfDay % (24 * 60)) + (24 * 60)) % (24 * 60);
        return String.format(java.util.Locale.US, "%02d:%02d", normalized / 60, normalized % 60);
    }

    private MonochromaticImage image() {
        return new MonochromaticImage.Builder(
                Icon.createWithResource(this, R.drawable.ic_complication_clock)
        ).build();
    }

    private PendingIntent openFastingSettingsIntent() {
        Intent intent = new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_OPEN_FASTING_SETTINGS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                this,
                8342,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static class FastingComplicationText {
        final String label;
        final String time;
        final String full;

        FastingComplicationText(String label, String time) {
            this.label = label;
            this.time = time;
            this.full = label + " " + time;
        }
    }
}
