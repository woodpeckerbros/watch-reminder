package com.woodpeckerbros.watchreminder;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.RemoteException;

import androidx.wear.watchface.complications.data.ComplicationData;
import androidx.wear.watchface.complications.data.ComplicationType;
import androidx.wear.watchface.complications.data.LongTextComplicationData;
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
        TimeRange timeRange = fastingTimeRange();
        if (type.equals(ComplicationType.SHORT_TEXT)) {
            return new ShortTextComplicationData.Builder(
                    new PlainComplicationText.Builder(timeRange.startLine).build(),
                    new PlainComplicationText.Builder(timeRange.full).build()
            )
                    .setTitle(new PlainComplicationText.Builder(timeRange.endLine).build())
                    .setTapAction(openFastingSettingsIntent())
                    .build();
        }
        if (type.equals(ComplicationType.LONG_TEXT)) {
            return new LongTextComplicationData.Builder(
                    new PlainComplicationText.Builder(timeRange.startLine + "\n" + timeRange.endLine).build(),
                    new PlainComplicationText.Builder(timeRange.full).build()
            )
                    .setTapAction(openFastingSettingsIntent())
                    .build();
        }
        return new NoDataComplicationData();
    }

    private TimeRange fastingTimeRange() {
        ReminderSettings settings = new ReminderSettings(this);
        if (!settings.intermittentFastingEnabled()) {
            int startMinutes = settings.fastingStartHour() * 60 + settings.fastingStartMinute();
            int endMinutes = (startMinutes + settings.fastingEatingHours() * 60) % (24 * 60);
            return new TimeRange(formatClock(startMinutes), formatClock(endMinutes));
        }
        IntermittentFastingStore.Window window = new IntermittentFastingStore(this).window();
        return new TimeRange(NextReminderCalculator.formatTime(window.startAt), NextReminderCalculator.formatTime(window.endAt));
    }

    private String formatClock(int minutesOfDay) {
        int normalized = ((minutesOfDay % (24 * 60)) + (24 * 60)) % (24 * 60);
        return String.format(java.util.Locale.US, "%02d:%02d", normalized / 60, normalized % 60);
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

    private static class TimeRange {
        final String start;
        final String end;
        final String startLine;
        final String endLine;
        final String full;

        TimeRange(String start, String end) {
            this.start = start;
            this.end = end;
            this.startLine = "מ-" + start;
            this.endLine = "עד-" + end;
            this.full = startLine + " " + endLine;
        }
    }
}
