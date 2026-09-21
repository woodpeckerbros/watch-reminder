package com.woodpeckerbros.watchreminder.calendar;

import android.content.Context;

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter;
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.woodpeckerbros.watchreminder.zmanim.ZmanimHelper;
import com.woodpeckerbros.watchreminder.zmanim.ZmanimSettings;
import com.woodpeckerbros.watchreminder.reminder.ReminderScheduler;

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
            startsAt = floorRawTime(context, startTimeKey(index),
                    previousDay.getTimeInMillis());
        } else {
            startsAt = floorRawTime(context, startTimeKey(index), dayMillis);
        }
        return new JewishFastInfo(label, startsAt,
                ZmanimHelper.timeForKey(context, endTimeKey(), dayMillis),
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

    /** Yom Kippur and Tisha B'Av begin at candle-lighting time on the preceding civil day. */
    public static boolean startsPreviousEvening(int index) {
        return index == JewishCalendar.TISHA_BEAV || index == JewishCalendar.YOM_KIPPUR;
    }

    static String startTimeKey(int index) {
        return startsPreviousEvening(index)
                ? ZmanimHelper.KEY_CANDLE_LIGHTING
                : ZmanimHelper.KEY_ALOS;
    }

    static String endTimeKey() {
        return ZmanimHelper.KEY_SHABBAT_END;
    }

    private static long floorRawTime(Context context, String key, long dayMillis) {
        long raw = ZmanimHelper.shabbatTimeForKey(context, key, dayMillis);
        return raw == Long.MAX_VALUE ? raw : ReminderScheduler.floorToMinute(raw);
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
