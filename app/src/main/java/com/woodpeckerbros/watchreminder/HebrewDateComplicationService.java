package com.woodpeckerbros.watchreminder;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.RemoteException;

import androidx.wear.watchface.complications.data.ComplicationData;
import androidx.wear.watchface.complications.data.ComplicationType;
import androidx.wear.watchface.complications.data.LongTextComplicationData;
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
        JewishCalendar jewishCalendar = JewishCalendarHelper.calendar(this, halachicDateCalendar());
        HebrewDate date = hebrewDate(jewishCalendar);
        String fullDate = date.day + " " + date.month;
        String description = getString(R.string.complication_hebrew_date) + ": " + fullDate;
        if (type.equals(ComplicationType.SHORT_TEXT)) {
            return new ShortTextComplicationData.Builder(
                    new PlainComplicationText.Builder(date.day).build(),
                    new PlainComplicationText.Builder(description).build()
            )
                    .setTitle(new PlainComplicationText.Builder(date.month).build())
                    .setTapAction(openZmanimIntent())
                    .build();
        }
        if (type.equals(ComplicationType.LONG_TEXT)) {
            return new LongTextComplicationData.Builder(
                    new PlainComplicationText.Builder(fullDate).build(),
                    new PlainComplicationText.Builder(description).build()
            )
                    .setTapAction(openZmanimIntent())
                    .build();
        }
        return new NoDataComplicationData();
    }

    private Calendar halachicDateCalendar() {
        Calendar calendar = Calendar.getInstance();
        long now = System.currentTimeMillis();
        long tzeis = ZmanimHelper.timeForKey(this, ZmanimHelper.KEY_TZAIS, now);
        if (tzeis != Long.MAX_VALUE && now >= tzeis) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        return calendar;
    }

    private HebrewDate hebrewDate(JewishCalendar jewishCalendar) {
        HebrewDateFormatter formatter = JewishCalendarHelper.formatter(this);
        return new HebrewDate(
                formatter.formatHebrewNumber(jewishCalendar.getJewishDayOfMonth()),
                formatter.formatMonth(jewishCalendar)
        );
    }

    private PendingIntent openZmanimIntent() {
        Intent intent = new Intent(this, MainActivity.class)
                .setAction("com.woodpeckerbros.watchreminder.OPEN_ZMANIM_DAY")
                .setData(Uri.parse("watchreminder://zmanim/day"))
                .putExtra(MainActivity.EXTRA_OPEN_ZMANIM_DAY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                this,
                8342,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
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
