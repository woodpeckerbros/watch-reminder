package com.woodpeckerbros.watchreminder.calendar;

import android.content.Context;

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter;
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.woodpeckerbros.watchreminder.zmanim.ZmanimHelper;
import com.woodpeckerbros.watchreminder.zmanim.ZmanimSettings;

import java.util.Calendar;

/** A civil-day fast and the halachic times shown alongside the daily zmanim. */
public final class JewishFastInfo {
    public final String label;
    public final long startsAt;
    public final long endsAt;
    public final long endsAtRabbeinuTam;

    private JewishFastInfo(String label, long startsAt, long endsAt, long endsAtRabbeinuTam) {
        this.label = label;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.endsAtRabbeinuTam = endsAtRabbeinuTam;
    }

    public static JewishFastInfo forDay(Context context, long dayMillis) {
        JewishCalendar calendar = JewishCalendarHelper.calendar(context, calendarFor(context, dayMillis));
        int index = calendar.getYomTovIndex();
        if (!calendar.isTaanis() && !isFastIndex(index)) {
            return null;
        }
        HebrewDateFormatter formatter = JewishCalendarHelper.formatter(context);
        String label = formatter.formatYomTov(calendar);
        if (label == null || label.trim().isEmpty()) {
            label = "צום";
        }
        long startsAt;
        if (startsPreviousEvening(index)) {
            Calendar previousDay = calendarFor(context, dayMillis);
            previousDay.add(Calendar.DAY_OF_YEAR, -1);
            startsAt = ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_SUNSET,
                    previousDay.getTimeInMillis());
        } else {
            startsAt = ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_ALOS, dayMillis);
        }
        return new JewishFastInfo(label, startsAt,
                ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_TZAIS, dayMillis),
                ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_RABBEINU_TAM, dayMillis));
    }

    public static boolean isFastIndex(int index) {
        return index == JewishCalendar.FAST_OF_GEDALYAH
                || index == JewishCalendar.TENTH_OF_TEVES
                || index == JewishCalendar.FAST_OF_ESTHER
                || index == JewishCalendar.SEVENTEEN_OF_TAMMUZ
                || index == JewishCalendar.TISHA_BEAV
                || index == JewishCalendar.YOM_KIPPUR;
    }

    /** Yom Kippur and Tisha B'Av begin at sunset on the preceding civil day. */
    public static boolean startsPreviousEvening(int index) {
        return index == JewishCalendar.TISHA_BEAV || index == JewishCalendar.YOM_KIPPUR;
    }

    private static java.util.Calendar calendarFor(Context context, long dayMillis) {
        java.util.TimeZone timeZone = java.util.TimeZone.getTimeZone(new ZmanimSettings(context).timeZoneId());
        java.util.Calendar day = java.util.Calendar.getInstance(timeZone);
        day.setTimeInMillis(dayMillis);
        day.set(java.util.Calendar.HOUR_OF_DAY, 12);
        day.set(java.util.Calendar.MINUTE, 0);
        day.set(java.util.Calendar.SECOND, 0);
        day.set(java.util.Calendar.MILLISECOND, 0);
        return day;
    }
}
