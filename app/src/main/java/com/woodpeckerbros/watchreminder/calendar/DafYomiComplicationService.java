package com.woodpeckerbros.watchreminder.calendar;

import com.woodpeckerbros.watchreminder.ComplicationRefresh;
import com.woodpeckerbros.watchreminder.JewishModeComplicationConfigActivity;
import com.woodpeckerbros.watchreminder.MainActivity;
import com.woodpeckerbros.watchreminder.R;
import com.woodpeckerbros.watchreminder.UiText;
import com.woodpeckerbros.watchreminder.reminder.ReminderSettings;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.RemoteException;

import androidx.wear.watchface.complications.data.ComplicationData;
import androidx.wear.watchface.complications.data.ComplicationType;
import androidx.wear.watchface.complications.data.LongTextComplicationData;
import androidx.wear.watchface.complications.data.MonochromaticImage;
import androidx.wear.watchface.complications.data.NoDataComplicationData;
import androidx.wear.watchface.complications.data.PlainComplicationText;
import androidx.wear.watchface.complications.data.ShortTextComplicationData;
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService;
import androidx.wear.watchface.complications.datasource.ComplicationRequest;
import androidx.wear.watchface.complications.datasource.TimeInterval;
import androidx.wear.watchface.complications.datasource.TimelineEntry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Daily Daf Yomi complication; the watch face changes it at local midnight. */
public class DafYomiComplicationService extends ComplicationDataSourceService {
    @Override
    public void onComplicationActivated(int complicationInstanceId, ComplicationType type) {
        ComplicationRefresh.requestActivated(this, DafYomiComplicationService.class,
                complicationInstanceId);
    }

    @Override
    public void onComplicationRequest(ComplicationRequest request,
                                      ComplicationRequestListener listener) {
        ComplicationRefresh.logDataRequest(this, DafYomiComplicationService.class,
                request.getComplicationInstanceId(), request.getComplicationType());
        try {
            listener.onComplicationDataTimeline(createTimeline(request.getComplicationType()));
        } catch (RemoteException ignored) {
        }
    }

    @Override
    public ComplicationData getPreviewData(ComplicationType type) {
        return createData(type, System.currentTimeMillis());
    }

    private ComplicationDataTimeline createTimeline(ComplicationType type) {
        long now = System.currentTimeMillis();
        if (!new ReminderSettings(this).jewishMode() || !supports(type)) {
            return new ComplicationDataTimeline(createData(type, now), new ArrayList<>());
        }
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        ComplicationData current = createData(type, now);
        ArrayList<TimelineEntry> entries = new ArrayList<>();
        for (int day = 0; day < 4; day++) {
            Calendar end = (Calendar) midnight.clone();
            end.add(Calendar.DAY_OF_YEAR, 1);
            long startAt = Math.max(now, midnight.getTimeInMillis());
            long endAt = end.getTimeInMillis();
            if (endAt > startAt) {
                entries.add(new TimelineEntry(
                        new TimeInterval(Instant.ofEpochMilli(startAt), Instant.ofEpochMilli(endAt)),
                        createData(type, midnight.getTimeInMillis())
                ));
            }
            midnight = end;
        }
        return new ComplicationDataTimeline(current, entries);
    }

    private ComplicationData createData(ComplicationType type, long dateMillis) {
        if (!new ReminderSettings(this).jewishMode()) {
            return jewishModeOffData(type);
        }
        if (!supports(type)) {
            return new NoDataComplicationData();
        }
        String masechta = DafYomiHelper.bavliMasechtaLabel(this, dateMillis);
        String daf = DafYomiHelper.bavliDafLabel(this, dateMillis);
        String description = UiText.t(this, "דף היומי") + ": " + masechta + " " + daf;
        if (type.equals(ComplicationType.SHORT_TEXT)) {
            return new ShortTextComplicationData.Builder(
                    new PlainComplicationText.Builder(daf).build(),
                    new PlainComplicationText.Builder(description).build()
            ).setTitle(new PlainComplicationText.Builder(masechta).build())
                    .setMonochromaticImage(image())
                    .setTapAction(openDafYomiIntent())
                    .build();
        }
        return new LongTextComplicationData.Builder(
                new PlainComplicationText.Builder(masechta + "\n" + daf).build(),
                new PlainComplicationText.Builder(description).build()
        ).setTitle(new PlainComplicationText.Builder(UiText.t(this, "דף היומי")).build())
                .setMonochromaticImage(image())
                .setTapAction(openDafYomiIntent())
                .build();
    }

    private boolean supports(ComplicationType type) {
        return type.equals(ComplicationType.SHORT_TEXT) || type.equals(ComplicationType.LONG_TEXT);
    }

    private ComplicationData jewishModeOffData(ComplicationType type) {
        String title = getString(R.string.ui_jewish_mode);
        String text = getString(R.string.ui_enable_mode);
        String description = getString(R.string.ui_enable_jewish_mode_hebrew_date);
        if (type.equals(ComplicationType.SHORT_TEXT)) {
            return new ShortTextComplicationData.Builder(
                    new PlainComplicationText.Builder(text).build(),
                    new PlainComplicationText.Builder(description).build()
            ).setTitle(new PlainComplicationText.Builder(title).build())
                    .setTapAction(openJewishModeIntent()).build();
        }
        if (type.equals(ComplicationType.LONG_TEXT)) {
            return new LongTextComplicationData.Builder(
                    new PlainComplicationText.Builder(title + ": " + text).build(),
                    new PlainComplicationText.Builder(description).build()
            ).setTapAction(openJewishModeIntent()).build();
        }
        return new NoDataComplicationData();
    }

    private MonochromaticImage image() {
        return new MonochromaticImage.Builder(
                Icon.createWithResource(this, R.drawable.ic_complication_clock)).build();
    }

    private PendingIntent openDafYomiIntent() {
        Intent intent = new Intent(this, MainActivity.class)
                .setAction("com.woodpeckerbros.watchreminder.OPEN_DAF_YOMI")
                .setData(Uri.parse("watchreminder://daf-yomi"))
                .putExtra(MainActivity.EXTRA_OPEN_DAF_YOMI, true)
                .putExtra(MainActivity.EXTRA_FROM_COMPLICATION, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 8344, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent openJewishModeIntent() {
        return PendingIntent.getActivity(this, 8345,
                JewishModeComplicationConfigActivity.createComplicationIntent(this),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
