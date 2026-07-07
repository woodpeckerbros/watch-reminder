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

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter;
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;

import java.util.Calendar;

public class HebrewDateComplicationService extends ComplicationDataSourceService {
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
        JewishCalendar jewishCalendar = JewishCalendarHelper.calendar(this, Calendar.getInstance());
        HebrewDate date = hebrewDate(jewishCalendar);
        String description = getString(R.string.complication_hebrew_date) + ": " + date.day + " " + date.month;
        if (type.equals(ComplicationType.SHORT_TEXT)) {
            return new ShortTextComplicationData.Builder(
                    new PlainComplicationText.Builder(date.day).build(),
                    new PlainComplicationText.Builder(description).build()
            )
                    .setTitle(new PlainComplicationText.Builder(date.month).build())
                    .setMonochromaticImage(image())
                    .setTapAction(openZmanimIntent())
                    .build();
        }
        if (type.equals(ComplicationType.LONG_TEXT)) {
            return new LongTextComplicationData.Builder(
                    new PlainComplicationText.Builder(date.day).build(),
                    new PlainComplicationText.Builder(description).build()
            )
                    .setTitle(new PlainComplicationText.Builder(date.month).build())
                    .setMonochromaticImage(image())
                    .setTapAction(openZmanimIntent())
                    .build();
        }
        return new NoDataComplicationData();
    }

    private HebrewDate hebrewDate(JewishCalendar jewishCalendar) {
        HebrewDateFormatter formatter = JewishCalendarHelper.formatter(this);
        return new HebrewDate(
                formatter.formatHebrewNumber(jewishCalendar.getJewishDayOfMonth()),
                formatter.formatMonth(jewishCalendar)
        );
    }

    private MonochromaticImage image() {
        return new MonochromaticImage.Builder(
                Icon.createWithResource(this, R.drawable.ic_complication_clock)
        ).build();
    }

    private PendingIntent openZmanimIntent() {
        Intent intent = new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_OPEN_ZMANIM_DAY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                this,
                8342,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static class HebrewDate {
        final String day;
        final String month;

        HebrewDate(String day, String month) {
            this.day = day;
            this.month = month;
        }
    }
}
