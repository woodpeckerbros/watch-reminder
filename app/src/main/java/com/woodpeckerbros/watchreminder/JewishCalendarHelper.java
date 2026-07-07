package com.woodpeckerbros.watchreminder;

import android.content.Context;

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter;
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;

import java.util.Calendar;

public class JewishCalendarHelper {
    private JewishCalendarHelper() {
    }

    public static JewishCalendar calendar(Context context, Calendar calendar) {
        JewishCalendar jewishCalendar = new JewishCalendar(calendar);
        jewishCalendar.setInIsrael(isInIsrael(context));
        jewishCalendar.setUseModernHolidays(true);
        return jewishCalendar;
    }

    public static JewishCalendar calendar(Context context, int jewishYear, int jewishMonth, int jewishDay) {
        JewishCalendar jewishCalendar = new JewishCalendar(jewishYear, jewishMonth, jewishDay);
        jewishCalendar.setInIsrael(isInIsrael(context));
        jewishCalendar.setUseModernHolidays(true);
        return jewishCalendar;
    }

    public static boolean isInIsrael(Context context) {
        ZmanimSettings settings = new ZmanimSettings(context);
        double latitude = settings.latitude();
        double longitude = settings.longitude();
        return latitude >= 29.45 && latitude <= 33.35
                && longitude >= 34.15 && longitude <= 35.95;
    }

    public static HebrewDateFormatter formatter(Context context) {
        HebrewDateFormatter formatter = new HebrewDateFormatter();
        formatter.setHebrewFormat(!AppLanguage.isEnglish(context));
        return formatter;
    }
}
