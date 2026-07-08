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
        ReminderSettings settings = new ReminderSettings(this);
        String shortText = settings.intermittentFastingEnabled() ? settings.fastingHours() + "/" + settings.fastingEatingHours() : "כבוי";
        String title = "צום";
        String description = fastingDescription(settings);
        if (type.equals(ComplicationType.SHORT_TEXT)) {
            return new ShortTextComplicationData.Builder(
                    new PlainComplicationText.Builder(shortText).build(),
                    new PlainComplicationText.Builder(description).build()
            )
                    .setTitle(new PlainComplicationText.Builder(title).build())
                    .setMonochromaticImage(image())
                    .setTapAction(openFastingSettingsIntent())
                    .build();
        }
        if (type.equals(ComplicationType.LONG_TEXT)) {
            return new LongTextComplicationData.Builder(
                    new PlainComplicationText.Builder(description).build(),
                    new PlainComplicationText.Builder(description).build()
            )
                    .setTitle(new PlainComplicationText.Builder("צום לסירוגין").build())
                    .setMonochromaticImage(image())
                    .setTapAction(openFastingSettingsIntent())
                    .build();
        }
        return new NoDataComplicationData();
    }

    private String fastingDescription(ReminderSettings settings) {
        if (!settings.intermittentFastingEnabled()) {
            return "צום לסירוגין כבוי";
        }
        IntermittentFastingStore.Window window = new IntermittentFastingStore(this).window();
        long now = System.currentTimeMillis();
        if (window.eatingOpen(now)) {
            return "אכילה עד " + NextReminderCalculator.formatTime(window.endAt);
        }
        if (window.finished) {
            return "הבא " + NextReminderCalculator.formatTime(window.nextStartAt);
        }
        return "אכילה ב-" + NextReminderCalculator.formatTime(window.startAt);
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
}
