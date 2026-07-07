package com.woodpeckerbros.watchreminder;

import android.content.Context;

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.kosherjava.zmanim.hebrewcalendar.JewishDate;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class MoonBlessingHelper {
    private static final int SEARCH_DAYS = 10;
    private static final long HOUR_MILLIS = 60 * 60_000L;

    private MoonBlessingHelper() {
    }

    public static Window windowFor(Context context, long dateMillis) {
        ZmanimSettings settings = new ZmanimSettings(context);
        TimeZone timeZone = TimeZone.getTimeZone(settings.timeZoneId());
        Calendar localDay = Calendar.getInstance(timeZone);
        localDay.setTimeInMillis(dateMillis);

        JewishCalendar jewishMonth = JewishCalendarHelper.calendar(context, localDay);
        Date baseStart = jewishMonth.getTchilasZmanKidushLevana7Days();
        Date baseEnd = jewishMonth.getSofZmanKidushLevanaBetweenMoldos();
        long startAt = adjustStart(context, timeZone, baseStart.getTime(), jewishMonth);
        long endAt = adjustEnd(context, timeZone, baseEnd.getTime());
        return new Window(
                jewishMonth.getJewishYear(),
                jewishMonth.getJewishMonth(),
                baseStart.getTime(),
                baseEnd.getTime(),
                startAt,
                endAt,
                startAt != ReminderScheduler.floorToMinute(baseStart.getTime()),
                endAt != ReminderScheduler.floorToMinute(baseEnd.getTime())
        );
    }

    public static String monthKey(Window window) {
        return window.jewishYear + ":" + window.jewishMonth;
    }

    public static String monthKey(int jewishYear, int jewishMonth) {
        return jewishYear + ":" + jewishMonth;
    }

    public static boolean isBlockedNightForTimeZone(Context context, TimeZone timeZone, long civilDayMillis) {
        Calendar day = nightCalendar(timeZone, civilDayMillis);
        return isBlockedNight(context, day);
    }

    public static long nextDayMillisForTimeZone(TimeZone timeZone, long civilDayMillis) {
        Calendar day = nightCalendar(timeZone, civilDayMillis);
        return nextDayMillis(day);
    }

    public static boolean isLastNight(Context context, Window window, long civilDayMillis) {
        ZmanimSettings settings = new ZmanimSettings(context);
        TimeZone timeZone = TimeZone.getTimeZone(settings.timeZoneId());
        Calendar day = nightCalendar(timeZone, civilDayMillis);
        long tzeis = ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_TZAIS, day.getTimeInMillis());
        long alos = ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_ALOS, nextDayMillis(day));
        if (tzeis == Long.MAX_VALUE || alos == Long.MAX_VALUE || tzeis > window.endAt) {
            return false;
        }
        Calendar next = (Calendar) day.clone();
        next.add(Calendar.DAY_OF_YEAR, 1);
        for (int i = 0; i < SEARCH_DAYS; i++) {
            long nextTzeis = ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_TZAIS, next.getTimeInMillis());
            long nextAlos = ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_ALOS, nextDayMillis(next));
            if (!isBlockedNight(context, next) && nextTzeis != Long.MAX_VALUE && nextAlos != Long.MAX_VALUE) {
                return nextTzeis > window.endAt;
            }
            next.add(Calendar.DAY_OF_YEAR, 1);
        }
        return true;
    }

    public static long questionTimeForNight(Context context, Window window, long civilDayMillis) {
        ZmanimSettings settings = new ZmanimSettings(context);
        TimeZone timeZone = TimeZone.getTimeZone(settings.timeZoneId());
        Calendar day = nightCalendar(timeZone, civilDayMillis);
        long tzeis = ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_TZAIS, day.getTimeInMillis());
        if (tzeis == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        long nightStart = Math.max(window.startAt, tzeis);
        return ReminderScheduler.ceilToMinute(nightStart + HOUR_MILLIS);
    }

    private static long adjustStart(Context context, TimeZone timeZone, long baseStart, JewishCalendar jewishMonth) {
        if (jewishMonth.getJewishMonth() == JewishDate.AV) {
            baseStart = Math.max(baseStart, afterTishaBav(context, timeZone, jewishMonth.getJewishYear()));
        }
        Calendar day = nightCalendar(timeZone, baseStart);
        for (int i = 0; i < SEARCH_DAYS; i++) {
            long tzeis = ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_TZAIS, day.getTimeInMillis());
            if (!isBlockedNight(context, day) && tzeis != Long.MAX_VALUE) {
                return ReminderScheduler.ceilToMinute(Math.max(baseStart, tzeis));
            }
            day.add(Calendar.DAY_OF_YEAR, 1);
        }
        return ReminderScheduler.ceilToMinute(baseStart);
    }

    private static long afterTishaBav(Context context, TimeZone timeZone, int jewishYear) {
        JewishCalendar av = JewishCalendarHelper.calendar(context, jewishYear, JewishDate.AV, 1);
        for (int i = 0; i < 12; i++) {
            JewishCalendar candidate = JewishCalendarHelper.calendar(context, jewishYear, JewishDate.AV, i + 1);
            if (candidate.isTishaBav()) {
                Calendar gregorian = candidate.getGregorianCalendar();
                gregorian.setTimeZone(timeZone);
                return ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_TZAIS, gregorian.getTimeInMillis());
            }
        }
        Calendar fallback = av.getGregorianCalendar();
        fallback.setTimeZone(timeZone);
        fallback.add(Calendar.DAY_OF_YEAR, 9);
        return ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_TZAIS, fallback.getTimeInMillis());
    }

    private static long adjustEnd(Context context, TimeZone timeZone, long baseEnd) {
        Calendar day = nightCalendar(timeZone, baseEnd);
        long sameDayTzeis = ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_TZAIS, day.getTimeInMillis());
        if (sameDayTzeis == Long.MAX_VALUE || baseEnd < sameDayTzeis) {
            day.add(Calendar.DAY_OF_YEAR, -1);
        }
        for (int i = 0; i < SEARCH_DAYS; i++) {
            long tzeis = ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_TZAIS, day.getTimeInMillis());
            long alos = ZmanimHelper.timeForKey(context, ZmanimHelper.KEY_ALOS, nextDayMillis(day));
            if (!isBlockedNight(context, day) && tzeis != Long.MAX_VALUE && alos != Long.MAX_VALUE) {
                if (baseEnd >= tzeis && baseEnd <= alos) {
                    return ReminderScheduler.floorToMinute(baseEnd);
                }
                return ReminderScheduler.floorToMinute(alos);
            }
            day.add(Calendar.DAY_OF_YEAR, -1);
        }
        return ReminderScheduler.floorToMinute(baseEnd);
    }

    private static Calendar nightCalendar(TimeZone timeZone, long time) {
        Calendar day = Calendar.getInstance(timeZone);
        day.setTimeInMillis(time);
        day.set(Calendar.HOUR_OF_DAY, 12);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        return day;
    }

    private static long nextDayMillis(Calendar day) {
        Calendar next = (Calendar) day.clone();
        next.add(Calendar.DAY_OF_YEAR, 1);
        return next.getTimeInMillis();
    }

    private static boolean isBlockedNight(Context context, Calendar civilDay) {
        return isBlockedDay(jewishDateForNight(context, civilDay));
    }

    private static JewishCalendar jewishDateForNight(Context context, Calendar civilDay) {
        JewishCalendar evening = JewishCalendarHelper.calendar(context, civilDay);
        // The night after the civil date belongs to the next Jewish date.
        // For example, 21/07/2026 after tzeis is already 8 Av, not 7 Av.
        evening.forward(Calendar.DAY_OF_MONTH, 1);
        return evening;
    }

    private static boolean isBlockedDay(JewishCalendar jewishCalendar) {
        return jewishCalendar.getDayOfWeek() == Calendar.SATURDAY
                || jewishCalendar.isYomTovAssurBemelacha()
                || jewishCalendar.isYomKippur()
                || jewishCalendar.isTishaBav();
    }

    public static String monthLabel(int jewishMonth) {
        switch (jewishMonth) {
            case JewishDate.NISSAN:
                return "ניסן";
            case JewishDate.IYAR:
                return "אייר";
            case JewishDate.SIVAN:
                return "סיון";
            case JewishDate.TAMMUZ:
                return "תמוז";
            case JewishDate.AV:
                return "אב";
            case JewishDate.ELUL:
                return "אלול";
            case JewishDate.TISHREI:
                return "תשרי";
            case JewishDate.CHESHVAN:
                return "חשוון";
            case JewishDate.KISLEV:
                return "כסלו";
            case JewishDate.TEVES:
                return "טבת";
            case JewishDate.SHEVAT:
                return "שבט";
            case JewishDate.ADAR:
                return "אדר";
            case JewishDate.ADAR_II:
                return "אדר ב׳";
            default:
                return String.valueOf(jewishMonth);
        }
    }

    public static class Window {
        public final int jewishYear;
        public final int jewishMonth;
        public final long baseStartAt;
        public final long baseEndAt;
        public final long startAt;
        public final long endAt;
        public final boolean startAdjusted;
        public final boolean endAdjusted;

        Window(int jewishYear, int jewishMonth, long baseStartAt, long baseEndAt,
               long startAt, long endAt, boolean startAdjusted, boolean endAdjusted) {
            this.jewishYear = jewishYear;
            this.jewishMonth = jewishMonth;
            this.baseStartAt = baseStartAt;
            this.baseEndAt = baseEndAt;
            this.startAt = startAt;
            this.endAt = endAt;
            this.startAdjusted = startAdjusted;
            this.endAdjusted = endAdjusted;
        }
    }
}
